package org.goldgom.chemicalelements.world.material;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Complete region-image COW storage codec. Integrator must close vanilla RegionFile channels
 * and hold the same physical-region lock for vanilla reads/writes before using this publisher.
 * Compression byte 127 is our immutable external-reference envelope; actual compression byte
 * and compressed bytes are contained in the referenced payload. This class reads legacy inline
 * and legacy fixed-path external chunks too. It never deletes old external files.
 * POWER LOSS is not proven by ATOMIC_MOVE/file.force: directory durability is a separate contract. */
public final class AtomicRegionPublisher {
    private static final int SECTOR = 4096, HEADER = 8192, REFERENCE_COMPRESSION = 127;
    private final Path region;
    private final int regionX, regionZ;
    private Throwable uncertain;
    public enum Point { PAYLOAD_FORCED, IMAGE_FORCED, BEFORE_RENAME, AFTER_RENAME, PUBLISHED_FILE_FORCED }
    @FunctionalInterface public interface FaultProbe { void reached(Point point) throws IOException; }
    public record Publication(String imageSha256, boolean directoryDurabilityConfirmed) {}
    public AtomicRegionPublisher(Path region, int regionX, int regionZ) {
        this.region = region.toAbsolutePath().normalize(); this.regionX = regionX; this.regionZ = regionZ;
    }
    public synchronized byte[] read(int chunkX, int chunkZ) throws IOException {
        int index = index(chunkX, chunkZ);
        if (!Files.exists(region)) return null;
        byte[] image = Files.readAllBytes(region); validateImage(image);
        ByteBuffer header = ByteBuffer.wrap(image);
        int location = header.getInt(index * 4);
        if (location == 0) return null;
        int offset = (location >>> 8) * SECTOR;
        int length = header.getInt(offset);
        int compression = Byte.toUnsignedInt(image[offset + 4]);
        byte[] payload = Arrays.copyOfRange(image, offset + 5, offset + 4 + length);
        if (compression == REFERENCE_COMPRESSION) {
            var reference = ExternalChunkPublication.Reference.decode(payload);
            byte[] encoded = ExternalChunkPublication.read(region.getParent(), reference);
            validateEncoded(encoded); return encoded;
        }
        if ((compression & 128) != 0) {
            if (length != 1) throw new IOException("invalid legacy external stub");
            byte[] compressed = Files.readAllBytes(region.getParent().resolve("c." + chunkX + "." + chunkZ + ".mcc"));
            byte[] encoded = new byte[compressed.length + 1]; encoded[0] = (byte)(compression & 127);
            System.arraycopy(compressed, 0, encoded, 1, compressed.length); validateEncoded(encoded); return encoded;
        }
        byte[] encoded = new byte[payload.length + 1]; encoded[0] = (byte) compression;
        System.arraycopy(payload, 0, encoded, 1, payload.length); validateEncoded(encoded); return encoded;
    }
    /** encoded = compression byte followed by compressed full chunk NBT; null deletes a chunk.
     * Deletion authorization belongs to world lifecycle, never this storage primitive. */
    public synchronized Publication publish(int chunkX, int chunkZ, byte[] encoded, FaultProbe probe) throws IOException {
        if (uncertain != null) throw new IOException("REGION_PUBLICATION_REQUIRES_RECONCILIATION", uncertain);
        Objects.requireNonNull(probe); int index = index(chunkX, chunkZ);
        byte[] immutable = encoded == null ? null : encoded.clone();
        if (immutable != null) validateEncoded(immutable);
        byte[] original = Files.exists(region) ? Files.readAllBytes(region) : new byte[HEADER];
        validateImage(original);
        byte[] record = null;
        if (immutable != null) {
            if ((immutable.length + 4L + SECTOR - 1) / SECTOR >= 256) {
                var reference = ExternalChunkPublication.publish(region.getParent(), immutable);
                probe.reached(Point.PAYLOAD_FORCED);
                byte[] ref = reference.encode();
                record = ByteBuffer.allocate(5 + ref.length).putInt(1 + ref.length)
                        .put((byte) REFERENCE_COMPRESSION).put(ref).array();
            } else record = ByteBuffer.allocate(4 + immutable.length).putInt(immutable.length).put(immutable).array();
        }
        // Rebuild a compact complete image without changing a single byte of the old authority.
        byte[] header = Arrays.copyOf(original, HEADER);
        var body = new ByteArrayOutputStream();
        ByteBuffer oldHeader = ByteBuffer.wrap(original), newHeader = ByteBuffer.wrap(header);
        int sectorOffset = 2;
        for (int slot = 0; slot < 1024; slot++) {
            byte[] current;
            if (slot == index) current = record;
            else {
                int location = oldHeader.getInt(slot * 4);
                if (location == 0) current = null;
                else {
                    int start = (location >>> 8) * SECTOR;
                    current = Arrays.copyOfRange(original, start, start + 4 + oldHeader.getInt(start));
                }
            }
            if (current == null) { newHeader.putInt(slot * 4, 0); continue; }
            int sectors = (current.length + SECTOR - 1) / SECTOR;
            if (sectorOffset > 0xFFFFFF || sectors > 255) throw new IOException("region image capacity exceeded");
            newHeader.putInt(slot * 4, (sectorOffset << 8) | sectors);
            body.write(current); body.write(new byte[sectors * SECTOR - current.length]); sectorOffset += sectors;
        }
        newHeader.putInt(SECTOR + index * 4, (int)Instant.now().getEpochSecond());
        var full = new ByteArrayOutputStream(HEADER + body.size()); full.write(header); body.writeTo(full);
        byte[] next = full.toByteArray(); validateImage(next);
        Path temporary = Files.createTempFile(region.getParent(), ".ce-region-", ".tmp");
        boolean renameStarted = false;
        try {
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ExternalChunkPublication.writeFully(file, ByteBuffer.wrap(next)); file.force(true);
            }
            probe.reached(Point.IMAGE_FORCED); probe.reached(Point.BEFORE_RENAME);
            renameStarted = true;
            Files.move(temporary, region, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            probe.reached(Point.AFTER_RENAME);
            try (FileChannel file = FileChannel.open(region, StandardOpenOption.WRITE)) { file.force(true); }
            probe.reached(Point.PUBLISHED_FILE_FORCED);
            if (!Arrays.equals(next, Files.readAllBytes(region))) throw new IOException("region publication readback mismatch");
            return new Publication(ExternalChunkPublication.digest(next), false);
        } catch (IOException | RuntimeException error) {
            if (renameStarted) uncertain = error;
            throw error;
        }
    }
    /** Read-only proof to aid the coordinator's explicit reconciliation; never clears uncertainty. */
    public synchronized Optional<Throwable> uncertainPublication() { return Optional.ofNullable(uncertain); }
    private int index(int x, int z) {
        if ((x >> 5) != regionX || (z >> 5) != regionZ) throw new IllegalArgumentException("foreign region coordinates");
        return (x & 31) + (z & 31) * 32;
    }
    private static void validateEncoded(byte[] encoded) throws IOException {
        if (encoded.length < 1 || (encoded[0] != 1 && encoded[0] != 2 && encoded[0] != 3 && encoded[0] != 4))
            throw new IOException("unsupported chunk compression");
    }
    private static void validateImage(byte[] bytes) throws IOException {
        if (bytes.length < HEADER || bytes.length % SECTOR != 0) throw new IOException("truncated region image");
        var b = ByteBuffer.wrap(bytes); BitSet allocated = new BitSet(); allocated.set(0, 2);
        for (int i = 0; i < 1024; i++) {
            int location = b.getInt(i * 4); if (location == 0) continue;
            int sector = location >>> 8, count = location & 255;
            if (sector < 2 || count == 0 || ((long)sector + count) * SECTOR > bytes.length)
                throw new IOException("invalid region sector bounds");
            int overlap = allocated.nextSetBit(sector);
            if (overlap >= 0 && overlap < sector + count) throw new IOException("overlapping region sectors");
            allocated.set(sector, sector + count);
            int length = b.getInt(sector * SECTOR);
            if (length < 1 || (long)length + 4 > (long)count * SECTOR)
                throw new IOException("invalid region record size");
        }
    }
}

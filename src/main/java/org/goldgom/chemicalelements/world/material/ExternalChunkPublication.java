package org.goldgom.chemicalelements.world.material;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Storage-format helper, NOT a material journal. The region COW image holds this reference;
 * old region images keep naming their immutable old payload, including external->external.
 * Must be paired with the matching reader. Never replace vanilla c.x.z.mcc in place. */
public final class ExternalChunkPublication {
    private static final int MAGIC = 0x43454d31; // CEM1
    public static final int REFERENCE_SIZE = 4 + 8 + 32;
    private ExternalChunkPublication() {}
    public record Reference(long length, String sha256) {
        public Reference {
            if (length < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("external reference");
        }
        public byte[] encode() {
            return ByteBuffer.allocate(REFERENCE_SIZE).putInt(MAGIC).putLong(length)
                    .put(HexFormat.of().parseHex(sha256)).array();
        }
        public static Reference decode(byte[] bytes) throws IOException {
            if (bytes.length != REFERENCE_SIZE) throw new IOException("invalid external reference length");
            var b = ByteBuffer.wrap(bytes);
            if (b.getInt() != MAGIC) throw new IOException("invalid external reference magic");
            long length = b.getLong(); byte[] hash = new byte[32]; b.get(hash);
            try { return new Reference(length, HexFormat.of().formatHex(hash)); }
            catch (IllegalArgumentException error) { throw new IOException("invalid external reference", error); }
        }
    }
    public static Reference publish(Path regionDirectory, byte[] compressedPayload) throws IOException {
        byte[] immutable = compressedPayload.clone();
        Reference reference = new Reference(immutable.length, digest(immutable));
        Path destination = path(regionDirectory, reference);
        if (Files.exists(destination)) { read(regionDirectory, reference); return reference; }
        Path temporary = Files.createTempFile(regionDirectory, ".ce-external-", ".tmp");
        // Keep incomplete temporary files for diagnosis. They are never candidates for reads.
        try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            writeFully(file, ByteBuffer.wrap(immutable)); file.force(true);
        }
        try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch (FileAlreadyExistsException concurrent) { read(regionDirectory, reference); }
        // No fallback to copy/non-atomic replace, and no claim that a file force flushes directory entries.
        read(regionDirectory, reference);
        return reference;
    }
    public static byte[] read(Path directory, Reference reference) throws IOException {
        Path file = path(directory, reference);
        if (Files.size(file) != reference.length()) throw new IOException("external payload length mismatch");
        byte[] result = Files.readAllBytes(file);
        if (!digest(result).equals(reference.sha256())) throw new IOException("external payload digest mismatch");
        return result;
    }
    static Path path(Path directory, Reference reference) {
        return directory.resolve("ce." + reference.sha256() + ".mcc");
    }
    static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.write(buffer) <= 0) throw new IOException("nonprogressing file write");
    }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}

package org.goldgom.chemicalelements.world.material;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

/** Shared path lock across storage instances. No channel remains open across Windows rename.
 * Codec paired with AtomicRegionPublisher, including immutable external-reference reads. */
public final class WorldRegionStorage {
    private static final Map<Path, WorldRegionStorage> REGIONS = new HashMap<>();
    private final AtomicRegionPublisher publisher;
    private WorldRegionStorage(Path file, int x, int z) { publisher = new AtomicRegionPublisher(file, x, z); }
    public static synchronized WorldRegionStorage at(Path directory, ChunkPos pos) throws IOException {
        Files.createDirectories(directory);
        Path folder = directory.toRealPath();
        Path file = folder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
        return REGIONS.computeIfAbsent(file, p -> new WorldRegionStorage(p, pos.getRegionX(), pos.getRegionZ()));
    }
    public synchronized CompoundTag read(ChunkPos pos) throws IOException {
        byte[] bytes = publisher.read(pos.x, pos.z);
        if (bytes == null) return null;
        try (var in = input(bytes)) { return NbtIo.read(in); }
    }
    public synchronized void scan(ChunkPos pos, StreamTagVisitor visitor) throws IOException {
        byte[] bytes = publisher.read(pos.x, pos.z);
        if (bytes == null) return;
        try (var in = input(bytes)) { NbtIo.parse(in, visitor, NbtAccounter.unlimitedHeap()); }
    }
    public synchronized AtomicRegionPublisher.Publication write(ChunkPos pos, CompoundTag tag) throws IOException {
        byte[] bytes = null;
        if (tag != null) {
            var output = new ByteArrayOutputStream();
            RegionFileVersion version = RegionFileVersion.getSelected();
            output.write(version.getId());
            try (var stream = new DataOutputStream(version.wrap(output))) { NbtIo.write(tag, stream); }
            bytes = output.toByteArray();
        }
        return publisher.publish(pos.x, pos.z, bytes, point -> {});
    }
    public synchronized void requireHealthy() throws IOException {
        if (publisher.uncertainPublication().isPresent())
            throw new IOException("region publication unresolved", publisher.uncertainPublication().orElseThrow());
    }
    private static DataInputStream input(byte[] bytes) throws IOException {
        var raw = new ByteArrayInputStream(bytes); int id = raw.read();
        RegionFileVersion version = RegionFileVersion.fromId(id);
        if (version == null || id == 127) throw new IOException("invalid stored compression " + id);
        return new DataInputStream(version.wrap(raw));
    }
}

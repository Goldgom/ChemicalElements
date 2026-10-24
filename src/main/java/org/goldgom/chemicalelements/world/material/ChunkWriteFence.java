package org.goldgom.chemicalelements.world.material;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Shared registry between actual serializer and ChunkStorage entry. No NBT self-asserted stamp.
 * Registry is in-process metadata only. It must be retired with a quiescent chunk lifecycle. */
public final class ChunkWriteFence {
    private record Encoding(ChunkWriteSequencer lane, long generation, ChunkPos position) {}
    private static final IdentityHashMap<ChunkAccess, ChunkWriteSequencer> LANES = new IdentityHashMap<>();
    private static final IdentityHashMap<ChunkAccess, Encoding> ENCODING = new IdentityHashMap<>();
    private static final IdentityHashMap<CompoundTag, Encoding> IMAGES = new IdentityHashMap<>();
    private static final IdentityHashMap<ChunkWriteSequencer, Object> STORAGE = new IdentityHashMap<>();
    private ChunkWriteFence() {}
    public static synchronized void begin(ChunkAccess chunk) {
        var lane = LANES.computeIfAbsent(chunk, ignored -> new ChunkWriteSequencer());
        if (ENCODING.containsKey(chunk)) throw new IllegalStateException("CHUNK_ENCODING_REENTRANT_OR_FAILED");
        ENCODING.put(chunk, new Encoding(lane, lane.beginEncoding(), chunk.getPos()));
    }
    public static synchronized void encoded(ChunkAccess chunk, CompoundTag tag) {
        var encoding = ENCODING.remove(chunk);
        if (encoding == null) throw new IllegalStateException("CHUNK_ENCODING_NOT_STARTED");
        encoding.lane().register(tag, encoding.generation()); IMAGES.put(tag, encoding);
    }
    public static synchronized void mutated(ChunkAccess chunk) {
        var lane = LANES.get(chunk); if (lane != null) lane.advance();
    }
    public static synchronized CompletableFuture<Void> write(Object storage, ChunkPos pos, CompoundTag tag,
            Function<CompoundTag, CompletableFuture<Void>> writer) {
        Encoding encoding = IMAGES.remove(tag);
        if (encoding == null || !encoding.position().equals(pos))
            return CompletableFuture.failedFuture(new IllegalStateException("UNVERIFIED_CHUNK_STORAGE_IMAGE"));
        Object previous = STORAGE.putIfAbsent(encoding.lane(), storage);
        if (previous != null && previous != storage)
            return CompletableFuture.failedFuture(new IllegalStateException("FOREIGN_CHUNK_STORAGE"));
        return encoding.lane().submit(tag, writer);
    }
}

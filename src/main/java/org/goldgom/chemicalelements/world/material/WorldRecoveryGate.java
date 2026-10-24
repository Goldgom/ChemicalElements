package org.goldgom.chemicalelements.world.material;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;

/** In-memory gate only, never a second durable journal. B's verified startup index is the
 * sole source for opening a server gate. There is intentionally no public markReady method. */
public final class WorldRecoveryGate {
    public record ChunkKey(ResourceLocation dimension, long chunkPosition) {
        public ChunkKey { Objects.requireNonNull(dimension); }
    }
    private final Map<ChunkKey, CompletableFuture<Void>> chunks = new HashMap<>();
    private boolean indexReady;
    private Set<ChunkKey> indexedPending = Set.of();
    public synchronized boolean isIndexReady() { return indexReady; }
    public synchronized CompletableFuture<Void> publicAccess(ChunkKey key) {
        // Return dependent stage: callers cannot complete/cancel the authoritative gate future.
        var gate = chunks.computeIfAbsent(key, ignored -> new CompletableFuture<>());
        if (indexReady && !indexedPending.contains(key)) gate.complete(null);
        return gate.thenApply(ignored -> null);
    }
    public synchronized boolean isClosed(ChunkKey key) {
        if (!indexReady || indexedPending.contains(key)) return true;
        var future = chunks.get(key);
        return future != null && !future.isDone();
    }
    public synchronized void requirePublic(ChunkKey key) {
        if (isClosed(key)) throw new IllegalStateException("WORLD_RECOVERY_GATE_CLOSED");
    }
    // Deliberately package-private and currently without a production caller. The world/B bridge
    // must verify actual SinkLedger index authority before invoking these, not accept caller booleans.
    synchronized void installVerifiedIndex(Set<ChunkKey> pending) {
        if (indexReady) throw new IllegalStateException("WORLD_INDEX_ALREADY_INSTALLED");
        indexedPending = Set.copyOf(pending); indexReady = true;
        chunks.forEach((key, gate) -> { if (!indexedPending.contains(key)) gate.complete(null); });
    }
    synchronized void reconciled(ChunkKey key) {
        if (!indexReady) throw new IllegalStateException("WORLD_INDEX_NOT_READY");
        var next = new HashSet<>(indexedPending); next.remove(key); indexedPending = Set.copyOf(next);
        chunks.computeIfAbsent(key, ignored -> new CompletableFuture<>()).complete(null);
    }
}

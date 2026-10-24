package org.goldgom.chemicalelements.world.material;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Per physical chunk write lane. Provenance is object identity, not a caller-supplied NBT field.
 * No next image reaches the writer until the previous exact future terminates. An uncertain or
 * failed write poisons the lane; recovery must explicitly reconcile it, never silently retry. */
public final class ChunkWriteSequencer {
    public record Stamp(long generation, CompoundTag image) {
        public Stamp { image = image.copy(); }
        @Override public CompoundTag image() { return image.copy(); }
    }
    private long generation;
    private long durableGeneration = -1;
    private final IdentityHashMap<CompoundTag, Stamp> provenance = new IdentityHashMap<>();
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private Throwable failure;
    public synchronized long beginEncoding() {
        requireHealthy(); return generation;
    }
    public synchronized long advance() {
        requireHealthy();
        if (!tail.isDone()) throw new IllegalStateException("CHUNK_WRITE_IN_FLIGHT");
        generation = Math.incrementExact(generation); return generation;
    }
    public synchronized void register(CompoundTag image, long startedAt) {
        requireHealthy();
        if (startedAt != generation) throw new IllegalStateException("STALE_CHUNK_ENCODING");
        if (provenance.putIfAbsent(image, new Stamp(startedAt, image)) != null)
            throw new IllegalStateException("DUPLICATE_CHUNK_PROVENANCE");
    }
    public synchronized CompletableFuture<Void> submit(CompoundTag tag,
            java.util.function.Function<CompoundTag, CompletableFuture<Void>> exactWriter) {
        requireHealthy();
        Stamp stamp = provenance.remove(tag);
        if (stamp == null || stamp.generation() != generation || !stamp.image().equals(tag))
            return CompletableFuture.failedFuture(new IllegalStateException("STALE_OR_UNVERIFIED_CHUNK_IMAGE"));
        // Even a synchronous completion is safe: this monitor is reentrant and no server loop is pumped.
        CompletableFuture<Void> exact = tail.thenCompose(ignored -> {
            synchronized (this) {
                requireHealthy();
                if (stamp.generation() != generation) throw new IllegalStateException("STALE_QUEUED_CHUNK_IMAGE");
            }
            return Objects.requireNonNull(exactWriter.apply(stamp.image()), "exact write future");
        });
        tail = exact.whenComplete((ignored, thrown) -> {
            if (thrown != null) synchronized (this) { failure = unwrap(thrown); }
        });
        return tail;
    }
    /** Flush must be done after joining the exact write, not substituted for it. */
    public void force(CompletableFuture<Void> exact, Runnable flush) {
        Objects.requireNonNull(exact).join();
        synchronized (this) {
            requireHealthy();
            if (exact != tail) throw new IllegalStateException("NOT_LATEST_EXACT_CHUNK_WRITE");
            try { flush.run(); durableGeneration = generation; }
            catch (RuntimeException | Error error) { failure = error; throw error; }
        }
    }
    public synchronized long durableGeneration() { return durableGeneration; }
    public synchronized Optional<Throwable> failure() { return Optional.ofNullable(failure); }
    private void requireHealthy() {
        if (failure != null) throw new IllegalStateException("CHUNK_WRITE_REQUIRES_RECONCILIATION", failure);
    }
    private static Throwable unwrap(Throwable e) {
        while ((e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null) e = e.getCause();
        return e;
    }
}

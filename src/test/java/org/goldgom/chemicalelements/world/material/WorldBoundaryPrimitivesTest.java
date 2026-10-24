package org.goldgom.chemicalelements.world.material;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier;
import static org.junit.jupiter.api.Assertions.*;

/** Pure fixtures; not real world integration evidence. */
class WorldBoundaryPrimitivesTest {
    @Test void equalAliasesStayBoundToTheirOriginalIdentity() {
        Object root = new Object();
        var alias = new String("same");
        var unrelated = new String("same");
        var child = new Object();
        WorldChunkBoundary.bind(alias, root);
        WorldChunkBoundary.bindChild(child, alias);
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "c".repeat(64), List.of(root));
        try {
            assertThrows(IllegalStateException.class, () -> WorldChunkBoundary.requireObservable(alias));
            assertThrows(IllegalStateException.class, () -> WorldChunkBoundary.requireObservable(child));
            assertDoesNotThrow(() -> WorldChunkBoundary.requireObservable(unrelated));
        } finally { lease.releaseUnprojected(); }
        assertDoesNotThrow(() -> WorldChunkBoundary.requireObservable(alias));
    }
    @Test void retainedMapAliasesRecheckEveryOperation() {
        Object root = new Object();
        var map = new GuardedBlockEntityMap<String, String>(root, Map.of("a", "b"));
        var keys = map.keySet(); var values = map.values(); var entries = map.entrySet();
        var iterator = entries.iterator(); var entry = iterator.next();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "a".repeat(64), List.of(root));
        try {
            assertThrows(IllegalStateException.class, () -> map.get("a"));
            assertThrows(IllegalStateException.class, () -> keys.contains("a"));
            assertThrows(IllegalStateException.class, values::clear);
            assertThrows(IllegalStateException.class, () -> entry.setValue("c"));
            assertThrows(IllegalStateException.class, entry::getValue);
            assertThrows(IllegalStateException.class, iterator::remove);
            assertThrows(IllegalStateException.class, () -> map.compute("a", (a, b) -> "c"));
        } finally { lease.releaseUnprojected(); }
        assertEquals("b", map.get("a"));
    }
    @Test void delayedOldEncodingNeverReachesWriter() {
        var lane = new ChunkWriteSequencer(); var old = new CompoundTag();
        long generation = lane.beginEncoding(); lane.register(old, generation); lane.advance();
        var future = lane.submit(old, tag -> { fail("stale image reached writer"); return null; });
        assertThrows(CompletionException.class, future::join);
    }
    @Test void mutationAfterRegistrationIsRejected() {
        var lane = new ChunkWriteSequencer(); var image = new CompoundTag();
        lane.register(image, lane.beginEncoding()); image.putInt("changed", 1);
        assertThrows(CompletionException.class, () -> lane.submit(image, tag -> {
            fail("mutated image reached writer"); return null;
        }).join());
    }
    @Test void exactWriteFailurePoisonsLaneWithOriginalCause() {
        var lane = new ChunkWriteSequencer(); var image = new CompoundTag();
        lane.register(image, lane.beginEncoding());
        var cause = new IllegalStateException("disk full");
        var future = lane.submit(image, tag -> CompletableFuture.failedFuture(cause));
        assertThrows(CompletionException.class, future::join);
        assertSame(cause, lane.failure().orElseThrow());
        assertThrows(IllegalStateException.class, lane::advance);
    }
    @Test void flushFailureNeverAdvancesDurability() {
        var lane = new ChunkWriteSequencer(); var image = new CompoundTag();
        lane.register(image, lane.beginEncoding());
        var exact = lane.submit(image, tag -> CompletableFuture.completedFuture(null));
        assertThrows(IllegalStateException.class, () -> lane.force(exact, () -> { throw new IllegalStateException("force failed"); }));
        assertEquals(-1, lane.durableGeneration());
    }
}

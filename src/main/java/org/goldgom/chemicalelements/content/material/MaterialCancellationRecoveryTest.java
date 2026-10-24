package org.goldgom.chemicalelements.content.material;

import java.util.*;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.content.material.tx.MaterialRootState;
import org.goldgom.chemicalelements.instance.TransactionEncoding;
import org.goldgom.chemicalelements.instance.materialtx.*;

/** Registered regression GameTest. NBT codec != disk durability.
 * Real loaded BE + authoritative server journal assertions are in MaterialRootGameTests.
 */
@net.neoforged.neoforge.gametest.GameTestHolder("chemicalelements")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class MaterialCancellationRecoveryTest {
    @net.minecraft.gametest.framework.GameTest(template="core_empty")
    public static void cancellationAndLease(net.minecraft.gametest.framework.GameTestHelper helper) throws Exception {
        main(new String[0]);
        helper.succeed();
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; }
        throw new AssertionError("expected rejection");
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var image = new MaterialProjection(net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3,
                new StackLedger(StackLedger.CURRENT_SCHEMA, 0, List.of()), Optional.empty());
        var after = new MaterialProjection(image.state(), 4, image.ledger(), image.finiteHeat());
        var root = new MaterialRootState(UUID.randomUUID(), image, Optional.empty(), Optional.empty());
        var op = UUID.randomUUID(); var hash = "a".repeat(64);
        var marker = new MaterialPendingMarker(op, hash, root.incarnation(), 3, 4,
                TransactionEncoding.sha256(image.canonical()), TransactionEncoding.sha256(after.canonical()),
                MaterialPendingMarker.Phase.RESERVED);
        var pending = MaterialRootState.decode(root.isolate(marker).encode());
        rejects(() -> pending.cancel(UUID.randomUUID(), hash));
        rejects(() -> pending.cancel(op, "b".repeat(64)));
        var cancelled = MaterialRootState.decode(pending.cancel(op, hash).encode());
        check(cancelled.equals(root), "cancel preserves complete business root, no fabricated commit marker");
        check(cancelled.cancel(op, hash).equals(cancelled), "repeated cancel is a no-op");
        check(cancelled.last().isEmpty(), "cancel must not become committed lineage");
        var projected = pending.project(after, marker.projected());
        rejects(() -> projected.cancel(op, hash));
        rejects(() -> projected.complete(op, hash).cancel(op, hash));
        var object = new Object();
        var lease = MaterialObservationBarrier.reserve(op, hash, List.of(object));
        try {
            check(MaterialObservationBarrier.isReservedFor(object, op, hash), "exact lease");
            check(!MaterialObservationBarrier.isReservedFor(new Object(), op, hash), "root identity");
            check(!MaterialObservationBarrier.isReservedFor(object, UUID.randomUUID(), hash), "operation identity");
            check(!MaterialObservationBarrier.isReservedFor(object, op, "b".repeat(64)), "hash identity");
            var result = new java.util.concurrent.atomic.AtomicReference<Boolean>();
            var worker = new Thread(() -> result.set(MaterialObservationBarrier.isReservedFor(object, op, hash)));
            worker.start(); worker.join(2000);
            check(Boolean.FALSE.equals(result.get()), "foreign thread cannot inherit proof");
        } finally { lease.releaseUnprojected(); }
        check(!MaterialObservationBarrier.isReservedFor(object, op, hash), "release invalidates proof");
        System.out.println("Cancellation root codec and lease checks passed; no world persistence tested");
    }
}

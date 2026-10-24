package org.goldgom.chemicalelements.gametest.material;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.content.material.*;
import org.goldgom.chemicalelements.content.material.tx.MaterialRootState;
import org.goldgom.chemicalelements.instance.materialtx.*;
import org.goldgom.chemicalelements.registry.ElementMaterials;

/** Source-only acceptance cases. No claim of runtime execution or durable world commit. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialRootGameTests {
    private static MaterialBlockEntity install(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1,2,1));
        h.getLevel().setBlock(pos, ElementMaterials.CONTENT.get("elemental_iron").get().defaultBlockState(), 3);
        return (MaterialBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalStateException | IllegalArgumentException expected) { return; }
        throw new AssertionError("expected rejection");
    }
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void completeOriginalFifoCodec(GameTestHelper h) {
        var bundle = org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle();
        var stack = new net.minecraft.world.item.ItemStack(org.goldgom.chemicalelements.registry.ElementItems.ELEMENTS.get("iron").get(), 2);
        var ledger = org.goldgom.chemicalelements.instance.InstanceSampler.mint(stack, 2,
                new org.goldgom.chemicalelements.api.instance.CreationContext(
                        new org.goldgom.chemicalelements.api.instance.Provenance("gametest", "material-owner-r24", 24L,
                                bundle.epoch(), Map.of()), Optional.empty(), false));
        var image = new MaterialProjection(ElementMaterials.CONTENT.get("elemental_iron").get().defaultBlockState(),
                7, ledger, Optional.empty());
        var root = new MaterialRootState(UUID.randomUUID(), image, Optional.empty(), Optional.empty());
        h.assertTrue(MaterialRootState.decode(root.encode()).equals(root), "nonempty UUID/FIFO/thermal/nuclides/pore/adsorbed roundtrip");
        h.assertTrue(MaterialProjection.decode(image.canonical()).ledger().instances().equals(ledger.instances()), "original order unchanged");
        var unit = stack.copy(); unit.setCount(1);
        unit.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Original material"));
        var one = new org.goldgom.chemicalelements.api.instance.StackLedger(ledger.schemaVersion(), ledger.revision(), List.of(ledger.instances().getFirst()));
        org.goldgom.chemicalelements.instance.StackLedgers.set(unit, one);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, h.getLevel().registryAccess());
        var encoded = org.goldgom.chemicalelements.instance.TransactionEncoding.canonical(net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, unit).getOrThrow());
        var withShell = new MaterialProjection(image.state(), 8, one, Optional.empty(), Optional.of(encoded));
        var roundtrip = MaterialProjection.decode(withShell.canonical()); roundtrip.validateUnit(h.getLevel().registryAccess());
        h.assertTrue(roundtrip.unitStack().orElseThrow().equals(encoded), "custom name and all components preserved");
        var wrong = new org.goldgom.chemicalelements.api.instance.StackLedger(one.schemaVersion(), one.revision()+1, one.instances());
        rejects(() -> new MaterialProjection(image.state(), 8, wrong, Optional.empty(), Optional.of(encoded)).validateUnit(h.getLevel().registryAccess()));
        h.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realBeNbtAndReplacementAba(GameTestHelper h) {
        var be = install(h); var id = be.persistentIncarnation(); var gen = be.liveGeneration();
        var nbt = be.saveWithoutMetadata(h.getLevel().registryAccess());
        var copy = new MaterialBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(nbt, h.getLevel().registryAccess());
        h.assertTrue(copy.persistentIncarnation().equals(id), "incarnation persisted");
        h.assertTrue(!copy.liveGeneration().equals(gen), "live load generation rotated");
        h.assertTrue(copy.ledger().equals(be.ledger()), "full original FIFO roundtrip");
        h.getLevel().setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        var replacement = install(h);
        h.assertTrue(!replacement.persistentIncarnation().equals(id), "replacement not same persistent owner");
        h.assertTrue(!be.materialAccess().isLoaded(), "old root unavailable");
        h.succeed();
    }
    /** Real BE + authoritative test-server journal. prepare/abort below are deliberately
     * NON-DURABLE test writes: this exercises classification, never disk crash recovery.
     * Integration must run only in its disposable GameTest world. */
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void cancellationRecoveryEvidence(GameTestHelper h) {
        var be = install(h); var a = be.materialAccess(); var before = a.snapshot();
        var business = MaterialProjection.decode(before.canonicalRoot());
        var image = new MaterialProjection(business.state(), before.revision()+1,
                business.ledger(), business.finiteHeat(), business.unitStack());
        var after = MaterialOwnerSnapshot.of(a.owner(), before.liveGeneration(), image.revision(), image.canonical());
        var tx = new MaterialTransaction(UUID.randomUUID(), MaterialTransaction.Action.PHASE, 0,
                "a".repeat(64), "classification-test-only", List.of(new MaterialTransaction.Change(before, after)));
        var op = tx.operation(); var hash = tx.hash();
        var payload = new net.minecraft.nbt.CompoundTag();
        payload.putString("kind", MaterialJournal.KIND); payload.putString("payload", tx.encode());
        payload.putString("payload_sha256", hash);
        var journal = org.goldgom.chemicalelements.persistence.SinkLedger.get();
        journal.prepare(op, payload); // NOT prepareDurably; no durability assertion
        var original = be.saveWithoutMetadata(h.getLevel().registryAccess());
        be.loadWithComponents(original, h.getLevel().registryAccess());
        h.assertTrue(!be.liveGeneration().equals(before.liveGeneration()), "historical generation");
        h.assertTrue(a.classify(op, hash, before, after, true)==MaterialOwnerAccess.Position.CONFLICT,
                "marker-free recovery requires lease even with journal");
        var lease = MaterialObservationBarrier.reserve(op, hash, List.of(be));
        try {
            h.assertTrue(a.classify(op, hash, before, after, true)==MaterialOwnerAccess.Position.BEFORE,
                    "PREPARE before first isolate is recoverable with exact journal and root lease");
            h.assertTrue(a.classify(op, hash, before, after, false)==MaterialOwnerAccess.Position.CONFLICT,
                    "ordinary path does not accept historical generation");
            h.assertTrue(!MaterialRecoveryEvidence.matches(UUID.randomUUID(), hash, before, after, be), "wrong op");
            h.assertTrue(!MaterialRecoveryEvidence.matches(op, "b".repeat(64), before, after, be), "wrong hash");
            h.assertTrue(!MaterialRecoveryEvidence.matches(op, hash, before, after, new Object()), "wrong original root");
            var otherAfter = MaterialOwnerSnapshot.of(after.owner(), UUID.randomUUID(), after.revision(), after.canonicalRoot());
            h.assertTrue(!MaterialRecoveryEvidence.matches(op, hash, before, otherAfter, be), "full change, not hash-only binding");
            var wrongImage = new MaterialProjection(image.state(), image.revision(),
                    new org.goldgom.chemicalelements.api.instance.StackLedger(business.ledger().schemaVersion(),
                            business.ledger().revision()+1, business.ledger().instances()), image.finiteHeat(), image.unitStack());
            var wrongBefore = MaterialOwnerSnapshot.of(before.owner(), before.liveGeneration(), wrongImage.revision(), wrongImage.canonical());
            h.assertTrue(a.classify(op, hash, wrongBefore, after, true)==MaterialOwnerAccess.Position.CONFLICT,
                    "different complete business root rejected");
            var offThread = new java.util.concurrent.atomic.AtomicReference<Boolean>();
            var worker = new Thread(() -> offThread.set(MaterialRecoveryEvidence.matches(op, hash, before, after, be)));
            worker.start();
            try { worker.join(2000); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
            }
            h.assertTrue(Boolean.FALSE.equals(offThread.get()), "non-server thread rejected");
            var marker = new MaterialPendingMarker(op, hash, be.persistentIncarnation(), before.revision(), after.revision(),
                    before.rootHash(), after.rootHash(), MaterialPendingMarker.Phase.RESERVED);
            a.isolate(marker);
            journal.abort(op); // model coordinator ordering; still NOT abortDurably
            a.cancelIsolation(op, hash);
            h.assertTrue(a.classify(op, hash, before, after, true)==MaterialOwnerAccess.Position.BEFORE, "CANCEL_AFTER lineage");
            a.cancelIsolation(op, hash);
            rejects(a::persist); // missing world durability remains an explicit failure
            var cancelled = be.saveWithoutMetadata(h.getLevel().registryAccess());
            var cancelledRoot = MaterialRootState.decode(cancelled.getCompound("material_root"));
            h.assertTrue(cancelledRoot.pending().isEmpty() && cancelledRoot.last().isEmpty(), "cancel is not commit");
            h.assertTrue(cancelledRoot.image().canonical().equals(before.canonicalRoot()), "metadata excluded from business hash");
        } finally { lease.releaseUnprojected(); } // test-only lease; never marked projectionStarted
        h.assertTrue(!MaterialRecoveryEvidence.matches(op, hash, before, after, be), "released lease is not durable evidence");
        be.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(a.classify(op, hash, before, after, true)==MaterialOwnerAccess.Position.CONFLICT, "reload needs new reservation");
        var rebuilt = MaterialObservationBarrier.reserve(op, hash, List.of(be));
        try {
            h.assertTrue(a.classify(op, hash, before, after, true)==MaterialOwnerAccess.Position.BEFORE,
                    "new original-root lease plus CANCELLED journal rebuilds proof");
        } finally { rebuilt.releaseUnprojected(); }
        // Loading an AFTER business image without lineage must not gain authorization from journal.
        var markerFreeAfter = new MaterialRootState(be.persistentIncarnation(), image, Optional.empty(), Optional.empty());
        var nbt = be.saveWithoutMetadata(h.getLevel().registryAccess()); nbt.put("material_root", markerFreeAfter.encode());
        be.loadWithComponents(nbt, h.getLevel().registryAccess());
        var afterLease = MaterialObservationBarrier.reserve(op, hash, List.of(be));
        try {
            h.assertTrue(a.classify(op, hash, before, after, true)==MaterialOwnerAccess.Position.CONFLICT, "no marker AFTER rejected");
        } finally { afterLease.releaseUnprojected(); }
        h.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void pendingNbtRecoveryAndWrongHash(GameTestHelper h) {
        var be = install(h); var a = be.materialAccess(); var before = a.snapshot();
        var business = MaterialProjection.decode(before.canonicalRoot());
        var image = new MaterialProjection(business.state(), before.revision()+1, business.ledger(), business.finiteHeat());
        var after = MaterialOwnerSnapshot.of(a.owner(), before.liveGeneration(), image.revision(), image.canonical());
        var op = UUID.randomUUID(); var hash = "a".repeat(64);
        var marker = new MaterialPendingMarker(op, hash, be.persistentIncarnation(), before.revision(), after.revision(),
                before.rootHash(), after.rootHash(), MaterialPendingMarker.Phase.RESERVED);
        a.isolate(marker);
        rejects(be::ledger);
        h.assertTrue(a.classify(op,"b".repeat(64),before,after,false)==MaterialOwnerAccess.Position.CONFLICT,"wrong hash isolated");
        var nbt = be.saveWithoutMetadata(h.getLevel().registryAccess());
        var recovered = MaterialRootState.decode(nbt.getCompound("material_root"));
        h.assertTrue(recovered.pending().orElseThrow().equals(marker), "reserved survives NBT");
        var projected = recovered.project(image, marker.projected());
        var restored = MaterialRootState.decode(projected.encode());
        rejects(() -> restored.cancel(op,hash));
        var completed = restored.complete(op,hash);
        h.assertTrue(completed.complete(op,hash).equals(completed), "completion duplicate idempotent");
        rejects(() -> completed.cancel(op,hash));
        var cancelled = recovered.cancel(op,hash);
        h.assertTrue(cancelled.cancel(op,hash).equals(cancelled), "cancel duplicate idempotent");
        var bad = projected.encode(); var pending = bad.getCompound("pending"); pending.putString("after_hash","0".repeat(64));
        rejects(() -> MaterialRootState.decode(bad));
        a.cancelIsolation(op,hash);
        h.assertTrue(be.ledger().equals(business.ledger()), "cancel preserved complete FIFO");
        rejects(a::persist); // explicit missing sync durability, not a successful persistence test
        h.succeed();
    }
}

package org.goldgom.chemicalelements.gametest.material;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.content.material.*;
import org.goldgom.chemicalelements.content.material.tx.MaterialRootState;
import org.goldgom.chemicalelements.instance.*;
import org.goldgom.chemicalelements.instance.materialtx.*;
import org.goldgom.chemicalelements.registry.*;
import org.goldgom.chemicalelements.world.material.*;

/** Real loaded-root tests. Reflective permits are leaf fixtures ONLY: no durable PREPARE,
 * public placement, disk transaction, or startup recovery is claimed by this suite. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialWorldBridgeGameTests {
    private static final String HASH = "d".repeat(64);
    private static MaterialBlockEntity install(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos, ElementMaterials.CONTENT.get("elemental_iron").get().defaultBlockState(), 3);
        return (MaterialBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static WorldPrimitivePermit fixture(OwnerKey.WorldBlock owner, UUID op, Object original,
            Object target, WorldPrimitivePermit.Primitive primitive) {
        try {
            var constructor = WorldPrimitivePermit.class.getDeclaredConstructor(OwnerKey.WorldBlock.class,
                    UUID.class, String.class, Object.class, Object.class, WorldPrimitivePermit.Primitive.class);
            constructor.setAccessible(true);
            return constructor.newInstance(owner, op, HASH, original, target, primitive);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("expected exact world capability rejection");
    }
    private static MaterialPendingMarker marker(OwnerKey.WorldBlock owner, UUID op,
            MaterialProjection before, MaterialProjection after) {
        return new MaterialPendingMarker(op, HASH, owner.incarnation(), before.revision(), after.revision(),
                TransactionEncoding.sha256(before.canonical()), TransactionEncoding.sha256(after.canonical()),
                MaterialPendingMarker.Phase.RESERVED);
    }
    private static MaterialProjection next(MaterialProjection before) {
        return new MaterialProjection(before.state(), before.revision() + 1, before.ledger(), before.finiteHeat(), before.unitStack());
    }
    private static MaterialRootState read(MaterialBlockEntity be, OwnerKey.WorldBlock owner, UUID op) {
        return be.worldReadRoot(fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.READ_ROOT), owner, op, HASH);
    }
    private static void replace(MaterialBlockEntity be, OwnerKey.WorldBlock owner, UUID op, MaterialRootState root) {
        be.worldReplaceRoot(fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.REPLACE_ROOT), owner, op, HASH, root);
    }
    private static MaterialProjection originalUnit(GameTestHelper h, MaterialBlockEntity be) {
        var bundle = org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle();
        var unit = new ItemStack(ElementMaterials.BLOCK_ITEMS.get("elemental_iron").get());
        unit.set(DataComponents.CUSTOM_NAME, Component.literal("Original bridge specimen"));
        var ledger = InstanceSampler.mint(unit, 1, new CreationContext(new Provenance("gametest", "world-root-bridge",
                25L, bundle.epoch(), Map.of()), Optional.empty(), false));
        StackLedgers.set(unit, ledger);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, h.getLevel().registryAccess());
        var shell = TransactionEncoding.canonical(ItemStack.CODEC.encodeStart(ops, unit).getOrThrow());
        return new MaterialProjection(be.getBlockState(), 0, ledger, Optional.empty(), Optional.of(shell));
    }

    @GameTest(template = "core_empty", timeoutTicks = 100)
    public static void originalRootPublicationKeepsComponentsAndLineage(GameTestHelper h) {
        var be = install(h);
        var original = new MaterialRootState(be.persistentIncarnation(), originalUnit(h, be), Optional.empty(), Optional.empty());
        var tag = be.saveWithoutMetadata(h.getLevel().registryAccess()); tag.put("material_root", original.encode());
        be.loadWithComponents(tag, h.getLevel().registryAccess());
        var access = be.materialAccess(); var before = access.snapshot();
        var owner = (OwnerKey.WorldBlock) access.owner(); var op = UUID.randomUUID();
        var chunk = h.getLevel().getChunkAt(be.getBlockPos());
        var map = chunk.getBlockEntities(); var section = chunk.getSection(chunk.getSectionIndex(be.getBlockPos().getY()));
        h.assertTrue(access.observationRoots().contains(chunk), "actual chunk must be reserved with original BE");
        var lease = MaterialObservationBarrier.reserve(op, HASH, access.observationRoots());
        try {
            h.assertTrue(access.isLoaded(), "internal liveness must work without guarded getters");
            rejects(be::ledger); rejects(access::snapshot); rejects(() -> map.get(be.getBlockPos()));
            rejects(section::hasOnlyAir);
            var permit = fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.READ_ROOT);
            var root = be.worldReadRoot(permit, owner, op, HASH);
            h.assertTrue(root.equals(original), "read original immutable root including complete item components");
            rejects(() -> be.worldReadRoot(permit, owner, op, HASH));
            var afterImage = next(root.image()); var reserved = marker(owner, op, root.image(), afterImage);
            var after = MaterialOwnerSnapshot.of(owner, before.liveGeneration(), afterImage.revision(), afterImage.canonical());
            replace(be, owner, op, root.isolate(reserved));
            h.assertTrue(access.classify(op, HASH, before, after, false) == MaterialOwnerAccess.Position.BEFORE,
                    "RESERVED is still full BEFORE");
            var projected = read(be, owner, op).project(afterImage, reserved.projected());
            replace(be, owner, op, projected);
            h.assertTrue(access.classify(op, HASH, before, after, false) == MaterialOwnerAccess.Position.AFTER,
                    "AFTER requires exact marker and image");
            rejects(() -> replace(be, owner, op, new MaterialRootState(owner.incarnation(), afterImage,
                    Optional.empty(), Optional.empty())));
            rejects(() -> replace(be, owner, op, projected.cancel(op, HASH)));
            replace(be, owner, op, projected.complete(op, HASH));
            var completed = read(be, owner, op);
            h.assertTrue(completed.last().orElseThrow().equals(reserved.projected()), "retain durable recovery lineage");
            h.assertTrue(completed.image().unitStack().equals(original.image().unitStack()), "same original item shell");
            h.assertTrue(completed.image().ledger().equals(original.image().ledger()), "same FIFO instances");
            rejects(access::persist); // leaf publication is deliberately not claimed durable
        } finally { lease.releaseUnprojected(); }
        var saved = MaterialRootState.decode(be.saveWithoutMetadata(h.getLevel().registryAccess()).getCompound("material_root"));
        h.assertTrue(saved.last().isPresent() && saved.pending().isEmpty(), "full BE NBT preserves completed root");
        h.assertTrue(be.ledger().equals(original.image().ledger()), "ordinary reads reopen with original balance");
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 100)
    public static void foreignAndReusedCapabilitiesCannotTouchRoot(GameTestHelper h) {
        var be = install(h); var access = be.materialAccess();
        var owner = (OwnerKey.WorldBlock) access.owner(); var op = UUID.randomUUID(); var unrelated = new Object();
        var roots = new ArrayList<Object>(access.observationRoots()); roots.add(unrelated);
        var lease = MaterialObservationBarrier.reserve(op, HASH, roots);
        try {
            var root = read(be, owner, op);
            rejects(() -> be.worldReadRoot(fixture(owner, op, unrelated, be, WorldPrimitivePermit.Primitive.READ_ROOT), owner, op, HASH));
            rejects(() -> be.worldReadRoot(fixture(owner, op, be, unrelated, WorldPrimitivePermit.Primitive.READ_ROOT), owner, op, HASH));
            rejects(() -> be.worldReadRoot(fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.REPLACE_ROOT), owner, op, HASH));
            rejects(() -> be.worldReadRoot(fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.READ_ROOT), owner, UUID.randomUUID(), HASH));
            rejects(() -> be.worldReadRoot(fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.READ_ROOT), owner, op, "e".repeat(64)));
            var wrongId = new OwnerKey.WorldBlock(owner.dimension(), owner.blockPos(), UUID.randomUUID());
            var wrongPos = new OwnerKey.WorldBlock(owner.dimension(), BlockPos.of(owner.blockPos()).above().asLong(), owner.incarnation());
            var wrongDimension = new OwnerKey.WorldBlock(ResourceLocation.withDefaultNamespace("the_nether"), owner.blockPos(), owner.incarnation());
            for (var foreign : List.of(wrongId, wrongPos, wrongDimension)) {
                rejects(() -> be.worldReadRoot(fixture(foreign, op, be, be, WorldPrimitivePermit.Primitive.READ_ROOT), foreign, op, HASH));
            }
            var marker = marker(owner, op, root.image(), next(root.image()));
            rejects(() -> replace(be, owner, op, new MaterialRootState(UUID.randomUUID(), root.image(), Optional.empty(), Optional.empty())));
            rejects(() -> replace(be, owner, op, new MaterialRootState(owner.incarnation(), next(root.image()), Optional.empty(), Optional.empty())));
            var permit = fixture(owner, op, be, be, WorldPrimitivePermit.Primitive.READ_ROOT);
            var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var thread = new Thread(() -> { try { be.worldReadRoot(permit, owner, op, HASH); }
                catch (Throwable expected) { result.set(expected); } });
            thread.start();
            try { thread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            h.assertTrue(result.get() instanceof IllegalStateException, "cross-thread capability rejected");
            h.assertTrue(be.worldReadRoot(permit, owner, op, HASH).equals(root), "failed foreign thread cannot consume server capability");
            replace(be, owner, op, root.isolate(marker));
            replace(be, owner, op, root); // all-BEFORE cancellation at the leaf, no AFTER marker
            h.assertTrue(read(be, owner, op).equals(root), "cancel preserves full BEFORE");
        } finally { lease.releaseUnprojected(); }
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 100)
    public static void unpublishedCandidateAdoptsPositionIncarnationOnce(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 2, 1)); var chunk = h.getLevel().getChunkAt(pos);
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var state = ElementMaterials.CONTENT.get("elemental_iron").get().defaultBlockState();
        var candidate = new MaterialBlockEntity(pos, state); var op = UUID.randomUUID();
        var owner = new OwnerKey.WorldBlock(h.getLevel().dimension().location(), pos.asLong(), UUID.randomUUID());
        var empty = new StackLedger(StackLedger.CURRENT_SCHEMA, 0, List.of());
        var air = new MaterialProjection(Blocks.AIR.defaultBlockState(), 9, empty, Optional.empty());
        var material = new MaterialProjection(state, 10, empty, Optional.empty());
        var marker = marker(owner, op, air, material).projected();
        var adopted = new MaterialRootState(owner.incarnation(), material, Optional.of(marker), Optional.of(marker));
        var lease = MaterialObservationBarrier.reserve(op, HASH, List.of(chunk, candidate));
        try {
            candidate.worldReplaceRoot(fixture(owner, op, chunk, candidate, WorldPrimitivePermit.Primitive.REPLACE_ROOT), owner, op, HASH, adopted);
            h.assertTrue(candidate.persistentIncarnation().equals(owner.incarnation()), "inherit position incarnation from air metadata");
            h.assertTrue(candidate.getLevel() == null, "publication leaf must not install BE or invoke onLoad");
            h.assertTrue(!((WorldChunkAccessBridge)chunk).worldContainsBlockEntity(candidate), "no fabricated air BE installed");
            h.assertTrue(candidate.worldReadRoot(fixture(owner, op, chunk, candidate, WorldPrimitivePermit.Primitive.READ_ROOT), owner, op, HASH).equals(adopted),
                    "candidate retains exact PROJECTED image and lineage");
            var foreignOwner = new OwnerKey.WorldBlock(owner.dimension(), owner.blockPos(), UUID.randomUUID());
            var foreignMarker = marker(foreignOwner, op, air, material).projected();
            var foreign = new MaterialRootState(foreignOwner.incarnation(), material, Optional.of(foreignMarker), Optional.of(foreignMarker));
            rejects(() -> candidate.worldReplaceRoot(fixture(foreignOwner, op, chunk, candidate, WorldPrimitivePermit.Primitive.REPLACE_ROOT),
                    foreignOwner, op, HASH, foreign));
        } finally { lease.releaseUnprojected(); }
        h.assertTrue(chunk.getBlockEntity(pos) == null && chunk.getBlockState(pos).isAir(), "candidate bridge leaves world air untouched");
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 100)
    public static void replacedAndQuarantinedRootsStayUnavailable(GameTestHelper h) {
        var be = install(h); var owner = (OwnerKey.WorldBlock)be.materialAccess().owner();
        var chunk = h.getLevel().getChunkAt(be.getBlockPos()); var op = UUID.randomUUID();
        var bad = be.saveWithoutMetadata(h.getLevel().registryAccess()); bad.getCompound("material_root").putInt("schema", 99);
        be.loadWithComponents(bad, h.getLevel().registryAccess());
        var lease = MaterialObservationBarrier.reserve(op, HASH, List.of(be, chunk));
        try { rejects(() -> read(be, owner, op)); } finally { lease.releaseUnprojected(); }
        h.assertTrue(be.saveWithoutMetadata(h.getLevel().registryAccess()).getCompound("material_root").getInt("schema") == 99,
                "quarantined original NBT is preserved");
        h.getLevel().setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        install(h);
        var stale = MaterialObservationBarrier.reserve(op, HASH, List.of(be, chunk));
        try {
            h.assertTrue(!be.materialAccess().isLoaded(), "old physical root is unavailable after ABA");
            rejects(() -> read(be, owner, op));
        } finally { stale.releaseUnprojected(); }
        h.succeed();
    }
}

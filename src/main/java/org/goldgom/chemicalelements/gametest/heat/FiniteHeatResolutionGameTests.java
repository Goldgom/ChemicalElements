package org.goldgom.chemicalelements.gametest.heat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.world.ThermalRuntime;
import org.goldgom.chemicalelements.world.heat.LoadedFiniteHeatResolver;

/** Real ServerLevel entry rejection plus explicitly labelled white-box real-BE lifecycle and
 * in-memory content-lineage tests. None claims durable thermal settlement or phase success. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class FiniteHeatResolutionGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void unknownPortNeverCreatesHeatRoot(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var port = ResourceLocation.parse("chemicalelements:unregistered_heat");
        var key = new OwnerKey.FiniteHeatSource(level.dimension().location(), pos.asLong(), UUID.randomUUID(), port);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var before = new HashMap<>(chunk.getBlockEntities());
        var result = ThermalRuntime.resolve(level, key);
        helper.assertTrue(result.status().equals("UNKNOWN_PORT") && !result.resolved(), "Unknown port rejected");
        helper.assertTrue(chunk.getBlockEntities().equals(before), "No synthetic BE or mutation");
        helper.assertTrue(!ThermalRuntime.ready(), "Incomplete production scheduling remains disabled");
        helper.succeed();
    }
    /** White-box lifecycle test on a REAL registered material BE. Reflection only constructs
     * an otherwise unreachable handle while the production enthalpy/isolation gates stay shut.
     * This does NOT claim successful resolver admission or thermodynamic recovery. */
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realMaterialReloadInvalidatesCapturedHandle(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = org.goldgom.chemicalelements.registry.ElementMaterials.CONTENT.get("mixture_carrier_unresolved").get().defaultBlockState();
        level.setBlock(pos, state, 3);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var be = (org.goldgom.chemicalelements.content.material.MaterialBlockEntity) chunk.getBlockEntities().get(pos);
        helper.assertTrue(be != null, "Registered material BE exists");
        var alias = new OwnerKey.FiniteHeatSource(level.dimension().location(), pos.asLong(), be.persistentIncarnation(),
                ResourceLocation.parse("chemicalelements:material"));
        var first = lifecycleHandle(level, be, alias);
        helper.assertTrue(first.isLoaded(), "Original loaded handle");
        var secondLocator = lifecycleHandle(level, be, alias);
        helper.assertTrue(first.physicalAccess() == be.materialAccess()
                && secondLocator.physicalAccess() == first.physicalAccess(), "Locators retain the SAME original physical access");
        var lease = org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.reserve(
                UUID.randomUUID(), "f".repeat(64), be.materialAccess().observationRoots());
        try {
            helper.assertTrue(first.isLoaded() && secondLocator.isLoaded(), "Exact identity checks survive chunk isolation");
            try { chunk.getBlockEntities(); throw new AssertionError("ordinary map access escaped reservation"); }
            catch (IllegalStateException expected) { /* internal liveness does not open ordinary observation */ }
        } finally { lease.releaseUnprojected(); }
        var identity = be.persistentIncarnation(); var previousGeneration = be.liveGeneration();
        be.onLoad(); // exercise the real load-generation callback; no fabricated material balance
        helper.assertTrue(be.persistentIncarnation().equals(identity) && !be.liveGeneration().equals(previousGeneration),
                "Load keeps persistent incarnation but rotates generation");
        helper.assertTrue(!first.isLoaded() && !secondLocator.isLoaded(), "Every old locator must remain invalid");
        try { first.snapshot(); throw new AssertionError("Stale handle snapshot accepted"); }
        catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().equals("STALE_FINITE_HEAT_HANDLE"), "Reject lifecycle before any heat/model lookup");
        }
        var reloaded = lifecycleHandle(level, be, alias);
        helper.assertTrue(reloaded.isLoaded(), "New handle captures current live generation");
        be.setRemoved();
        helper.assertTrue(!first.isLoaded() && !reloaded.isLoaded(), "Removal invalidates both handles");
        helper.assertTrue(!ThermalRuntime.ready(), "Lifecycle coverage does not enable production");
        helper.succeed();
    }
    /** Real content adapter, empty-material image, IN-MEMORY lineage only: no journal,
     * durability, finite heat or phase-success claim. Does not bypass any production gate. */
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realContentHistoricalGenerationLineage(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(2, 1, 1));
        var state = org.goldgom.chemicalelements.registry.ElementMaterials.CONTENT.get("mixture_carrier_unresolved").get().defaultBlockState();
        level.setBlock(pos, state, 3);
        var be = (org.goldgom.chemicalelements.content.material.MaterialBlockEntity)
                level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockEntities().get(pos);
        var access = be.materialAccess(); var before = access.snapshot();
        var image = org.goldgom.chemicalelements.content.material.MaterialProjection.decode(before.canonicalRoot());
        var next = new org.goldgom.chemicalelements.content.material.MaterialProjection(image.state(), image.revision() + 1,
                image.ledger(), image.finiteHeat(), image.unitStack());
        var after = org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerSnapshot.of(access.owner(),
                before.liveGeneration(), next.revision(), next.canonical());
        var op = UUID.randomUUID(); var hash = "d".repeat(64);
        var marker = new org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker(op, hash,
                be.persistentIncarnation(), before.revision(), after.revision(), before.rootHash(), after.rootHash(),
                org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker.Phase.RESERVED);
        access.isolate(marker); be.onLoad();
        helper.assertTrue(!be.liveGeneration().equals(before.liveGeneration()), "Real load generation rotated");
        helper.assertTrue(access.classify(op, hash, before, after, false)
                == org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.CONFLICT, "Initial path rejects historical generation");
        helper.assertTrue(access.classify(UUID.randomUUID(), hash, before, after, true)
                == org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.CONFLICT, "Wrong operation lineage rejected");
        helper.assertTrue(access.classify(op, "e".repeat(64), before, after, true)
                == org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.CONFLICT, "Wrong global hash rejected");
        helper.assertTrue(access.classify(op, hash, before, after, true)
                == org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.BEFORE, "Original complete pending lineage accepts history");
        access.project(after, marker.projected());
        helper.assertTrue(access.classify(op, hash, before, after, true)
                == org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.AFTER, "Legal AFTER recognized without rewriting journal generation");
        helper.assertTrue(after.liveGeneration().equals(before.liveGeneration()), "Historical evidence unchanged");
        access.complete(op, hash); // test-only in-memory metadata cleanup, NOT a durable commit
        helper.succeed();
    }

    /** White-box candidate state on a real BE, NOT admitted thermal recovery. No fabricated
     * enthalpy, durable journal, lease or production whitelist is installed. */
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void candidateDoesNotGrantHistoricalProjection(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3, 1, 1));
        var state = org.goldgom.chemicalelements.registry.ElementMaterials.CONTENT.get("mixture_carrier_unresolved").get().defaultBlockState();
        level.setBlock(pos, state, 3);
        var be = (org.goldgom.chemicalelements.content.material.MaterialBlockEntity)
                level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockEntities().get(pos);
        var access = be.materialAccess(); var before = access.snapshot();
        var image = org.goldgom.chemicalelements.content.material.MaterialProjection.decode(before.canonicalRoot());
        var next = new org.goldgom.chemicalelements.content.material.MaterialProjection(image.state(), image.revision() + 1,
                image.ledger(), image.finiteHeat(), image.unitStack());
        var after = org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerSnapshot.of(access.owner(),
                before.liveGeneration(), next.revision(), next.canonical());
        var op = UUID.randomUUID(); var hash = "a".repeat(64);
        be.onLoad();
        var alias = new OwnerKey.FiniteHeatSource(level.dimension().location(), pos.asLong(), be.persistentIncarnation(),
                ResourceLocation.parse("chemicalelements:material"));
        var handle = lifecycleHandle(level, be, alias);
        try {
            var type = Class.forName("org.goldgom.chemicalelements.world.heat.LoadedFiniteHeatResolver$RecoveryBinding");
            var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var candidate = constructor.newInstance(op, hash, before, after);
            var field = handle.getClass().getDeclaredField("recoveryCandidate"); field.setAccessible(true);
            field.set(handle, candidate); // only a white-box locator, deliberately not admitted
            var authority = handle.getClass().getDeclaredField("recoveryBinding"); authority.setAccessible(true);
            helper.assertTrue(authority.get(handle) == null, "Candidate has no successful lineage binding");
            var conflict = org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.CONFLICT;
            var unavailable = org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.Position.UNAVAILABLE;
            var wrong = handle.classify(UUID.randomUUID(), hash, before, after, true);
            helper.assertTrue(wrong == conflict || wrong == unavailable, "Wrong op never obtains history authority");
            wrong = handle.classify(op, "b".repeat(64), before, after, true);
            helper.assertTrue(wrong == conflict || wrong == unavailable, "Wrong hash never obtains history authority");
            helper.assertTrue(authority.get(handle) == null, "Failed checks leave no historical project binding");
            helper.assertTrue(access.classify(op, hash, before, after, true) == conflict,
                    "Without pending marker or original journal lease, full content classification rejects candidate");
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Candidate test contract drift", failure); }
        helper.assertTrue(!ThermalRuntime.ready(), "White-box candidate does not enable production");
        helper.succeed();
    }

    private static LoadedFiniteHeatResolver.Handle lifecycleHandle(net.minecraft.server.level.ServerLevel level,
            org.goldgom.chemicalelements.content.material.MaterialBlockEntity be, OwnerKey.FiniteHeatSource alias) {
        try {
            var constructor = LoadedFiniteHeatResolver.Handle.class.getDeclaredConstructor(
                    net.minecraft.server.level.ServerLevel.class, OwnerKey.FiniteHeatSource.class,
                    net.minecraft.world.level.block.entity.BlockEntity.class,
                    org.goldgom.chemicalelements.world.heat.FiniteHeatBlockEntityRoot.class,
                    org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess.class,
                    UUID.class, org.goldgom.chemicalelements.data.ChemistryBundle.class);
            constructor.setAccessible(true);
            return constructor.newInstance(level, alias, be, be, be.materialAccess(), be.liveGeneration(),
                    org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle());
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Lifecycle test constructor drift", failure); }
    }

    /** Real recovery entry boundary; successful reload/dual-port settlement still needs the
     * B guard contract, content durability and a genuinely evidenced thermal model. */
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void recoveryCannotBypassProductionWhitelist(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var id = UUID.randomUUID(); var generation = UUID.randomUUID();
        var physical = new OwnerKey.WorldBlock(level.dimension().location(), pos.asLong(), id);
        var before = org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerSnapshot.of(
                physical, generation, 0, "{\"revision\":0}");
        var after = org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerSnapshot.of(
                physical, generation, 1, "{\"revision\":1}");
        var alias = new OwnerKey.FiniteHeatSource(level.dimension().location(), pos.asLong(), id,
                ResourceLocation.parse("chemicalelements:material"));
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var original = new HashMap<>(chunk.getBlockEntities());
        var result = new LoadedFiniteHeatResolver(Set.of()).resolveRecovery(level, alias,
                UUID.randomUUID(), "a".repeat(64), before, after);
        helper.assertTrue(result.status().equals("UNKNOWN_PORT") && !result.resolved(), "Recovery is not a whitelist bypass");
        helper.assertTrue(chunk.getBlockEntities().equals(original), "No root created by historical evidence");
        helper.assertTrue(!ThermalRuntime.ready(), "No production enablement");
        helper.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void unloadedLookupDoesNotLoadChunk(GameTestHelper helper) {
        var level = helper.getLevel(); var port = ResourceLocation.parse("chemicalelements:test_lookup_only");
        var resolver = new LoadedFiniteHeatResolver(Set.of(port));
        BlockPos pos = null;
        // Bounded lookup-only search; never force-load or scan world contents.
        for (int i = 0; i < 16; i++) {
            var candidate = new BlockPos(29000000 - i * 16, 64, 29000000);
            if (level.getChunkSource().getChunkNow(candidate.getX() >> 4, candidate.getZ() >> 4) == null) { pos = candidate; break; }
        }
        helper.assertTrue(pos != null, "Require an unloaded test coordinate");
        var result = resolver.resolve(level, new OwnerKey.FiniteHeatSource(level.dimension().location(), pos.asLong(), UUID.randomUUID(), port));
        helper.assertTrue(result.status().equals("UNLOADED") && !result.resolved(), "Unloaded owner rejected");
        helper.assertTrue(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "No load side effect");
        helper.succeed();
    }
}

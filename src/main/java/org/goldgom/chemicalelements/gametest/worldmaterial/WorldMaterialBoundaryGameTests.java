package org.goldgom.chemicalelements.gametest.worldmaterial;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier;

/** Real loaded chunk entry tests. Uses a reservation fixture, not B production admission;
 * this does not establish air/material transactions or crash durability. */
@GameTestHolder("chemicalelements")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class WorldMaterialBoundaryGameTests {
    private WorldMaterialBoundaryGameTests() {}
    @GameTest(template = "core_empty")
    public static void removedEntityKeepsVanillaSaveFallback(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var entity = level.getBlockEntity(pos);
        entity.setRemoved();
        try {
            helper.assertTrue(level.getChunkAt(pos).getBlockEntityNbtForSaving(pos, level.registryAccess()) == null,
                    "Removed BE without pending NBT is excluded from ordinary chunk saves");
        } finally { entity.clearRemoved(); }
        helper.succeed();
    }
    @GameTest(template = "core_empty")
    public static void retainedRealChunkSectionPaletteAndMapCannotObserveReservation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        LevelChunk chunk = helper.getLevel().getChunkAt(pos);
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        var palette = section.getStates(); var map = chunk.getBlockEntities();
        var entries = map.entrySet(); var sections = chunk.getSections();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "a".repeat(64), List.of(chunk));
        try {
            blocked(helper, () -> chunk.getBlockState(pos), "chunk state");
            blocked(helper, () -> chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false), "chunk replacement");
            blocked(helper, () -> section.getBlockState(1, pos.getY() & 15, 1), "old section");
            blocked(helper, () -> palette.get(1, pos.getY() & 15, 1), "old palette");
            blocked(helper, () -> palette.set(1, pos.getY() & 15, 1, Blocks.STONE.defaultBlockState()), "palette mutation");
            blocked(helper, () -> map.get(pos), "old BE map");
            blocked(helper, entries::size, "old map view");
            blocked(helper, chunk::clearAllBlockEntities, "unload cleanup");
            blocked(helper, () -> sections[chunk.getSectionIndex(pos.getY())].hasOnlyAir(), "escaped array section");
        } finally { lease.releaseUnprojected(); }
        chunk.getBlockState(pos); helper.succeed();
    }
    @GameTest(template = "core_empty")
    public static void sectionArrayReplacementCannotReplaceLiveSection(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
        var copy = chunk.getSections(); var original = copy[0]; copy[0] = null;
        helper.assertTrue(chunk.getSection(0) == original, "returned section array must be detached"); helper.succeed();
    }
    private static void blocked(GameTestHelper helper, Runnable action, String entry) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "reservation must reject " + entry);
    }
}

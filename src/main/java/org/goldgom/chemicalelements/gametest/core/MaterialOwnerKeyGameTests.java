package org.goldgom.chemicalelements.gametest.core;

import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.goldgom.chemicalelements.api.tx.OwnerKey;

/** Value-only checks; no world mutation, persistent owner allocation or durable CAS exercise. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialOwnerKeyGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void materialIdentityValues(GameTestHelper helper) {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var otherDimension = ResourceLocation.parse("minecraft:the_nether");
        var port = ResourceLocation.parse("chemicalelements:heat");
        var otherPort = ResourceLocation.parse("chemicalelements:other_heat");
        var incarnation = new UUID(1, 2);
        var otherIncarnation = new UUID(1, 3);
        var world = new OwnerKey.WorldBlock(dimension, Long.MIN_VALUE, incarnation);
        var worldCopy = new OwnerKey.WorldBlock(dimension, Long.MIN_VALUE, incarnation);
        var heat = new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, incarnation, port);
        var heatCopy = new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, incarnation, port);
        helper.assertTrue(world.equals(worldCopy) && world.hashCode() == worldCopy.hashCode(), "World value equality/hash");
        helper.assertTrue(heat.equals(heatCopy) && heat.hashCode() == heatCopy.hashCode(), "Heat value equality/hash");
        helper.assertTrue(!world.equals(new OwnerKey.WorldBlock(otherDimension, Long.MIN_VALUE, incarnation)), "World dimension differs");
        helper.assertTrue(!world.equals(new OwnerKey.WorldBlock(dimension, Long.MAX_VALUE, incarnation)), "World position differs");
        helper.assertTrue(!world.equals(new OwnerKey.WorldBlock(dimension, Long.MIN_VALUE, otherIncarnation)), "World incarnation differs");
        helper.assertTrue(!heat.equals(new OwnerKey.FiniteHeatSource(otherDimension, Long.MIN_VALUE, incarnation, port)), "Heat dimension differs");
        helper.assertTrue(!heat.equals(new OwnerKey.FiniteHeatSource(dimension, Long.MAX_VALUE, incarnation, port)), "Heat position differs");
        helper.assertTrue(!heat.equals(new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, otherIncarnation, port)), "Heat incarnation differs");
        helper.assertTrue(!heat.equals(new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, incarnation, otherPort)), "Heat port differs, not proof of independent heat budget");
        List<OwnerKey> variants = List.of(new OwnerKey.PlayerInventory(incarnation),
                new OwnerKey.MachineInventory(dimension, Long.MIN_VALUE, incarnation, port), world, heat);
        for (int i = 0; i < variants.size(); i++) {
            for (int j = 0; j < variants.size(); j++) {
                helper.assertTrue(variants.get(i).equals(variants.get(j)) == (i == j), "Old/new variants remain distinct");
            }
        }
        helper.succeed();
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void materialIdentityRejectsNull(GameTestHelper helper) {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var port = ResourceLocation.parse("chemicalelements:heat");
        var incarnation = new UUID(1, 2);
        rejectsNull(helper, () -> new OwnerKey.WorldBlock(null, 0, incarnation));
        rejectsNull(helper, () -> new OwnerKey.WorldBlock(dimension, 0, null));
        rejectsNull(helper, () -> new OwnerKey.FiniteHeatSource(null, 0, incarnation, port));
        rejectsNull(helper, () -> new OwnerKey.FiniteHeatSource(dimension, 0, null, port));
        rejectsNull(helper, () -> new OwnerKey.FiniteHeatSource(dimension, 0, incarnation, null));
        rejectsNull(helper, () -> new OwnerKey.PlayerInventory(null));
        rejectsNull(helper, () -> new OwnerKey.MachineInventory(null, 0, incarnation, port));
        rejectsNull(helper, () -> new OwnerKey.MachineInventory(dimension, 0, null, port));
        rejectsNull(helper, () -> new OwnerKey.MachineInventory(dimension, 0, incarnation, null));
        helper.succeed();
    }

    private static void rejectsNull(GameTestHelper helper, Runnable action) {
        try { action.run(); } catch (NullPointerException expected) { return; }
        helper.assertTrue(false, "Null reference must be rejected");
    }
}

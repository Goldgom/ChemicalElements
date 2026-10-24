package org.goldgom.chemicalelements.api.tx;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Standalone assertion main; no JUnit dependency. Value tests only, not owner/CAS proof. */
public final class MaterialOwnerKeyTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void rejectsNull(Runnable action) {
        checks++;
        try { action.run(); } catch (NullPointerException expected) { return; }
        throw new AssertionError("Expected null rejection");
    }
    public static void main(String[] args) {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var otherDimension = ResourceLocation.parse("minecraft:the_nether");
        var port = ResourceLocation.parse("chemicalelements:heat");
        var otherPort = ResourceLocation.parse("chemicalelements:other_heat");
        // Fixed fixtures are not production incarnation allocation or persistent authorization.
        var incarnation = new UUID(1, 2);
        var otherIncarnation = new UUID(1, 3);
        var world = new OwnerKey.WorldBlock(dimension, Long.MIN_VALUE, incarnation);
        var equalWorld = new OwnerKey.WorldBlock(dimension, Long.MIN_VALUE, incarnation);
        check(world.equals(equalWorld) && equalWorld.equals(world), "World equality");
        check(world.hashCode() == equalWorld.hashCode(), "World equal hash");
        check(world.dimension().equals(dimension) && world.blockPos() == Long.MIN_VALUE
                && world.incarnation().equals(incarnation), "World components unchanged");
        check(!world.equals(new OwnerKey.WorldBlock(otherDimension, Long.MIN_VALUE, incarnation)), "World dimension");
        check(!world.equals(new OwnerKey.WorldBlock(dimension, Long.MAX_VALUE, incarnation)), "World position");
        check(!world.equals(new OwnerKey.WorldBlock(dimension, Long.MIN_VALUE, otherIncarnation)), "World incarnation");
        var heat = new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, incarnation, port);
        var equalHeat = new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, incarnation, port);
        check(heat.equals(equalHeat) && equalHeat.equals(heat), "Heat equality");
        check(heat.hashCode() == equalHeat.hashCode(), "Heat equal hash");
        check(heat.dimension().equals(dimension) && heat.blockPos() == Long.MIN_VALUE
                && heat.incarnation().equals(incarnation) && heat.port().equals(port), "Heat components unchanged");
        check(!heat.equals(new OwnerKey.FiniteHeatSource(otherDimension, Long.MIN_VALUE, incarnation, port)), "Heat dimension");
        check(!heat.equals(new OwnerKey.FiniteHeatSource(dimension, Long.MAX_VALUE, incarnation, port)), "Heat position");
        check(!heat.equals(new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, otherIncarnation, port)), "Heat incarnation");
        check(!heat.equals(new OwnerKey.FiniteHeatSource(dimension, Long.MIN_VALUE, incarnation, otherPort)), "Heat port");
        var player = new OwnerKey.PlayerInventory(incarnation);
        var machine = new OwnerKey.MachineInventory(dimension, Long.MIN_VALUE, incarnation, port);
        check(player.equals(new OwnerKey.PlayerInventory(incarnation)), "Legacy player equality");
        check(!player.equals(new OwnerKey.PlayerInventory(otherIncarnation)), "Legacy player difference");
        check(machine.equals(new OwnerKey.MachineInventory(dimension, Long.MIN_VALUE, incarnation, port)), "Legacy machine equality");
        check(machine.controllerPos() == Long.MIN_VALUE && machine.machineType().equals(port), "Legacy machine fields");
        List<OwnerKey> variants = List.of(player, machine, world, heat);
        for (int i = 0; i < variants.size(); i++) {
            for (int j = 0; j < variants.size(); j++) {
                check(variants.get(i).equals(variants.get(j)) == (i == j), "Variant separation " + i + "/" + j);
            }
        }
        check(new HashSet<>(variants).size() == 4, "Four distinct variants");
        check(!world.equals(null) && !heat.equals(null), "Null is not identity");
        rejectsNull(() -> new OwnerKey.WorldBlock(null, 0, incarnation));
        rejectsNull(() -> new OwnerKey.WorldBlock(dimension, 0, null));
        rejectsNull(() -> new OwnerKey.FiniteHeatSource(null, 0, incarnation, port));
        rejectsNull(() -> new OwnerKey.FiniteHeatSource(dimension, 0, null, port));
        rejectsNull(() -> new OwnerKey.FiniteHeatSource(dimension, 0, incarnation, null));
        rejectsNull(() -> new OwnerKey.PlayerInventory(null));
        rejectsNull(() -> new OwnerKey.MachineInventory(null, 0, incarnation, port));
        rejectsNull(() -> new OwnerKey.MachineInventory(dimension, 0, null, port));
        rejectsNull(() -> new OwnerKey.MachineInventory(dimension, 0, incarnation, null));
        System.out.println("MaterialOwnerKeyTest PASS checks=" + checks + "; value identity only, not owner/CAS readiness");
    }
}

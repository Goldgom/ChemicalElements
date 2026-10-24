package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.instance.*;

/** Actual server player/inventory/mixin snapshot tests, but detached plan assertions only.
 * Synthetic empty-composition records are explicit fixtures, not production phase/material data.
 * Successful plan roundtrip is NOT a successful multi-owner durable transaction. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialPlayerPlanGameTests {
    private MaterialPlayerPlanGameTests() {}
    @GameTest(template="core_empty")
    public static void actualInventoryPlanRoundtripPreservesFifo(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var first = fixture(); var second = fixture();
        var stack = new ItemStack(Items.STONE, 2);
        StackLedgers.set(stack, new StackLedger(1, 0, List.of(first, second)));
        player.getInventory().setItem(0, stack);
        var before = MaterialPlayerSnapshot.capture(player);
        var removed = MaterialPlayerSlotPlan.removeHead(before.snapshot(), 0, player.registryAccess());
        h.assertTrue(removed.instance().equals(first), "original FIFO head preserved");
        var returned = MaterialPlayerSlotPlan.insertUnit(removed.after(), 0, removed.unitStack(), player.registryAccess());
        var root = com.google.gson.JsonParser.parseString(returned.canonicalRoot()).getAsJsonObject();
        var image = TransactionEncoding.decodeMirror(TransactionEncoding.canonical(root.get("inventory")), player.registryAccess());
        h.assertTrue(image.slots().getFirst().instances().equals(List.of(second, first)), "pickup appends after original remaining FIFO");
        h.assertTrue(MaterialPlayerSnapshot.capture(player).equals(before), "planning must not mutate actual inventory");
        h.succeed();
    }
    @GameTest(template="core_empty")
    public static void actualInventoryFullDestinationPreservesInput(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var source = new ItemStack(Items.STONE); StackLedgers.set(source, new StackLedger(1, 0, List.of(fixture())));
        var full = new ItemStack(Items.STONE, 64); var records = new ArrayList<InstanceRecord>();
        for (int i=0;i<64;i++) records.add(fixture()); StackLedgers.set(full, new StackLedger(1, 0, records));
        player.getInventory().setItem(0, source); player.getInventory().setItem(1, full);
        var before = MaterialPlayerSnapshot.capture(player);
        var removed = MaterialPlayerSlotPlan.removeHead(before.snapshot(), 0, player.registryAccess());
        boolean refused=false;
        try { MaterialPlayerSlotPlan.insertUnit(removed.after(), 1, removed.unitStack(), player.registryAccess()); }
        catch (IllegalStateException expected) { refused=true; }
        h.assertTrue(refused, "full destination must reject");
        h.assertTrue(MaterialPlayerSnapshot.capture(player).equals(before), "full inventory rejection retains input"); h.succeed();
    }
    @GameTest(template="core_empty")
    public static void actualInventoryAbaInvalidatesMutationEvidence(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var stack = new ItemStack(Items.STONE); StackLedgers.set(stack, new StackLedger(1, 0, List.of(fixture())));
        player.getInventory().setItem(0, stack); var before = MaterialPlayerSnapshot.capture(player);
        player.getInventory().setItem(0, ItemStack.EMPTY); player.getInventory().setItem(0, stack);
        h.assertTrue(!PlayerMutationVersion.matches(player.getInventory(), before.mutation()), "A-B-A invalidates live CAS evidence");
        h.succeed();
    }
    private static InstanceRecord fixture() {
        return new InstanceRecord(UUID.randomUUID(), Composition.EMPTY, Provenance.of("test_fixture", "material_slot_plan"), 0);
    }
}

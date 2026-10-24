package org.goldgom.chemicalelements.instance;

import java.math.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.api.tx.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Pure validation over nonempty complete real ledger stacks; NOT successful commit evidence. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class OwnerTransferValidationGameTests {
    @GameTest(template="core_empty", timeoutTicks=40)
    public static void fullRecordAndContainerRejectedBeforeProjection(GameTestHelper h) {
        var actor = h.makeMockServerPlayerInLevel();
        var water = new Composition(Map.of(ResourceLocation.parse("chemicalelements:water"), MolAmount.parseDecimal("1")));
        var original = new InstanceRecord(UUID.randomUUID(), water, Provenance.of("test", "transfer-validation"), 0);
        var before = image(0, Items.STONE, original, 0);
        var moved = image(1, Items.STONE, original, 1);
        var authority = new MenuAuthority(actor.getUUID(), UUID.randomUUID(), ResourceLocation.parse("chemicalelements:transfer_test"), 1);
        var proposal = new ReactionProposal(ResourceLocation.parse("chemicalelements:transfer_test"), Composition.EMPTY,
                Composition.EMPTY, Composition.EMPTY, MolAmount.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, "relocation", List.of());
        var owner = new OwnerKey.PlayerInventory(actor.getUUID());
        var generation = UUID.randomUUID();
        var request = new MultiOwnerRequest(UUID.randomUUID(), authority, new CatalogVersion(0, "0".repeat(64)), proposal,
                List.of(new MultiOwnerRequest.Change(owner, generation, before, moved)));
        OwnerTransferValidation.validate(request, h.getLevel().registryAccess());
        var hot = new InstanceRecord(original.id(), original.remaining(), original.origin(), 0,
                new ThermalState(BigDecimal.ONE, new BigDecimal("298.15"), Map.of(), 0), original.nuclides(), original.poreSolution(), original.adsorbed());
        reject(h, new MultiOwnerRequest(UUID.randomUUID(), authority, request.catalog(), proposal,
                List.of(new MultiOwnerRequest.Change(owner, generation, before, image(1, Items.STONE, hot, 1)))));
        var adsorbed = new InstanceRecord(original.id(), original.remaining(), original.origin(), 0,
                original.thermal(), original.nuclides(), original.poreSolution(), water);
        reject(h, new MultiOwnerRequest(UUID.randomUUID(), authority, request.catalog(), proposal,
                List.of(new MultiOwnerRequest.Change(owner, generation, before, image(1, Items.STONE, adsorbed, 1)))));
        reject(h, new MultiOwnerRequest(UUID.randomUUID(), authority, request.catalog(), proposal,
                List.of(new MultiOwnerRequest.Change(owner, generation, before, image(1, Items.BUCKET, original, 1)))));
        h.assertTrue(before.slots().getFirst().instances().equals(List.of(original)), "validator did not mutate retained input");
        h.succeed();
    }
    private static Mirror image(long revision, Item item, InstanceRecord record, int position) {
        var stack = new ItemStack(item); StackLedgers.set(stack, LedgerOps.of(0, List.of(record)));
        var filled = new SlotImage(position, stack, List.of(record));
        var empty = new SlotImage(1-position, ItemStack.EMPTY, List.of());
        return new Mirror(revision, position == 0 ? List.of(filled, empty) : List.of(empty, filled), List.of(),
                new EnergyImage(BigInteger.ZERO, BigInteger.ZERO, 0), new ControlImage("player_inventory", 0, 0, Map.of()));
    }
    private static void reject(GameTestHelper h, MultiOwnerRequest request) {
        try { OwnerTransferValidation.validate(request, h.getLevel().registryAccess()); }
        catch (IllegalStateException expected) { return; }
        h.fail("full field/container mismatch accepted");
    }
}

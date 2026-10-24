package org.goldgom.chemicalelements.instance;

import java.util.*;
import java.math.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.machine.*;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** SOURCE ONLY r21. Run only after test permission. No successful CAS or Owner-ready assertion. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MultiOwnerAdmissionGameTests {
    @GameTest(template="core_empty", timeoutTicks=40)
    public static void realMenuRejectionAndRecovery(GameTestHelper h) {
        var actor = h.makeMockServerPlayerInLevel(); var pos = h.absolutePos(BlockPos.ZERO);
        h.getLevel().setBlock(pos, MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(), 3);
        var be = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
        actor.setPos(pos.getX()+.5, pos.getY()+1, pos.getZ()+.5);
        var menu = new MachineMenu(41, actor.getInventory(), be); actor.containerMenu = menu;
        var authority = menu.transactionAuthority(actor).orElseThrow();
        var indexes = List.of(menu.layoutSide()*menu.layoutSide()+9);
        var observation = MenuOwnerTransactions.observe(actor, authority, indexes).getFirst();
        var energy = new EnergyImage(BigInteger.ZERO, BigInteger.ZERO, 0);
        var control = new ControlImage("idle", 0, 0, Map.of());
        var before = new Mirror(0, observation.slots(), List.of(), energy, control);
        var after = new Mirror(1, observation.slots(), List.of(), energy, control);
        var proposal = new ReactionProposal(ResourceLocation.parse("chemicalelements:admission_test"),
                Composition.EMPTY, Composition.EMPTY, Composition.EMPTY, MolAmount.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, "test-intent-not-production", List.of());
        var request = new MultiOwnerRequest(UUID.randomUUID(), authority, new CatalogVersion(0, "0".repeat(64)), proposal,
                List.of(new MultiOwnerRequest.Change(observation.owner(), UUID.randomUUID(), before, after)));
        var registries = h.getLevel().registryAccess(); var journal = SinkLedger.get();
        String encoded = request.encode(registries);
        h.assertTrue(MultiOwnerRequest.decode(encoded, registries).encode(registries).equals(encoded), "strict recovery roundtrip");
        rejects(h, () -> MultiOwnerRequest.decode(encoded+" ", registries));
        rejects(h, () -> MultiOwnerRequest.decode(encoded.replace("\"schema\":3", "\"schema\":3.0"), registries));
        rejects(h, () -> MultiOwnerRequest.decode(encoded.replace("\"schema\":3", "\"schema\":3,\"schema\":3"), registries));
        OwnerTransferValidation.validate(request, registries);
        var changedEnergy = new Mirror(1, observation.slots(), List.of(),
                new EnergyImage(BigInteger.ONE, BigInteger.ONE, 1), control);
        var energyRequest = new MultiOwnerRequest(UUID.randomUUID(), authority, request.catalog(), proposal,
                List.of(new MultiOwnerRequest.Change(observation.owner(), UUID.randomUUID(), before, changedEnergy)));
        var energyReceipt = MenuOwnerTransactions.commit(actor, energyRequest, indexes);
        h.assertTrue(energyReceipt.state() == MultiOwnerJournal.State.REJECTED
                        && energyReceipt.reason().equals("TRANSFER_CONTROL_OR_ENERGY_CHANGED"),
                "full owner energy mismatch rejected before any projection");
        h.assertTrue(MenuOwnerTransactions.observe(actor, authority, indexes).getFirst().slots().size()
                        == observation.slots().size() && actor.getInventory().isEmpty(),
                "pre-projection rejection keeps actual player inventory");
        var receipt = MenuOwnerTransactions.commit(actor, request, indexes);
        h.assertTrue(receipt.state()==MultiOwnerJournal.State.REJECTED && receipt.reason().equals("UNSUPPORTED_OWNER_ABA"),
                "real player observation cannot fabricate generation/revision coverage");
        h.assertTrue(!journal.committed(request.operation()) && journal.aborted(request.operation()), "durable refusal, never COMMITTED");
        h.assertTrue(MenuOwnerTransactions.commit(actor, request, indexes).equals(receipt), "same UUID retry routes original refusal");
        h.assertTrue(MenuOwnerTransactions.cancel(actor, request).equals(receipt), "cancel does not refund or clear");
        var persisted = journal.save(new CompoundTag(), registries);
        var recovered = SinkLedger.load(persisted, registries);
        h.assertTrue(MultiOwnerJournal.query(recovered, request, registries).equals(receipt), "SavedData recovery retains same refusal");
        var corrupt = persisted.copy();
        var rows = corrupt.getList("journal", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            if (row.hasUUID("__tx") && row.getUUID("__tx").equals(request.operation())) row.putString("payload_sha256", "f".repeat(64));
        }
        var quarantined = SinkLedger.load(corrupt, registries);
        h.assertTrue(quarantined.readOnly() && quarantined.save(new CompoundTag(), registries).equals(corrupt),
                "bad schema3 hash keeps the complete original read-only");
        var conflict = new MultiOwnerRequest(request.operation(), authority, request.catalog(), proposal,
                List.of(new MultiOwnerRequest.Change(observation.owner(), UUID.randomUUID(), before, after)));
        h.assertTrue(MenuOwnerTransactions.commit(actor, conflict, indexes).state()==MultiOwnerJournal.State.CONFLICT,
                "same UUID different generation cannot replace payload");
        var legacy = new CompoundTag(); legacy.putString("kind", "legacy-schema1-test");
        var legacyRequest = new MultiOwnerRequest(UUID.randomUUID(), authority, request.catalog(), proposal, request.owners());
        journal.prepareDurably(legacyRequest.operation(), legacy);
        h.assertTrue(MenuOwnerTransactions.commit(actor, legacyRequest, indexes).state()==MultiOwnerJournal.State.CONFLICT
                && journal.matches(legacyRequest.operation(), legacy) && !journal.aborted(legacyRequest.operation()),
                "legacy UUID is neither converted nor aborted by schema3");
        journal.abortDurably(legacyRequest.operation()); // test's own never-projected fixture only
        h.assertTrue(MenuOwnerTransactions.observe(actor, authority, indexes).equals(List.of(observation)), "empty original inventory untouched");
        menu.removed(actor);
        rejects(h, () -> MenuOwnerTransactions.commit(actor, request, indexes));
        h.assertTrue(journal.aborted(request.operation()), "closed menu cannot change durable refusal");
        actor.containerMenu = actor.inventoryMenu; h.succeed();
    }
    private static void rejects(GameTestHelper h, Runnable operation) {
        boolean rejected = false;
        try { operation.run(); } catch (RuntimeException expected) { rejected = true; }
        h.assertTrue(rejected, "invalid request rejected");
    }
}

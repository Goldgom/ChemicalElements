package org.goldgom.chemicalelements.instance;

import java.util.*;
import java.math.*;
import java.lang.reflect.Modifier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.machine.*;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Source-only: actual registered BE/menu and sole world journal. No mock permit constructor. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MachineJournalGameTests {
    @GameTest(template="core_empty", timeoutTicks=80)
    public static void durablePrepareTankCancelAndRestartBoundary(GameTestHelper h) throws Exception {
        var actor = h.makeMockServerPlayerInLevel(); var pos = h.absolutePos(BlockPos.ZERO);
        h.getLevel().setBlock(pos, MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(), 3);
        var machine = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
        actor.setPos(pos.getX()+.5, pos.getY()+1, pos.getZ()+.5);
        var menu = new MachineMenu(72, actor.getInventory(), machine); actor.containerMenu = menu;
        var before = machine.projection.snapshot();
        var envelope = MachineOwnerEnvelope.capture(machine, before);
        var registries = actor.registryAccess();
        h.assertTrue(envelope.beforeTank().equals(AggregateTankImage.capture(machine.tank)), "bind real aggregate tank");
        h.assertTrue(MachineOwnerEnvelope.decode(envelope.encode(), registries).encode().equals(envelope.encode()), "envelope exact roundtrip");
        rejects(h, () -> AggregateTankImage.decode(envelope.beforeTank().encode()+" "));
        rejects(h, () -> MachineOwnerEnvelope.decode(envelope.encode().replace(envelope.beforeTank().digest(), "f".repeat(64)), registries));
        for (var constructor : DurableMachinePermit.class.getDeclaredConstructors())
            h.assertTrue(Modifier.isPrivate(constructor.getModifiers()), "no public/package permit construction");
        var image = machine.projection.image(before);
        var after = new Mirror(image.revision()+1, image.slots(), image.tanks(), image.energy(), image.control());
        var catalog = org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.snapshot();
        var proposal = new ReactionProposal(ResourceLocation.parse("chemicalelements:permit_test"), Composition.EMPTY,
                Composition.EMPTY, Composition.EMPTY, MolAmount.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, catalog.epoch(), "relocation-test", List.of());
        var intent = new MultiOwnerRequest(UUID.randomUUID(), menu.transactionAuthority(actor).orElseThrow(),
                new CatalogVersion(catalog.epoch(), catalog.contentHash()), proposal, List.of(new MultiOwnerRequest.Change(envelope.owner(), before.version().generation(), image, after)));
        var request = new MachineJournal.Request(intent, envelope);
        var permit = MachineJournal.prepare(actor, request);
        permit.require(machine, before, before, intent.operation());
        rejects(h, () -> permit.require(machine, before, before, UUID.randomUUID()));
        var altered = MachineProjection.replacement(before, before.canonicalMirror(), MachineCanonicalNbt.encode(new AggregateTankImage(envelope.beforeTank().capacity(), 1, Composition.EMPTY).toNbt()));
        rejects(h, () -> permit.require(machine, before, altered, intent.operation()));
        var ledger = SinkLedger.get();
        h.assertTrue(ledger.pending().contains(intent.operation()), "real durable PREPARE exists");
        var diskImage = ledger.save(new CompoundTag(), registries);
        var reloaded = SinkLedger.load(diskImage, registries);
        h.assertTrue(!reloaded.readOnly() && reloaded.pending().contains(intent.operation()), "same journal restart decoding");
        rejects(h, reloaded::flushDurably); // detached copy cannot claim a successful durability barrier
        // Actual new-schema disk image and write failure, isolated under this test world's root.
        var root = h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        var testDir = java.nio.file.Files.createTempDirectory(root, "permit-disk-fault-");
        var blocker = java.nio.file.Files.createFile(testDir.resolve("not-directory"));
        reloaded.setDirty();
        rejects(h, () -> reloaded.save(blocker.resolve("journal.dat").toFile(), registries));
        h.assertTrue(reloaded.isDirty() && machine.projection.snapshot().equals(before), "schema3 disk failure cannot mutate machine");
        var testFile = testDir.resolve("journal.dat"); reloaded.save(testFile.toFile(), registries);
        var written = net.minecraft.nbt.NbtIo.readCompressed(testFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var diskReload = SinkLedger.load(written.getCompound("data"), registries);
        h.assertTrue(!diskReload.readOnly() && diskReload.journalEntry(intent.operation()).equals(ledger.journalEntry(intent.operation())), "schema3 disk retry/reload preserves exact PREPARE");
        machine.onChunkUnloaded(); machine.onLoad();
        h.assertTrue(!machine.projection.snapshot().version().generation().equals(before.version().generation()), "live generation changes on lifecycle");
        h.assertTrue(MachineJournal.classify(machine, envelope, intent.operation()) == MachineJournal.Recovery.BEFORE, "persistent lineage and exact before survive live generation change");
        rejects(h, () -> permit.require(machine, before, before, intent.operation()));
        // Reopen a real authoritative menu after unload invalidated the previous session.
        var nextMenu = new MachineMenu(73, actor.getInventory(), machine); actor.containerMenu = nextMenu;
        // Original authorization is intentionally stale, so cancellation must not silently use it.
        rejects(h, () -> MachineJournal.cancel(actor, request));
        h.assertTrue(!ledger.aborted(intent.operation()) && machine.inventory().isEmpty(), "stale authority neither cancels nor overwrites");
        var resumed = MachineJournal.resume(actor, intent.operation(), nextMenu.transactionAuthority(actor).orElseThrow());
        resumed.require(machine, resumed.expected(), resumed.replacement(), intent.operation());
        h.assertTrue(resumed.payloadSha256().equals(permit.payloadSha256())
                && resumed.projectionRequestDigest().equals(permit.projectionRequestDigest()), "reload preserves full original journal and projection request digests");
        h.assertTrue(!resumed.expected().version().generation().equals(before.version().generation()), "resume binds actual new live generation");
        h.assertTrue(ledger.journalEntry(intent.operation()).orElseThrow().getString("payload").equals(request.encode(registries)), "resume never rewrites durable intent");
        machine.tank.restore(Composition.EMPTY, machine.tank.revision()+1);
        rejects(h, () -> resumed.require(machine, resumed.expected(), resumed.replacement(), intent.operation()));
        rejects(h, () -> MachineJournal.resume(actor, intent.operation(), nextMenu.transactionAuthority(actor).orElseThrow()));
        h.assertTrue(MachineJournal.recover(machine, intent.operation()) == MachineJournal.Recovery.CONFLICT, "changed persisted tank revision rejects recovery without inventory overwrite");
        actor.containerMenu = actor.inventoryMenu; h.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=80)
    public static void cancellationDuplicateAndUuidConflict(GameTestHelper h) {
        var actor = h.makeMockServerPlayerInLevel(); var pos = h.absolutePos(BlockPos.ZERO);
        h.getLevel().setBlock(pos, MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(), 3);
        var machine = (MachineBlockEntity) h.getLevel().getBlockEntity(pos);
        actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        var menu = new MachineMenu(74, actor.getInventory(), machine); actor.containerMenu = menu;
        var before = machine.projection.snapshot(); var envelope = MachineOwnerEnvelope.capture(machine,before);
        var image = machine.projection.image(before);
        var after = new Mirror(image.revision()+1,image.slots(),image.tanks(),image.energy(),image.control());
        var catalog = org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.snapshot();
        var proposal = new ReactionProposal(ResourceLocation.parse("chemicalelements:cancel_test"),Composition.EMPTY,Composition.EMPTY,Composition.EMPTY,MolAmount.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,catalog.epoch(),"relocation-test",List.of());
        var intent = new MultiOwnerRequest(UUID.randomUUID(),menu.transactionAuthority(actor).orElseThrow(),new CatalogVersion(catalog.epoch(),catalog.contentHash()),proposal,List.of(new MultiOwnerRequest.Change(envelope.owner(),before.version().generation(),image,after)));
        var request = new MachineJournal.Request(intent,envelope); var permit = MachineJournal.prepare(actor,request);
        actor.containerMenu = actor.inventoryMenu;
        rejects(h,()->permit.require(machine,before,before,intent.operation()));
        actor.containerMenu = menu;
        MachineJournal.cancel(actor,request); MachineJournal.cancel(actor,request);
        h.assertTrue(SinkLedger.get().aborted(intent.operation()),"duplicate cancellation remains terminal");
        rejects(h,()->permit.require(machine,before,before,intent.operation()));
        rejects(h,()->MachineJournal.prepare(actor,request));
        var conflictIntent = new MultiOwnerRequest(intent.operation(),intent.authority(),new CatalogVersion(catalog.epoch(),"1".repeat(64)),proposal,intent.owners());
        rejects(h,()->MachineJournal.cancel(actor,new MachineJournal.Request(conflictIntent,envelope)));
        h.assertTrue(machine.projection.snapshot().equals(before),"cancel/conflict preserves full real inventory");
        actor.containerMenu=actor.inventoryMenu; h.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=80)
    public static void actualPermitProjectionCommitAndReload(GameTestHelper h) {
        relocation(h, false);
    }
    @GameTest(template="core_empty", timeoutTicks=80)
    public static void resumedProjectionRejectsMissingAndWrongJournalProof(GameTestHelper h) {
        relocation(h, true);
    }
    private static void relocation(GameTestHelper h, boolean resumeFirst) {
        var actor=h.makeMockServerPlayerInLevel(); var pos=h.absolutePos(BlockPos.ZERO);
        h.getLevel().setBlock(pos,MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(),3);
        var machine=(MachineBlockEntity)h.getLevel().getBlockEntity(pos);
        actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        MutablePayloadPolicy.requireSupported(stack);
        // An empty lore equals the default and is removed by ItemStack.set; use an
        // actual retained payload so this tests the policy instead of a no-op patch.
        var mutableLore=new net.minecraft.world.item.component.ItemLore(new ArrayList<>(List.of(net.minecraft.network.chat.Component.literal("mutable payload"))));
        var bad=stack.copy(); bad.set(net.minecraft.core.component.DataComponents.LORE,mutableLore);
        rejects(h,()->MutablePayloadPolicy.requireSupported(bad));
        var record=new org.goldgom.chemicalelements.api.instance.InstanceRecord(UUID.randomUUID(),Composition.EMPTY,
                org.goldgom.chemicalelements.api.instance.Provenance.of("test","permit-relocation"),0);
        stack.set(org.goldgom.chemicalelements.persistence.ChemistryComponents.LEDGER.get(),
                org.goldgom.chemicalelements.persistence.LedgerPayload.of(new org.goldgom.chemicalelements.api.instance.StackLedger(
                        org.goldgom.chemicalelements.api.instance.StackLedger.CURRENT_SCHEMA,0,List.of(record))));
        machine.inventory().setItem(0,stack);
        var menu=new MachineMenu(75,actor.getInventory(),machine);actor.containerMenu=menu;
        var before=machine.projection.snapshot();var image=machine.projection.image(before);
        var slots=new ArrayList<>(image.slots()); var source=slots.get(0);
        slots.set(0,new SlotImage(0,net.minecraft.world.item.ItemStack.EMPTY,List.of()));
        slots.set(81,new SlotImage(81,source.stack(),source.instances()));
        var afterImage=new Mirror(image.revision()+1,slots,image.tanks(),image.energy(),image.control());
        var replacement=MachineProjection.replacement(before,TransactionEncoding.mirror(new Mirror(image.revision(),slots,image.tanks(),image.energy(),image.control()),actor.registryAccess()),before.tankNbt());
        var envelope=MachineOwnerEnvelope.capture(machine,replacement);
        var catalog=org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.snapshot();
        var proposal=new ReactionProposal(ResourceLocation.parse("chemicalelements:relocation_test"),Composition.EMPTY,Composition.EMPTY,Composition.EMPTY,MolAmount.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,catalog.epoch(),"test-only exact relocation",List.of());
        var intent=new MultiOwnerRequest(UUID.randomUUID(),menu.transactionAuthority(actor).orElseThrow(),new CatalogVersion(catalog.epoch(),catalog.contentHash()),proposal,
                List.of(new MultiOwnerRequest.Change(envelope.owner(),before.version().generation(),image,afterImage)));
        var request=new MachineJournal.Request(intent,envelope);
        var permit=MachineJournal.prepare(actor,request);
        if (resumeFirst) {
            machine.onChunkUnloaded(); machine.onLoad();
            var freshMenu=new MachineMenu(76,actor.getInventory(),machine); actor.containerMenu=freshMenu;
            var resumed=MachineJournal.resume(actor,intent.operation(),freshMenu.transactionAuthority(actor).orElseThrow());
            h.assertTrue(machine.projection.apply(resumed)==MachineProjection.Result.APPLIED,"fresh permit forward projection");
            h.assertTrue(resumed.payloadSha256().equals(permit.payloadSha256())
                    && resumed.projectionRequestDigest().equals(permit.projectionRequestDigest()),"original durable intent unchanged");
        } else {
            h.assertTrue(machine.projection.apply(before,replacement,intent.operation(),permit)==MachineProjection.Result.APPLIED,"real permit root publication");
            MachineJournal.commit(actor,request);MachineJournal.commit(actor,request);
        }
        var saved=machine.saveWithoutMetadata(actor.registryAccess());
        for (String proof : List.of("", "f".repeat(64))) {
            var damaged=saved.copy(); damaged.getCompound("machine_projection").putString("journal_payload_sha256",proof);
            machine.loadWithComponents(damaged,actor.registryAccess());
            h.assertTrue(MachineJournal.classify(machine,envelope,intent.operation())==MachineJournal.Recovery.AFTER,"local marker alone still classifies AFTER");
            h.assertTrue(MachineJournal.recover(machine,intent.operation())==MachineJournal.Recovery.CONFLICT,"missing or wrong global proof rejects AFTER");
            if (resumeFirst) h.assertTrue(!SinkLedger.get().committed(intent.operation()),"bad proof cannot commit pending journal");
        }
        var legacy=saved.copy(); legacy.getCompound("machine_projection").putInt("schema",1);
        legacy.getCompound("machine_projection").remove("journal_payload_sha256");
        machine.loadWithComponents(legacy,actor.registryAccess());
        h.assertTrue(MachineJournal.recover(machine,intent.operation())==MachineJournal.Recovery.CONFLICT,"legacy marker is not journal proof");
        machine.loadWithComponents(saved,actor.registryAccess());
        h.assertTrue(MachineJournal.recover(machine,intent.operation())==MachineJournal.Recovery.AFTER,"restored original proof confirms exact AFTER");
        h.assertTrue(SinkLedger.get().committed(intent.operation())&&machine.inventory().getItem(0).isEmpty()
                &&StackLedgers.get(machine.inventory().getItem(81)).orElseThrow().instances().equals(List.of(record)),"sole journal commit and exact real destination");
        rejects(h,()->MachineJournal.cancel(actor,request));
        machine.onChunkUnloaded();machine.onLoad();
        h.assertTrue(MachineJournal.recover(machine,intent.operation())==MachineJournal.Recovery.AFTER,"committed after confirmed across live generation without replay");
        h.assertTrue(MachineJournal.recover(machine,intent.operation())==MachineJournal.Recovery.AFTER,"recovery repeat remains after");
        actor.containerMenu=actor.inventoryMenu;h.succeed();
    }
    private static void rejects(GameTestHelper h,Runnable operation) {
        boolean rejected=false; try {operation.run();} catch(RuntimeException expected){rejected=true;}
        h.assertTrue(rejected,"invalid operation rejected");
    }
}

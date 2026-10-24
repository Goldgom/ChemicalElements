package org.goldgom.chemicalelements.machine;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.instance.TransactionEncoding;
import org.goldgom.chemicalelements.persistence.*;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Real BE entry points. Source only until the integration owner runs this exact version. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MachineProjectionGameTests {
 private static MachineBlockEntity machine(GameTestHelper h){var pos=h.absolutePos(BlockPos.ZERO);h.getLevel().setBlock(pos,MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(),3);return (MachineBlockEntity)h.getLevel().getBlockEntity(pos);}
 private static ItemStack stack(){var r=new InstanceRecord(UUID.randomUUID(),Composition.EMPTY,Provenance.of("test","machine-projection"),0);var s=new ItemStack(Items.IRON_INGOT);s.set(ChemistryComponents.LEDGER.get(),LedgerPayload.of(new StackLedger(StackLedger.CURRENT_SCHEMA,0,List.of(r))));return s;}
 private static MachineProjection.Snapshot move(MachineBlockEntity be,MachineProjection.Snapshot before){var m=be.projection.image(before);var slots=new ArrayList<>(m.slots());var a=slots.get(0);slots.set(0,new SlotImage(0,ItemStack.EMPTY,List.of()));slots.set(81,new SlotImage(81,a.stack(),a.instances()));var after=new Mirror(m.revision(),slots,m.tanks(),m.energy(),m.control());return MachineProjection.replacement(before,TransactionEncoding.mirror(after,be.getLevel().registryAccess()),before.tankNbt());}
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void fullMirrorAndDefensiveImage(GameTestHelper h){var be=machine(h);be.inventory().setItem(0,stack());be.power.receiveEnergy(50,false);var s=be.projection.snapshot();var m=be.projection.image(s);h.assertTrue(m.slots().size()==90,"All physical slots, including unused grid and destinations");h.assertTrue(m.energy().stored().intValueExact()==50&&m.control().attributes().containsKey("thermal_millij")&&m.control().attributes().containsKey("pressure_pa"),"Full energy and controls");h.assertTrue(s.tankNbt().equals(MachineCanonicalNbt.encode(be.tank.save())),"Real aggregate tank, no fabricated parcels");m.slots().get(0).stack().set(DataComponents.CUSTOM_NAME,Component.literal("detached"));h.assertTrue(s.equals(be.projection.snapshot()),"Decoded image cannot mutate live owner");h.succeed();}
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void slotAndComponentAba(GameTestHelper h){var be=machine(h);var item=stack();be.inventory().setItem(0,item);var s=be.projection.snapshot();be.inventory().setItem(0,ItemStack.EMPTY);be.inventory().setItem(0,item);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"Slot ABA");s=be.projection.snapshot();item.setCount(2);item.setCount(1);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"In-place count ABA");s=be.projection.snapshot();item.set(DataComponents.CUSTOM_NAME,Component.literal("ABA"));item.remove(DataComponents.CUSTOM_NAME);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"Component ABA");h.succeed();}
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void tankPowerControlAba(GameTestHelper h){var be=machine(h);var s=be.projection.snapshot();be.tank.restore(Composition.EMPTY,0);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"Tank restore cannot rewind mutation clock");s=be.projection.snapshot();be.power.receiveEnergy(1,false);be.power.spend(1);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"Power ABA");s=be.projection.snapshot();var p=new CompoundTag();be.power.save(p);var changed=p.copy();changed.putLong("thermal_millij",p.getLong("thermal_millij")+1);changed.putInt("pressure_pa",p.getInt("pressure_pa")+1);be.power.load(changed);be.power.load(p);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"Thermal/pressure ABA");s=be.projection.snapshot();be.configureGrid(6);be.configureGrid(5);h.assertTrue(!s.version().equals(be.projection.snapshot().version()),"Control ABA");h.succeed();}
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void rejectExpectedAndInvalidProjection(GameTestHelper h){var be=machine(h);be.inventory().setItem(0,stack());var before=be.projection.snapshot();var after=move(be,before);be.power.receiveEnergy(4,false);var unchanged=be.projection.snapshot();h.assertTrue(be.projection.apply(before,after,UUID.randomUUID())==MachineProjection.Result.CONFLICT,"Stale expected rejected");h.assertTrue(unchanged.equals(be.projection.snapshot()),"Rejected expected preserves inputs/energy");var m=be.projection.image(unchanged);var empty=new ArrayList<>(m.slots());empty.set(0,new SlotImage(0,ItemStack.EMPTY,List.of()));var invalid=new Mirror(m.revision(),empty,m.tanks(),m.energy(),m.control());var replacement=MachineProjection.replacement(unchanged,TransactionEncoding.mirror(invalid,h.getLevel().registryAccess()),unchanged.tankNbt());h.assertTrue(be.projection.apply(unchanged,replacement,UUID.randomUUID())==MachineProjection.Result.REJECTED,"Loss rejected before publish");h.assertTrue(unchanged.equals(be.projection.snapshot()),"No partial clear or spend on failure");var bad=MachineProjection.replacement(unchanged,"not json",unchanged.tankNbt());h.assertTrue(be.projection.apply(unchanged,bad,UUID.randomUUID())==MachineProjection.Result.REJECTED&&unchanged.equals(be.projection.snapshot()),"Codec exception before write");h.succeed();}
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void projectionRepeatReloadAndConflict(GameTestHelper h){var be=machine(h);be.inventory().setItem(0,stack());var before=be.projection.snapshot();var after=move(be,before);var op=UUID.randomUUID();h.assertTrue(be.projection.apply(before,after,op)==MachineProjection.Result.APPLIED,"Actual root projection");h.assertTrue(be.inventory().getItem(0).isEmpty()&&!be.inventory().getItem(81).isEmpty(),"Original container exposes projected slots");h.assertTrue(be.projection.apply(before,after,op)==MachineProjection.Result.ALREADY_APPLIED,"Identical retry");h.assertTrue(be.projection.apply(before,before,op)==MachineProjection.Result.CONFLICT,"Operation payload conflict");var marker=be.projection.marker();var old=be.projection.snapshot();var nbt=new CompoundTag();be.saveAdditional(nbt,h.getLevel().registryAccess());be.loadAdditional(nbt,h.getLevel().registryAccess());h.assertTrue(marker.equals(be.projection.marker()),"Durable marker survives NBT");h.assertTrue(!old.version().generation().equals(be.projection.snapshot().version().generation()),"Reload replaces live generation");h.assertTrue(be.projection.apply(before,after,op)==MachineProjection.Result.ALREADY_APPLIED,"Persisted retry verifies actual result content");be.onChunkUnloaded();h.assertTrue(!old.version().generation().equals(be.projection.snapshot().version().generation()),"Unload invalidates generation");h.succeed();}
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void canonicalTankAndRecoveryBefore(GameTestHelper h){
  var be=machine(h);be.inventory().setItem(0,stack());var before=be.projection.snapshot();var after=move(be,before);var op=UUID.randomUUID();
  h.assertTrue(before.owner().controllerPos()==be.getBlockPos().asLong()&&before.owner().dimension().equals(h.getLevel().dimension().location()),"Actual owner identity");
  h.assertTrue(before.mirrorDigest().equals(TransactionEncoding.sha256(before.canonicalMirror()))&&before.projection().equals(be.projection.marker()),"Mirror digest and persistent lineage bound");
  var a=new CompoundTag();a.putLong("z",1);a.putString("a","v");var b=new CompoundTag();b.putString("a","v");b.putLong("z",1);
  h.assertTrue(MachineCanonicalNbt.encode(a).equals(MachineCanonicalNbt.encode(b)),"Insertion order independent canonical NBT");
  h.assertTrue(MachineCanonicalNbt.decode(MachineCanonicalNbt.encode(a)).equals(a),"NBT types preserved");
  var nbt=new CompoundTag();be.saveAdditional(nbt,h.getLevel().registryAccess());be.loadAdditional(nbt,h.getLevel().registryAccess());
  h.assertTrue(be.projection.inspectRecovery(before,after,op)==MachineProjection.RecoveryView.BEFORE,"Persistent before classified across new live generation");
  h.assertTrue(be.projection.apply(before,after,op)==MachineProjection.Result.CONFLICT,"Classification is not permission to bypass live CAS");
  be.power.receiveEnergy(1,false);h.assertTrue(be.projection.inspectRecovery(before,after,op)==MachineProjection.RecoveryView.CONFLICT,"Neither before nor after cannot be overwritten");h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void missingPermitAndMutablePayloadRejected(GameTestHelper h){
  var be=machine(h);var before=be.projection.snapshot();
  h.assertTrue(be.projection.apply(before,before,UUID.randomUUID(),null)==MachineProjection.Result.REJECTED,"Null permit never authorizes journal projection");
  h.assertTrue(before.equals(be.projection.snapshot()),"Rejected permit leaves full snapshot unchanged");
  var item=stack();item.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(new CompoundTag()));be.inventory().setItem(0,item);
  boolean rejected=false;try{be.projection.snapshot();}catch(IllegalStateException expected){rejected=true;}
  h.assertTrue(rejected,"Unknown mutable component payload fails closed");h.assertTrue(be.inventory().getItem(0)==item,"Rejected snapshot does not delete input");h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=80)
 public static void durableResumeAndIndependentHash(GameTestHelper h){
  var be=machine(h);be.inventory().setItem(0,stack());var actor=h.makeMockServerPlayerInLevel();var pos=be.getBlockPos();actor.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
  var menu=new MachineMenu(91,actor.getInventory(),be);actor.containerMenu=menu;
  var before=be.projection.snapshot();var after=move(be,before);var image=be.projection.image(before);var result=be.projection.image(after);
  var envelope=org.goldgom.chemicalelements.instance.MachineOwnerEnvelope.capture(be,after);
  var catalog=org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.snapshot();
  var proposal=new org.goldgom.chemicalelements.api.reaction.ReactionProposal(net.minecraft.resources.ResourceLocation.parse("chemicalelements:machine_resume_test"),Composition.EMPTY,Composition.EMPTY,Composition.EMPTY,org.goldgom.chemicalelements.api.chem.MolAmount.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,catalog.epoch(),"test-only relocation",List.of());
  var intent=new org.goldgom.chemicalelements.instance.MultiOwnerRequest(UUID.randomUUID(),menu.transactionAuthority(actor).orElseThrow(),new CatalogVersion(catalog.epoch(),catalog.contentHash()),proposal,List.of(new org.goldgom.chemicalelements.instance.MultiOwnerRequest.Change(envelope.owner(),before.version().generation(),image,new Mirror(image.revision()+1,result.slots(),result.tanks(),result.energy(),result.control()))));
  var request=new org.goldgom.chemicalelements.instance.MachineJournal.Request(intent,envelope);
  var original=org.goldgom.chemicalelements.instance.MachineJournal.prepare(actor,request);
  var saved=new CompoundTag();be.saveAdditional(saved,h.getLevel().registryAccess());be.loadAdditional(saved,h.getLevel().registryAccess());
  h.assertTrue(be.projection.apply(original)==MachineProjection.Result.CONFLICT,"Old live permit cannot overwrite reload");
  var fresh=new MachineMenu(92,actor.getInventory(),be);actor.containerMenu=fresh;
  var resumed=org.goldgom.chemicalelements.instance.MachineJournal.resume(actor,intent.operation(),fresh.transactionAuthority(actor).orElseThrow());
  h.assertTrue(be.projection.apply(resumed)==MachineProjection.Result.APPLIED,"Fresh authority resumes original PREPARE");
  h.assertTrue(be.projection.marker().requestDigest().equals(original.projectionRequestDigest()),"Original local request digest, not rebound digest");
  h.assertTrue(be.projection.matchesJournalProjection(intent.operation(),original.payloadSha256()),"Independent full journal proof");
  h.assertTrue(!be.projection.matchesJournalProjection(intent.operation(),"f".repeat(64)),"Different journal payload rejected");
  h.assertTrue(be.projection.apply(resumed)==MachineProjection.Result.ALREADY_APPLIED,"Exact retry never reprojects");
  var out=new CompoundTag();be.saveAdditional(out,h.getLevel().registryAccess());be.loadAdditional(out,h.getLevel().registryAccess());
  h.assertTrue(be.projection.journalPayloadSha256().equals(original.payloadSha256()),"Global digest survives NBT independently");
  h.assertTrue(org.goldgom.chemicalelements.instance.MachineJournal.recover(be,intent.operation())==org.goldgom.chemicalelements.instance.MachineJournal.Recovery.AFTER,"AFTER confirmed without projection");
  actor.containerMenu=actor.inventoryMenu;h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void legacyMetadataAndSameGenerationAba(GameTestHelper h){
  var be=machine(h);var before=be.projection.snapshot();be.power.receiveEnergy(1,false);be.power.spend(1);
  h.assertTrue(be.projection.inspectRecovery(before,before,UUID.randomUUID())==MachineProjection.RecoveryView.CONFLICT,"Same-generation ABA is not BEFORE");
  var nbt=new CompoundTag();be.saveAdditional(nbt,h.getLevel().registryAccess());var tag=nbt.getCompound("machine_projection");tag.putInt("schema",1);tag.remove("journal_payload_sha256");be.loadAdditional(nbt,h.getLevel().registryAccess());
  h.assertTrue(!be.projection.quarantined()&&be.projection.journalPayloadSha256().isEmpty(),"Legacy schema readable, no invented global digest");
  var out=new CompoundTag();be.saveAdditional(out,h.getLevel().registryAccess());h.assertTrue(out.getCompound("machine_projection").getInt("schema")==2,"Save upgrades metadata shape only");
  out.getCompound("machine_projection").putString("journal_payload_sha256","not-sha256");var raw=out.get("machine_projection").copy();be.loadAdditional(out,h.getLevel().registryAccess());
  h.assertTrue(be.projection.quarantined(),"Invalid full digest quarantined");var preserved=new CompoundTag();be.saveAdditional(preserved,h.getLevel().registryAccess());h.assertTrue(raw.equals(preserved.get("machine_projection")),"Damaged proof retained, not repaired");h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void unknownMetadataPreserved(GameTestHelper h){var be=machine(h);var nbt=new CompoundTag();be.saveAdditional(nbt,h.getLevel().registryAccess());nbt.getCompound("machine_projection").putString("future","retain");var raw=nbt.get("machine_projection").copy();be.loadAdditional(nbt,h.getLevel().registryAccess());h.assertTrue(be.projection.quarantined(),"Unknown metadata quarantined");var out=new CompoundTag();be.saveAdditional(out,h.getLevel().registryAccess());h.assertTrue(raw.equals(out.get("machine_projection")),"Unknown metadata retained byte-shape");h.assertTrue(be.projection.apply(null,null,UUID.randomUUID())==MachineProjection.Result.QUARANTINED,"No projection while quarantined");h.succeed();}
}

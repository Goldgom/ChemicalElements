package org.goldgom.chemicalelements.machine;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.*;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Machine-local optimistic projection, NOT a journal receipt or multi-owner transaction.
 * Existing aggregate tank has no parcel identities. Its real NBT is bound separately, never forged
 * into TankImage parcels or hidden in control attributes. Current admissible domain is exact local
 * slot relocation; cross-owner delivery and reactions require B's journal-bound validation port.
 */
public final class MachineProjection {
 public record Version(UUID generation,long mutationRevision) {}
 public record Snapshot(org.goldgom.chemicalelements.api.tx.OwnerKey.MachineInventory owner,Version version,String canonicalMirror,String mirrorDigest,String tankNbt,long tankMutationRevision,Marker projection,String digest) {
  public Snapshot {Objects.requireNonNull(owner);Objects.requireNonNull(version);Objects.requireNonNull(canonicalMirror);Objects.requireNonNull(tankNbt);Objects.requireNonNull(projection);
   if(tankMutationRevision<0||!TransactionEncoding.sha256(canonicalMirror).equals(mirrorDigest)||!hash(owner,version,canonicalMirror,tankNbt,tankMutationRevision,projection).equals(digest))throw new IllegalArgumentException("snapshot digest");}
  public UUID incarnation(){return owner.incarnation();}
 }
 public record Marker(UUID generation,long revision,Optional<UUID> lastOperation,String requestDigest,String resultDigest) {
  public String lastPayloadHash(){return requestDigest;}
 }
 public enum RecoveryView { BEFORE, AFTER, CONFLICT, QUARANTINED }
 /** Classification only: caller evidence cannot authorize writes, reset lineage or unquarantine.
  * B must authenticate the persisted PREPARE before using this result for recovery. */
 public RecoveryView inspectRecovery(Snapshot expected,Snapshot replacement,UUID operation){
  server();if(quarantine!=null)return RecoveryView.QUARANTINED;
  try{
   var current=snapshot();
   if(!current.owner().equals(expected.owner())||!expected.owner().equals(replacement.owner())||!expected.projection().equals(replacement.projection()))return RecoveryView.CONFLICT;
   String request=TransactionEncoding.sha256(operation+"\n"+expected.digest()+"\n"+replacement.digest());
   if(!projectionGeneration.equals(expected.projection().generation()))return RecoveryView.CONFLICT;
   if(operation.equals(lastOperation)&&request.equals(lastRequest)&&projectionRevision==Math.incrementExact(expected.projection().revision())&&contentDigest(current).equals(contentDigest(replacement))&&lastResult.equals(contentDigest(current)))return RecoveryView.AFTER;
   if(marker().equals(expected.projection())&&contentDigest(current).equals(contentDigest(expected))&&(!current.version().generation().equals(expected.version().generation())||current.version().equals(expected.version())))return RecoveryView.BEFORE;
   return RecoveryView.CONFLICT;
  }catch(RuntimeException invalid){return RecoveryView.CONFLICT;}
 }
 public enum Result { APPLIED, ALREADY_APPLIED, CONFLICT, REJECTED, QUARANTINED }
 private record Mark(Object identity,long revision) {}
 private final MachineBlockEntity owner;
 private UUID generation=UUID.randomUUID(),projectionGeneration=UUID.randomUUID(),lastOperation;
 private long mutationRevision,projectionRevision;
 private String lastRequest="",lastResult="",lastJournalPayloadSha256="";
 /** Separate from the legacy local request digest; empty means no durable journal proof. */
 public String journalPayloadSha256(){server();if(quarantine!=null)throw new IllegalStateException("MACHINE_PROJECTION_QUARANTINED");return lastJournalPayloadSha256;}
 public boolean matchesJournalProjection(UUID operation,String payloadSha256){
  server();return quarantine==null&&operation!=null&&operation.equals(lastOperation)&&payloadSha256!=null&&payloadSha256.matches("[0-9a-f]{64}")&&payloadSha256.equals(lastJournalPayloadSha256);
 }
 private List<Mark> observed;
 private Tag quarantine;
 private static final String KEY="machine_projection";
 MachineProjection(MachineBlockEntity owner){this.owner=owner;}
 void lifecycle(){generation=UUID.randomUUID();observed=null;}
 private void server(){if(owner.getLevel()==null||owner.getLevel().isClientSide||owner.isRemoved()||owner.getLevel().getServer()==null||!owner.getLevel().getServer().isSameThread())throw new IllegalStateException("SERVER_THREAD_REQUIRED");}
 private Mark mark(Object value){if(!(value instanceof MutationTracked clock))throw new IllegalStateException("MUTATION_MIXIN_UNAVAILABLE");return new Mark(value,clock.chemicalelements$mutationRevision());}
 private Version version(){
  var marks=new ArrayList<Mark>();marks.add(mark(owner.ownedInventory().items()));
  for(var stack:owner.ownedInventory().items()){marks.add(mark(stack));if(!stack.isEmpty())marks.add(mark(stack.getComponents()));}
  marks.add(new Mark(owner.power,owner.power.mutationRevision()));marks.add(new Mark(owner.tank,owner.tank.mutationRevision()));marks.add(new Mark(owner,owner.controlMutationRevision()));
  boolean same=observed!=null&&observed.size()==marks.size();
  if(same)for(int i=0;i<marks.size();i++)if(marks.get(i).identity()!=observed.get(i).identity()||marks.get(i).revision()!=observed.get(i).revision()){same=false;break;}
  if(observed!=null&&!same)mutationRevision=Math.incrementExact(mutationRevision);
  observed=List.copyOf(marks);return new Version(generation,mutationRevision);
 }
 public Snapshot snapshot(){
  server();if(quarantine!=null)throw new IllegalStateException("MACHINE_PROJECTION_QUARANTINED");
  var v=version();var slots=new ArrayList<SlotImage>();
  for(int i=0;i<owner.inventory().getContainerSize();i++){
   var stack=owner.inventory().getItem(i);
   MutablePayloadPolicy.requireSupported(stack);
   var records=stack.isEmpty()?List.<org.goldgom.chemicalelements.api.instance.InstanceRecord>of():StackLedgers.get(stack).orElseThrow(()->new IllegalStateException("UNRESOLVED_LEDGER")).instances();
   slots.add(new SlotImage(i,stack,records));
  }
  var mirror=new Mirror(v.mutationRevision(),slots,List.of(),new EnergyImage(BigInteger.valueOf(owner.power.getEnergyStored()),BigInteger.valueOf(owner.power.getMaxEnergyStored()),0),owner.controlImage());
  String encoded=TransactionEncoding.mirror(mirror,owner.getLevel().registryAccess());
  var decoded=TransactionEncoding.decodeMirror(encoded,owner.getLevel().registryAccess());
  if(!TransactionEncoding.mirror(decoded,owner.getLevel().registryAccess()).equals(encoded))throw new IllegalStateException("LOSSY_OWNER_CODEC");
  String tank=MachineCanonicalNbt.encode(owner.tank.save());
  MachineCanonicalNbt.decode(tank);
  if(!v.equals(version()))throw new IllegalStateException("OWNER_MUTATED_DURING_READ");
  var key=new org.goldgom.chemicalelements.api.tx.OwnerKey.MachineInventory(owner.getLevel().dimension().location(),owner.getBlockPos().asLong(),owner.incarnation(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements",owner.kind().id()));
  var marker=marker();long tankVersion=owner.tank.mutationRevision();
  return new Snapshot(key,v,encoded,TransactionEncoding.sha256(encoded),tank,tankVersion,marker,hash(key,v,encoded,tank,tankVersion,marker));
 }
 public Mirror image(Snapshot snapshot){server();return TransactionEncoding.decodeMirror(snapshot.canonicalMirror(),owner.getLevel().registryAccess());}
 public Marker marker(){if(quarantine!=null)throw new IllegalStateException("MACHINE_PROJECTION_QUARANTINED");return new Marker(projectionGeneration,projectionRevision,Optional.ofNullable(lastOperation),lastRequest,lastResult);}
 public boolean quarantined(){return quarantine!=null;}
 /** Full replacement is mandatory. No slot-at-a-time public transaction writer. */
 public Result apply(Snapshot expected,Snapshot replacement,UUID operation){
  return applyLocal(expected,replacement,operation,null);
 }
 /** Journal-bound local projection. This overload does not expand the conservation domain.
  * Rebound live snapshots still persist the ORIGINAL durable projection request digest. */
 public Result apply(Snapshot expected,Snapshot replacement,UUID operation,DurableMachinePermit permit){
  if(permit==null)return Result.REJECTED;
  return applyLocal(expected,replacement,operation,permit);
 }
 /** Consume B's freshly authorized live snapshots, including restart reissuance. No journal write. */
 public Result apply(DurableMachinePermit permit){
  return permit==null?Result.REJECTED:apply(permit.expected(),permit.replacement(),permit.operation(),permit);
 }
 private Result applyLocal(Snapshot expected,Snapshot replacement,UUID operation,DurableMachinePermit permit){
  server();if(quarantine!=null)return Result.QUARANTINED;
  if(expected==null||replacement==null||operation==null)return Result.REJECTED;
  String request=permit==null?TransactionEncoding.sha256(operation+"\n"+expected.digest()+"\n"+replacement.digest()):permit.projectionRequestDigest();
  try{
   String journalHash=permit==null?"":permit.payloadSha256();
   if(permit!=null&&(!operation.equals(permit.operation())||!journalHash.matches("[0-9a-f]{64}")))return Result.REJECTED;
   var current=snapshot();
   if(operation.equals(lastOperation))return journalHash.equals(lastJournalPayloadSha256)&&request.equals(lastRequest)&&current.owner().equals(expected.owner())&&projectionGeneration.equals(expected.projection().generation())&&projectionRevision==Math.incrementExact(expected.projection().revision())&&contentDigest(current).equals(lastResult)&&contentDigest(current).equals(contentDigest(replacement))?Result.ALREADY_APPLIED:Result.CONFLICT;
   if(!current.equals(expected))return Result.CONFLICT;
   if(!expected.owner().equals(replacement.owner())||!expected.version().equals(replacement.version())||!expected.projection().equals(replacement.projection())||expected.tankMutationRevision()!=replacement.tankMutationRevision())return Result.REJECTED;
   MachineCanonicalNbt.decode(expected.tankNbt());MachineCanonicalNbt.decode(replacement.tankNbt());
   var before=image(expected);var after=image(replacement);
   if(before.revision()!=after.revision()||!before.energy().equals(after.energy())||!before.control().equals(after.control())||!before.tanks().equals(after.tanks())||!expected.tankNbt().equals(replacement.tankNbt()))return Result.REJECTED;
   // Full record equality preserves all heat, nuclides, pore solution and adsorbed matter, as well as
   // exact non-chemistry components/container contents. This is stronger than element totals.
   if(!carried(before).equals(carried(after)))return Result.REJECTED;
   MaterialStateBalance.requireUnchanged(before.slots().stream().flatMap(s->s.instances().stream()).toList(),after.slots().stream().flatMap(s->s.instances().stream()).toList());
   if(after.slots().size()!=owner.inventory().getContainerSize())return Result.REJECTED;
   var prepared=NonNullList.withSize(owner.inventory().getContainerSize(),ItemStack.EMPTY);
   var seen=new HashSet<Integer>();
   for(var slot:after.slots()){
    if(slot.index()<0||slot.index()>=prepared.size()||!seen.add(slot.index()))return Result.REJECTED;
    var stack=slot.stack();MutablePayloadPolicy.requireSupported(stack);if(!stack.isEmpty()&&(stack.getCount()>stack.getMaxStackSize()||stack.getCount()>owner.inventory().getMaxStackSize()))return Result.REJECTED;
    prepared.set(slot.index(),stack);
   }
   // Everything which can fail is before publication, including codec, counters and final CAS.
   long nextProjection=Math.incrementExact(projectionRevision),nextMutation=Math.incrementExact(mutationRevision);
   String result=contentDigest(replacement);
   if(!snapshot().equals(expected))return Result.CONFLICT;
   owner.projectionDirty(); // no listener callback after publication can turn success into rejection
   if(permit!=null)permit.require(owner,expected,replacement,operation);
   owner.ownedInventory().publish(prepared);
   projectionRevision=nextProjection;mutationRevision=nextMutation;observed=null;lastOperation=operation;lastRequest=request;lastResult=result;lastJournalPayloadSha256=journalHash;
   return Result.APPLIED;
  }catch(RuntimeException invalid){return Result.REJECTED;}
 }
 private Map<UUID,String> carried(Mirror mirror){
  var result=new HashMap<UUID,String>();
  for(var slot:mirror.slots()){
   var shell=slot.stack();if(shell.isEmpty())continue;shell.setCount(1);shell.remove(org.goldgom.chemicalelements.persistence.ChemistryComponents.LEDGER.get());
   String encoded=TransactionEncoding.canonical(ItemStack.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,owner.getLevel().registryAccess()),shell).getOrThrow());
   for(var instance:slot.instances()){
    String full=org.goldgom.chemicalelements.data.LedgerJson.encode(new org.goldgom.chemicalelements.api.instance.StackLedger(org.goldgom.chemicalelements.api.instance.StackLedger.CURRENT_SCHEMA,0,List.of(instance))).toString();
    if(result.putIfAbsent(instance.id(),encoded+"\n"+full)!=null)throw new IllegalArgumentException("duplicate matter");
   }
  }return result;
 }
 private static String hash(org.goldgom.chemicalelements.api.tx.OwnerKey.MachineInventory owner,Version version,String mirror,String tank,long tankVersion,Marker marker){return TransactionEncoding.sha256(owner.dimension()+"\n"+owner.controllerPos()+"\n"+owner.incarnation()+"\n"+owner.machineType()+"\n"+version.generation()+"\n"+version.mutationRevision()+"\n"+mirror+"\n"+tank+"\n"+tankVersion+"\n"+marker);}
 public static Snapshot replacement(Snapshot expected,String canonicalMirror,String tankNbt){return new Snapshot(expected.owner(),expected.version(),canonicalMirror,TransactionEncoding.sha256(canonicalMirror),tankNbt,expected.tankMutationRevision(),expected.projection(),hash(expected.owner(),expected.version(),canonicalMirror,tankNbt,expected.tankMutationRevision(),expected.projection()));}
 private String contentDigest(Snapshot s){var m=image(s);return TransactionEncoding.sha256(TransactionEncoding.mirror(new Mirror(0,m.slots(),m.tanks(),m.energy(),m.control()),owner.getLevel().registryAccess())+"\n"+s.tankNbt());}
 void save(CompoundTag parent){
  if(quarantine!=null){parent.put(KEY,quarantine.copy());return;}
  var tag=new CompoundTag();tag.putInt("schema",2);tag.putString("journal_payload_sha256",lastJournalPayloadSha256);tag.putUUID("generation",projectionGeneration);tag.putLong("revision",projectionRevision);tag.putString("request",lastRequest);tag.putString("result",lastResult);if(lastOperation!=null)tag.putUUID("operation",lastOperation);parent.put(KEY,tag);
 }
 void load(CompoundTag parent){
  lifecycle();
  if(!parent.contains(KEY)){projectionGeneration=UUID.randomUUID();projectionRevision=0;lastOperation=null;lastRequest="";lastResult="";lastJournalPayloadSha256="";quarantine=null;return;}
  Tag raw=parent.get(KEY);
  try{
   if(!(raw instanceof CompoundTag tag))throw new IllegalArgumentException("metadata type");
   long revision=tag.getLong("revision");
   int schema=tag.getInt("schema");
   var keys=new HashSet<>(revision==0?Set.of("schema","generation","revision","request","result"):Set.of("schema","generation","revision","request","result","operation"));
   if(schema==2)keys.add("journal_payload_sha256");
   if(!tag.getAllKeys().equals(keys)||!tag.contains("schema",Tag.TAG_INT)||(schema!=1&&schema!=2)||!tag.hasUUID("generation")||!tag.contains("revision",Tag.TAG_LONG)||revision<0||!tag.contains("request",Tag.TAG_STRING)||!tag.contains("result",Tag.TAG_STRING))throw new IllegalArgumentException("metadata shape");
   String request=tag.getString("request"),result=tag.getString("result");
   if(revision==0?(!request.isEmpty()||!result.isEmpty()):(!tag.hasUUID("operation")||!request.matches("[0-9a-f]{64}")||!result.matches("[0-9a-f]{64}")))throw new IllegalArgumentException("metadata digest");
   String journalHash=schema==1?"":tag.getString("journal_payload_sha256");
   if(schema==2&&(!tag.contains("journal_payload_sha256",Tag.TAG_STRING)||(!journalHash.isEmpty()&&!journalHash.matches("[0-9a-f]{64}"))||(revision==0&&!journalHash.isEmpty())))throw new IllegalArgumentException("journal metadata digest");
   projectionGeneration=tag.getUUID("generation");projectionRevision=revision;lastOperation=revision==0?null:tag.getUUID("operation");lastRequest=request;lastResult=result;lastJournalPayloadSha256=journalHash;quarantine=null;
  }catch(RuntimeException invalid){quarantine=Objects.requireNonNull(raw).copy();}
 }
}

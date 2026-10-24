package org.goldgom.chemicalelements.machine;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;

/** T0 contract only. No implementation, codec, inventory or production installation.
 * B owns durable CAS and whole-mirror reconciliation. Receipts never authorize appending matter.
 * Requests bind the complete slot/port layout, including empty destinations; B must independently
 * validate the schema, canonical full payload, conservation and expected revisions before reserve.
 * UNKNOWN/CORRUPT/UNAVAILABLE do not authorize retrying material delivery. */
public interface MachineTransactions {
 Receipt reserve(ServerLevel level, Request request);
 Receipt validate(ServerLevel level, OperationKey key);
 Receipt commit(ServerLevel level, OperationKey key);
 Receipt cancel(ServerLevel level, OperationKey key);
 Receipt query(ServerLevel level, OperationKey key);
 Recovery inspectRecovery(ServerLevel level, Destination destination, ObservedMirror observed);
 Receipt reconcile(ServerLevel level, OperationKey key);
 record Destination(ResourceLocation dimension,long controllerPos,UUID incarnation,ResourceLocation machineType){
  public Destination{Objects.requireNonNull(dimension);Objects.requireNonNull(incarnation);Objects.requireNonNull(machineType);}
 }
 record OperationKey(UUID operation,Destination destination,String payloadSha256){
  public OperationKey{Objects.requireNonNull(operation);Objects.requireNonNull(destination);digest(payloadSha256);}
 }
 record CatalogVersion(long epoch,String sha256){public CatalogVersion{nonnegative(epoch);digest(sha256);}}
 record SlotImage(int index,ItemStack stack,List<InstanceRecord> instances){
  public SlotImage{
   nonnegative(index);stack=Objects.requireNonNull(stack).copy();instances=List.copyOf(instances);uniqueInstances(instances);
   if(stack.isEmpty()){if(!instances.isEmpty())throw new IllegalArgumentException("empty slot has instances");}
   else{
    if(stack.getCount()!=instances.size())throw new IllegalArgumentException("ledger/count mismatch");
    if(!ChemistryComponents.ready())throw new IllegalStateException("ledger component not registered");
    var payload=stack.get(ChemistryComponents.LEDGER.get());
    if(payload==null||!payload.ledger().orElseThrow(()->new IllegalArgumentException("unreadable ledger")).instances().equals(instances))throw new IllegalArgumentException("component/instance mismatch");
   }
  }
  @Override public ItemStack stack(){return stack.copy();}
 }
 record TankImage(String port,UUID identity,MolAmount capacity,long revision,List<InstanceRecord> parcels){
  public TankImage{nonempty(port);Objects.requireNonNull(identity);Objects.requireNonNull(capacity);nonnegative(revision);parcels=List.copyOf(parcels);uniqueInstances(parcels);
   var total=MolAmount.ZERO;
   for(var p:parcels)total=total.add(p.remaining().totalMol()).add(p.adsorbed().totalMol()).add(p.poreSolution().waterMol()).add(p.poreSolution().dissolved().totalMol()).add(p.poreSolution().suspended().totalMol());
   if(total.compareTo(capacity)>0)throw new IllegalArgumentException("tank capacity");
   // Nuclides describe nuclei, not an extra molecular inventory. B checks physical legality.
  }
 }
 record EnergyImage(BigInteger stored,BigInteger capacity,long revision){
  public EnergyImage{Objects.requireNonNull(stored);Objects.requireNonNull(capacity);nonnegative(revision);if(stored.signum()<0||capacity.signum()<0||stored.compareTo(capacity)>0)throw new IllegalArgumentException("energy capacity");}
 }
 /** attributes are versioned non-material control values, never an alternate inventory. */
 record ControlImage(String phase,long progressTicks,long requiredTicks,Map<String,String> attributes){
  public ControlImage{nonempty(phase);nonnegative(progressTicks);nonnegative(requiredTicks);if(progressTicks>requiredTicks)throw new IllegalArgumentException("progress");attributes=Map.copyOf(attributes);}
 }
 record Mirror(long revision,List<SlotImage> slots,List<TankImage> tanks,EnergyImage energy,ControlImage control){
  public Mirror{nonnegative(revision);slots=List.copyOf(slots);tanks=List.copyOf(tanks);Objects.requireNonNull(energy);Objects.requireNonNull(control);
   var indexes=new HashSet<Integer>();var ports=new HashSet<String>();var identities=new HashSet<UUID>();var matter=new HashSet<UUID>();
   for(var slot:slots){if(!indexes.add(slot.index()))throw new IllegalArgumentException("duplicate slot");for(var x:slot.instances())if(!matter.add(x.id()))throw new IllegalArgumentException("duplicate matter identity");}
   for(var tank:tanks){if(!ports.add(tank.port())||!identities.add(tank.identity()))throw new IllegalArgumentException("duplicate tank");for(var x:tank.parcels())if(!matter.add(x.id()))throw new IllegalArgumentException("duplicate matter identity");}
  }
 }
 record Request(OperationKey key,CatalogVersion catalog,ReactionProposal proposal,String canonicalProposal,Mirror before,Mirror after){
  public Request{Objects.requireNonNull(key);Objects.requireNonNull(catalog);Objects.requireNonNull(proposal);nonempty(canonicalProposal);Objects.requireNonNull(before);Objects.requireNonNull(after);
   var a=new TreeSet<Integer>();var b=new TreeSet<Integer>();before.slots().forEach(x->a.add(x.index()));after.slots().forEach(x->b.add(x.index()));if(!a.equals(b))throw new IllegalArgumentException("slot layout changed");
   var x=new TreeMap<String,UUID>();var y=new TreeMap<String,UUID>();before.tanks().forEach(t->x.put(t.port(),t.identity()));after.tanks().forEach(t->y.put(t.port(),t.identity()));if(!x.equals(y))throw new IllegalArgumentException("port identity changed");
  }
 }
 record ObservedMirror(long projectionRevision,String digest,Optional<UUID> lastOperation,Mirror image){
  public ObservedMirror{nonnegative(projectionRevision);MachineTransactions.digest(digest);Objects.requireNonNull(lastOperation);Objects.requireNonNull(image);if(projectionRevision!=image.revision())throw new IllegalArgumentException("projection revision mismatch");}
 }
 enum State{UNKNOWN,RESERVED,COMMITTED,CANCELLED,REJECTED,CONFLICT,CORRUPT,UNAVAILABLE}
 record Receipt(OperationKey key,State state,long authoritativeRevision,Optional<String> mirrorSha256,String reason){
  public Receipt{Objects.requireNonNull(key);Objects.requireNonNull(state);nonnegative(authoritativeRevision);Objects.requireNonNull(mirrorSha256).ifPresent(MachineTransactions::digest);Objects.requireNonNull(reason);}
 }
 enum RecoveryAction{NONE,FROZEN,RECONCILE_REQUIRED,QUARANTINED,UNAVAILABLE}
 record Recovery(Destination destination,RecoveryAction action,Optional<Receipt> latest,Optional<String> authoritativeDigest,String reason){
  public Recovery{Objects.requireNonNull(destination);Objects.requireNonNull(action);Objects.requireNonNull(latest);Objects.requireNonNull(authoritativeDigest).ifPresent(MachineTransactions::digest);Objects.requireNonNull(reason);if(latest.isPresent()&&!latest.get().key().destination().equals(destination))throw new IllegalArgumentException("recovery destination mismatch");}
 }
 private static void nonnegative(long value){if(value<0)throw new IllegalArgumentException("negative revision/quantity");}
 private static void nonempty(String value){if(Objects.requireNonNull(value).isBlank())throw new IllegalArgumentException("blank value");}
 private static void digest(String value){if(!Objects.requireNonNull(value).matches("[0-9a-f]{64}"))throw new IllegalArgumentException("expected canonical SHA256");}
 private static void uniqueInstances(List<InstanceRecord> values){var ids=new HashSet<UUID>();for(var x:values)if(!ids.add(x.id()))throw new IllegalArgumentException("duplicate instance");}
}

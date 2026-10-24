package org.goldgom.chemicalelements.machine;

import java.math.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;

/** Single-owner sealed tank. Units are mol quanta, not buckets or floating point concentrations. */
public final class MaterialTank {
 private Composition material=Composition.EMPTY;
 private final MolAmount capacity;
 private long revision,mutationRevision;
 public long mutationRevision(){return mutationRevision;}
 public MolAmount capacity(){return capacity;}
 private final Runnable dirty;
 public MaterialTank(MolAmount capacity){this(capacity,()->{});}
 public MaterialTank(MolAmount capacity,Runnable dirty){this.capacity=Objects.requireNonNull(capacity);this.dirty=Objects.requireNonNull(dirty);}
 public Composition contents(){return material;}public long revision(){return revision;}
 public MolAmount free(){return capacity.subtract(material.totalMol());}
 /** Display-only basis points, computed from the exact authoritative mol ledger. */
 public int fillBasisPoints(){return capacity.isZero()?0:material.totalMol().quanta().multiply(BigInteger.valueOf(10000)).divide(capacity.quanta()).intValueExact();}
 public record Transfer(long sourceRevision,long targetRevision,Composition material){}
 public Transfer preview(MaterialTank target,MolAmount budget,Predicate<ResourceLocation> selected){
  if(target==this)throw new IllegalArgumentException("self transfer");
  BigInteger available=budget.quanta().min(target.free().quanta());var moved=new TreeMap<ResourceLocation,MolAmount>();
  for(var entry:material.amounts().entrySet())if(selected.test(entry.getKey())&&available.signum()>0){var amount=entry.getValue().quanta().min(available);moved.put(entry.getKey(),new MolAmount(amount));available=available.subtract(amount);}
  return new Transfer(revision,target.revision,new Composition(moved));
 }
 public boolean commit(MaterialTank target,Transfer plan){
  if(target==this||revision!=plan.sourceRevision||target.revision!=plan.targetRevision)return false;
  if(plan.material.totalMol().compareTo(target.free())>0)return false;
  Composition remaining;try{remaining=material.subtract(plan.material);}catch(RuntimeException failure){return false;}
  var combined=target.material.add(plan.material);long next=Math.incrementExact(mutationRevision),targetNext=Math.incrementExact(target.mutationRevision);long r=Math.incrementExact(revision),tr=Math.incrementExact(target.revision);material=remaining;target.material=combined;revision=r;target.revision=tr;mutationRevision=next;target.mutationRevision=targetNext;dirty.run();target.dirty.run();return true;
 }
 /** Transaction owner only; no network setter or source creation. */
 public void restore(Composition value,long revision){if(revision<0||value.totalMol().compareTo(capacity)>0)throw new IllegalArgumentException("tank overflow/revision");long next=Math.incrementExact(mutationRevision);material=value;this.revision=revision;mutationRevision=next;dirty.run();}
 public CompoundTag save(){var tag=new CompoundTag();tag.putInt("schema",1);tag.putString("capacity_quanta",capacity.quanta().toString());tag.putLong("revision",revision);var amounts=new CompoundTag();material.amounts().forEach((id,mol)->amounts.putString(id.toString(),mol.decimal().toPlainString()));tag.put("amounts",amounts);return tag;}
 public void load(CompoundTag tag){if(tag.getInt("schema")!=1)throw new IllegalStateException("unknown tank schema");if(tag.contains("capacity_quanta")&&!tag.getString("capacity_quanta").equals(capacity.quanta().toString()))throw new IllegalStateException("tank capacity mismatch");var amounts=new TreeMap<ResourceLocation,MolAmount>();var values=tag.getCompound("amounts");for(var id:values.getAllKeys())amounts.put(ResourceLocation.parse(id),MolAmount.parseDecimal(values.getString(id)));restore(new Composition(amounts),tag.getLong("revision"));}
}

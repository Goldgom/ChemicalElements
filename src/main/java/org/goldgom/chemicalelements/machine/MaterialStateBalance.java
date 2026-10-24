package org.goldgom.chemicalelements.machine;

import java.math.BigDecimal;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.nuclear.NuclideId;

/** Guard for the current chemical-only materializer contract: stored material heat and isotope
 * inventory must pass through unchanged. Explicit thermal/nuclear exchange needs a wider atomic
 * settlement contract before this guard can be replaced; electrical work alone is not that contract. */
public final class MaterialStateBalance {
 private MaterialStateBalance(){}
 public record Totals(BigDecimal storedHeatJ,Map<NuclideId,MolAmount> nuclides){
  public Totals{Objects.requireNonNull(storedHeatJ);nuclides=Collections.unmodifiableMap(new TreeMap<>(nuclides));}
 }
 public static Totals sum(Collection<InstanceRecord> instances){
  BigDecimal heat=BigDecimal.ZERO;var nuclides=new TreeMap<NuclideId,MolAmount>();
  for(var instance:instances){heat=heat.add(instance.thermal().energyJ());instance.nuclides().amounts().forEach((id,amount)->{if(!amount.isZero())nuclides.merge(id,amount,MolAmount::add);});}
  return new Totals(heat,nuclides);
 }
 public static void requireUnchanged(Collection<InstanceRecord> input,Collection<InstanceRecord> output){
  var before=sum(input);var after=sum(output);
  if(before.storedHeatJ().compareTo(after.storedHeatJ())!=0)throw new IllegalStateException("materialization changed stored heat without atomic thermal exchange; inputs retained");
  if(!before.nuclides().equals(after.nuclides()))throw new IllegalStateException("materialization changed nuclides without atomic nuclear exchange; inputs retained");
 }
}

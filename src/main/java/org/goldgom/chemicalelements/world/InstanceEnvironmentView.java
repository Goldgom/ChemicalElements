package org.goldgom.chemicalelements.world;
import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.chem.*;
/** Pure read-only projection of a REAL B snapshot. D must authorize equipment/range before calling;
 * this class never resolves nominal templates, initializes material or writes/caches client state. */
public final class InstanceEnvironmentView {
 public record View(UUID identity,long revision,long epoch,Map<Phase,Composition> phases,
                    org.goldgom.chemicalelements.api.thermal.ThermalState thermal,
                    org.goldgom.chemicalelements.api.fluid.SolutionState pores,
                    org.goldgom.chemicalelements.api.nuclear.NuclideInventory nuclides,
                    Composition adsorbed,Set<ResourceLocation> unknownSpecies) {
  public View{phases=Map.copyOf(phases);unknownSpecies=Set.copyOf(unknownSpecies);}
 }
 private InstanceEnvironmentView(){}
 public static View of(InstanceRecord record,RegistrySnapshot catalog){
  Map<Phase,Map<ResourceLocation,MolAmount>> grouped=new EnumMap<>(Phase.class);Set<ResourceLocation> unknown=new TreeSet<>();
  record.remaining().amounts().forEach((id,n)->{var definition=catalog.species().get(id);if(definition==null)unknown.add(id);else grouped.computeIfAbsent(definition.phase(),p->new TreeMap<>()).put(id,n);});
  Map<Phase,Composition> phases=new EnumMap<>(Phase.class);grouped.forEach((p,c)->phases.put(p,new Composition(c)));
  return new View(record.id(),record.revision(),catalog.epoch(),phases,record.thermal(),record.poreSolution(),record.nuclides(),record.adsorbed(),unknown);
 }
}

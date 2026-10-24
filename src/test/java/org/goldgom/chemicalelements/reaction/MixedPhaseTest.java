package org.goldgom.chemicalelements.reaction;
import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.thermal.DiluteWaterPhase;
public final class MixedPhaseTest {
 static ResourceLocation id(String s){return ResourceLocation.parse("chemicalelements:"+s);}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var h=new ElementDefinition(id("hydrogen"),1,"H",BigDecimal.ONE,DataBasis.GAME_CONVENTION,"fixture");
  var o=new ElementDefinition(id("oxygen"),8,"O",new BigDecimal("16"),DataBasis.GAME_CONVENTION,"fixture");
  Map<ResourceLocation,SpeciesDefinition> species=new HashMap<>();
  for(var entry:Map.of("water",Phase.LIQUID,"ice",Phase.SOLID,"water_vapor",Phase.GAS).entrySet())species.put(id(entry.getKey()),new SpeciesDefinition(id(entry.getKey()),Map.of(id("hydrogen"),2,id("oxygen"),1),0,new BigDecimal("18"),entry.getValue(),DataBasis.GAME_CONVENTION,"fixture"));
  species.put(id("solute"),new SpeciesDefinition(id("solute"),Map.of(id("hydrogen"),1),0,BigDecimal.ONE,Phase.AQUEOUS,DataBasis.GAME_CONVENTION,"synthetic nonvolatile fixture"));
  var catalog=new RegistrySnapshot(1,"fixture",Map.of(h.id(),h,o.id(),o),species,Map.of());
  var real=new org.goldgom.chemicalelements.api.instance.InstanceRecord(UUID.randomUUID(),new Composition(Map.of(id("water"),MolAmount.ONE,id("orphan"),MolAmount.ONE)),new org.goldgom.chemicalelements.api.instance.Provenance("fixture","fixture",0,1,Map.of()),7);
  var view=org.goldgom.chemicalelements.world.InstanceEnvironmentView.of(real,catalog);
  check(view.identity().equals(real.id())&&view.revision()==7);check(view.unknownSpecies().contains(id("orphan")));check(view.thermal()==real.thermal()&&view.pores()==real.poreSolution()&&view.nuclides()==real.nuclides());check(real.remaining().totalMol().equals(MolAmount.parseDecimal("2")));
  var unknown=DiluteWaterPhase.separate(real.remaining(),true,new BigDecimal("273.10"),new BigDecimal("101325"),BigDecimal.ZERO,new BigDecimal("1000"),MolAmount.ONE,catalog);
  check(unknown.status().equals("UNMODELED"));check(unknown.remaining().equals(real.remaining()));check(unknown.output().equals(Composition.EMPTY));check(unknown.energyConsumed().signum()==0&&unknown.heatReleased().signum()==0);
  var input=new Composition(Map.of(id("water"),MolAmount.parseDecimal("55"),id("solute"),MolAmount.parseDecimal("0.01")));
  var p=DiluteWaterPhase.separate(input,true,new BigDecimal("273.10"),new BigDecimal("101325"),BigDecimal.ZERO,new BigDecimal("1000000"),MolAmount.parseDecimal("100"),catalog);
  check(p.status().equals("READY"));check(p.remaining().amounts().get(id("solute")).equals(input.amounts().get(id("solute"))));
  check(p.remaining().amounts().get(id("water")).quanta().signum()>0);check(p.remaining().add(p.output()).inventory(catalog).equals(input.inventory(catalog)));check(p.heatReleased().signum()>0);
  var warm=DiluteWaterPhase.separate(input,true,new BigDecimal("273.15"),new BigDecimal("101325"),BigDecimal.ZERO,new BigDecimal("1000000"),MolAmount.ONE,catalog);check(!warm.status().equals("READY"));check(warm.remaining().equals(input));
  var gas=DiluteWaterPhase.separate(input,false,new BigDecimal("373.20"),new BigDecimal("101325"),new BigDecimal("406.5"),BigDecimal.ZERO,MolAmount.ONE,catalog);check(gas.status().equals("READY"));check(gas.output().totalMol().equals(MolAmount.parseDecimal("0.01")));check(gas.energyConsumed().compareTo(new BigDecimal("406.5"))==0);
  var hot=new org.goldgom.chemicalelements.api.thermal.ThermalState(new BigDecimal("100.000000000000000001"),new BigDecimal("350"),Map.of(),3);
  var cold=new org.goldgom.chemicalelements.api.thermal.ThermalState(new BigDecimal("20.123456789012345678"),new BigDecimal("300"),Map.of(),2);
  var capacity=new org.goldgom.chemicalelements.thermal.InstanceHeatExchange.Capacity(BigDecimal.ONE,new BigDecimal("290"),new BigDecimal("400"),"fixture");
  var heat=org.goldgom.chemicalelements.thermal.InstanceHeatExchange.exchange(hot,cold,capacity,capacity,new BigDecimal("0.000000000000000001"));
  check(heat.status().equals("READY"));check(heat.a().energyJ().add(heat.b().energyJ()).equals(hot.energyJ().add(cold.energyJ())));check(heat.transferredJ().compareTo(new BigDecimal("1e-18"))==0);
  System.out.println("PASS: exact decimal heat transfer and real-instance read-only view; mixed dilute freezing partial endpoint, solute/element/heat conservation, warm rejection unchanged, energy-limited evaporation");
 }
}

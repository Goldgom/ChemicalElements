package org.goldgom.chemicalelements.reaction;
import org.goldgom.chemicalelements.nuclear.*;
import java.math.*;
import java.util.*;
public final class NuclearInvariantTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var parent=new DecayIntegrator.Nuclide(6,14,0);var daughter=new DecayIntegrator.Nuclide(7,14,0);var excited=new DecayIntegrator.Nuclide(7,14,1);
  var emission=List.of(new DecayIntegrator.Emission("electron",0,-1,1),new DecayIntegrator.Emission("antineutrino",0,0,1));
  var rule=new BranchingDecay.Rule(parent,1,List.of(new BranchingDecay.Branch("a",new BigDecimal("0.3"),daughter,emission,BigDecimal.ONE),new BranchingDecay.Branch("b",new BigDecimal("0.7"),excited,emission,BigDecimal.ONE)),"synthetic branch/half-life fixture, not physical constants");
  var initial=new BranchingDecay.State(new DecayIntegrator.State(Map.of(parent,BigInteger.valueOf(10001)),Map.of(),Map.of(),BigDecimal.ZERO),Map.of());
  var state=initial;
  for(int i=0;i<200;i++){
   state=BranchingDecay.tick(state,List.of(rule),0.05,true);
   BigInteger total=state.inventory().nuclides().values().stream().reduce(BigInteger.ZERO,BigInteger::add);check(total.equals(BigInteger.valueOf(10001)));
   check(state.inventory().nuclides().values().stream().allMatch(n->n.signum()>=0));
   var restored=new BranchingDecay.State(new DecayIntegrator.State(state.inventory().nuclides(),state.inventory().residual(),state.inventory().emitted(),state.inventory().energy()),state.branchDebt());
   check(BranchingDecay.tick(restored,List.of(rule),0.05,true).equals(BranchingDecay.tick(state,List.of(rule),0.05,true)));
  }
  check(BranchingDecay.tick(state,List.of(rule),100,false)==state);
  var neutron=new DecayIntegrator.Emission("neutron",1,0,1);var c13=new DecayIntegrator.Nuclide(6,13,0);
  var capture=new NuclearReaction.Rule("synthetic_capture",Map.of(c13,1),Map.of(parent,1),List.of(neutron),List.of(),new BigDecimal("-2"),Set.of("nuclear_facility"),"game_convention","synthetic energy test only");
  var stock=new DecayIntegrator.State(Map.of(c13,BigInteger.TEN),Map.of(),Map.of("neutron",BigInteger.valueOf(4)),BigDecimal.ZERO);
  var result=NuclearReaction.apply(stock,capture,Set.of("nuclear_facility"),new BigDecimal("6"));check(result.extent().equals(BigInteger.valueOf(3)));check(result.consumedEnergy().equals(new BigDecimal("6")));check(result.state().emitted().get("neutron").equals(BigInteger.ONE));
  check(NuclearReaction.apply(stock,capture,Set.of(),new BigDecimal("6")).state()==stock);
  boolean rejected=false;try{new NuclearReaction.Rule("bad",Map.of(c13,1),Map.of(parent,1),List.of(),List.of(),BigDecimal.ZERO,Set.of(),"fixture","fixture");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  var c12=new DecayIntegrator.Nuclide(6,12,0);
  var doubleCapture=new NuclearReaction.Rule("duplicate_particle_fixture",Map.of(c12,1),Map.of(parent,1),List.of(neutron,neutron),List.of(),BigDecimal.ZERO,Set.of(),"fixture","synthetic two-neutron accounting test");
  var doubleStock=new DecayIntegrator.State(Map.of(c12,BigInteger.TEN),Map.of(),Map.of("neutron",BigInteger.valueOf(3)),BigDecimal.ZERO);
  var doubleResult=NuclearReaction.apply(doubleStock,doubleCapture,Set.of(),BigDecimal.ZERO);
  check(doubleResult.extent().equals(BigInteger.ONE));check(doubleResult.state().emitted().get("neutron").equals(BigInteger.ONE));
  rejected=false;try{new DecayIntegrator.State(Map.of(),Map.of(),Map.of("neutron",BigInteger.ONE.negate()),BigDecimal.ZERO);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  System.out.println("PASS: duplicate particle demand aggregated; negative particle stock rejected; " );
  System.out.println("PASS: 200 branching ticks; nuclide conservation; residual snapshot restart equivalence; unload pause; particle/energy-limited capture; invalid nuclear rule rejection");
 }
}

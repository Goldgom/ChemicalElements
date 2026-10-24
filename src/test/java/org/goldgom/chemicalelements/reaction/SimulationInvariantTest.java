package org.goldgom.chemicalelements.reaction;

import org.goldgom.chemicalelements.fluid.FiniteVolume;
import org.goldgom.chemicalelements.world.NaturalWaterInitialization;
import org.goldgom.chemicalelements.thermal.ThermalExchange;
import java.math.*;
import java.util.*;

/** Standalone deterministic harness; run with -ea. MC integration tests are separate. */
public final class SimulationInvariantTest {
    private static BigInteger n(long n){return BigInteger.valueOf(n);}
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        Random random=new Random(418);
        for(int i=0;i<1000;i++){
            BigInteger v=n(1+random.nextInt(100000));var p=new FiniteVolume.Parcel(v,Map.of("salt",n(random.nextInt(100))),n(random.nextInt(10000)));
            var out=FiniteVolume.distribute(p,Map.of("a",n(random.nextInt(100000)),"b",n(random.nextInt(100000)),"c",n(random.nextInt(100000))));
            var sum=out.values().stream().reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus);check(sum.equals(p));
        }
        var tiny=new FiniteVolume.Parcel(n(100),Map.of("trace",n(1)),n(1));check(FiniteVolume.take(tiny,n(99)).remainder().species().get("trace").equals(n(1)));
        var ctx=new NaturalWaterInitialization.Context(1,"overworld","ocean",-5,"stone",123,"v1");
        var marker=new NaturalWaterInitialization.Marker(ctx,true,FiniteVolume.Parcel.empty());
        check(NaturalWaterInitialization.initialize(marker,ctx,true,n(100),n(100),(c,s,v)->{throw new AssertionError("resampled");})==marker);
        var domain=new CandidateSolver.Domain(new BigDecimal("273"),new BigDecimal("373"),new BigDecimal("100000"),new BigDecimal("102000"),Set.of("electricity"));
        Map<String,CandidateSolver.Species> species=Map.of("water",new CandidateSolver.Species(Map.of("H",2,"O",1),0,"liquid"),"hydrogen",new CandidateSolver.Species(Map.of("H",2),0,"gas"),"oxygen",new CandidateSolver.Species(Map.of("O",2),0,"gas"));
        var rule=new CandidateSolver.Rule("electrolysis","electrolysis",Map.of("water",2),Map.of("hydrogen",2,"oxygen",1),domain,null,20,"game_convention","test condition fixture; no fabricated thermodynamics");
        var solver=new CandidateSolver(species,List.of(rule));
        check(solver.solve(Map.of("water",n(10)),CandidateSolver.Conditions.workbench()).status().equals("CONDITIONS_UNMET"));
        check(solver.solve(Map.of("water",n(10)),new CandidateSolver.Conditions(new BigDecimal("298.15"),new BigDecimal("101325"),Set.of("electricity"))).candidates().getFirst().extent().equals(n(5)));
        check(solver.solve(Map.of("unknown",n(1)),CandidateSolver.Conditions.workbench()).status().equals("UNMODELED"));
        boolean rejected=false;try{new CandidateSolver(species,List.of(new CandidateSolver.Rule("bad","bad",Map.of("water",1),Map.of("hydrogen",1),domain,null,1,"fixture","fixture")));}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        var job=ReactionJob.start("electrolysis","hash",2);check(job.tick(false,true,"hash").elapsed()==0);job=job.tick(true,true,"hash");check(job.elapsed()==1);check(job.tick(true,false,"hash").elapsed()==1);job=job.tick(true,true,"hash");check(job.state()==ReactionJob.State.READY);
        final int[] settled={0};var committed=job.finish((j,complete)->{settled[0]++;return new ReactionJob(j.id(),j.ruleId(),j.catalogHash(),j.elapsed(),j.duration(),ReactionJob.State.COMMITTED);},true,"hash");check(committed.finish((j,c)->{throw new AssertionError();},true,"hash")==committed);check(settled[0]==1);
        var thermal=ThermalExchange.exchange(new ThermalExchange.Body(n(100),BigDecimal.ONE),new ThermalExchange.Body(n(20),BigDecimal.ONE),n(30));check(thermal.a().energy().add(thermal.b().energy()).equals(n(120)));
        BigInteger mol=BigInteger.TEN.pow(18);var salt=new PhaseEquilibrium.Salt("AB",Map.of("A",1,"B",1),List.of(new PhaseEquilibrium.Point(new BigDecimal("298"),BigDecimal.ONE)),"synthetic test fixture, not empirical data");
        var equilibrium=PhaseEquilibrium.solve(Map.of(),Map.of("AB",mol.multiply(n(3))),BigDecimal.ONE,new BigDecimal("298"),List.of(salt),10);check(equilibrium.converged());check(equilibrium.aqueous().get("A").equals(mol));check(equilibrium.solids().get("AB").equals(mol.multiply(n(2))));
        var common=PhaseEquilibrium.solve(Map.of("A",mol),Map.of("AB",mol.multiply(n(3))),BigDecimal.ONE,new BigDecimal("298"),List.of(salt),10);check(common.aqueous().get("B").compareTo(mol)<0);
        var p0=new org.goldgom.chemicalelements.fluid.WaterTransport.Position(0,1,0);var p1=new org.goldgom.chemicalelements.fluid.WaterTransport.Position(0,0,0);
        var c0=new org.goldgom.chemicalelements.fluid.WaterTransport.Cell(tiny,n(100),true,true,1);var c1=new org.goldgom.chemicalelements.fluid.WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(100),true,true,1);
        var flow=org.goldgom.chemicalelements.fluid.WaterTransport.plan(Map.of(p0,c0,p1,c1),List.of(new org.goldgom.chemicalelements.fluid.WaterTransport.Edge(p0,p1)),n(50));check(flow.replacements().get(p0).volume().equals(n(50)));check(flow.replacements().values().stream().reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus).equals(tiny));
        var unloaded=new org.goldgom.chemicalelements.fluid.WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(100),false,true,1);check(org.goldgom.chemicalelements.fluid.WaterTransport.plan(Map.of(p0,c0,p1,unloaded),List.of(new org.goldgom.chemicalelements.fluid.WaterTransport.Edge(p0,p1)),n(50)).replacements().isEmpty());
        var material=new org.goldgom.chemicalelements.fluid.SorptionExchange.Material(n(100),BigDecimal.ONE,n(100),"fixture","fixture");var absorbed=org.goldgom.chemicalelements.fluid.SorptionExchange.absorb(tiny,FiniteVolume.Parcel.empty(),material);check(absorbed.pores().equals(tiny));check(org.goldgom.chemicalelements.fluid.SorptionExchange.release(absorbed.water(),absorbed.pores(),n(100)).water().equals(tiny));
        var gas=new org.goldgom.chemicalelements.atmosphere.AtmosphereTransport.Cell(Map.of("oxygen",n(100)),n(200),BigDecimal.ONE);var far=new org.goldgom.chemicalelements.atmosphere.AtmosphereTransport.Cell(Map.of(),n(0),new BigDecimal("1000"));var diluted=org.goldgom.chemicalelements.atmosphere.AtmosphereTransport.dilute(gas,far,1,2);check(diluted.source().gases().get("oxygen").add(diluted.destination().gases().get("oxygen")).equals(n(100)));check(diluted.source().energy().add(diluted.destination().energy()).equals(n(200)));
        var carbon=new org.goldgom.chemicalelements.nuclear.DecayIntegrator.Nuclide(6,14,0);var nitrogen=new org.goldgom.chemicalelements.nuclear.DecayIntegrator.Nuclide(7,14,0);
        var decay=new org.goldgom.chemicalelements.nuclear.DecayIntegrator.Rule(carbon,nitrogen,1,List.of(new org.goldgom.chemicalelements.nuclear.DecayIntegrator.Emission("electron",0,-1,1),new org.goldgom.chemicalelements.nuclear.DecayIntegrator.Emission("antineutrino",0,0,1)),BigDecimal.ONE,"game_convention","synthetic one-second test half-life, not real C14");
        var nuclei=new org.goldgom.chemicalelements.nuclear.DecayIntegrator.State(Map.of(carbon,n(100)),Map.of(),Map.of(),BigDecimal.ZERO);var decayed=org.goldgom.chemicalelements.nuclear.DecayIntegrator.tick(nuclei,List.of(decay),1,true);check(decayed.nuclides().get(carbon).equals(n(50)));check(decayed.nuclides().get(nitrogen).equals(n(50)));check(org.goldgom.chemicalelements.nuclear.DecayIntegrator.tick(nuclei,List.of(decay),1,false)==nuclei);
        System.out.println("PASS: 1000 flux conservation cases; trace remainder; initialization tombstone; conditions; unknown; imbalance; progress; heat; saturation/common-ion; finite level/unloaded; sorption cycle; atmosphere; nuclear ledger");
    }
}

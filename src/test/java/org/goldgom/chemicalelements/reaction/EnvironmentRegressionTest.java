package org.goldgom.chemicalelements.reaction;
import java.math.*;
import java.util.*;
import org.goldgom.chemicalelements.atmosphere.*;
public final class EnvironmentRegressionTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  Random r=new Random(15);
  for(int i=0;i<1000;i++){
   BigInteger a=BigInteger.valueOf(r.nextInt(10000)),b=BigInteger.valueOf(r.nextInt(10000)),heat=BigInteger.valueOf(r.nextInt(10000));
   var source=new AtmosphereTransport.Cell(Map.of("a",a,"b",b),heat,BigDecimal.ONE);
   var target=new AtmosphereTransport.Cell(Map.of(),BigInteger.ZERO,BigDecimal.ONE);
   BigInteger cap=BigInteger.valueOf(r.nextInt(10000));
   var p=GasCollection.collect(source,target,Set.of("a","b"),cap,cap);
   for(String id:List.of("a","b"))check(p.atmosphere().gases().getOrDefault(id,BigInteger.ZERO).add(p.captured().gases().getOrDefault(id,BigInteger.ZERO)).equals(source.gases().get(id)));
   check(p.atmosphere().energy().add(p.captured().energy()).equals(heat));
   check(p.captured().gases().values().stream().reduce(BigInteger.ZERO,BigInteger::add).equals(cap.min(a.add(b))));
   check(p.equals(GasCollection.collect(source,target,Set.of("b","a"),cap,cap)));
  }
  var unrelated=new PhaseEquilibrium.Salt("XY",Map.of("X",1,"Y",1),List.of(new PhaseEquilibrium.Point(new BigDecimal("500"),BigDecimal.ONE)),"fixture");
  var result=PhaseEquilibrium.solve(Map.of("A",BigInteger.ONE),Map.of(),BigDecimal.ONE,new BigDecimal("298"),List.of(unrelated),10);
  check(result.converged());check(result.aqueous().get("A").equals(BigInteger.ONE));
  boolean rejected=false;try{PhaseEquilibrium.solve(Map.of(),Map.of("XY",BigInteger.ONE),BigDecimal.ONE,new BigDecimal("298"),List.of(unrelated),10);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  var natural=new org.goldgom.chemicalelements.world.NaturalWaterProfile();
  var ctx=new org.goldgom.chemicalelements.world.NaturalWaterInitialization.Context(42,"minecraft:overworld","minecraft:ocean",-10,"minecraft:calcite",99,org.goldgom.chemicalelements.world.NaturalWaterProfile.VERSION);
  var sampled=org.goldgom.chemicalelements.world.NaturalWaterInitialization.initialize(null,ctx,true,BigInteger.TEN.pow(18),BigInteger.ZERO,natural);
  check(sampled.remaining().species().get("chemicalelements:sodium_ion").equals(sampled.remaining().species().get("chemicalelements:chloride_ion")));
  check(sampled.equals(org.goldgom.chemicalelements.world.NaturalWaterInitialization.initialize(null,ctx,true,BigInteger.TEN.pow(18),BigInteger.ZERO,natural)));
  var dry=new org.goldgom.chemicalelements.world.NaturalWaterInitialization.Marker(ctx,true,org.goldgom.chemicalelements.fluid.FiniteVolume.Parcel.empty());
  check(org.goldgom.chemicalelements.world.NaturalWaterInitialization.initialize(dry,ctx,true,BigInteger.TEN.pow(18),BigInteger.ZERO,natural)==dry);
  var domain=new CandidateSolver.Domain(BigDecimal.ONE,new BigDecimal("1000"),BigDecimal.ONE,new BigDecimal("1000000"),Set.of());
  var first=new CandidateSolver.Rule("a","fixture",Map.of("x",2,"y",1),Map.of("p",1),domain,null,1,"fixture","fixture");
  var second=new CandidateSolver.Rule("b","fixture",Map.of("x",1,"y",2),Map.of("q",1),domain,null,1,"fixture","fixture");
  check(CompetingExtents.candidates(first,BigInteger.valueOf(150),Map.of("x",BigInteger.valueOf(300),"y",BigInteger.valueOf(300)),List.of(first,second)).contains(BigInteger.valueOf(100)));
  check(CompetingExtents.candidates(first,BigInteger.valueOf(3),Map.of(),List.of()).size()==3);
  System.out.println("PASS: competition intermediate vertex and small exhaustive extents; 1000 finite collection capacity/material/heat/determinism cases; unrelated temperature domain; relevant unsupported domain rejected");
 }
}

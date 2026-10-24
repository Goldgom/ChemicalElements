package org.goldgom.chemicalelements.gametest.machine;
import java.math.*;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.thermal.*;
import org.goldgom.chemicalelements.api.nuclear.*;
import org.goldgom.chemicalelements.api.fluid.*;
import org.goldgom.chemicalelements.machine.MaterialStateBalance;
public final class MaterialStateBalanceTest {
 private static final NuclideId U=new NuclideId(92,238,0);
 private static InstanceRecord instance(String heat,String mol){return new InstanceRecord(UUID.randomUUID(),Composition.EMPTY,new Provenance("test","balance",0,0,Map.of()),0,new ThermalState(new BigDecimal(heat),new BigDecimal("298.15"),Map.of(),0),new NuclideInventory(Map.of(U,MolAmount.parseDecimal(mol))),SolutionState.empty(),Composition.EMPTY);}
 public static void main(String[] args){int checks=0;
  var input=List.of(instance("123.000000000000000001","2"));
  var output=List.of(instance("100","1.25"),instance("23.000000000000000001","0.75"));
  MaterialStateBalance.requireUnchanged(input,output);checks++;
  MaterialStateBalance.requireUnchanged(output.reversed(),input);checks++;
  var original=MaterialStateBalance.sum(input);
  for(var bad:List.of(List.of(instance("123","2")),List.of(instance("123.000000000000000001","1.999999999999999999")),List.<InstanceRecord>of())){
   try{MaterialStateBalance.requireUnchanged(input,bad);throw new AssertionError("state loss permitted");}catch(IllegalStateException expected){checks++;}
   if(!MaterialStateBalance.sum(input).equals(original))throw new AssertionError("input mutated");checks++;
  }
  MaterialStateBalance.requireUnchanged(List.of(instance("0.00","0")),List.of());checks++;
  try{original.nuclides().clear();throw new AssertionError("mutable totals");}catch(UnsupportedOperationException expected){checks++;}
  System.out.println(checks+" stored heat/nuclide balance assertions passed (not thermal exchange, durable settlement, or game evidence)");
 }
}

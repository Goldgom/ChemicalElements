package org.goldgom.chemicalelements.reaction;
import java.math.*;import java.util.*;
public final class CompetingExtentsTest {
 static CandidateSolver.Rule rule(String id,Map<String,Integer> input){return new CandidateSolver.Rule(id,"fixture",input,Map.of("out",1),new CandidateSolver.Domain(BigDecimal.ONE,new BigDecimal("1000"),BigDecimal.ONE,new BigDecimal("1000000"),Set.of()),BigDecimal.ZERO,1,"fixture","geometry-only synthetic rules, not chemistry");}
 public static void main(String[] args){
  var a=rule("a",Map.of("x",1,"y",1));var b=rule("b",Map.of("y",1,"z",1));var c=rule("c",Map.of("x",1,"z",1));
  for(var scale:List.of(BigInteger.ONE,BigInteger.TEN.pow(80))){var stock=Map.of("x",scale.multiply(BigInteger.valueOf(100)),"y",scale.multiply(BigInteger.valueOf(100)),"z",scale.multiply(BigInteger.valueOf(100)));var max=scale.multiply(BigInteger.valueOf(100));var forward=CompetingExtents.candidates(a,max,stock,List.of(a,b,c));var reverse=CompetingExtents.candidates(a,max,stock,List.of(c,b,a));if(!forward.contains(scale.multiply(BigInteger.valueOf(50)))||!forward.equals(reverse))throw new AssertionError();}
  System.out.println("PASS: three-way exact resource vertex; 80-digit scale; competitor permutation deterministic (finite candidates, not global optimum)");
 }
}

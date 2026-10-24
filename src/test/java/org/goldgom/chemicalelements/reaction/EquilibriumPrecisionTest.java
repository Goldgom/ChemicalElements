package org.goldgom.chemicalelements.reaction;

import java.math.*;
import java.util.*;

/** Synthetic ideal model precision regression, not empirical high-concentration chemistry. */
public final class EquilibriumPrecisionTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var salt=new PhaseEquilibrium.Salt("AB",Map.of("A",1,"B",1),List.of(new PhaseEquilibrium.Point(new BigDecimal("298"),BigDecimal.ONE)),"synthetic exact arithmetic fixture");
        for(int exponent:new int[]{0,20,30,62}){
            BigDecimal litres=BigDecimal.TEN.pow(exponent);
            BigInteger saturated=BigInteger.TEN.pow(18+exponent);
            var excess=saturated.add(BigInteger.ONE);
            var precipitated=PhaseEquilibrium.solve(Map.of("A",excess,"B",excess),Map.of(),litres,new BigDecimal("298"),List.of(salt),4);
            check(precipitated.converged(),"precipitation convergence");
            check(precipitated.solids().getOrDefault("AB",BigInteger.ZERO).equals(BigInteger.ONE),"one quantum must precipitate at exponent "+exponent);
            check(precipitated.aqueous().get("A").equals(saturated),"exact saturation");
            var dissolved=PhaseEquilibrium.solve(Map.of("A",saturated.subtract(BigInteger.ONE),"B",saturated.subtract(BigInteger.ONE)),Map.of("AB",BigInteger.TWO),litres,new BigDecimal("298"),List.of(salt),4);
            check(dissolved.converged(),"dissolution convergence");
            check(dissolved.solids().get("AB").equals(BigInteger.ONE),"one quantum must dissolve at exponent "+exponent);
            check(dissolved.aqueous().get("A").equals(saturated),"no rounded overshoot");
            check(dissolved.aqueous().get("A").add(dissolved.solids().get("AB")).equals(saturated.add(BigInteger.ONE)),"conservation");
        }
        System.out.println("PASS: exact one-quantum precipitation/dissolution at 18,38,48,80-digit inventories");
    }
}

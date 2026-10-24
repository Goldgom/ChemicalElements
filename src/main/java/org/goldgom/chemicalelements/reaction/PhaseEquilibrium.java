package org.goldgom.chemicalelements.reaction;

import java.math.*;
import java.util.*;

/** Ideal dilute concentration-product approximation, not a universal activity model. */
public final class PhaseEquilibrium {
    private static final MathContext MC=MathContext.DECIMAL128;
    public static final BigDecimal QUANTA_PER_MOL=new BigDecimal("1000000000000000000");
    public record Point(BigDecimal kelvin,BigDecimal ksp) { public Point {if(kelvin.signum()<=0||ksp.signum()<=0)throw new IllegalArgumentException("invalid Ksp");} }
    public record Salt(String solid,Map<String,Integer> ions,List<Point> table,String source) {
        public Salt {ions=Map.copyOf(ions);table=table.stream().sorted(Comparator.comparing(Point::kelvin)).toList();if(ions.isEmpty()||ions.values().stream().anyMatch(n->n<=0)||table.isEmpty()||source.isBlank())throw new IllegalArgumentException("invalid salt");for(int i=1;i<table.size();i++)if(table.get(i-1).kelvin.compareTo(table.get(i).kelvin)==0)throw new IllegalArgumentException("duplicate temperature");}
        public BigDecimal at(BigDecimal k){
            if(k.compareTo(table.getFirst().kelvin)<0||k.compareTo(table.getLast().kelvin)>0)throw new IllegalArgumentException("temperature outside measured/model table");
            for(int i=0;i<table.size();i++){Point a=table.get(i);if(k.compareTo(a.kelvin)==0)return a.ksp;if(i+1<table.size()){Point b=table.get(i+1);if(k.compareTo(b.kelvin)<0)return a.ksp.add(b.ksp.subtract(a.ksp).multiply(k.subtract(a.kelvin),MC).divide(b.kelvin.subtract(a.kelvin),MC),MC);}}
            throw new IllegalStateException();
        }
    }
    public record Result(Map<String,BigInteger> aqueous,Map<String,BigInteger> solids,boolean converged,int passes) {}
    public static Result solve(Map<String,BigInteger> aqueous,Map<String,BigInteger> solids,BigDecimal litres,BigDecimal kelvin,List<Salt> salts,int maxPasses){
        if(litres.signum()<=0||maxPasses<1)throw new IllegalArgumentException("invalid solution");
        TreeMap<String,BigInteger> aq=copy(aqueous),so=copy(solids);List<Salt> ordered=salts.stream().sorted(Comparator.comparing(Salt::solid)).toList();
        // Caller validates salt stoichiometry against the species catalog before publishing a snapshot.
        for(int pass=1;pass<=maxPasses;pass++) {boolean changed=false;
            for(Salt salt:ordered){
                // A disjoint model cannot reject an otherwise modeled solution on temperature alone.
                boolean hasSolid=so.getOrDefault(salt.solid,BigInteger.ZERO).signum()>0;
                boolean canPrecipitate=true;
                for(String ion:salt.ions.keySet())if(aq.getOrDefault(ion,BigInteger.ZERO).signum()==0){canPrecipitate=false;break;}
                if(!hasSolid&&!canPrecipitate)continue;
                BigDecimal ksp=salt.at(kelvin);int direction=compareProduct(aq,salt,litres,ksp);if(direction==0)continue;
                BigInteger cap;
                if(direction<0)cap=so.getOrDefault(salt.solid,BigInteger.ZERO);
                else{cap=null;for(var ion:salt.ions.entrySet()){BigInteger n=aq.getOrDefault(ion.getKey(),BigInteger.ZERO).divide(BigInteger.valueOf(ion.getValue()));cap=cap==null?n:cap.min(n);}}
                if(cap==null||cap.signum()==0)continue;
                BigInteger low=BigInteger.ZERO,high=cap;
                while(low.compareTo(high)<0){BigInteger mid=low.add(high).add(BigInteger.ONE).shiftRight(1);Map<String,BigInteger> trial=shift(aq,salt,direction<0?mid:mid.negate());int cmp=compareProduct(trial,salt,litres,ksp);
                    if(direction<0?cmp<=0:cmp>=0)low=mid;else high=mid.subtract(BigInteger.ONE);}
                if(low.signum()>0){BigInteger extent=direction<0?low:low.negate();aq=shift(aq,salt,extent);so.merge(salt.solid,extent.negate(),BigInteger::add);changed=true;}
            }
            if(!changed)return new Result(Map.copyOf(aq),Map.copyOf(so),true,pass);
        }
        return new Result(Map.copyOf(aq),Map.copyOf(so),false,maxPasses);
    }
    private static TreeMap<String,BigInteger> copy(Map<String,BigInteger> input){TreeMap<String,BigInteger> out=new TreeMap<>(input);if(out.values().stream().anyMatch(n->n.signum()<0))throw new IllegalArgumentException("negative inventory");return out;}
    private static TreeMap<String,BigInteger> shift(Map<String,BigInteger> aq,Salt salt,BigInteger extent){TreeMap<String,BigInteger> out=new TreeMap<>(aq);salt.ions.forEach((id,n)->out.merge(id,extent.multiply(BigInteger.valueOf(n)),BigInteger::add));return out;}
    /** Compare Q with Ksp by cross multiplication: no rounded concentration can hide a quantum.
     * The tabulated/interpolated Ksp remains the model value; settlement against that value is exact.
     * Integer multiplication also makes this independent of Map iteration order. */
    private static int compareProduct(Map<String,BigInteger> aq,Salt salt,BigDecimal litres,BigDecimal ksp){
        BigInteger numerator=BigInteger.ONE;
        BigDecimal denominator=BigDecimal.ONE,scale=QUANTA_PER_MOL.multiply(litres);
        for(var ion:salt.ions.entrySet()){
            numerator=numerator.multiply(aq.getOrDefault(ion.getKey(),BigInteger.ZERO).pow(ion.getValue()));
            denominator=denominator.multiply(scale.pow(ion.getValue()));
        }
        return new BigDecimal(numerator).compareTo(ksp.multiply(denominator));
    }
}

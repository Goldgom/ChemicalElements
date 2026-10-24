package org.goldgom.chemicalelements.atmosphere;

import java.math.*;
import java.util.*;

/** Immutable transfers; no constant-concentration boundary and no species deletion on dilution. */
public final class AtmosphereTransport {
    public record Cell(Map<String,BigInteger> gases,BigInteger energy,BigDecimal volume) {
        public Cell {gases=Map.copyOf(gases);if(gases.values().stream().anyMatch(n->n.signum()<0)||energy.signum()<0||volume.signum()<=0)throw new IllegalArgumentException("invalid gas cell");}
        public BigDecimal concentration(String species){return new BigDecimal(gases.getOrDefault(species,BigInteger.ZERO)).divide(new BigDecimal("1e18").multiply(volume),MathContext.DECIMAL128);}
    }
    public record Transfer(Cell source,Cell destination) {}
    public static Transfer transfer(Cell from,Cell to,Map<String,BigInteger> requested){
        TreeMap<String,BigInteger> a=new TreeMap<>(from.gases),b=new TreeMap<>(to.gases);BigInteger amount=BigInteger.ZERO;
        for(var e:requested.entrySet()){if(e.getValue().signum()<0||e.getValue().compareTo(a.getOrDefault(e.getKey(),BigInteger.ZERO))>0)throw new IllegalArgumentException("insufficient gas");a.merge(e.getKey(),e.getValue().negate(),BigInteger::add);b.merge(e.getKey(),e.getValue(),BigInteger::add);amount=amount.add(e.getValue());}
        BigInteger total=from.gases.values().stream().reduce(BigInteger.ZERO,BigInteger::add);
        BigInteger heat=total.signum()==0?BigInteger.ZERO:from.energy.multiply(amount).divide(total);
        return new Transfer(new Cell(a,from.energy.subtract(heat),from.volume),new Cell(b,to.energy.add(heat),to.volume));
    }
    public static Transfer dilute(Cell local,Cell farField,int numerator,int denominator){
        if(numerator<0||denominator<=0||numerator>denominator)throw new IllegalArgumentException("invalid fraction");
        TreeMap<String,BigInteger> moved=new TreeMap<>();local.gases.forEach((id,n)->moved.put(id,n.multiply(BigInteger.valueOf(numerator)).divide(BigInteger.valueOf(denominator))));return transfer(local,farField,moved);
    }
}

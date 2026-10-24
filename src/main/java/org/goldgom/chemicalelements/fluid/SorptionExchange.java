package org.goldgom.chemicalelements.fluid;

import java.math.*;
import java.util.*;

/** Porosity/wettability are catalog properties, including explicit zero-capacity impermeable materials. */
public final class SorptionExchange {
    public record Material(BigInteger poreCapacity,BigDecimal wettability,BigInteger rate,String basis,String source){
        public Material {if(poreCapacity.signum()<0||wettability.signum()<0||wettability.compareTo(BigDecimal.ONE)>0||rate.signum()<0||basis.isBlank()||source.isBlank())throw new IllegalArgumentException("invalid material");}
    }
    public record Exchange(FiniteVolume.Parcel water,FiniteVolume.Parcel pores) {}
    public static Exchange absorb(FiniteVolume.Parcel water,FiniteVolume.Parcel pores,Material material){
        if(pores.volume().compareTo(material.poreCapacity)>0)throw new IllegalArgumentException("pore capacity decreased; migrate or release explicitly");
        BigInteger desired=new BigDecimal(material.rate).multiply(material.wettability).toBigInteger().min(material.poreCapacity.subtract(pores.volume())).min(water.volume());
        var split=FiniteVolume.take(water,desired);return new Exchange(split.remainder(),pores.plus(split.moved()));
    }
    public static Exchange release(FiniteVolume.Parcel water,FiniteVolume.Parcel pores,BigInteger requested){var split=FiniteVolume.take(pores,requested);return new Exchange(water.plus(split.moved()),split.remainder());}
}

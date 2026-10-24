package org.goldgom.chemicalelements.thermal;

import java.math.*;

/** Fixed energy quanta. Heat capacity model and its valid domain must come from catalog provenance. */
public final class ThermalExchange {
    public record Body(BigInteger energy,BigDecimal capacity) {
        public Body {if(energy.signum()<0||capacity.signum()<=0)throw new IllegalArgumentException("invalid thermal state");}
        public BigDecimal temperature(){return new BigDecimal(energy).divide(capacity,MathContext.DECIMAL128);}
    }
    public record Pair(Body a,Body b) {}
    public static Pair exchange(Body a,Body b,BigInteger conductanceBudget){
        if(conductanceBudget.signum()<0)throw new IllegalArgumentException("negative exchange");
        BigDecimal equilibrium=new BigDecimal(a.energy.add(b.energy)).divide(a.capacity.add(b.capacity),MathContext.DECIMAL128);
        BigInteger target=equilibrium.multiply(a.capacity).toBigInteger();
        BigInteger delta=target.subtract(a.energy);BigInteger transfer=delta.abs().min(conductanceBudget);
        if(delta.signum()<0)transfer=transfer.negate();
        return new Pair(new Body(a.energy.add(transfer),a.capacity),new Body(b.energy.subtract(transfer),b.capacity));
    }
    public record Phase(BigInteger sensibleEnergy,BigInteger liquidQuanta,BigInteger latentEnergy) {}
    /** Melting plateau: all unspent energy remains sensible; no latent energy is discarded. */
    public static Phase melt(BigInteger available,BigInteger solidQuanta,BigInteger latentPerQuantum){
        if(available.signum()<0||solidQuanta.signum()<0||latentPerQuantum.signum()<=0)throw new IllegalArgumentException("invalid phase state");
        BigInteger melted=available.divide(latentPerQuantum).min(solidQuanta);BigInteger latent=melted.multiply(latentPerQuantum);
        return new Phase(available.subtract(latent),melted,latent);
    }
}

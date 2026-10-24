package org.goldgom.chemicalelements.fluid.waterbody;

import java.math.BigInteger;
import java.util.*;
import org.goldgom.chemicalelements.fluid.FiniteVolume;

/** Immutable transaction input/output, NOT an inventory store, cache, or independently mutable cell ledger.
 * One connected body's solutes occur exactly once. Dry nonvolatile residue is retained explicitly. */
public record WaterBody(WaterVolume volume, Map<String,BigInteger> solutes, BigInteger energy) {
    public WaterBody {
        Objects.requireNonNull(volume); Objects.requireNonNull(energy);
        if(energy.signum()<0) throw new IllegalArgumentException("negative heat");
        var copy=new TreeMap<String,BigInteger>();
        solutes.forEach((id,n)-> { if(id.isBlank()||n.signum()<0) throw new IllegalArgumentException("invalid solute"); if(n.signum()>0)copy.put(id,n); });
        solutes=Collections.unmodifiableMap(copy);
    }
    public static WaterBody empty() { return new WaterBody(WaterVolume.ZERO,Map.of(),BigInteger.ZERO); }
    public static WaterBody fromLegacy(FiniteVolume.Parcel parcel) {
        return new WaterBody(WaterVolume.ofLegacyLitreQuanta(parcel.volume()),parcel.species(),parcel.energy());
    }
    public WaterBody plus(WaterBody other) {
        var all=new TreeMap<>(solutes); other.solutes.forEach((k,v)->all.merge(k,v,BigInteger::add));
        return new WaterBody(volume.plus(other.volume),all,energy.add(other.energy));
    }
    public record Allocation(Map<String,WaterBody> destinations, WaterBody remainder) {
        public Allocation { destinations=Collections.unmodifiableMap(new TreeMap<>(destinations)); }
    }
    /** Simultaneous proportional split using the original shared concentration, not sequential re-rounding.
     * Floor each destination. All indivisible solute/heat residual stays in source, or (on full withdrawal)
     * goes to the lexically last positive destination. Ordering is stable regardless of input map order. */
    public Allocation allocate(Map<String,WaterVolume> requests) {
        var ordered=new TreeMap<>(requests); BigInteger total=BigInteger.ZERO;
        for(var e:ordered.entrySet()) { if(e.getKey().isBlank())throw new IllegalArgumentException("empty destination"); total=total.add(e.getValue().subquanta()); }
        var available=volume.subquanta();
        if(total.compareTo(available)>0)throw new IllegalArgumentException("insufficient water; no side effects");
        var result=new TreeMap<String,WaterBody>();
        if(total.signum()==0) { ordered.forEach((k,v)->result.put(k,empty()));return new Allocation(result,this); }
        var left=new TreeMap<>(solutes); var heat=energy;
        String last=ordered.entrySet().stream().filter(e->e.getValue().subquanta().signum()>0).map(Map.Entry::getKey).reduce((a,b)->b).orElseThrow();
        for(var e:ordered.entrySet()) {
            var moved=new TreeMap<String,BigInteger>(); var q=e.getValue().subquanta();
            boolean allResidual=total.equals(available)&&e.getKey().equals(last);
            for(var s:solutes.entrySet()) {
                var n=allResidual?left.get(s.getKey()):s.getValue().multiply(q).divide(available);
                moved.put(s.getKey(),n);left.put(s.getKey(),left.get(s.getKey()).subtract(n));
            }
            var h=allResidual?heat:energy.multiply(q).divide(available); heat=heat.subtract(h);
            result.put(e.getKey(),new WaterBody(e.getValue(),moved,h));
        }
        return new Allocation(result,new WaterBody(WaterVolume.ofSubquanta(available.subtract(total)),left,heat));
    }
    public record Evaporation(WaterBody liquidAndResidue, WaterBody vapor) {}
    /** Nonvolatile solutes remain even at dryness. Caller supplies modeled heat carried to explicit vapor owner. */
    public Evaporation evaporate(WaterVolume amount, BigInteger carriedEnergy) {
        if(amount.compareTo(volume)>0||carriedEnergy.signum()<0||carriedEnergy.compareTo(energy)>0
            ||(amount.equals(WaterVolume.ZERO)&&carriedEnergy.signum()!=0)) throw new IllegalArgumentException("invalid evaporation");
        return new Evaporation(new WaterBody(volume.minus(amount),solutes,energy.subtract(carriedEnergy)),
            new WaterBody(amount,Map.of(),carriedEnergy));
    }
}

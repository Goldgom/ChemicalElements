package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import java.util.*;

/** Exact extensive inventory. Volume and energy units are supplied by the caller, never doubles. */
public final class FiniteVolume {
    private FiniteVolume() {}
    public record Parcel(BigInteger volume, Map<String, BigInteger> species, BigInteger energy) {
        public Parcel {
            Objects.requireNonNull(volume); Objects.requireNonNull(energy);
            if (volume.signum() < 0 || energy.signum() < 0) throw new IllegalArgumentException("negative inventory");
            TreeMap<String, BigInteger> copy = new TreeMap<>();
            species.forEach((id, n) -> { if (n.signum() < 0) throw new IllegalArgumentException("negative species"); if (n.signum() > 0) copy.put(id,n); });
            species = Collections.unmodifiableMap(copy);
            if (volume.signum() == 0 && (!species.isEmpty() || energy.signum() != 0)) throw new IllegalArgumentException("use solid/gas pool for dry inventory");
        }
        public static Parcel empty() { return new Parcel(BigInteger.ZERO, Map.of(), BigInteger.ZERO); }
        public Parcel plus(Parcel other) {
            TreeMap<String,BigInteger> all = new TreeMap<>(species);
            other.species.forEach((id,n)->all.merge(id,n,BigInteger::add));
            return new Parcel(volume.add(other.volume), all, energy.add(other.energy));
        }
    }
    public record Split(Parcel moved, Parcel remainder) {}
    public static Split take(Parcel source, BigInteger requested) {
        if (requested.signum() < 0 || requested.compareTo(source.volume) > 0) throw new IllegalArgumentException("invalid transfer volume");
        if (requested.signum() == 0) return new Split(Parcel.empty(),source);
        TreeMap<String,BigInteger> moved = new TreeMap<>(), left = new TreeMap<>();
        source.species.forEach((id,n)-> { BigInteger q=n.multiply(requested).divide(source.volume); moved.put(id,q); left.put(id,n.subtract(q)); });
        BigInteger e=source.energy.multiply(requested).divide(source.volume);
        return new Split(new Parcel(requested,moved,e),new Parcel(source.volume.subtract(requested),left,source.energy.subtract(e)));
    }
    /** Simultaneous source-limited flux allocation; lexical destination order breaks equal remainders. */
    public static Map<String,BigInteger> limit(BigInteger available, Map<String,BigInteger> requested) {
        if (available.signum()<0) throw new IllegalArgumentException("negative available");
        TreeMap<String,BigInteger> result=new TreeMap<>(), remainder=new TreeMap<>();
        BigInteger total=BigInteger.ZERO;
        for (BigInteger n:requested.values()) { if(n.signum()<0)throw new IllegalArgumentException("negative flux"); total=total.add(n); }
        if(total.compareTo(available)<=0) return Collections.unmodifiableMap(new TreeMap<>(requested));
        BigInteger assigned=BigInteger.ZERO;
        for(var entry:new TreeMap<>(requested).entrySet()) { BigInteger[] qr=entry.getValue().multiply(available).divideAndRemainder(total); result.put(entry.getKey(),qr[0]); remainder.put(entry.getKey(),qr[1]); assigned=assigned.add(qr[0]); }
        List<String> order=new ArrayList<>(result.keySet());
        order.sort(Comparator.<String,BigInteger>comparing(remainder::get).reversed().thenComparing(Comparator.naturalOrder()));
        int extras=available.subtract(assigned).intValueExact();
        for(int i=0;i<extras;i++)result.compute(order.get(i),(k,n)->n.add(BigInteger.ONE));
        return Collections.unmodifiableMap(result);
    }
    /** Source residual carries sub-quantum solute; no separate endpoint rounding. */
    public static Map<String,Parcel> distribute(Parcel source, Map<String,BigInteger> requests) {
        Map<String,BigInteger> volumes=limit(source.volume,requests);
        TreeMap<String,Parcel> out=new TreeMap<>(); Parcel remaining=source;
        for(var entry:volumes.entrySet()) { Split split=take(remaining,entry.getValue()); out.put(entry.getKey(),split.moved); remaining=split.remainder; }
        if(out.containsKey("$source"))throw new IllegalArgumentException("reserved destination");
        out.put("$source",remaining); return Collections.unmodifiableMap(out);
    }
}

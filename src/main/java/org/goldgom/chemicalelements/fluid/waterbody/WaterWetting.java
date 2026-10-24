package org.goldgom.chemicalelements.fluid.waterbody;

import java.math.*;
import java.util.*;
import org.goldgom.chemicalelements.fluid.SorptionExchange;

/** Snapshot-only wetting planner. Owner must persist this entire result with admission, cursors and tombstones. */
public final class WaterWetting {
    private WaterWetting() {}
    public record Target(String identity, long revision, boolean loaded, boolean directlyTouching,
                         boolean initializationTombstone, WaterBody pores, SorptionExchange.Material material) {
        public Target { if(identity.isBlank()||revision<0)throw new IllegalArgumentException("invalid target");Objects.requireNonNull(pores); }
    }
    public enum Status { READY, ALREADY_INITIALIZED, UNLOADED, UNMODELED, CONFLICT, INSUFFICIENT_WATER }
    public record Plan(Status status, WaterBody before, WaterBody remaining, Map<String,WaterBody> pores,
                       Map<String,Long> expectedRevisions, Set<String> newTombstones, WaterVolume deficit) {
        public Plan { pores=Map.copyOf(pores);expectedRevisions=Map.copyOf(expectedRevisions);newTombstones=Set.copyOf(newTombstones); }
    }
    public static Plan initialize(WaterBody source, boolean bodyAdmissionTombstone, Collection<Target> contacts) {
        if(bodyAdmissionTombstone)return reject(Status.ALREADY_INITIALIZED,source,WaterVolume.ZERO);
        return plan(source,contacts,true);
    }
    public static Plan progressive(WaterBody source, Collection<Target> contacts) { return plan(source,contacts,false); }
    private static Plan plan(WaterBody source,Collection<Target> contacts,boolean initial) {
        var unique=new TreeMap<String,Target>();
        for(var t:contacts) {
            if(!t.directlyTouching())continue;
            var old=unique.putIfAbsent(t.identity(),t);
            if(old!=null&&!old.equals(t))return reject(Status.CONFLICT,source,WaterVolume.ZERO);
        }
        var requests=new TreeMap<String,WaterVolume>(); var versions=new TreeMap<String,Long>();var markers=new TreeSet<String>();
        for(var t:unique.values()) {
            if(!t.loaded())return reject(Status.UNLOADED,source,WaterVolume.ZERO);
            if(t.material()==null)return reject(Status.UNMODELED,source,WaterVolume.ZERO);
            var cap=WaterVolume.ofLegacyLitreQuanta(t.material().poreCapacity());
            if(t.pores().volume().compareTo(cap)>0)return reject(Status.CONFLICT,source,WaterVolume.ZERO);
            versions.put(t.identity(),t.revision());
            if(initial&&t.initializationTombstone())continue;
            var need=cap.minus(t.pores().volume());
            if(!initial)need=need.min(WaterVolume.ofLegacyLitreQuanta(new BigDecimal(t.material().rate()).multiply(t.material().wettability()).toBigInteger()));
            requests.put(t.identity(),need);
            if(initial)markers.add(t.identity());
        }
        var total=requests.values().stream().reduce(WaterVolume.ZERO,WaterVolume::plus);
        if(total.compareTo(source.volume())>0) {
            if(initial)return reject(Status.INSUFFICIENT_WATER,source,total.minus(source.volume()));
            var raw=new TreeMap<String,BigInteger>();requests.forEach((k,v)->raw.put(k,v.subquanta()));
            org.goldgom.chemicalelements.fluid.FiniteVolume.limit(source.volume().subquanta(),raw).forEach((k,v)->requests.put(k,WaterVolume.ofSubquanta(v)));
        }
        var allocation=source.allocate(requests);var after=new TreeMap<String,WaterBody>();
        allocation.destinations().forEach((k,v)->after.put(k,unique.get(k).pores().plus(v)));
        return new Plan(Status.READY,source,allocation.remainder(),after,versions,markers,WaterVolume.ZERO);
    }
    private static Plan reject(Status status,WaterBody original,WaterVolume deficit) {
        return new Plan(status,original,original,Map.of(),Map.of(),Set.of(),deficit);
    }
}

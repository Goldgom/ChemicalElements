package org.goldgom.chemicalelements.fluid.waterbody;

import java.util.*;

/** Local read-only connectivity planning: membership points to a single B-owned body snapshot.
 * Never sum the same body's composition once per voxel. No persistent index is created here. */
public final class ConnectedWaterBodies {
    private ConnectedWaterBodies() {}
    public record Snapshot(String identity,long revision,WaterBody total) {
        public Snapshot { if(identity.isBlank()||revision<0)throw new IllegalArgumentException("invalid body identity");Objects.requireNonNull(total); }
    }
    public record Cell(boolean loaded,Optional<Snapshot> body) { public Cell { Objects.requireNonNull(body); } }
    public interface Reader { Cell read(WaterGeometry.Pos position); }
    public enum Status { READY, UNLOADED, BUDGET, CONFLICT }
    public record Merge(Status status,Map<String,Snapshot> expected,Optional<WaterBody> combined,int reads) {
        public Merge { expected=Map.copyOf(expected); }
    }
    public static Merge discover(WaterGeometry.Pos start,int budget,Reader reader) {
        if(budget<1||budget>2048)throw new IllegalArgumentException("bounded traversal required");
        var queue=new ArrayDeque<WaterGeometry.Pos>();var seen=new HashSet<WaterGeometry.Pos>();var bodies=new TreeMap<String,Snapshot>();
        queue.add(start);seen.add(start);int reads=0;
        while(!queue.isEmpty()) {
            if(reads==budget)return new Merge(Status.BUDGET,Map.of(),Optional.empty(),reads);
            var p=queue.remove();var cell=reader.read(p);reads++;
            if(!cell.loaded())return new Merge(Status.UNLOADED,Map.of(),Optional.empty(),reads);
            if(cell.body().isEmpty())continue;
            var s=cell.body().orElseThrow();var previous=bodies.putIfAbsent(s.identity(),s);
            if(previous!=null&&!previous.equals(s))return new Merge(Status.CONFLICT,Map.of(),Optional.empty(),reads);
            for(int[] d:new int[][]{{0,-1,0},{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,1,0}}){var next=p.offset(d[0],d[1],d[2]);if(seen.add(next))queue.add(next);}
        }
        var combined=WaterBody.empty();for(var body:bodies.values())combined=combined.plus(body.total());
        return new Merge(Status.READY,bodies,Optional.of(combined),reads);
    }
}

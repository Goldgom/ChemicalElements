package org.goldgom.chemicalelements.fluid.waterbody;

import java.math.*;
import java.util.*;

/** Bounded local cavity query. No world index and no mutable water balances.
 * Cubic empty spaces and full collision shells are exact; unmodeled partial shapes pause, never leak. */
public final class WaterGeometry {
    private WaterGeometry() {}
    public record Pos(int x,int y,int z) implements Comparable<Pos> {
        public Pos offset(int dx,int dy,int dz){return new Pos(Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz));}
        @Override public int compareTo(Pos p){int c=Integer.compare(y,p.y);if(c==0)c=Integer.compare(x,p.x);return c==0?Integer.compare(z,p.z):c;}
    }
    public enum Kind { SPACE, SOLID, UNLOADED, UNMODELED, RECEIVER }
    public record Cell(Kind kind,String receiver) {
        public Cell { Objects.requireNonNull(kind);if(kind==Kind.RECEIVER&&(receiver==null||receiver.isBlank()))throw new IllegalArgumentException("explicit receiver required"); }
        public static Cell of(Kind k){return new Cell(k,null);}
    }
    public interface LoadedGeometry { Cell read(Pos position); }
    public enum Status { RETAINED, OUTFLOW, PAUSED_UNLOADED, PAUSED_UNMODELED, BUDGET, INSUFFICIENT_DOMAIN }
    /** Exact rational liquid height in block metres: numerator / denominator, never rounded inventory. */
    public record Level(BigInteger numerator,BigInteger denominator) {}
    public record Plan(Status status,Set<Pos> cavity,Level level,String receiver,WaterBody retained,WaterBody outgoing,int reads) {
        public Plan { cavity=Set.copyOf(cavity); }
    }
    /** ceilingExclusive is a known local spill rim, not a claim that unknown space is a wall.
     * Any outlet encountered at/below the liquid head drains to that explicit receiver only.
     * Discovery is conservative: unknown bounds reject the whole plan without modifying its source. */
    public static Plan plan(WaterBody source,Pos seed,int ceilingExclusive,int budget,LoadedGeometry geometry) {
        if(budget<1||budget>2048||seed.y()>=ceilingExclusive)throw new IllegalArgumentException("invalid geometry bound");
        var queue=new PriorityQueue<Pos>();var seen=new HashSet<Pos>();var cavity=new TreeSet<Pos>();var outlets=new TreeMap<Pos,String>();
        queue.add(seed);seen.add(seed);int reads=0;
        while(!queue.isEmpty()) {
            if(reads==budget)return paused(Status.BUDGET,source,cavity,reads);
            var p=queue.remove();var c=Objects.requireNonNull(geometry.read(p));reads++;
            if(c.kind()==Kind.UNLOADED)return paused(Status.PAUSED_UNLOADED,source,cavity,reads);
            if(c.kind()==Kind.UNMODELED)return paused(Status.PAUSED_UNMODELED,source,cavity,reads);
            if(c.kind()==Kind.SOLID)continue;
            if(c.kind()==Kind.RECEIVER){outlets.put(p,c.receiver());continue;}
            cavity.add(p);
            for(int[] d:new int[][]{{0,-1,0},{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,1,0}}) {
                var next=p.offset(d[0],d[1],d[2]);
                if(next.y()<ceilingExclusive&&seen.add(next))queue.add(next);
            }
        }
        if(cavity.isEmpty())return paused(Status.INSUFFICIENT_DOMAIN,source,cavity,reads);
        var remaining=source.volume().subquanta(); Level head=null;
        var sections=new TreeMap<Integer,Integer>();cavity.forEach(p->sections.merge(p.y(),1,Integer::sum));
        for(var s:sections.entrySet()) {
            var area=WaterVolume.SUBQUANTA_PER_M3.multiply(BigInteger.valueOf(s.getValue()));
            if(remaining.compareTo(area)<=0){head=new Level(BigInteger.valueOf(s.getKey()).multiply(area).add(remaining),area);break;}
            remaining=remaining.subtract(area);
        }
        // Spill at a lower outlet before exceeding local capacity, but never discard to an absent domain.
        int outletHeight=outlets.isEmpty()?Integer.MAX_VALUE:outlets.firstKey().y();
        boolean spills=!outlets.isEmpty()&&(head==null||head.numerator().compareTo(BigInteger.valueOf(outletHeight).multiply(head.denominator()))>0);
        if(spills) {
            BigInteger keep=BigInteger.ZERO;
            for(var s:sections.entrySet())if(s.getKey()<outletHeight)keep=keep.add(WaterVolume.SUBQUANTA_PER_M3.multiply(BigInteger.valueOf(s.getValue())));
            keep=keep.min(source.volume().subquanta());
            var amount=source.volume().minus(WaterVolume.ofSubquanta(keep));
            var receiver=outlets.firstEntry().getValue();var allocation=source.allocate(Map.of(receiver,amount));
            return new Plan(Status.OUTFLOW,cavity,new Level(BigInteger.valueOf(outletHeight),BigInteger.ONE),receiver,allocation.remainder(),allocation.destinations().get(receiver),reads);
        }
        if(head==null)return paused(Status.INSUFFICIENT_DOMAIN,source,cavity,reads);
        return new Plan(Status.RETAINED,cavity,head,null,source,WaterBody.empty(),reads);
    }
    private static Plan paused(Status s,WaterBody source,Set<Pos> cavity,int reads){return new Plan(s,cavity,null,null,source,WaterBody.empty(),reads);}
}

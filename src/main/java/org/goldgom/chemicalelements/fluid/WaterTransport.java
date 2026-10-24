package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import java.util.*;

/** Snapshot solver. Commit all changed cells in one world-store transaction, not cell-by-cell writes. */
public final class WaterTransport {
    public record Position(int x,int y,int z) implements Comparable<Position>{
        public int compareTo(Position p){int c=Integer.compare(y,p.y);if(c==0)c=Integer.compare(x,p.x);return c==0?Integer.compare(z,p.z):c;}
        public String key(){return x+","+y+","+z;}
    }
    public record Cell(FiniteVolume.Parcel parcel,BigInteger capacity,boolean loaded,boolean permeable,long revision){
        public Cell {if(capacity.signum()<0||parcel.volume().compareTo(capacity)>0)throw new IllegalArgumentException("over capacity");}
    }
    public record Edge(Position from,Position to) {}
    public record Plan(Map<Position,Long> expectedRevisions,Map<Position,FiniteVolume.Parcel> replacements) {}
    public static Plan plan(Map<Position,Cell> cells,Collection<Edge> adjacency,BigInteger rate){
        if(rate.signum()<0)throw new IllegalArgumentException("negative rate");
        TreeMap<Position,Map<String,BigInteger>> requests=new TreeMap<>();Map<String,Position> lookup=new HashMap<>();
        for(Position p:cells.keySet())lookup.put(p.key(),p);
        // Each destination's total accepted capacity is limited again after source limiting.
        for(Edge edge:adjacency){Cell a=cells.get(edge.from),b=cells.get(edge.to);if(a==null||b==null||!a.loaded||!b.loaded||!a.permeable||!b.permeable||a.parcel.volume().signum()==0)continue;
            BigInteger room=b.capacity.subtract(b.parcel.volume());BigInteger wanted=BigInteger.ZERO;
            if(edge.to.y<edge.from.y)wanted=a.parcel.volume().min(room).min(rate);
            else if(edge.to.y==edge.from.y&&a.capacity.signum()>0&&b.capacity.signum()>0){
                BigInteger imbalance=a.parcel.volume().multiply(b.capacity).subtract(b.parcel.volume().multiply(a.capacity));
                if(imbalance.signum()>0)wanted=imbalance.divide(a.capacity.add(b.capacity)).min(room).min(rate);
            }
            if(wanted.signum()>0)requests.computeIfAbsent(edge.from,k->new TreeMap<>()).merge(edge.to.key(),wanted,BigInteger::max);
        }
        Map<Position,Map<String,BigInteger>> incoming=new TreeMap<>();
        requests.forEach((source,wants)->FiniteVolume.limit(cells.get(source).parcel.volume(),wants).forEach((dest,n)->incoming.computeIfAbsent(lookup.get(dest),k->new TreeMap<>()).put(source.key(),n)));
        Map<Position,Map<String,BigInteger>> accepted=new TreeMap<>();
        incoming.forEach((dest,wants)->FiniteVolume.limit(cells.get(dest).capacity.subtract(cells.get(dest).parcel.volume()),wants).forEach((source,n)->{if(n.signum()>0)accepted.computeIfAbsent(lookup.get(source),k->new TreeMap<>()).put(dest.key(),n);}));
        TreeMap<Position,FiniteVolume.Parcel> outgoingRemainders=new TreeMap<>(),arrivals=new TreeMap<>();TreeMap<Position,Long> versions=new TreeMap<>();
        accepted.forEach((source,wants)->{Map<String,FiniteVolume.Parcel> split=FiniteVolume.distribute(cells.get(source).parcel,wants);outgoingRemainders.put(source,split.get("$source"));versions.put(source,cells.get(source).revision);split.forEach((dest,p)->{if(!dest.equals("$source")){Position d=lookup.get(dest);arrivals.merge(d,p,FiniteVolume.Parcel::plus);versions.put(d,cells.get(d).revision);}});});
        TreeMap<Position,FiniteVolume.Parcel> result=new TreeMap<>(outgoingRemainders);
        arrivals.forEach((p,parcel)->result.put(p,result.getOrDefault(p,cells.get(p).parcel).plus(parcel)));
        return new Plan(Collections.unmodifiableMap(versions),Collections.unmodifiableMap(result));
    }
}

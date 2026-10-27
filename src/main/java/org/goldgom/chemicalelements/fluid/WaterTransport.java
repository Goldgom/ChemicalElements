package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import java.util.*;

/** Snapshot solver. Commit all changed cells in one world-store transaction, not cell-by-cell writes. */
public final class WaterTransport {
    public record Position(int x,int y,int z) implements Comparable<Position>{
        public int compareTo(Position p){int c=Integer.compare(y,p.y);if(c==0)c=Integer.compare(x,p.x);return c==0?Integer.compare(z,p.z):c;}
        public String key(){return x+","+y+","+z;}
    }
    /** Null absorptionRate denotes free water; a non-null value denotes retained pores with a shared tick intake cap. */
    public record Cell(FiniteVolume.Parcel parcel,BigInteger capacity,boolean loaded,boolean permeable,long revision,BigInteger absorptionRate,String fluidId,boolean continuesAbove){
        public Cell(FiniteVolume.Parcel parcel,BigInteger capacity,boolean loaded,boolean permeable,long revision,BigInteger absorptionRate,String fluidId){this(parcel,capacity,loaded,permeable,revision,absorptionRate,fluidId,false);}
        public Cell(FiniteVolume.Parcel parcel,BigInteger capacity,boolean loaded,boolean permeable,long revision,BigInteger absorptionRate){this(parcel,capacity,loaded,permeable,revision,absorptionRate,"minecraft:water");}
        public Cell(FiniteVolume.Parcel parcel,BigInteger capacity,boolean loaded,boolean permeable,long revision){this(parcel,capacity,loaded,permeable,revision,null);}
        public Cell {Objects.requireNonNull(fluidId);if(capacity.signum()<0||parcel.volume().compareTo(capacity)>0)throw new IllegalArgumentException("over capacity");if(absorptionRate!=null&&absorptionRate.signum()<0)throw new IllegalArgumentException("negative absorption rate");}
    }
    public record Edge(Position from,Position to) {}
    public record Plan(Map<Position,Long> expectedRevisions,Map<Position,FiniteVolume.Parcel> replacements,Map<Position,FiniteVolume.Parcel> evaporated,Map<Position,String> fluidTypes) {
        public Plan(Map<Position,Long> revisions,Map<Position,FiniteVolume.Parcel> replacements,Map<Position,FiniteVolume.Parcel> evaporated){this(revisions,replacements,evaporated,Map.of());}
        public Plan(Map<Position,Long> revisions,Map<Position,FiniteVolume.Parcel> replacements){this(revisions,replacements,Map.of());}
    }
    public static Plan plan(Map<Position,Cell> cells,Collection<Edge> adjacency,BigInteger rate){
        return plan(cells,adjacency,rate,BigInteger.ZERO);
    }
    /** minimumLevelDifference uses the same fixed-point litre units as parcel volume; only lateral free flow is gated. */
    public static Plan plan(Map<Position,Cell> cells,Collection<Edge> adjacency,BigInteger rate,BigInteger minimumLevelDifference){
        if(minimumLevelDifference.signum()<0)throw new IllegalArgumentException("negative level difference");
        if(rate.signum()<0)throw new IllegalArgumentException("negative rate");
        TreeMap<Position,Map<String,BigInteger>> requests=new TreeMap<>();Map<String,Position> lookup=new HashMap<>();
        for(Position p:cells.keySet())lookup.put(p.key(),p);
        // One immiscible fluid may claim each empty destination per snapshot. Stable source order
        // makes competing arrivals deterministic and prevents silent water/lava conversion.
        Map<Position,String> destinationTypes=new TreeMap<>();
        adjacency.stream().sorted(Comparator.<Edge>comparingInt(e->e.to.y<e.from.y?0:1).thenComparing(Edge::from).thenComparing(Edge::to)).forEach(edge->{
            var a=cells.get(edge.from);var b=cells.get(edge.to);
            if(a!=null&&b!=null&&a.loaded&&b.loaded&&a.permeable&&b.permeable&&a.absorptionRate==null
                    &&a.parcel.volume().signum()>0&&b.parcel.volume().signum()==0&&b.capacity.signum()>0
                    &&(b.absorptionRate!=null?b.absorptionRate.signum()>0:edge.to.y<edge.from.y||a.parcel.volume().compareTo(minimumLevelDifference)>=0)
                    &&(edge.to.y<=edge.from.y||b.absorptionRate!=null))destinationTypes.putIfAbsent(edge.to,a.fluidId);
        });
        Set<Position> falling=new HashSet<>();
        for(Edge edge:adjacency){var a=cells.get(edge.from);var b=cells.get(edge.to);
            if(a!=null&&b!=null&&compatible(a,b,destinationTypes.get(edge.to))&&a.loaded&&b.loaded&&a.permeable&&b.permeable&&a.absorptionRate==null&&b.absorptionRate==null
                    &&edge.to.y<edge.from.y&&a.parcel.volume().signum()>0&&b.parcel.volume().compareTo(b.capacity)<0)falling.add(edge.from);
        }
        // Each destination's total accepted capacity is limited again after source limiting.
        for(Edge edge:adjacency){Cell a=cells.get(edge.from),b=cells.get(edge.to);if(a==null||b==null||!compatible(a,b,destinationTypes.get(edge.to))||!a.loaded||!b.loaded||!a.permeable||!b.permeable||a.absorptionRate!=null||a.parcel.volume().signum()==0)continue;
            if(falling.contains(edge.from)&&(edge.to.y>=edge.from.y||b.absorptionRate!=null))continue;
            BigInteger room=b.capacity.subtract(b.parcel.volume());BigInteger wanted=BigInteger.ZERO;
            if(b.absorptionRate!=null)wanted=a.parcel.volume().min(room).min(b.absorptionRate);
            else if(edge.to.y<edge.from.y)wanted=a.parcel.volume().min(room).min(rate);
            else if(edge.to.y==edge.from.y&&a.capacity.signum()>0&&b.capacity.signum()>0){
                BigInteger imbalance=a.parcel.volume().multiply(b.capacity).subtract(b.parcel.volume().multiply(a.capacity));
                if(a.parcel.volume().subtract(b.parcel.volume()).compareTo(minimumLevelDifference)>=0&&imbalance.signum()>0)
                    wanted=imbalance.divide(a.capacity.add(b.capacity)).min(room).min(rate);
            }
            if(wanted.signum()>0)requests.computeIfAbsent(edge.from,k->new TreeMap<>()).merge(edge.to.key(),wanted,BigInteger::max);
        }
        Map<Position,Map<String,BigInteger>> incoming=new TreeMap<>();
        requests.forEach((source,wants)->{
            var cell=cells.get(source);var limited=new TreeMap<String,BigInteger>();
            if(falling.contains(source))limited.putAll(FiniteVolume.limit(cell.parcel.volume(),wants));
            else{
                var pores=new TreeMap<String,BigInteger>();var lateral=new TreeMap<String,BigInteger>();
                wants.forEach((dest,n)->{if(cells.get(lookup.get(dest)).absorptionRate!=null)pores.put(dest,n);else lateral.put(dest,n);});
                limited.putAll(FiniteVolume.limit(cell.parcel.volume(),pores));
                var absorbed=limited.values().stream().reduce(BigInteger.ZERO,BigInteger::add);
                var available=cell.parcel.volume().subtract(absorbed);
                // Simultaneous neighbors share the pressure drop. Keep the source's share too,
                // otherwise a fresh bucket makes a low center surrounded by four higher cells.
                var retained=available.divide(BigInteger.valueOf(lateral.size()+1L));
                var mobile=available.subtract(retained).max(BigInteger.ZERO);
                limited.putAll(FiniteVolume.limit(mobile,lateral));
            }
            limited.forEach((dest,n)->incoming.computeIfAbsent(lookup.get(dest),k->new TreeMap<>()).put(source.key(),n));
        });
        Map<Position,Map<String,BigInteger>> accepted=new TreeMap<>();
        incoming.forEach((dest,wants)->{var cell=cells.get(dest);var room=cell.capacity.subtract(cell.parcel.volume());if(cell.absorptionRate!=null)room=room.min(cell.absorptionRate);FiniteVolume.limit(room,wants).forEach((source,n)->{if(n.signum()>0)accepted.computeIfAbsent(lookup.get(source),k->new TreeMap<>()).put(dest.key(),n);});});
        TreeMap<Position,FiniteVolume.Parcel> outgoingRemainders=new TreeMap<>(),arrivals=new TreeMap<>();TreeMap<Position,Long> versions=new TreeMap<>();
        accepted.forEach((source,wants)->{Map<String,FiniteVolume.Parcel> split=FiniteVolume.distribute(cells.get(source).parcel,wants);outgoingRemainders.put(source,split.get("$source"));versions.put(source,cells.get(source).revision);split.forEach((dest,p)->{if(!dest.equals("$source")){Position d=lookup.get(dest);arrivals.merge(d,p,FiniteVolume.Parcel::plus);versions.put(d,cells.get(d).revision);}});});
        TreeMap<Position,FiniteVolume.Parcel> result=new TreeMap<>(outgoingRemainders);
        arrivals.forEach((p,parcel)->result.put(p,result.getOrDefault(p,cells.get(p).parcel).plus(parcel)));
        var types=new TreeMap<Position,String>();
        result.forEach((p,parcel)->types.put(p,cells.get(p).parcel.volume().signum()>0?cells.get(p).fluidId:destinationTypes.getOrDefault(p,cells.get(p).fluidId)));
        return new Plan(Collections.unmodifiableMap(versions),Collections.unmodifiableMap(result),Map.of(),Collections.unmodifiableMap(types));
    }

    private static boolean compatible(Cell a,Cell b,String claimed){
        return b.parcel.volume().signum()>0?a.fluidId.equals(b.fluidId):a.fluidId.equals(claimed);
    }

    /** One bounded-region solve: gravity, shared pore intake, connected bodies,
     * then gravity for their new arrivals. Wet members share one pressure/volume calculation
     * instead of repeatedly exchanging opposing pairwise fluxes. */
    public static Plan equilibrate(Map<Position,Cell> original,Collection<Edge> adjacency,BigInteger rate,BigInteger minimumDifference){
        return equilibrate(original,adjacency,rate,minimumDifference,Integer.MAX_VALUE);
    }
    /** One observable stage: an unsupported cell falls one block first; otherwise the wet
     * frontier advances one layer. No intermediate stage is discarded by a multi-round solve. */
    public static Plan stage(Map<Position,Cell> original,Collection<Edge> adjacency,BigInteger rate,BigInteger minimumDifference){
        if(rate.signum()<0||minimumDifference.signum()<0)throw new IllegalArgumentException("negative flow setting");
        var cells=new TreeMap<>(original);var falling=new TreeSet<Position>();
        var down=new HashMap<Position,Position>();
        for(var e:adjacency)if(e.to.y==e.from.y-1&&e.to.x==e.from.x&&e.to.z==e.from.z)down.put(e.from,e.to);
        // Bottom first: an existing water column can refill the space its lower cells just
        // vacated. Each parcel still crosses only one edge, without alternating air pockets.
        for(var p:cells.keySet()){
            var q=down.get(p);if(q==null)continue;var a=cells.get(p);var b=cells.get(q);
            if(!free(a)||!free(b)||a.parcel.volume().signum()==0||b.parcel.volume().signum()>0&&!a.fluidId.equals(b.fluidId))continue;
            var amount=a.parcel.volume().min(b.capacity.subtract(b.parcel.volume())).min(rate);if(amount.signum()==0)continue;
            var split=FiniteVolume.take(a.parcel,amount);
            cells.put(p,withParcel(a,split.remainder(),a.fluidId));cells.put(q,withParcel(b,b.parcel.plus(split.moved()),a.fluidId));
            falling.add(p);falling.add(q);
        }
        // Freeze only the cells that moved vertically. An unrelated waterfall must not
        // suspend every puddle, lake edge and pore intake in the same scheduling region.
        var lateral=new TreeMap<>(cells);
        for(var p:falling){var c=cells.get(p);lateral.put(p,new Cell(c.parcel,c.capacity,c.loaded,false,c.revision,c.absorptionRate,c.fluidId,c.continuesAbove));}
        var rest=equilibrate(lateral,adjacency,rate,minimumDifference,1);
        var changed=new TreeSet<>(falling);
        rest.replacements.forEach((p,v)->{cells.put(p,withParcel(cells.get(p),v,rest.fluidTypes.getOrDefault(p,cells.get(p).fluidId)));changed.add(p);});
        return difference(original,cells,changed);
    }
    private static Cell withParcel(Cell c,FiniteVolume.Parcel parcel,String fluid){return new Cell(parcel,c.capacity,c.loaded,c.permeable,c.revision,c.absorptionRate,fluid,c.continuesAbove);}
    private static Plan difference(Map<Position,Cell> original,Map<Position,Cell> cells,Collection<Position> changed){
        var replacements=new TreeMap<Position,FiniteVolume.Parcel>();var versions=new TreeMap<Position,Long>();var types=new TreeMap<Position,String>();
        for(var p:changed){var c=cells.get(p);var before=original.get(p);if(c.parcel.equals(before.parcel)&&c.fluidId.equals(before.fluidId))continue;
            replacements.put(p,c.parcel);versions.put(p,before.revision);types.put(p,c.fluidId);}
        return new Plan(Collections.unmodifiableMap(versions),Collections.unmodifiableMap(replacements),Map.of(),Collections.unmodifiableMap(types));
    }
    private static Plan equilibrate(Map<Position,Cell> original,Collection<Edge> adjacency,BigInteger rate,BigInteger minimumDifference,int frontierLayers){
        if(rate.signum()<0||minimumDifference.signum()<0)throw new IllegalArgumentException("negative flow setting");
        var cells=new TreeMap<>(original);var changed=new TreeSet<Position>();
        settleGravity(cells,adjacency,rate,changed);
        var poreEdges=adjacency.stream().filter(e->cells.get(e.to)!=null&&cells.get(e.to).absorptionRate!=null).toList();
        var uptake=plan(cells,poreEdges,rate,minimumDifference);
        uptake.replacements.forEach((p,v)->{var c=cells.get(p);cells.put(p,withParcel(c,v,uptake.fluidTypes.getOrDefault(p,c.fluidId)));changed.add(p);});
        var connected=new HashMap<Position,SortedSet<Position>>();
        for(var e:adjacency)connected.computeIfAbsent(e.from,k->new TreeSet<>()).add(e.to);
        var assigned=new HashSet<Position>();
        for(var start:cells.keySet()){
            var first=cells.get(start);if(assigned.contains(start)||!free(first)||first.parcel.volume().signum()==0)continue;
            String fluid=first.fluidId;var members=new TreeSet<Position>();var frontier=new TreeSet<Position>();var queue=new ArrayDeque<Position>();queue.add(start);
            BigInteger total=BigInteger.ZERO;
            int admittedLayers=0;
            while(true){
                while(!queue.isEmpty()){
                    var p=queue.remove();if(members.contains(p)||assigned.contains(p))continue;
                    var c=cells.get(p);if(!free(c)||c.parcel.volume().signum()>0&&!fluid.equals(c.fluidId))continue;
                    members.add(p);total=total.add(c.parcel.volume());
                    for(var next:connected.getOrDefault(p,Collections.emptySortedSet())){
                        var neighbor=cells.get(next);if(!free(neighbor)||assigned.contains(next)||members.contains(next))continue;
                        if(neighbor.parcel.volume().signum()>0){if(fluid.equals(neighbor.fluidId))queue.add(next);}
                        else frontier.add(next);
                    }
                }
                frontier.removeAll(members);
                // Test the body pressure against the dry frontier, then recompute after
                // admitting that frontier. Do not stop merely because a round count ran out.
                if(frontier.isEmpty()||admittedLayers>=frontierLayers)break;
                var surface=waterline(cells,members,total,first.capacity);
                boolean capped=continuesAbove(cells,members);
                var admissible=frontier.stream().filter(p->capped
                        ?connected.getOrDefault(p,Collections.emptySortedSet()).stream().anyMatch(q->members.contains(q)&&q.y==p.y&&cells.get(q).parcel.volume().signum()>0&&cells.get(q).parcel.volume().compareTo(minimumDifference)>=0)
                        :p.y<surface.y||p.y==surface.y&&surface.volume.signum()>0&&surface.volume.compareTo(minimumDifference)>=0).toList();
                if(admissible.isEmpty())break;
                queue.addAll(admissible);frontier.removeAll(admissible);admittedLayers++;
            }
            assigned.addAll(members);
            if(members.size()<2)continue;
            // Runtime free cells have equal block capacity. Keep foreign adapters with
            // nonstandard capacities on the conservative edge solver rather than overfill.
            if(members.stream().anyMatch(p->!cells.get(p).capacity.equals(first.capacity)))continue;
            // The top of a snapshot (or a temporarily frozen falling column) is not a
            // physical free surface. Do not pool every depth's deficit into that artificial cap.
            if(continuesAbove(cells,members)){
                balanceCappedLayers(cells,members,connected,minimumDifference,fluid,changed);
                continue;
            }
            var desired=new TreeMap<Position,BigInteger>();var layers=new TreeMap<Integer,List<Position>>();
            for(var p:members)layers.computeIfAbsent(p.y,k->new ArrayList<>()).add(p);
            var remaining=total;boolean differs=false;
            for(var layer:layers.values()){
                var layerVolume=remaining.min(first.capacity.multiply(BigInteger.valueOf(layer.size())));remaining=remaining.subtract(layerVolume);
                var division=layerVolume.divideAndRemainder(BigInteger.valueOf(layer.size()));int index=0;
                for(var p:layer){var volume=division[0].add(index++<division[1].intValueExact()?BigInteger.ONE:BigInteger.ZERO);desired.put(p,volume);if(!volume.equals(cells.get(p).parcel.volume()))differs=true;}
            }
            boolean active=members.stream().anyMatch(p->connected.getOrDefault(p,Collections.emptySortedSet()).stream()
                    .anyMatch(q->members.contains(q)&&(p.y==q.y&&cells.get(p).parcel.volume().subtract(cells.get(q).parcel.volume()).abs().compareTo(minimumDifference)>=0
                            ||q.y<p.y&&cells.get(p).parcel.volume().signum()>0&&cells.get(q).parcel.volume().compareTo(cells.get(q).capacity)<0)));
            // Surface columns connected through a submerged passage share pressure too.
            // Comparing only horizontal edges would leave such communicating vessels unequal.
            var heads=members.stream().filter(p->cells.get(p).parcel.volume().signum()>0
                    &&(!members.contains(new Position(p.x,p.y+1,p.z))||cells.get(new Position(p.x,p.y+1,p.z)).parcel.volume().signum()==0))
                    .map(p->first.capacity.multiply(BigInteger.valueOf(p.y)).add(cells.get(p).parcel.volume())).toList();
            boolean differentSurfaceElevations=members.stream().filter(p->cells.get(p).parcel.volume().signum()>0
                    &&(!members.contains(new Position(p.x,p.y+1,p.z))||cells.get(new Position(p.x,p.y+1,p.z)).parcel.volume().signum()==0)).map(Position::y).distinct().limit(2).count()>1;
            if(differentSurfaceElevations&&!heads.isEmpty()&&Collections.max(heads).subtract(Collections.min(heads)).compareTo(minimumDifference)>=0)active=true;
            if(!differs||!active)continue;
            var pooled=FiniteVolume.Parcel.empty();for(var p:members)pooled=pooled.plus(cells.get(p).parcel);
            var requests=new TreeMap<String,BigInteger>();desired.forEach((p,v)->requests.put(p.key(),v));
            var allocation=FiniteVolume.distribute(pooled,requests);
            for(var p:members){var c=cells.get(p);cells.put(p,withParcel(c,allocation.get(p.key()),fluid));changed.add(p);}
        }
        if(frontierLayers==Integer.MAX_VALUE)settleGravity(cells,adjacency,rate,changed);
        var replacements=new TreeMap<Position,FiniteVolume.Parcel>();var versions=new TreeMap<Position,Long>();var types=new TreeMap<Position,String>();
        for(var p:changed){var c=cells.get(p);var before=original.get(p);if(c.parcel.equals(before.parcel)&&c.fluidId.equals(before.fluidId))continue;
            replacements.put(p,c.parcel);versions.put(p,before.revision);types.put(p,c.fluidId);}
        return new Plan(Collections.unmodifiableMap(versions),Collections.unmodifiableMap(replacements),Map.of(),Collections.unmodifiableMap(types));
    }
    private static boolean free(Cell c){return c!=null&&c.loaded&&c.permeable&&c.absorptionRate==null&&c.capacity.signum()>0;}
    private static boolean continuesAbove(Map<Position,Cell> cells,Set<Position> members){
        for(var p:members){var c=cells.get(p);if(c.continuesAbove)return true;
            var up=new Position(p.x,p.y+1,p.z);var above=cells.get(up);
            if(!members.contains(up)&&above!=null&&above.loaded&&above.absorptionRate==null&&above.parcel.volume().signum()>0&&above.fluidId.equals(c.fluidId))return true;}
        return false;
    }
    private static void balanceCappedLayers(TreeMap<Position,Cell> cells,Set<Position> members,Map<Position,SortedSet<Position>> adjacency,BigInteger threshold,String fluid,Set<Position> changed){
        var layers=new TreeMap<Integer,List<Position>>();for(var p:members)layers.computeIfAbsent(p.y,k->new ArrayList<>()).add(p);
        for(var layer:layers.values()){
            if(layer.size()<2||layer.stream().noneMatch(p->adjacency.getOrDefault(p,Collections.emptySortedSet()).stream()
                    .anyMatch(q->q.y==p.y&&members.contains(q)&&cells.get(p).parcel.volume().subtract(cells.get(q).parcel.volume()).abs().compareTo(threshold)>=0)))continue;
            var total=FiniteVolume.Parcel.empty();for(var p:layer)total=total.plus(cells.get(p).parcel);
            var divided=total.volume().divideAndRemainder(BigInteger.valueOf(layer.size()));int i=0;boolean differs=false;
            var requests=new TreeMap<String,BigInteger>();
            for(var p:layer){var amount=divided[0].add(i++<divided[1].intValueExact()?BigInteger.ONE:BigInteger.ZERO);requests.put(p.key(),amount);differs|=!amount.equals(cells.get(p).parcel.volume());}
            if(!differs)continue;
            var split=FiniteVolume.distribute(total,requests);
            for(var p:layer){cells.put(p,withParcel(cells.get(p),split.get(p.key()),fluid));changed.add(p);}
        }
    }
    private record Waterline(int y,BigInteger volume){}
    private static Waterline waterline(Map<Position,Cell> cells,Set<Position> members,BigInteger total,BigInteger capacity){
        var counts=new TreeMap<Integer,Integer>();for(var p:members)counts.merge(p.y,1,Integer::sum);
        for(var layer:counts.entrySet()){
            var layerCapacity=capacity.multiply(BigInteger.valueOf(layer.getValue()));
            if(total.compareTo(layerCapacity)<=0)return new Waterline(layer.getKey(),total.divide(BigInteger.valueOf(layer.getValue())));
            total=total.subtract(layerCapacity);
        }
        throw new IllegalStateException("body exceeds available capacity");
    }

    /** Multiple propagation rounds in memory, one final transaction. Pore intake is shared over all rounds. */
    public static Plan advance(Map<Position,Cell> original,Collection<Edge> adjacency,BigInteger rate,BigInteger minimumDifference,int rounds){
        if(rounds<1||rounds>16)throw new IllegalArgumentException("flow round budget");
        var cells=new TreeMap<>(original);var changed=new TreeSet<Position>();
        for(int round=0;round<rounds;round++){
            var snapshot=new TreeMap<Position,Cell>();
            for(var entry:cells.entrySet()){
                var c=entry.getValue();BigInteger intake=null;
                if(c.absorptionRate!=null){
                    var divided=original.get(entry.getKey()).absorptionRate.divideAndRemainder(BigInteger.valueOf(rounds));
                    intake=divided[0].add(round<divided[1].intValueExact()?BigInteger.ONE:BigInteger.ZERO);
                }
                snapshot.put(entry.getKey(),new Cell(c.parcel,c.capacity,c.loaded,c.permeable,c.revision,intake,c.fluidId,c.continuesAbove));
            }
            var flow=plan(snapshot,adjacency,rate,minimumDifference);
            for(var entry:flow.replacements.entrySet()){
                var p=entry.getKey();var c=cells.get(p);changed.add(p);
                cells.put(p,withParcel(c,entry.getValue(),flow.fluidTypes.getOrDefault(p,c.fluidId)));
            }
        }
        settleGravity(cells,adjacency,rate,changed);
        var replacements=new TreeMap<Position,FiniteVolume.Parcel>();var versions=new TreeMap<Position,Long>();var types=new TreeMap<Position,String>();
        for(var p:changed){var c=cells.get(p);var before=original.get(p);
            if(c.parcel.equals(before.parcel)&&c.fluidId.equals(before.fluidId))continue;
            replacements.put(p,c.parcel);versions.put(p,before.revision);types.put(p,c.fluidId);
        }
        return new Plan(Collections.unmodifiableMap(versions),Collections.unmodifiableMap(replacements),Map.of(),Collections.unmodifiableMap(types));
    }

    /** Finish lateral arrivals in the same transaction. Pack connected columns from the bottom,
     * including empty intermediate cells; never pass through pores, solids or another fluid. */
    private static void settleGravity(TreeMap<Position,Cell> cells,Collection<Edge> adjacency,BigInteger rate,Set<Position> changed){
        var below=new HashMap<Position,Position>();
        for(var edge:adjacency)if(edge.to.x==edge.from.x&&edge.to.z==edge.from.z&&edge.to.y==edge.from.y-1)below.put(edge.from,edge.to);
        for(var source:new ArrayList<>(cells.keySet())){
            var a=cells.get(source);
            if(!a.loaded||!a.permeable||a.absorptionRate!=null||a.parcel.volume().signum()==0)continue;
            var destinations=new ArrayList<Position>();
            for(var p=below.get(source);p!=null;p=below.get(p)){
                var b=cells.get(p);
                if(b==null||!b.loaded||!b.permeable||b.absorptionRate!=null||b.parcel.volume().signum()>0&&!a.fluidId.equals(b.fluidId))break;
                if(b.parcel.volume().compareTo(b.capacity)<0)destinations.add(p);
            }
            var remaining=rate.min(a.parcel.volume());
            for(int i=destinations.size()-1;i>=0&&remaining.signum()>0;i--){
                var p=destinations.get(i);var b=cells.get(p);
                var amount=remaining.min(b.capacity.subtract(b.parcel.volume()));
                var split=FiniteVolume.take(a.parcel,amount);
                cells.put(p,withParcel(b,b.parcel.plus(split.moved()),a.fluidId));
                a=withParcel(a,split.remainder(),a.fluidId);
                cells.put(source,a);changed.add(source);changed.add(p);remaining=remaining.subtract(amount);
            }
        }
    }

    /** Explicit evaporation is separate from conservative transport and committed in the same receipt. */
    public static Plan evaporateTraces(Map<Position,Cell> cells,Plan flow,BigInteger threshold){
        if(threshold.signum()<0)throw new IllegalArgumentException("negative evaporation threshold");
        var versions=new TreeMap<>(flow.expectedRevisions());var replacements=new TreeMap<>(flow.replacements());var removed=new TreeMap<>(flow.evaporated());
        cells.forEach((position,cell)->{
            if(!cell.loaded||!cell.permeable||cell.absorptionRate!=null)return;
            var parcel=replacements.getOrDefault(position,cell.parcel);
            if(parcel.volume().signum()>0&&parcel.volume().compareTo(threshold)<0){
                versions.put(position,cell.revision);replacements.put(position,FiniteVolume.Parcel.empty());removed.merge(position,parcel,FiniteVolume.Parcel::plus);
            }
        });
        return new Plan(Collections.unmodifiableMap(versions),Collections.unmodifiableMap(replacements),Collections.unmodifiableMap(removed),flow.fluidTypes());
    }
}

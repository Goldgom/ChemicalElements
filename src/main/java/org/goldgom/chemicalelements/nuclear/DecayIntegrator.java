package org.goldgom.chemicalelements.nuclear;

import java.math.*;
import java.util.*;

/** Game simulation only. Nuclides refine chemical inventory, never form an extra additive element pool. */
public final class DecayIntegrator {
    public record Nuclide(int z,int a,int isomer) implements Comparable<Nuclide> {
        public Nuclide {if(z<0||a<1||z>a||isomer<0)throw new IllegalArgumentException("invalid nuclide");}
        public int compareTo(Nuclide n){int c=Integer.compare(z,n.z);if(c==0)c=Integer.compare(a,n.a);return c==0?Integer.compare(isomer,n.isomer):c;}
    }
    public record Emission(String id,int nucleons,int charge,int count) {public Emission {if(id==null||id.isBlank()||count<=0||nucleons<0)throw new IllegalArgumentException("invalid emission");}}
    public record Rule(Nuclide parent,Nuclide daughter,double halfLifeSeconds,List<Emission> emissions,BigDecimal energyPerQuantum,String basis,String source) {
        public Rule {emissions=List.copyOf(emissions);if(!Double.isFinite(halfLifeSeconds)||halfLifeSeconds<=0||energyPerQuantum.signum()<0||source.isBlank()||basis.isBlank())throw new IllegalArgumentException("invalid decay");int a=daughter.a,z=daughter.z;for(Emission e:emissions){a=Math.addExact(a,Math.multiplyExact(e.nucleons,e.count));z=Math.addExact(z,Math.multiplyExact(e.charge,e.count));}if(a!=parent.a||z!=parent.z)throw new IllegalArgumentException("nuclear charge/nucleon imbalance");}
    }
    public record State(Map<Nuclide,BigInteger> nuclides,Map<Nuclide,BigDecimal> residual,Map<String,BigInteger> emitted,BigDecimal energy) {
        public State {nuclides=Map.copyOf(nuclides);residual=Map.copyOf(residual);emitted=Map.copyOf(emitted);if(emitted.keySet().stream().anyMatch(String::isBlank)||emitted.values().stream().anyMatch(n->n.signum()<0)||nuclides.values().stream().anyMatch(n->n.signum()<0)||residual.values().stream().anyMatch(n->n.signum()<0||n.compareTo(BigDecimal.ONE)>=0)||energy.signum()<0)throw new IllegalArgumentException("invalid nuclear inventory");}
    }
    /** Frozen source: daughters start decaying on the next tick. Unloaded inventories pass loaded=false. */
    public static State tick(State state,List<Rule> rules,double seconds,boolean loaded){
        if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("invalid time");if(!loaded||seconds==0)return state;
        TreeMap<Nuclide,BigInteger> inventory=new TreeMap<>(state.nuclides);TreeMap<Nuclide,BigDecimal> residual=new TreeMap<>(state.residual);TreeMap<String,BigInteger> emissions=new TreeMap<>(state.emitted);BigDecimal energy=state.energy;Set<Nuclide> seen=new HashSet<>();
        for(Rule rule:rules.stream().sorted(Comparator.comparing(Rule::parent)).toList()){
            if(!seen.add(rule.parent))throw new IllegalArgumentException("multiple branches require an explicit branching model, not independent decay");
            BigInteger available=state.nuclides.getOrDefault(rule.parent,BigInteger.ZERO);if(available.signum()==0)continue;
            double fraction=-StrictMath.expm1(-StrictMath.log(2)*seconds/rule.halfLifeSeconds);
            BigDecimal exact=new BigDecimal(available).multiply(BigDecimal.valueOf(fraction)).add(residual.getOrDefault(rule.parent,BigDecimal.ZERO));BigInteger count=exact.toBigInteger().min(available);
            residual.put(rule.parent,count.equals(available)?BigDecimal.ZERO:exact.subtract(new BigDecimal(count)));
            inventory.merge(rule.parent,count.negate(),BigInteger::add);inventory.merge(rule.daughter,count,BigInteger::add);
            for(Emission e:rule.emissions)emissions.merge(e.id,count.multiply(BigInteger.valueOf(e.count)),BigInteger::add);
            energy=energy.add(rule.energyPerQuantum.multiply(new BigDecimal(count)));
        }
        return new State(inventory,residual,emissions,energy);
    }
    public static Map<Integer,BigInteger> elementView(State state){TreeMap<Integer,BigInteger> out=new TreeMap<>();state.nuclides.forEach((id,n)->out.merge(id.z,n,BigInteger::add));return Collections.unmodifiableMap(out);}
}

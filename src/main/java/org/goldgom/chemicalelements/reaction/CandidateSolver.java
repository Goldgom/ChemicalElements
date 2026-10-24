package org.goldgom.chemicalelements.reaction;

import java.math.*;
import java.util.*;

/** Grid-independent finite-domain chemistry solver. Balance is validation, never feasibility evidence. */
public final class CandidateSolver {
    public record Species(Map<String,Integer> atoms,int charge,String phase) {
        public Species { atoms=Map.copyOf(atoms); if(atoms.values().stream().anyMatch(n->n<=0))throw new IllegalArgumentException("invalid atoms"); Objects.requireNonNull(phase); }
    }
    public record Conditions(BigDecimal kelvin,BigDecimal pascals,Set<String> capabilities) {
        public Conditions { if(kelvin.signum()<=0||pascals.signum()<=0)throw new IllegalArgumentException("invalid conditions"); capabilities=Set.copyOf(capabilities); }
        public static Conditions workbench(){return new Conditions(new BigDecimal("298.15"),new BigDecimal("101325"),Set.of());}
    }
    public record Domain(BigDecimal minK,BigDecimal maxK,BigDecimal minPa,BigDecimal maxPa,Set<String> capabilities) {
        public Domain { capabilities=Set.copyOf(capabilities); if(minK.compareTo(maxK)>0||minPa.compareTo(maxPa)>0)throw new IllegalArgumentException("invalid domain"); }
        public String missing(Conditions c){
            if(c.kelvin.compareTo(minK)<0||c.kelvin.compareTo(maxK)>0)return "temperature outside modeled range";
            if(c.pascals.compareTo(minPa)<0||c.pascals.compareTo(maxPa)>0)return "pressure outside modeled range";
            TreeSet<String> missing=new TreeSet<>(capabilities);missing.removeAll(c.capabilities);
            return missing.isEmpty()?null:"requires "+String.join(", ",missing);
        }
    }
    /** deltaG is supplied only from a cited model at this domain; null means unknown, not zero. */
    public record Rule(String id,String type,Map<String,Integer> reactants,Map<String,Integer> products,
                       Domain domain,BigDecimal deltaG,int durationTicks,String basis,String source) {
        public Rule { reactants=Map.copyOf(reactants);products=Map.copyOf(products);if(durationTicks<=0||reactants.isEmpty()||products.isEmpty())throw new IllegalArgumentException("invalid rule");
            if(reactants.values().stream().anyMatch(n->n<=0)||products.values().stream().anyMatch(n->n<=0))throw new IllegalArgumentException("invalid stoichiometry");
            if(basis==null||basis.isBlank()||source==null||source.isBlank())throw new IllegalArgumentException("missing feasibility provenance"); }
    }
    public record Proposal(Rule rule,BigInteger extent,Map<String,BigInteger> consumed,Map<String,BigInteger> produced) {}
    public record Result(String status,String explanation,Map<String,BigInteger> elements,List<Proposal> candidates) {}
    private final Map<String,Species> species;
    private final List<Rule> rules;
    public CandidateSolver(Map<String,Species> species,Collection<Rule> rules){
        this.species=Map.copyOf(species); Set<String> ids=new HashSet<>();
        for(Rule r:rules){if(!ids.add(r.id))throw new IllegalArgumentException("duplicate rule "+r.id);validate(r);}
        this.rules=rules.stream().sorted(Comparator.comparing(Rule::id)).toList();
    }
    private Map<String,BigInteger> balance(Map<String,? extends Number> inventory){
        TreeMap<String,BigInteger> result=new TreeMap<>();
        inventory.forEach((id,value)->{Species s=species.get(id);if(s==null)throw new IllegalArgumentException("unknown species "+id);
            BigInteger n=value instanceof BigInteger b?b:BigInteger.valueOf(value.longValue());if(n.signum()<0)throw new IllegalArgumentException("negative input");
            s.atoms.forEach((e,a)->result.merge(e,n.multiply(BigInteger.valueOf(a)),BigInteger::add));
            result.merge("$charge",n.multiply(BigInteger.valueOf(s.charge)),BigInteger::add);});
        result.values().removeIf(n->n.signum()==0);return Collections.unmodifiableMap(result);
    }
    public void validate(Rule r){if(!balance(r.reactants).equals(balance(r.products)))throw new IllegalArgumentException("element/charge imbalance: "+r.id);}
    public Result solve(Map<String,BigInteger> inventory,Conditions conditions){
        final Map<String,BigInteger> elements;
        try{elements=balance(inventory);}catch(IllegalArgumentException e){return new Result("UNMODELED",e.getMessage(),Map.of(),List.of());}
        List<Proposal> proposals=new ArrayList<>();TreeSet<String> failures=new TreeSet<>();boolean recognized=false;
        for(Rule rule:rules){
            BigInteger extent=null;
            for(var term:rule.reactants.entrySet()){BigInteger n=inventory.getOrDefault(term.getKey(),BigInteger.ZERO).divide(BigInteger.valueOf(term.getValue()));extent=extent==null?n:extent.min(n);}
            if(extent==null||extent.signum()==0)continue;
            recognized=true;String missing=rule.domain.missing(conditions);
            if(missing!=null){failures.add(rule.id+": "+missing);continue;}
            // The finite library itself must justify kinetics/feasibility. Positive Gibbs requires externally supplied work.
            if(rule.deltaG!=null&&rule.deltaG.signum()>0&&!conditions.capabilities.contains("external_work")){failures.add(rule.id+": requires external_work");continue;}
            proposals.add(new Proposal(rule,extent,scale(rule.reactants,extent),scale(rule.products,extent)));
        }
        proposals.sort(Comparator.<Proposal,Boolean>comparing(p->p.rule.deltaG==null)
            .thenComparing(p->p.rule.deltaG,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(p->p.rule.id));
        return new Result(proposals.isEmpty()?(recognized?"CONDITIONS_UNMET":"UNMODELED"):"READY",
            proposals.isEmpty()?(failures.isEmpty()?"No modeled feasible pathway for these species/phases":String.join("; ",failures)):"Choose first candidate; re-solve after each committed extent",elements,List.copyOf(proposals));
    }
    private static Map<String,BigInteger> scale(Map<String,Integer> terms,BigInteger extent){TreeMap<String,BigInteger> result=new TreeMap<>();terms.forEach((id,n)->result.put(id,extent.multiply(BigInteger.valueOf(n))));return Collections.unmodifiableMap(result);}
}

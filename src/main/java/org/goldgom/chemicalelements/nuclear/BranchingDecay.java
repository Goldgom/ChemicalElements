package org.goldgom.chemicalelements.nuclear;
import java.math.*;
import java.util.*;
public final class BranchingDecay {
 public record Branch(String id,BigDecimal probability,DecayIntegrator.Nuclide daughter,List<DecayIntegrator.Emission> emissions,BigDecimal energyPerQuantum){
  public Branch {emissions=List.copyOf(emissions);if(probability.signum()<=0||probability.compareTo(BigDecimal.ONE)>0||energyPerQuantum.signum()<0)throw new IllegalArgumentException("invalid branch");}
 }
 public record Rule(DecayIntegrator.Nuclide parent,double halfLife,List<Branch> branches,String source){
  public Rule {branches=branches.stream().sorted(Comparator.comparing(Branch::id)).toList();if(branches.isEmpty()||source.isBlank()||!Double.isFinite(halfLife)||halfLife<=0)throw new IllegalArgumentException("invalid chain");BigDecimal sum=BigDecimal.ZERO;Set<String> ids=new HashSet<>();for(Branch b:branches){sum=sum.add(b.probability);if(!ids.add(b.id))throw new IllegalArgumentException("duplicate branch");new DecayIntegrator.Rule(parent,b.daughter,halfLife,b.emissions,b.energyPerQuantum,"branch_validation",source);}if(sum.compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("branch probabilities must sum exactly to one");}
 }
 /** Per-branch rounding debts may be negative; they are numerical residuals, not physical matter. */
 public record State(DecayIntegrator.State inventory,Map<String,BigDecimal> branchDebt){public State {branchDebt=Map.copyOf(branchDebt);}}
 public static State tick(State input,List<Rule> rules,double seconds,boolean loaded){
  if(seconds<0||!Double.isFinite(seconds))throw new IllegalArgumentException("invalid time");if(!loaded||seconds==0)return input;
  var initial=input.inventory;TreeMap<DecayIntegrator.Nuclide,BigInteger> nuclei=new TreeMap<>(initial.nuclides());TreeMap<DecayIntegrator.Nuclide,BigDecimal> residual=new TreeMap<>(initial.residual());TreeMap<String,BigInteger> emissions=new TreeMap<>(initial.emitted());TreeMap<String,BigDecimal> debts=new TreeMap<>(input.branchDebt);BigDecimal energy=initial.energy();Set<DecayIntegrator.Nuclide> seen=new HashSet<>();
  for(Rule r:rules.stream().sorted(Comparator.comparing(Rule::parent)).toList()){
   if(!seen.add(r.parent))throw new IllegalArgumentException("duplicate parent decay");BigInteger available=initial.nuclides().getOrDefault(r.parent,BigInteger.ZERO);if(available.signum()==0)continue;
   BigDecimal exact=new BigDecimal(available).multiply(BigDecimal.valueOf(-StrictMath.expm1(-StrictMath.log(2)*seconds/r.halfLife))).add(residual.getOrDefault(r.parent,BigDecimal.ZERO));BigInteger count=exact.toBigInteger().min(available);residual.put(r.parent,count.equals(available)?BigDecimal.ZERO:exact.subtract(new BigDecimal(count)));if(count.signum()==0)continue;
   Map<String,BigInteger> allocation=new TreeMap<>();Map<String,BigDecimal> scores=new TreeMap<>();BigInteger assigned=BigInteger.ZERO;
   for(Branch b:r.branches){String key=r.parent+"/"+b.id;BigDecimal quota=new BigDecimal(count).multiply(b.probability).add(debts.getOrDefault(key,BigDecimal.ZERO));BigInteger floor=quota.setScale(0,RoundingMode.FLOOR).toBigInteger().max(BigInteger.ZERO);allocation.put(b.id,floor);scores.put(b.id,quota.subtract(new BigDecimal(floor)));assigned=assigned.add(floor);}
   List<String> order=new ArrayList<>(allocation.keySet());order.sort(Comparator.<String,BigDecimal>comparing(scores::get).reversed().thenComparing(Comparator.naturalOrder()));
   BigInteger difference=count.subtract(assigned);
   if(difference.signum()<0)throw new IllegalStateException("invalid persisted branch debts");
   int extras=difference.intValueExact();if(extras>order.size())throw new IllegalStateException("invalid persisted branch debts");for(int i=0;i<extras;i++)allocation.compute(order.get(i),(k,n)->n.add(BigInteger.ONE));
   nuclei.merge(r.parent,count.negate(),BigInteger::add);
   for(Branch b:r.branches){BigInteger q=allocation.get(b.id);String key=r.parent+"/"+b.id;BigDecimal quota=new BigDecimal(count).multiply(b.probability).add(debts.getOrDefault(key,BigDecimal.ZERO));debts.put(key,quota.subtract(new BigDecimal(q)));nuclei.merge(b.daughter,q,BigInteger::add);for(var p:b.emissions)emissions.merge(p.id(),q.multiply(BigInteger.valueOf(p.count())),BigInteger::add);energy=energy.add(b.energyPerQuantum.multiply(new BigDecimal(q)));}
  }
  return new State(new DecayIntegrator.State(nuclei,residual,emissions,energy),debts);
 }
}

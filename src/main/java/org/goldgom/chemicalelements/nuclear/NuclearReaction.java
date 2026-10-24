package org.goldgom.chemicalelements.nuclear;
import java.math.*;
import java.util.*;

/** Nuclear game inventory only: chemical element view must be rebuilt from resulting nuclides. */
public final class NuclearReaction {
 public record Rule(String id,Map<DecayIntegrator.Nuclide,Integer> reactants,Map<DecayIntegrator.Nuclide,Integer> products,List<DecayIntegrator.Emission> incoming,List<DecayIntegrator.Emission> outgoing,BigDecimal energyPerQuantum,Set<String> capabilities,String basis,String source){
  public Rule {reactants=Map.copyOf(reactants);products=Map.copyOf(products);incoming=List.copyOf(incoming);outgoing=List.copyOf(outgoing);capabilities=Set.copyOf(capabilities);
   if(reactants.isEmpty()||products.isEmpty()||reactants.values().stream().anyMatch(n->n<=0)||products.values().stream().anyMatch(n->n<=0)||basis.isBlank()||source.isBlank())throw new IllegalArgumentException("invalid nuclear rule");
   Map<String,DecayIntegrator.Emission> identities=new HashMap<>();
   for(var p:java.util.stream.Stream.concat(incoming.stream(),outgoing.stream()).toList()){
    var previous=identities.putIfAbsent(p.id(),p);
    if(previous!=null&&(previous.nucleons()!=p.nucleons()||previous.charge()!=p.charge()))throw new IllegalArgumentException("conflicting particle identity: "+p.id());
   }
   if(!Arrays.equals(balance(reactants,incoming),balance(products,outgoing)))throw new IllegalArgumentException("nuclear nucleon/charge imbalance: "+id);
  }
 }
 public record Result(DecayIntegrator.State state,BigInteger extent,BigDecimal consumedEnergy,String reason) {}
 public static Result apply(DecayIntegrator.State initial,Rule rule,Set<String> capabilities,BigDecimal suppliedEnergy){
  if(suppliedEnergy.signum()<0)throw new IllegalArgumentException("negative energy");
  if(!capabilities.containsAll(rule.capabilities))return new Result(initial,BigInteger.ZERO,BigDecimal.ZERO,"missing nuclear facility conditions");
  BigInteger extent=null;for(var e:rule.reactants.entrySet()){BigInteger q=initial.nuclides().getOrDefault(e.getKey(),BigInteger.ZERO).divide(BigInteger.valueOf(e.getValue()));extent=extent==null?q:extent.min(q);}
  TreeMap<String,BigInteger> incomingCounts=new TreeMap<>();
  for(var particle:rule.incoming)incomingCounts.merge(particle.id(),BigInteger.valueOf(particle.count()),BigInteger::add);
  for(var particle:incomingCounts.entrySet()){BigInteger q=initial.emitted().getOrDefault(particle.getKey(),BigInteger.ZERO).divide(particle.getValue());extent=extent.min(q);}
  if(rule.energyPerQuantum.signum()<0)extent=extent.min(suppliedEnergy.divideToIntegralValue(rule.energyPerQuantum.negate()).toBigInteger());
  if(extent.signum()==0)return new Result(initial,BigInteger.ZERO,BigDecimal.ZERO,"insufficient reactants, particles or energy");
  TreeMap<DecayIntegrator.Nuclide,BigInteger> inventory=new TreeMap<>(initial.nuclides());TreeMap<String,BigInteger> particles=new TreeMap<>(initial.emitted());
  final BigInteger q=extent;rule.reactants.forEach((id,n)->inventory.merge(id,q.multiply(BigInteger.valueOf(n)).negate(),BigInteger::add));rule.products.forEach((id,n)->inventory.merge(id,q.multiply(BigInteger.valueOf(n)),BigInteger::add));
  for(var p:rule.incoming)particles.merge(p.id(),q.multiply(BigInteger.valueOf(p.count())).negate(),BigInteger::add);for(var p:rule.outgoing)particles.merge(p.id(),q.multiply(BigInteger.valueOf(p.count())),BigInteger::add);
  BigDecimal delta=rule.energyPerQuantum.multiply(new BigDecimal(q));
  return new Result(new DecayIntegrator.State(inventory,initial.residual(),particles,initial.energy().add(delta.max(BigDecimal.ZERO))),q,delta.min(BigDecimal.ZERO).negate(),"READY");
 }
 private static long[] balance(Map<DecayIntegrator.Nuclide,Integer> nuclei,List<DecayIntegrator.Emission> particles){long a=0,z=0;for(var e:nuclei.entrySet()){a=Math.addExact(a,Math.multiplyExact((long)e.getKey().a(),e.getValue()));z=Math.addExact(z,Math.multiplyExact((long)e.getKey().z(),e.getValue()));}for(var p:particles){a=Math.addExact(a,Math.multiplyExact((long)p.nucleons(),p.count()));z=Math.addExact(z,Math.multiplyExact((long)p.charge(),p.count()));}return new long[]{a,z};}
}

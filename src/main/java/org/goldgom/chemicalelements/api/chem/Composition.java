package org.goldgom.chemicalelements.api.chem;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import java.math.*;
import java.util.*;
public record Composition(Map<ResourceLocation,MolAmount> amounts) {
 public static final Composition EMPTY=new Composition(Map.of());
 public Composition { var m=new TreeMap<ResourceLocation,MolAmount>(); amounts.forEach((k,v)->{Objects.requireNonNull(k); if(!v.isZero())m.put(k,v);}); amounts=Collections.unmodifiableMap(m); }
 public MolAmount totalMol(){return amounts.values().stream().reduce(MolAmount.ZERO,MolAmount::add);}
 public Composition add(Composition b){var m=new TreeMap<>(amounts); b.amounts.forEach((k,v)->m.merge(k,v,MolAmount::add)); return new Composition(m);}
 public Composition subtract(Composition b){var m=new TreeMap<>(amounts); b.amounts.forEach((k,v)->m.put(k,m.getOrDefault(k,MolAmount.ZERO).subtract(v))); return new Composition(m);}
 public Composition multiply(BigInteger n){var m=new TreeMap<ResourceLocation,MolAmount>(); amounts.forEach((k,v)->m.put(k,v.multiply(n)));return new Composition(m);}
 public ElementInventory inventory(RegistrySnapshot s){var m=new TreeMap<ResourceLocation,BigInteger>(); BigInteger charge=BigInteger.ZERO; for(var e:amounts.entrySet()){var d=s.requireSpecies(e.getKey()); for(var atom:d.atoms().entrySet())m.merge(atom.getKey(),e.getValue().quanta().multiply(BigInteger.valueOf(atom.getValue())),BigInteger::add); charge=charge.add(e.getValue().quanta().multiply(BigInteger.valueOf(d.charge())));} return new ElementInventory(m,charge);}
 public BigDecimal mass(RegistrySnapshot s){BigDecimal mass=BigDecimal.ZERO; for(var e:amounts.entrySet()) mass=mass.add(e.getValue().decimal().multiply(s.requireSpecies(e.getKey()).molarMass()));return mass;}
 public Map<ResourceLocation,BigDecimal> massFractions(RegistrySnapshot s){var m=new TreeMap<ResourceLocation,BigDecimal>(); var total=mass(s); if(total.signum()!=0)amounts.forEach((k,v)->m.put(k,v.decimal().multiply(s.requireSpecies(k).molarMass()).divide(total,MathContext.DECIMAL128)));return Collections.unmodifiableMap(m);}
 public Optional<BigDecimal> meanMolarMass(RegistrySnapshot s){return totalMol().isZero()?Optional.empty():Optional.of(mass(s).divide(totalMol().decimal(),MathContext.DECIMAL128));}
}
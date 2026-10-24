package org.goldgom.chemicalelements.api.chem;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.*;
public record SpeciesDefinition(ResourceLocation id,Map<ResourceLocation,Integer> atoms,int charge,BigDecimal molarMass,Phase phase,DataBasis basis,String source) {
 public SpeciesDefinition { Objects.requireNonNull(id); Objects.requireNonNull(phase); Objects.requireNonNull(basis); Objects.requireNonNull(source); atoms=Collections.unmodifiableMap(new TreeMap<>(atoms)); if(atoms.values().stream().anyMatch(n->n<=0)||molarMass.signum()<0)throw new IllegalArgumentException("invalid species"); if(atoms.isEmpty()&&phase!=Phase.SPECIAL)throw new IllegalArgumentException("empty species requires special carrier"); }
}
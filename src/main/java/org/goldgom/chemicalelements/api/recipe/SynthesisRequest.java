package org.goldgom.chemicalelements.api.recipe;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.ReactionContext;
import java.util.*;
public record SynthesisRequest(List<Composition> slots,int width,int height,ReactionContext conditions,long registryEpoch) {
 public SynthesisRequest {slots=List.copyOf(slots);if(width<1||height<1||Math.multiplyExact(width,height)!=slots.size()||registryEpoch<0)throw new IllegalArgumentException("invalid grid");if(!aggregate(slots).equals(conditions.inputs()))throw new IllegalArgumentException("conditions input mismatch");}
 private static Composition aggregate(List<Composition> slots){return slots.stream().reduce(Composition.EMPTY,Composition::add);}
 public Composition species(){return aggregate(slots);}
 public ElementInventory elements(RegistrySnapshot snapshot){if(snapshot.epoch()!=registryEpoch)throw new IllegalStateException("stale registry");return species().inventory(snapshot);}
}
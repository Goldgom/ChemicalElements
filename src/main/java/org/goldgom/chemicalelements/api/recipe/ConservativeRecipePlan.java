package org.goldgom.chemicalelements.api.recipe;
import java.math.BigDecimal;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.ReactionContext;
/** Every material exit belongs here, including returned containers, waste and atmosphere. */
public record ConservativeRecipePlan(Composition inputs,Composition outputs,Composition byproducts,Composition returnedContainers,BigDecimal energyJ,BigDecimal durationSeconds,long registryEpoch,List<String> conditions) {
 public ConservativeRecipePlan {Objects.requireNonNull(inputs);Objects.requireNonNull(outputs);Objects.requireNonNull(byproducts);Objects.requireNonNull(returnedContainers);Objects.requireNonNull(energyJ);conditions=List.copyOf(conditions);if(durationSeconds.signum()<0||registryEpoch<0)throw new IllegalArgumentException("invalid plan");}
 public void validate(RegistrySnapshot snapshot,ReactionContext context){
  if(snapshot.epoch()!=registryEpoch)throw new IllegalStateException("stale_registry");
  if(!inputs.equals(context.inputs()))throw new IllegalArgumentException("input_species_changed");
  if(!inputs.inventory(snapshot).equals(outputs.add(byproducts).add(returnedContainers).inventory(snapshot)))throw new IllegalArgumentException("element_or_charge_imbalance");
  if(energyJ.signum()>0&&context.availableEnergyJ().compareTo(energyJ)<0)throw new IllegalArgumentException("insufficient_energy");
  if(!context.capabilities().containsAll(conditions))throw new IllegalArgumentException("conditions_not_met: "+conditions);
  if(context.elapsedSeconds().compareTo(durationSeconds)<0)throw new IllegalArgumentException("reaction_time_remaining");
 }
}

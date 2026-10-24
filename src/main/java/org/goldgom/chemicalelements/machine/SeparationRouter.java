package org.goldgom.chemicalelements.machine;

import java.util.*;
import java.math.BigDecimal;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;

/** Physical separation never invents species. Missing physical properties are UNMODELED, not guessed boiling points. */
public final class SeparationRouter {
 public record Result(String status,Composition selected,Composition remainder,List<String> reasons){public Result{reasons=List.copyOf(reasons);}}
 private SeparationRouter(){}
 public static Result separate(MachineKind kind,Composition input,RegistrySnapshot catalog){
  Set<Phase> accepted=switch(kind){case FILTER->Set.of(Phase.SOLID);case GAS_COLLECTOR,CONDENSER->Set.of(Phase.GAS);case CENTRIFUGE->Set.of(Phase.SOLID);default->Set.of();};
  if(accepted.isEmpty())return new Result("UNMODELED",Composition.EMPTY,input,List.of("Required solubility, vapor equilibrium or isotope separation model is not connected"));
  if(kind==MachineKind.CONDENSER)return new Result("UNMODELED",Composition.EMPTY,input,List.of("Condensation requires temperature-dependent phase equilibrium; no guessed phase conversion"));
  var selected=new TreeMap<ResourceLocation,MolAmount>();var rest=new TreeMap<ResourceLocation,MolAmount>();
  for(var entry:input.amounts().entrySet()){
   var definition=catalog.requireSpecies(entry.getKey());(accepted.contains(definition.phase())?selected:rest).put(entry.getKey(),entry.getValue());
  }
  return new Result("READY",new Composition(selected),new Composition(rest),List.of("Species-phase mechanical separation; no chemical conversion"));
 }
}

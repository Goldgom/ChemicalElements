package org.goldgom.chemicalelements.nuclear;
import com.google.gson.*;
import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
/** Pure parser. Owner publishes validated rules together and persists numerical residuals unchanged. */
public final class DecayLibrary {
 private DecayLibrary(){}
 public static List<BranchingDecay.Rule> parse(Map<ResourceLocation,JsonElement> files){
  List<BranchingDecay.Rule> rules=new ArrayList<>();Set<DecayIntegrator.Nuclide> parents=new HashSet<>();
  for(var file:new TreeMap<>(files).entrySet()){
   var j=file.getValue().getAsJsonObject();if(j.get("schema_version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported decay schema "+file.getKey());
   var parent=nuclide(j.getAsJsonObject("parent"));if(!parents.add(parent))throw new IllegalArgumentException("Duplicate decay parent "+parent);
   List<BranchingDecay.Branch> branches=new ArrayList<>();
   for(var value:j.getAsJsonArray("branches")){
    var b=value.getAsJsonObject();List<DecayIntegrator.Emission> emissions=new ArrayList<>();
    for(var emitted:b.getAsJsonArray("emissions")){var e=emitted.getAsJsonObject();emissions.add(new DecayIntegrator.Emission(e.get("id").getAsString(),integer(e,"nucleons"),integer(e,"charge"),integer(e,"count")));}
    branches.add(new BranchingDecay.Branch(b.get("id").getAsString(),new BigDecimal(b.get("probability").getAsString()),nuclide(b.getAsJsonObject("daughter")),emissions,new BigDecimal(b.get("energy_j_per_quantum").getAsString())));
   }
   rules.add(new BranchingDecay.Rule(parent,Double.parseDouble(j.get("half_life_seconds").getAsString()),branches,j.get("basis").getAsString()+": "+j.get("source").getAsString()));
  }
  return List.copyOf(rules);
 }
 private static int integer(JsonObject j,String field){return j.get(field).getAsBigDecimal().intValueExact();}
 private static DecayIntegrator.Nuclide nuclide(JsonObject j){return new DecayIntegrator.Nuclide(integer(j,"z"),integer(j,"a"),integer(j,"isomer"));}
}

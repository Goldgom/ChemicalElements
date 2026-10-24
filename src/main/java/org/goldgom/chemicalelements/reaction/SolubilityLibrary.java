package org.goldgom.chemicalelements.reaction;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.math.*;
import java.util.*;
public final class SolubilityLibrary extends SimpleJsonResourceReloadListener {
 private volatile List<PhaseEquilibrium.Salt> current=List.of();
 public SolubilityLibrary(){super(new Gson(),"chemicalelements/solubility");}
 public List<PhaseEquilibrium.Salt> snapshot(){return org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle().solubility();}
 @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
  current=parse(resources);
 }
 public static List<PhaseEquilibrium.Salt> parse(Map<ResourceLocation,JsonElement> resources){
  List<PhaseEquilibrium.Salt> next=new ArrayList<>();Set<String> solids=new HashSet<>();
  for(var entry:new TreeMap<>(resources).entrySet()){
   JsonObject j=entry.getValue().getAsJsonObject();if(j.get("schema_version").getAsBigDecimal().intValueExact()!=1||!j.get("model").getAsString().equals("ideal_concentration_product"))throw new IllegalArgumentException(entry.getKey()+": unsupported solubility schema/model");
   String solid=j.get("solid").getAsString();if(!solids.add(solid))throw new IllegalArgumentException("duplicate solubility solid "+solid);
   TreeMap<String,Integer> ions=new TreeMap<>();j.getAsJsonObject("ions").entrySet().forEach(e->ions.put(e.getKey(),e.getValue().getAsBigDecimal().intValueExact()));List<PhaseEquilibrium.Point> table=new ArrayList<>();
   for(JsonElement e:j.getAsJsonArray("temperature_table")){JsonObject point=e.getAsJsonObject();table.add(new PhaseEquilibrium.Point(new BigDecimal(point.get("kelvin").getAsString()),new BigDecimal(point.get("ksp").getAsString())));}
   next.add(new PhaseEquilibrium.Salt(solid,ions,table,j.get("basis").getAsString()+": "+j.get("source").getAsString()));
  }
  return List.copyOf(next);
 }
 public static void validate(List<PhaseEquilibrium.Salt> salts,org.goldgom.chemicalelements.api.data.RegistrySnapshot catalog){
  for(var salt:salts){
   var solid=catalog.requireSpecies(ResourceLocation.parse(salt.solid()));
   if(solid.phase()!=org.goldgom.chemicalelements.api.chem.Phase.SOLID)throw new IllegalArgumentException("Solubility source must be solid: "+salt.solid());
   TreeMap<String,CandidateSolver.Species> species=new TreeMap<>();
   catalog.species().forEach((id,s)->{TreeMap<String,Integer> atoms=new TreeMap<>();s.atoms().forEach((a,n)->atoms.put(a.toString(),n));species.put(id.toString(),new CandidateSolver.Species(atoms,s.charge(),s.phase().name()));});
   var domain=new CandidateSolver.Domain(salt.table().getFirst().kelvin(),salt.table().getLast().kelvin(),java.math.BigDecimal.ONE,new java.math.BigDecimal("1e20"),Set.of());
   var rule=new CandidateSolver.Rule(salt.solid(),"dissolution",Map.of(salt.solid(),1),salt.ions(),domain,null,1,"solubility",salt.source());
   new CandidateSolver(species,List.of(rule));
   for(String ion:salt.ions().keySet())if(catalog.requireSpecies(ResourceLocation.parse(ion)).phase()!=org.goldgom.chemicalelements.api.chem.Phase.AQUEOUS)throw new IllegalArgumentException("Solubility ion must be aqueous: "+ion);
  }
 }
}

package org.goldgom.chemicalelements.reaction;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.math.BigDecimal;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Immutable publication on successful parse. Catalog-dependent balance is validated by the engine. */
public final class ReactionLibrary extends SimpleJsonResourceReloadListener {
    private volatile ModeledSynthesisEngine.Library current=new ModeledSynthesisEngine.Library("empty",List.of(),32,4096);
    public ReactionLibrary(){super(new Gson(),"chemicalelements/reactions");}
    public ModeledSynthesisEngine.Library snapshot(){return org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle().reactions();}
    public static void validate(ModeledSynthesisEngine.Library library, org.goldgom.chemicalelements.api.data.RegistrySnapshot catalog){
        TreeMap<String,CandidateSolver.Species> species=new TreeMap<>();
        catalog.species().forEach((id,s)->{TreeMap<String,Integer> atoms=new TreeMap<>();s.atoms().forEach((a,n)->atoms.put(a.toString(),n));species.put(id.toString(),new CandidateSolver.Species(atoms,s.charge(),s.phase().name()));});
        new CandidateSolver(species,library.pathways().stream().map(ModeledSynthesisEngine.Pathway::rule).toList());
    }
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        current=parse(resources);
    }
    /** Pure preparation: A validates this against the candidate species catalog before atomic publication. */
    public static ModeledSynthesisEngine.Library parse(Map<ResourceLocation,JsonElement> resources){
        List<ModeledSynthesisEngine.Pathway> next=new ArrayList<>();StringBuilder canonical=new StringBuilder();
        for(var e:new TreeMap<>(resources).entrySet()){
            JsonObject j=e.getValue().getAsJsonObject();if(j.get("schema_version").getAsBigDecimal().intValueExact()!=1)throw new IllegalArgumentException(e.getKey()+": unsupported reaction schema");
            JsonObject c=j.getAsJsonObject("conditions");
            var domain=new CandidateSolver.Domain(decimal(c,"min_k"),decimal(c,"max_k"),decimal(c,"min_pa"),decimal(c,"max_pa"),strings(c,"capabilities"));
            BigDecimal delta=j.has("delta_g_j_per_mol")?decimal(j,"delta_g_j_per_mol"):null;
            var rule=new CandidateSolver.Rule(e.getKey().toString(),j.get("type").getAsString(),terms(j.getAsJsonObject("reactants")),terms(j.getAsJsonObject("products")),domain,delta,j.get("duration_ticks").getAsBigDecimal().intValueExact(),j.get("basis").getAsString(),j.get("source").getAsString());
            next.add(new ModeledSynthesisEngine.Pathway(rule,strings(c,"catalysts"),decimal(j,"energy_j_per_mol"),j.has("game_priority")?j.get("game_priority").getAsBigDecimal().intValueExact():0));canonical.append(e.getKey()).append(j);
        }
        try{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));return new ModeledSynthesisEngine.Library(hash,next,32,4096);}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static BigDecimal decimal(JsonObject j,String name){return new BigDecimal(j.get(name).getAsString());}
    private static Map<String,Integer> terms(JsonObject j){TreeMap<String,Integer> result=new TreeMap<>();j.entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsBigDecimal().intValueExact()));return result;}
    private static Set<String> strings(JsonObject j,String name){TreeSet<String> result=new TreeSet<>();if(j.has(name))j.getAsJsonArray(name).forEach(e->result.add(e.getAsString()));return result;}
}

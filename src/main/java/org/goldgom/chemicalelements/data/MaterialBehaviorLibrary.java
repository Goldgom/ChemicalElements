package org.goldgom.chemicalelements.data;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.thermal.*;

/** Immutable server-side rules. Query results are hints, NEVER authorization or phase predictions. */
public final class MaterialBehaviorLibrary {
    public enum ModelStatus { UNKNOWN, UNMODELED, MODEL_AVAILABLE }
    public record Query(long epoch, String catalogHash, Set<ResourceLocation> tags,
            Set<ResourceLocation> matchedRules, ModelStatus modelStatus) {
        public Query { tags=Set.copyOf(tags); matchedRules=Set.copyOf(matchedRules); }
    }
    private final RegistrySnapshot catalog;
    private final PhaseLibrary phases;
    private final Map<ResourceLocation,MaterialBehaviorDefinition> definitions;
    private MaterialBehaviorLibrary(RegistrySnapshot catalog, PhaseLibrary phases,
            Map<ResourceLocation,MaterialBehaviorDefinition> definitions) {
        this.catalog=catalog; this.phases=phases; this.definitions=Map.copyOf(definitions);
    }
    public RegistrySnapshot catalog(){return catalog;}
    public PhaseLibrary phases(){return phases;}
    public Map<ResourceLocation,MaterialBehaviorDefinition> definitions(){return definitions;}
    public static MaterialBehaviorLibrary load(RegistrySnapshot catalog, PhaseLibrary phases,
            Map<ResourceLocation,String> documents) {
        if(phases.catalog()!=catalog) throw new IllegalArgumentException("Different phase catalog identity");
        var result=new TreeMap<ResourceLocation,MaterialBehaviorDefinition>();
        for(var e:documents.entrySet()) {
            var o=parseRaw(e.getValue()).getAsJsonObject();
            if(!o.keySet().equals(Set.of("schema_version","id","composition_kind","all_species","tags","phase_model","basis","source","limitations")))
                throw new IllegalArgumentException("Missing/unknown material fields: "+e.getKey());
            if(!o.get("schema_version").isJsonPrimitive() || !o.getAsJsonPrimitive("schema_version").isNumber()
                    || !o.get("schema_version").getAsString().equals("1")) throw new IllegalArgumentException("schema_version");
            var id=ResourceLocation.parse(text(o.get("id")));
            if(!id.equals(e.getKey())) throw new IllegalArgumentException("Material resource ID mismatch");
            var kind=MaterialBehaviorDefinition.CompositionKind.valueOf(text(o.get("composition_kind")));
            var species=ids(o.get("all_species")); species.forEach(catalog::requireSpecies);
            var tags=ids(o.get("tags")); if(tags.isEmpty()) throw new IllegalArgumentException("Empty tags");
            Optional<ResourceLocation> model=o.get("phase_model").isJsonNull()?Optional.empty():Optional.of(ResourceLocation.parse(text(o.get("phase_model"))));
            if(kind==MaterialBehaviorDefinition.CompositionKind.PURE && species.size()>1) throw new IllegalArgumentException("Impossible pure selector");
            if(model.isPresent()) {
                var m=phases.get(model.get());
                if(m==null) throw new IllegalArgumentException("Unknown phase model "+model.get());
                if(kind!=MaterialBehaviorDefinition.CompositionKind.PURE || species.size()!=1 || !species.contains(m.subject()))
                    throw new IllegalArgumentException("Phase reference requires exact pure subject selector");
            }
            String basis=text(o.get("basis"));
            if(!basis.equals("GAME_CONVENTION")) throw new IllegalArgumentException("Classification is a game convention, not physical evidence");
            result.put(id,new MaterialBehaviorDefinition(id,kind,species,tags,model,basis,text(o.get("source")),text(o.get("limitations"))));
        }
        return new MaterialBehaviorLibrary(catalog,phases,result);
    }
    /** Caller captures one bundle, supplies authoritative Composition; T/P and inventory remain with the Owner.
     * MODEL_AVAILABLE still requires EnthalpyPlanner domain/reference/heat checks and the existing transaction. */
    public Query query(Composition composition) {
        Objects.requireNonNull(composition);
        var species=composition.amounts().keySet(); species.forEach(catalog::requireSpecies);
        var tags=new TreeSet<ResourceLocation>(); var matched=new TreeSet<ResourceLocation>();
        ModelStatus status=species.size()>1?ModelStatus.UNMODELED:ModelStatus.UNKNOWN;
        for(var d:definitions.values()) {
            boolean kind=switch(d.compositionKind()){case ANY->true;case PURE->species.size()==1;case MIXTURE->species.size()>1;};
            if(species.isEmpty() || !kind || !species.containsAll(d.allSpecies())) continue;
            tags.addAll(d.tags()); matched.add(d.id());
            if(species.size()==1 && d.phaseModel().isPresent()) {
                var m=phases.get(d.phaseModel().get());
                if(m.hasEnthalpy()) status=ModelStatus.MODEL_AVAILABLE;
                else if(m.kind()==PhaseModel.Kind.UNMODELED && status!=ModelStatus.MODEL_AVAILABLE) status=ModelStatus.UNMODELED;
            }
        }
        return new Query(catalog.epoch(),catalog.contentHash(),tags,matched,status);
    }
    private static Set<ResourceLocation> ids(JsonElement value) {
        if(!value.isJsonArray()) throw new IllegalArgumentException("Array required");
        var result=new TreeSet<ResourceLocation>();
        for(var e:value.getAsJsonArray()) if(!result.add(ResourceLocation.parse(text(e)))) throw new IllegalArgumentException("Duplicate ID");
        return result;
    }
    private static String text(JsonElement e) {
        if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank() || e.getAsString().length()>4096)
            throw new IllegalArgumentException("Nonempty bounded string required");
        return e.getAsString();
    }
    /** Used by the resource listener before conversion, so duplicate keys cannot disappear in a JsonObject. */
    public static JsonElement parseRaw(String raw) {
        if(raw.length()>1048576) throw new IllegalArgumentException("Chemistry document too large");
        try(var r=new JsonReader(new StringReader(raw))) {
            r.setLenient(false); var value=read(r,0);
            if(r.peek()!=JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON");
            return value;
        } catch(IOException e){throw new IllegalArgumentException("Invalid chemistry JSON",e);}
    }
    private static JsonElement read(JsonReader r,int depth) throws IOException {
        if(depth>32) throw new IllegalArgumentException("JSON depth");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {var o=new JsonObject();r.beginObject();while(r.hasNext()){String k=r.nextName();if(o.has(k))throw new IllegalArgumentException("Duplicate JSON key: "+k);o.add(k,read(r,depth+1));}r.endObject();yield o;}
            case BEGIN_ARRAY -> {var a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>=16384)throw new IllegalArgumentException("Array size");a.add(read(r,depth+1));}r.endArray();yield a;}
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> {r.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IllegalArgumentException("Invalid JSON token");
        };
    }
}

package org.goldgom.chemicalelements.thermal;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.Phase;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;

/** Explicit candidate loader; caller must publish alongside the SAME catalog atomically. Not a reload listener. */
public final class PhaseLibrary {
    private final RegistrySnapshot catalog;
    private final Map<ResourceLocation, PhaseModel> models;
    private PhaseLibrary(RegistrySnapshot catalog, Map<ResourceLocation, PhaseModel> models) {
        this.catalog = catalog; this.models = Map.copyOf(models);
    }
    public RegistrySnapshot catalog() { return catalog; }
    public Map<ResourceLocation, PhaseModel> models() { return models; }
    public PhaseModel get(ResourceLocation id) { return models.get(id); }
    public static PhaseLibrary load(RegistrySnapshot catalog, Map<ResourceLocation, String> documents) {
        var models = new TreeMap<ResourceLocation, PhaseModel>();
        for (var entry : documents.entrySet()) {
            var o = parse(entry.getValue()).getAsJsonObject();
            keys(o, "schema_version,id,subject,allotrope,kind,reason,domain,energy_reference,low_species,high_species,transition_k,cp_low_j_mol_k,cp_high_j_mol_k,latent_j_mol,legacy_operation,sources");
            if (!o.get("schema_version").isJsonPrimitive() || !o.get("schema_version").getAsJsonPrimitive().isNumber()
                    || !o.get("schema_version").getAsString().equals("1")) fail("schema_version");
            var id = id(text(o,"id")); if (!id.equals(entry.getKey())) fail("resource/model ID mismatch");
            var subject = id(text(o,"subject")); catalog.requireSpecies(subject);
            var kind = PhaseModel.Kind.valueOf(text(o,"kind"));
            var sources = new ArrayList<PhaseModel.Source>(); var sourceIds = new HashSet<String>();
            if (!o.get("sources").isJsonArray()) fail("sources array required");
            for (var e : o.getAsJsonArray("sources")) {
                var s=e.getAsJsonObject(); keys(s,"id,uri,citation,locator,accessed,basis,method,pressure_evidence,uncertainty,license_status,units,limitations");
                String sid=text(s,"id"), uri=text(s,"uri"), basis=text(s,"basis"), license=text(s,"license_status");
                if (!sourceIds.add(sid) || !URI.create(uri).isAbsolute()) fail("source ID/URI");
                LocalDate.parse(text(s,"accessed"));
                if (!Set.of("MEASURED","EVALUATED","PREDICTED","GAME_CONVENTION","UNVERIFIED").contains(basis)) fail("source basis");
                if (!Set.of("CLEARED","NOT_CLEARED","UNKNOWN","EXISTING_CODE_ONLY").contains(license)) fail("license status");
                if (!text(s,"units").equals("K;Pa;J/mol;J/mol/K")) fail("SI units required (pressure Pa, not bar)");
                sources.add(new PhaseModel.Source(sid,uri,text(s,"citation"),text(s,"locator"),text(s,"accessed"),basis,
                        text(s,"method"),text(s,"pressure_evidence"),text(s,"uncertainty"),license,text(s,"units"),text(s,"limitations")));
            }
            PhaseModel.Domain domain=null;
            if (!o.get("domain").isJsonNull()) {
                var d=o.getAsJsonObject("domain"); keys(d,"min_k,max_k,min_pa,max_pa,ensemble");
                if (!text(d,"ensemble").equals("ISOBARIC_APPROXIMATION")) fail("ensemble");
                domain=new PhaseModel.Domain(number(d,"min_k"),number(d,"max_k"),number(d,"min_pa"),number(d,"max_pa"));
            }
            String reference=nullable(o,"energy_reference"), low=nullable(o,"low_species"), high=nullable(o,"high_species"), op=nullable(o,"legacy_operation");
            BigDecimal t=nullableNumber(o,"transition_k"), cl=nullableNumber(o,"cp_low_j_mol_k"), ch=nullableNumber(o,"cp_high_j_mol_k"), latent=nullableNumber(o,"latent_j_mol");
            if (kind==PhaseModel.Kind.UNKNOWN || kind==PhaseModel.Kind.UNMODELED) {
                if (domain!=null || reference!=null || low!=null || high!=null || op!=null || t!=null || cl!=null || ch!=null || latent!=null) fail("unknown must not claim physical parameters");
            } else if (kind==PhaseModel.Kind.LEGACY_WATER_LATENT) {
                if (!Set.of("melt","freeze","vaporize","condense").contains(op==null?"":op) || domain!=null || reference!=null || low!=null || high!=null || t!=null || cl!=null || ch!=null || latent!=null || sources.isEmpty()) fail("legacy adapter cannot override old constants or claim complete enthalpy");
                if (!subject.equals(legacy(op).from())) fail("legacy subject");
            } else {
                if (domain==null || reference==null || low==null || high==null || t==null || cl==null || ch==null || latent==null || op!=null || sources.isEmpty()) fail("incomplete enthalpy model");
                if (cl.signum()<=0 || ch.signum()<=0 || latent.signum()<=0 || t.compareTo(domain.minK())<=0 || t.compareTo(domain.maxK())>=0) fail("positive Cp/latent and interior boundary required");
                if (sources.stream().anyMatch(s -> !s.licenseStatus().equals("CLEARED") || s.basis().equals("UNVERIFIED"))) fail("unverified/uncleared enthalpy source");
                var a=catalog.requireSpecies(id(low)); var b=catalog.requireSpecies(id(high));
                if (low.equals(high) || !subject.equals(id(low)) || a.charge()!=0 || b.charge()!=0 || !a.atoms().equals(b.atoms()) || a.molarMass().compareTo(b.molarMass())!=0 || a.molarMass().signum()<=0) fail("phase identity/mass mismatch");
                if (!Set.of(Phase.SOLID,Phase.LIQUID,Phase.GAS).contains(a.phase()) || !Set.of(Phase.SOLID,Phase.LIQUID,Phase.GAS).contains(b.phase()) || a.phase().ordinal()>=b.phase().ordinal()) fail("positive-latent heating branch must follow solid/liquid/gas order; allotrope transition requires separate model");
                for (var old : models.values()) if (old.hasEnthalpy() && Set.of(old.lowSpecies(),old.highSpecies()).stream().anyMatch(x->x.equals(a.id())||x.equals(b.id()))) fail("ambiguous species models (no priority rule)");
            }
            var model=new PhaseModel(id,subject,text(o,"allotrope"),kind,text(o,"reason"),domain,reference,
                    low==null?null:id(low),high==null?null:id(high),t,cl,ch,latent,op,sources);
            if(models.put(id,model)!=null) fail("duplicate model");
        }
        return new PhaseLibrary(catalog,models);
    }
    public static PhaseProcess.Transition legacy(String operation) {
        return switch(operation) {case "melt"->WaterPhaseModels.melt(); case "freeze"->WaterPhaseModels.freeze(); case "vaporize"->WaterPhaseModels.vaporize(); case "condense"->WaterPhaseModels.condense(); default->throw new IllegalArgumentException("Unknown legacy operation");};
    }
    static BigDecimal number(JsonObject o,String key) {
        String s=text(o,key); if(s.length()>80 || !s.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) throw new IllegalArgumentException("Invalid decimal: "+key);
        var n=new BigDecimal(s); if(n.precision()>40 || n.scale()>18 || n.abs().compareTo(new BigDecimal("1e21"))>0) fail("decimal bounds"); return n;
    }
    private static BigDecimal nullableNumber(JsonObject o,String k){return o.get(k).isJsonNull()?null:number(o,k);}
    private static String nullable(JsonObject o,String k){return o.get(k).isJsonNull()?null:text(o,k);}
    static String text(JsonObject o,String k) {var e=o.get(k); if(e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank() || e.getAsString().length()>4096) throw new IllegalArgumentException("Nonempty string required: "+k);return e.getAsString();}
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static void keys(JsonObject o,String csv){if(!o.keySet().equals(Set.of(csv.split(",")))) fail("Missing/unknown fields: "+csv);}
    private static void fail(String message){throw new IllegalArgumentException(message);}
    /** Reject duplicate keys, trailing values and non-JSON leniency BEFORE Gson object conversion. */
    private static JsonElement parse(String raw) {
        if(raw.length()>65536) throw new IllegalArgumentException("model too large");
        try(var reader=new JsonReader(new StringReader(raw))) {
            reader.setLenient(false); JsonElement value=read(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT) fail("trailing JSON"); return value;
        } catch(IOException e){throw new IllegalArgumentException("Invalid model JSON",e);}
    }
    private static JsonElement read(JsonReader r,int depth) throws IOException {
        if(depth>12) throw new IllegalArgumentException("JSON nesting");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {var o=new JsonObject();r.beginObject();while(r.hasNext()){String n=r.nextName();if(o.has(n))fail("duplicate JSON key "+n);o.add(n,read(r,depth+1));}r.endObject();yield o;}
            case BEGIN_ARRAY -> {var a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>=256)fail("array limit");a.add(read(r,depth+1));}r.endArray();yield a;}
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> {r.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IllegalArgumentException("Invalid JSON token");
        };
    }
}

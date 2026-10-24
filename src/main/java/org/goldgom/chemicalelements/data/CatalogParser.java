package org.goldgom.chemicalelements.data;
import com.google.gson.*;
import java.util.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
public final class CatalogParser {
 private CatalogParser(){}
 private static final MigrationChain MIGRATION=new MigrationChain(1,Map.of());
 public static RegistrySnapshot parse(long epoch,Map<ResourceLocation,JsonElement> files){
 var elements=new TreeMap<ResourceLocation,ElementDefinition>();var species=new TreeMap<ResourceLocation,SpeciesDefinition>();var items=new TreeMap<ResourceLocation,Composition>();var priorities=new HashMap<ResourceLocation,Integer>();var ordered=new TreeMap<>(files);
 for(var e:ordered.entrySet())if(e.getKey().getPath().startsWith("elements/")){var d=object(e);var id=id(e.getKey(),"elements/");elements.put(id,new ElementDefinition(id,d.get("atomic_number").getAsInt(),d.get("symbol").getAsString(),decimal(d,"molar_mass_g_mol"),basis(d),d.get("source").getAsString()));}
 var numbers=new HashSet<Integer>();for(var d:elements.values())if(!numbers.add(d.atomicNumber()))throw new IllegalArgumentException("duplicate atomic number "+d.atomicNumber());if(numbers.size()!=118)throw new IllegalArgumentException("expected all 118 elements, got "+numbers.size());
 for(var e:ordered.entrySet())if(e.getKey().getPath().startsWith("species/")){var d=object(e);var id=id(e.getKey(),"species/");var atoms=new TreeMap<ResourceLocation,Integer>();d.getAsJsonObject("atoms").entrySet().forEach(a->atoms.put(ResourceLocation.parse(a.getKey()),a.getValue().getAsInt()));species.put(id,new SpeciesDefinition(id,atoms,d.get("charge").getAsInt(),decimal(d,"molar_mass_g_mol"),Phase.valueOf(d.get("phase").getAsString().toUpperCase(Locale.ROOT)),basis(d),d.get("source").getAsString()));}
 for(var e:ordered.entrySet())if(e.getKey().getPath().startsWith("item_compositions/")){var d=object(e);basis(d);var m=new TreeMap<ResourceLocation,MolAmount>();d.getAsJsonObject("per_instance").entrySet().forEach(a->m.put(ResourceLocation.parse(a.getKey()),MolAmount.readBounded(a.getValue().getAsString())));var c=new Composition(m);int priority=d.get("priority").getAsInt();for(var value:d.getAsJsonObject("match").getAsJsonArray("items")){var id=ResourceLocation.parse(value.getAsString());var old=priorities.get(id);if(old!=null&&old==priority)throw new IllegalArgumentException("conflicting composition "+id+" at "+e.getKey());if(old==null||priority>old){priorities.put(id,priority);items.put(id,c);}}}
 try{var digest=MessageDigest.getInstance("SHA-256");ordered.forEach((k,v)->{digest.update(k.toString().getBytes(StandardCharsets.UTF_8));digest.update(v.toString().getBytes(StandardCharsets.UTF_8));});return new RegistrySnapshot(epoch,HexFormat.of().formatHex(digest.digest()),elements,species,items);}catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}
 }
 private static JsonObject object(Map.Entry<ResourceLocation,JsonElement> e){try{return MIGRATION.migrate(e.getValue().getAsJsonObject());}catch(RuntimeException ex){throw new IllegalArgumentException(e.getKey()+": "+ex.getMessage(),ex);}}
 private static ResourceLocation id(ResourceLocation file,String prefix){return ResourceLocation.fromNamespaceAndPath(file.getNamespace(),file.getPath().substring(prefix.length()));}
 private static BigDecimal decimal(JsonObject d,String key){if(!d.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be a decimal string");return new BigDecimal(d.get(key).getAsString());}
 private static DataBasis basis(JsonObject d){return DataBasis.valueOf(d.get("basis").getAsString().toUpperCase(Locale.ROOT));}
}
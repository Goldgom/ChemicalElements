package org.goldgom.chemicalelements.data;

import java.util.*;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.data.*;
import org.goldgom.chemicalelements.api.chem.SpeciesDefinition;
import org.goldgom.chemicalelements.reaction.*;

public final class AtomicChemistryCatalog implements ChemistryCatalog {
 public static final AtomicChemistryCatalog INSTANCE=new AtomicChemistryCatalog();
 private final AtomicReference<ChemistryBundle> current=new AtomicReference<>(new ChemistryBundle(
   new RegistrySnapshot(0,"uninitialized",Map.of(),Map.of(),Map.of()),
   new ModeledSynthesisEngine.Library("empty",List.of(),32,4096),List.of()));
 private final Map<ResourceLocation,SpeciesDefinition> tombstones=new HashMap<>();
 public ChemistryBundle bundle(){return current.get();}
 public RegistrySnapshot snapshot(){return bundle().catalog();}
 public synchronized RegistrySnapshot reload(Map<ResourceLocation,JsonElement> files){
   var origins=new TreeMap<ResourceLocation,String>();
   files.keySet().forEach(id->origins.put(id,"programmatic:unspecified"));
   return reload(files,origins);
 }
 /** Legacy programmatic trees have no original lexical JSON. Never used by the resource listener. */
 public synchronized RegistrySnapshot reload(Map<ResourceLocation,JsonElement> files,Map<ResourceLocation,String> sourcePacks){
   var documents=new TreeMap<ResourceLocation,String>();
   files.forEach((id,json)->documents.put(id,json.toString()));
   return reload(files,sourcePacks,documents);
 }
 /** Resource reload MUST supply the selected resource's actual original text. */
 public synchronized RegistrySnapshot reload(Map<ResourceLocation,JsonElement> files,Map<ResourceLocation,String> sourcePacks,
                                             Map<ResourceLocation,String> originalDocuments){
   files=Map.copyOf(files);
   var documents=Map.copyOf(originalDocuments);
   if(!documents.keySet().equals(files.keySet())) throw new IllegalArgumentException("Original resource set mismatch");
   for(var entry:files.entrySet()) if(!MaterialBehaviorLibrary.parseRaw(documents.get(entry.getKey())).equals(entry.getValue()))
     throw new IllegalArgumentException("Original resource/tree mismatch: "+entry.getKey());
   var sources=Map.copyOf(sourcePacks);
   if(!sources.keySet().equals(files.keySet())||sources.values().stream().anyMatch(String::isBlank))
     throw new IllegalArgumentException("Each chemistry resource requires its selected source pack");
   var old=current.get();
   var next=CatalogParser.parse(Math.addExact(old.epoch(),1),files);
   var reactions=ReactionLibrary.parse(section(files,"reactions/"));
   var solubility=SolubilityLibrary.parse(section(files,"solubility/"));
   ReactionLibrary.validate(reactions,next);
   validateSolubility(solubility,next);
   var phases=org.goldgom.chemicalelements.thermal.PhaseLibrary.load(next,rawSection(documents,"phase_models/"));
   var materials=MaterialBehaviorLibrary.load(next,phases,rawSection(documents,"material_behaviors/"));
   var prepared=new ChemistryBundle(next,reactions,solubility,sources,phases,materials);
   // No externally visible writes until every parser and cross-reference validator succeeds.
   old.catalog().species().forEach((id,d)->{if(!next.species().containsKey(id))tombstones.put(id,d);});
   current.set(prepared);
   return next;
 }
 private static Map<ResourceLocation,String> rawSection(Map<ResourceLocation,String> files,String prefix){
   var result=new TreeMap<ResourceLocation,String>();
   files.forEach((id,raw)->{if(id.getPath().startsWith(prefix))result.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),id.getPath().substring(prefix.length())),raw);});
   return result;
 }
 private static Map<ResourceLocation,JsonElement> section(Map<ResourceLocation,JsonElement> files,String prefix){
   var result=new TreeMap<ResourceLocation,JsonElement>();
   files.forEach((id,json)->{if(id.getPath().startsWith(prefix))result.put(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),id.getPath().substring(prefix.length())),json);});
   return result;
 }
 private static void validateSolubility(List<PhaseEquilibrium.Salt> salts,RegistrySnapshot catalog){
   for(var salt:salts){
     var solid=requireSpecies(catalog,salt.solid());
     var atoms=new TreeMap<ResourceLocation,BigInteger>();
     BigInteger charge=BigInteger.ZERO;
     for(var entry:salt.ions().entrySet()){
       var ion=requireSpecies(catalog,entry.getKey());
       BigInteger coefficient=BigInteger.valueOf(entry.getValue());
       ion.atoms().forEach((id,n)->atoms.merge(id,coefficient.multiply(BigInteger.valueOf(n)),BigInteger::add));
       charge=charge.add(coefficient.multiply(BigInteger.valueOf(ion.charge())));
     }
     var expected=new TreeMap<ResourceLocation,BigInteger>();
     solid.atoms().forEach((id,n)->expected.put(id,BigInteger.valueOf(n)));
     if(!expected.equals(atoms)||!charge.equals(BigInteger.valueOf(solid.charge())))throw new IllegalArgumentException("Unbalanced dissolution: "+salt.solid());
   }
 }
 private static SpeciesDefinition requireSpecies(RegistrySnapshot catalog,String id){
   var result=catalog.species().get(ResourceLocation.parse(id));
   if(result==null)throw new IllegalArgumentException("Unknown solubility species: "+id);
   return result;
 }
 public synchronized Optional<SpeciesDefinition> orphaned(ResourceLocation id){return Optional.ofNullable(tombstones.get(id));}
}

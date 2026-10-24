package org.goldgom.chemicalelements.gametest.core;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.data.*;
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class CatalogGameTests {
 @GameTest(template="core_empty", timeoutTicks=100)
 public static void catalogLoads(GameTestHelper helper) {
  var snapshot=AtomicChemistryCatalog.INSTANCE.snapshot();
  helper.assertTrue(snapshot.elements().size()==118,"All 118 elements must load through real resource manager");
  helper.assertTrue(snapshot.species().size()>=138,"Bundled molecular and phase species must load");
  var bundle=AtomicChemistryCatalog.INSTANCE.bundle();
  var manager=helper.getLevel().getServer().getResourceManager();
  helper.assertTrue(!bundle.sourcePacks().isEmpty(),"Live reload must publish resource-pack provenance");
  bundle.sourcePacks().forEach((key,pack)->{
   var path=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(key.getNamespace(),"chemicalelements/"+key.getPath()+".json");
   helper.assertTrue(manager.getResource(path).orElseThrow().sourcePackId().equals(pack),"Published selected pack must match actual resource manager: "+key);
  });
  var models=org.goldgom.chemicalelements.world.SimulationBootstrap.models();
  helper.assertTrue(models.epoch()==bundle.epoch()&&models.reactions()==bundle.reactions()&&models.salts().equals(bundle.solubility()),"Runtime models must be bound to one published epoch");
  helper.assertTrue(!models.reactions().pathways().isEmpty()&&!models.salts().isEmpty(),"Production reaction and dissolution definitions must be present");
  var categories=org.goldgom.chemicalelements.registry.CreativeCategories.classifications();
  helper.assertTrue(categories.values().stream().filter(c->c==org.goldgom.chemicalelements.registry.CreativeCategories.Category.ELEMENTS).count()==118,"118 registered elements in separate primary category");
  for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM){var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);if(id.getNamespace().equals("minecraft"))helper.assertTrue(snapshot.itemCompositions().containsKey(id),"Missing composition: "+id);}
  var water=new org.goldgom.chemicalelements.api.chem.Composition(java.util.Map.of(net.minecraft.resources.ResourceLocation.parse("chemicalelements:water"),org.goldgom.chemicalelements.api.chem.MolAmount.ONE));
  var context=org.goldgom.chemicalelements.api.reaction.ReactionContext.workbench(water);
  helper.assertTrue(context.temperatureK().toPlainString().equals("298.15")&&context.availableEnergyJ().signum()==0,"Workbench cannot supply machine conditions");
  try {RuntimeCoverageAudit.write(java.nio.file.Path.of("a-audit"));}catch(java.io.IOException ex){throw new RuntimeException(ex);}
  helper.succeed();
 }
 @GameTest(template="core_empty", timeoutTicks=100)
 public static void resourceReloadIsAtomic(GameTestHelper helper) {
  var files=new java.util.TreeMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonElement>();
  var manager=helper.getLevel().getServer().getResourceManager();
  manager.listResources("chemicalelements",id->id.getPath().endsWith(".json")).forEach((id,resource)->{
   String path=id.getPath();
   var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(),path.substring("chemicalelements/".length(),path.length()-5));
   try(var reader=resource.openAsReader()){files.put(key,com.google.gson.JsonParser.parseReader(reader));}
   catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
  });
  var isolated=new AtomicChemistryCatalog();
  isolated.reload(files);var first=isolated.bundle();
  helper.assertTrue(first.catalog().elements().size()==118&&!first.reactions().pathways().isEmpty(),"Actual resource-manager data must publish a complete bundle");
  var broken=new java.util.TreeMap<>(files);
  var reaction=broken.keySet().stream().filter(id->id.getPath().startsWith("reactions/")).findFirst().orElseThrow();
  broken.put(reaction,new com.google.gson.JsonPrimitive("malformed production reaction"));
  boolean rejected=false;try{isolated.reload(broken);}catch(RuntimeException expected){rejected=true;}
  helper.assertTrue(rejected&&isolated.bundle()==first,"Invalid reaction must retain identical prior catalog/reaction/solubility bundle");
  for(String section:java.util.List.of("elements/","species/","solubility/")){
   var invalid=new java.util.TreeMap<>(files);
   var key=invalid.keySet().stream().filter(id->id.getPath().startsWith(section)).findFirst().orElseThrow();
   invalid.put(key,new com.google.gson.JsonPrimitive("malformed "+section));
   boolean failed=false;try{isolated.reload(invalid);}catch(RuntimeException expected){failed=true;}
   helper.assertTrue(failed&&isolated.bundle()==first,"Invalid "+section+" must preserve identical published bundle and epoch");
  }
  isolated.reload(files);var second=isolated.bundle();
  helper.assertTrue(second.epoch()==first.epoch()+1&&second.catalog().contentHash().equals(first.catalog().contentHash()),"Failure consumes no epoch and corrected resource reload recovers deterministically");
  helper.assertTrue(AtomicChemistryCatalog.INSTANCE.bundle()!=second,"Test must never replace live server catalog");
  helper.succeed();
 }
}

package org.goldgom.chemicalelements.gametest.core;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.data.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialReloadGameTests {
    private static ResourceLocation id(String path){return ResourceLocation.parse("chemicalelements:"+path);}
    /** Calls the actual public asynchronous reload lifecycle, including prepare and apply. */
    public static void reload(AtomicChemistryCatalog target, ResourceManager manager) {
        var barrier=new PreparableReloadListener.PreparationBarrier(){
            public <T> CompletableFuture<T> wait(T value){return CompletableFuture.completedFuture(value);}
        };
        new ChemistryReloadListener(target).reload(barrier,manager,InactiveProfiler.INSTANCE,InactiveProfiler.INSTANCE,Runnable::run,Runnable::run).join();
    }
    private static ResourceManager changed(ResourceManager original,ResourceLocation path,String raw) {
        Resource selected=original.getResource(path).orElseThrow();
        var replacement=new Resource(selected.source(),()->new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},(proxy,method,args)->{
            if(method.getName().equals("listResources")) {
                @SuppressWarnings("unchecked") var found=(Map<ResourceLocation,Resource>)method.invoke(original,args);
                var result=new TreeMap<>(found); if(result.containsKey(path))result.put(path,replacement); return result;
            }
            if(method.getName().equals("getResource") && path.equals(args[0]))return Optional.of(replacement);
            return method.invoke(original,args);
        });
    }
    private static String raw(ResourceManager manager,ResourceLocation path) throws IOException {
        try(var r=manager.getResource(path).orElseThrow().openAsReader()){var b=new StringBuilder();char[] c=new char[4096];int n;while((n=r.read(c))!=-1)b.append(c,0,n);return b.toString();}
    }
    private static void rejected(AtomicChemistryCatalog target,ResourceManager manager,ChemistryBundle old,String label) {
        boolean failed=false;try{reload(target,manager);}catch(RuntimeException expected){failed=true;}
        require(failed && target.bundle()==old,"Entire bundle/epoch preserved: "+label);
        require(target.bundle().phases()==old.phases() && target.bundle().materials()==old.materials(),"Library identity preserved");
    }
    public static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void selectedResourceReloadIsAtomic(GameTestHelper h) throws Exception {
        var manager=h.getLevel().getServer().getResourceManager();
        var live=AtomicChemistryCatalog.INSTANCE.bundle();var target=new AtomicChemistryCatalog();
        reload(target,manager);var first=target.bundle();
        require(first.catalog()==first.phases().catalog() && first.catalog()==first.materials().catalog(),"One catalog identity and epoch");
        require(first.phases().models().size()>=122 && first.materials().definitions().size()>=3,"Actual phase and material resources");
        first.sourcePacks().forEach((key,pack)->require(manager.getResource(ResourceLocation.fromNamespaceAndPath(key.getNamespace(),"chemicalelements/"+key.getPath()+".json")).orElseThrow().sourcePackId().equals(pack),"Selected pack provenance"));
        var phase=id("chemicalelements/phase_models/iron_unknown.json");
        var material=id("chemicalelements/material_behaviors/iron_model.json");
        String phaseRaw=raw(manager,phase),materialRaw=raw(manager,material);
        rejected(target,changed(manager,phase,"{broken"),first,"malformed phase must not be skipped by prepare");
        rejected(target,changed(manager,material,"[]"),first,"bad behavior");
        rejected(target,changed(manager,phase,phaseRaw.replace("\"kind\": \"UNKNOWN\"","\"kind\": \"UNKNOWN\", \"kind\": \"UNKNOWN\"")),first,"raw duplicate phase key");
        rejected(target,changed(manager,material,materialRaw.replace("\"schema_version\": 1","\"schema_version\": 1, \"schema_version\": 1")),first,"raw duplicate behavior key");
        rejected(target,changed(manager,material,materialRaw.replace("chemicalelements:iron_unknown","chemicalelements:missing_model")),first,"invalid phase reference");
        rejected(target,changed(manager,material,materialRaw.replace("\"chemicalelements:iron\"","\"chemicalelements:missing_species\"")),first,"invalid species reference");
        rejected(target,changed(manager,phase,phaseRaw.replace("\"subject\": \"chemicalelements:iron\"","\"subject\": \"chemicalelements:missing_species\"")),first,"invalid phase subject");
        for(String section:List.of("elements/","species/","reactions/","solubility/")) {
            var key=first.sourcePacks().keySet().stream().filter(k->k.getPath().startsWith(section)).findFirst().orElseThrow();
            rejected(target,changed(manager,ResourceLocation.fromNamespaceAndPath(key.getNamespace(),"chemicalelements/"+key.getPath()+".json"),"{}"),first,section);
        }
        reload(target,manager);require(target.bundle().epoch()==first.epoch()+1,"Failures consume no epoch; next reload recovers");
        require(AtomicChemistryCatalog.INSTANCE.bundle()==live,"Isolated listener never replaces live server catalog");h.succeed();
    }
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void unknownAndLegacyContracts(GameTestHelper h) throws Exception {
        var manager=h.getLevel().getServer().getResourceManager();var target=new AtomicChemistryCatalog();reload(target,manager);
        checkQueries(target.bundle());
        var files=new TreeMap<ResourceLocation,JsonElement>();
        for(var e:manager.listResources("chemicalelements",p->p.getPath().endsWith(".json")).entrySet()) {
            String p=e.getKey().getPath();files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),p.substring(17,p.length()-5)),MaterialBehaviorLibrary.parseRaw(raw(manager,e.getKey())));
        }
        target.reload(files);checkQueries(target.bundle());target.reload(files,target.bundle().sourcePacks());
        var b=target.bundle();var legacy3=new ChemistryBundle(b.catalog(),b.reactions(),b.solubility());
        var legacy4=new ChemistryBundle(b.catalog(),b.reactions(),b.solubility(),b.sourcePacks());
        require(legacy3.epoch()==b.epoch() && legacy4.phases().catalog()==b.catalog(),"Old constructors remain usable with empty new libraries");h.succeed();
    }
    public static void checkQueries(ChemistryBundle b) {
        var pure=new Composition(Map.of(id("iron"),MolAmount.ONE));
        var q=b.materials().query(pure);
        require(q.tags().contains(id("material/pure")) && q.tags().contains(id("material/phase_data_pending")),"Data-driven union of classifications");
        require(q.modelStatus()==MaterialBehaviorLibrary.ModelStatus.UNKNOWN,"Unknown model never upgraded to READY");
        var mixed=b.materials().query(pure.add(new Composition(Map.of(id("water"),MolAmount.ONE))));
        require(mixed.tags().contains(id("material/mixture")) && mixed.modelStatus()==MaterialBehaviorLibrary.ModelStatus.UNMODELED,"Mixture not extrapolated from pure model");
        require(q.epoch()==b.epoch() && q.catalogHash().equals(b.catalog().contentHash()),"Query bound to captured bundle");
    }
}

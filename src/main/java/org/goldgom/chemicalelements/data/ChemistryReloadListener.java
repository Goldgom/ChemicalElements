package org.goldgom.chemicalelements.data;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import com.mojang.logging.LogUtils;

public final class ChemistryReloadListener extends SimpleJsonResourceReloadListener {
 private final AtomicChemistryCatalog target;
 public ChemistryReloadListener(){this(AtomicChemistryCatalog.INSTANCE);}
 /** Isolated target permits testing the actual reload lifecycle without replacing the live singleton. */
 public ChemistryReloadListener(AtomicChemistryCatalog target){super(new Gson(),"chemicalelements");this.target=Objects.requireNonNull(target);}
 @Override
 protected Map<ResourceLocation,JsonElement> prepare(ResourceManager manager,ProfilerFiller profiler){
   // The superclass scanner logs/skips malformed JSON; that would publish a partial catalog.
   // Defer selected-resource reading and strict validation to the single apply transaction instead.
   return Map.of();
 }
 @Override
 protected void apply(Map<ResourceLocation,JsonElement> ignored,ResourceManager manager,ProfilerFiller profiler){
   try {
     var files=new TreeMap<ResourceLocation,JsonElement>();
     var originals=new TreeMap<ResourceLocation,String>();
     var sources=new TreeMap<ResourceLocation,String>();
     for(var entry:manager.listResources("chemicalelements",id->id.getPath().endsWith(".json")).entrySet()) {
       String path=entry.getKey().getPath();
       var id=ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),path.substring("chemicalelements/".length(),path.length()-5));
       // One selected Resource object supplies BOTH raw contents and sourcePackId.
       var resource=entry.getValue(); String raw;
       try(var reader=resource.openAsReader()) {
         var text=new StringBuilder();char[] buffer=new char[4096];int n;
         while((n=reader.read(buffer))!=-1){text.append(buffer,0,n);if(text.length()>1048576)throw new IllegalArgumentException("Chemistry resource too large: "+id);}
         raw=text.toString();
       }
       if(files.put(id,MaterialBehaviorLibrary.parseRaw(raw))!=null)throw new IllegalArgumentException("Duplicate resource: "+id);
       originals.put(id,raw); sources.put(id,resource.sourcePackId());
     }
     var next=target.reload(files,sources,originals);
     LogUtils.getLogger().info("Chemical catalog epoch {}: {} elements, {} species, {} item compositions",next.epoch(),next.elements().size(),next.species().size(),next.itemCompositions().size());
   } catch(IOException | RuntimeException ex){
     LogUtils.getLogger().error("Chemistry reload rejected; previous bundle preserved",ex);
     throw new IllegalArgumentException("Chemistry reload rejected; previous bundle preserved",ex);
   }
 }
}

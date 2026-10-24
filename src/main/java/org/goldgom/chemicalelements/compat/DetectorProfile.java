package org.goldgom.chemicalelements.compat;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fail closed, read per request: no persistent authorization/data cache survives reload. */
public record DetectorProfile(String id,int priority,int precision,int maxSlot,boolean instances,boolean liquid,boolean gas,boolean temperature,boolean nuclides,Set<ResourceLocation> elements){
 public DetectorProfile {elements=Set.copyOf(elements);if(precision<1||precision>18||maxSlot<0||maxSlot>255)throw new IllegalArgumentException("detector profile limits");}
 public boolean permits(ResourceLocation element){return elements.isEmpty()||elements.contains(element);}
 public static Optional<DetectorProfile> load(ServerPlayer player,String id){
  if(!id.matches("[a-z0-9_]{1,48}"))return Optional.empty();
  var location=ResourceLocation.fromNamespaceAndPath("chemicalelements","chemicalelements/detectors/"+id+".json");
  var resource=player.serverLevel().getServer().getResourceManager().getResource(location);
  if(resource.isEmpty())return Optional.empty();
  try(var reader=new InputStreamReader(resource.get().open(),java.nio.charset.StandardCharsets.UTF_8)){
   var j=JsonParser.parseReader(reader).getAsJsonObject();if(j.get("schema_version").getAsInt()!=1)return Optional.empty();
   var elements=new HashSet<ResourceLocation>();for(var e:j.getAsJsonArray("elements"))elements.add(ResourceLocation.parse(e.getAsString()));
   return Optional.of(new DetectorProfile(id,j.get("priority").getAsInt(),j.get("precision").getAsInt(),j.get("max_inventory_slot").getAsInt(),j.get("instances").getAsBoolean(),j.get("liquid").getAsBoolean(),j.get("gas").getAsBoolean(),j.get("temperature").getAsBoolean(),j.get("nuclides").getAsBoolean(),elements));
  }catch(Exception invalid){return Optional.empty();}
 }
}

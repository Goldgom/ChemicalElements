package org.goldgom.chemicalelements.core;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import com.google.gson.*;
import java.nio.file.*;

public final class VanillaInventoryAudit {
 public static void main(String[] args)throws Exception{
  SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
  var list=new JsonArray();
  for(var item:BuiltInRegistries.ITEM){var id=BuiltInRegistries.ITEM.getKey(item);if(id.getNamespace().equals("minecraft")){var row=new JsonObject();row.addProperty("id",id.toString());row.addProperty("class",item.getClass().getSimpleName());row.addProperty("max_stack_size",item.getDefaultMaxStackSize());list.add(row);}}
  var out=Path.of("build/a-audit/vanilla-items.json");Files.createDirectories(out.getParent());Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(list));System.out.println("VANILLA REGISTERED ITEMS="+list.size());
 }
}

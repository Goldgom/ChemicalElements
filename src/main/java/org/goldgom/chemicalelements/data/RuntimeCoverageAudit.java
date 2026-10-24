package org.goldgom.chemicalelements.data;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;

public final class RuntimeCoverageAudit {
 private RuntimeCoverageAudit(){}
 public static void write(Path directory) throws java.io.IOException {
  var catalog=AtomicChemistryCatalog.INSTANCE.snapshot();var items=new JsonArray();var missing=new JsonArray();
  for(var item:BuiltInRegistries.ITEM){ResourceLocation id=BuiltInRegistries.ITEM.getKey(item);if(!id.getNamespace().equals("minecraft"))continue;var entry=new JsonObject();entry.addProperty("id",id.toString());entry.addProperty("class",item.getClass().getSimpleName());entry.addProperty("max_stack_size",item.getDefaultMaxStackSize());items.add(entry);if(!catalog.itemCompositions().containsKey(id))missing.add(id.toString());}
  Files.createDirectories(directory);var gson=new GsonBuilder().setPrettyPrinting().create();Files.writeString(directory.resolve("vanilla-items-runtime.json"),gson.toJson(items));Files.writeString(directory.resolve("missing-compositions.json"),gson.toJson(missing));
  com.mojang.logging.LogUtils.getLogger().info("Chemistry runtime coverage: vanilla items={}, missing={}, catalog epoch={}",items.size(),missing.size(),catalog.epoch());
 }
}

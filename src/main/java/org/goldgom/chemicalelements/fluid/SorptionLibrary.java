package org.goldgom.chemicalelements.fluid;
import com.google.gson.*;
import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
public final class SorptionLibrary extends SimpleJsonResourceReloadListener {
 public static final SorptionLibrary INSTANCE=new SorptionLibrary();
 private volatile Map<ResourceLocation,SorptionExchange.Material> materials=Map.of();
 private SorptionLibrary(){super(new Gson(),"chemicalelements/sorption");}
 public Optional<SorptionExchange.Material> material(ResourceLocation block){return Optional.ofNullable(materials.get(block));}
 @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
  materials=parse(files);
 }
 /** Prepare the complete immutable map before publishing; malformed reloads retain the old map. */
 public static Map<ResourceLocation,SorptionExchange.Material> parse(Map<ResourceLocation,JsonElement> files){
  TreeMap<ResourceLocation,SorptionExchange.Material> next=new TreeMap<>();
  for(var entry:new TreeMap<>(files).entrySet()){
   var j=entry.getValue().getAsJsonObject();if(j.get("schema_version").getAsBigDecimal().intValueExact()!=1)throw new IllegalArgumentException("unsupported sorption schema");
   var m=new SorptionExchange.Material(quanta(j,"pore_litres"),new BigDecimal(j.get("wettability").getAsString()),quanta(j,"litres_per_tick"),j.get("basis").getAsString(),j.get("source").getAsString());
   for(var value:j.getAsJsonArray("blocks")){var id=ResourceLocation.parse(value.getAsString());if(next.putIfAbsent(id,m)!=null)throw new IllegalArgumentException("duplicate sorption block "+id);}
  }
  return Map.copyOf(next);
 }
 private static BigInteger quanta(JsonObject j,String key){return new BigDecimal(j.get(key).getAsString()).movePointRight(18).toBigIntegerExact();}
}

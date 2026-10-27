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
 /** Explicit gameplay surface wetting for every block without a more specific material model. */
 private static final SorptionExchange.Material DEFAULT=new SorptionExchange.Material(new BigDecimal("1").movePointRight(18).toBigIntegerExact(),BigDecimal.ONE,new BigDecimal("0.01").movePointRight(18).toBigIntegerExact(),"game_convention","Default finite surface water capacity; gameplay calibration, not measured bulk porosity.");
 private static SorptionExchange.Material calibrated(String capacity,String rate,String wettability,String name){return new SorptionExchange.Material(new BigDecimal(capacity).movePointRight(18).toBigIntegerExact(),new BigDecimal(wettability),new BigDecimal(rate).movePointRight(18).toBigIntegerExact(),"game_convention",name+" gameplay absorption calibration, not measured physical porosity.");}
 private static final SorptionExchange.Material WOOD=calibrated("100","0.5","0.7","Wood");
 private static final SorptionExchange.Material FIBRE=calibrated("200","2","0.9","Fibrous material");
 private static final SorptionExchange.Material LEAVES=calibrated("100","5","0.8","Leaves");
 private static final SorptionExchange.Material STONE=calibrated("10","0.05","0.6","Stone");
 public SorptionExchange.Material materialForBlock(ResourceLocation block){
  var explicit=materials.get(block);if(explicit!=null)return explicit;
  var state=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(block).defaultBlockState();
  if(state.is(net.minecraft.tags.BlockTags.LOGS)||state.is(net.minecraft.tags.BlockTags.PLANKS)||state.is(net.minecraft.tags.BlockTags.WOODEN_SLABS)||state.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS))return WOOD;
  if(state.is(net.minecraft.tags.BlockTags.WOOL)||state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS))return FIBRE;
  if(state.is(net.minecraft.tags.BlockTags.LEAVES))return LEAVES;
  if(state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)||state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)||state.is(net.minecraft.tags.BlockTags.STONE_BRICKS))return STONE;
  return DEFAULT;
 }
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

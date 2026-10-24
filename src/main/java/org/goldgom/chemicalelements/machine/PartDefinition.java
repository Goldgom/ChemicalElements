package org.goldgom.chemicalelements.machine;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
public record PartDefinition(int width,int height,int depth,String category){
 public PartDefinition {if(width<1||width>3||height<1||height>6||depth<1||depth>3)throw new IllegalArgumentException("part bounds 3x6x3");Objects.requireNonNull(category);}
 public List<BlockPos> cells(BlockPos origin,Direction facing){var cells=new ArrayList<BlockPos>();for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<depth;z++)cells.add(origin.offset(rotate(x,y,z,facing)));return List.copyOf(cells);}
 public static BlockPos rotate(int x,int y,int z,Direction facing){return switch(facing){case EAST->new BlockPos(-z,y,x);case SOUTH->new BlockPos(-x,y,-z);case WEST->new BlockPos(z,y,-x);default->new BlockPos(x,y,z);};}
 public static Optional<PartDefinition> load(Level level,String id){
  if(level.getServer()==null)return Optional.empty();
  var path=ResourceLocation.fromNamespaceAndPath("chemicalelements","chemicalelements/parts/"+id+".json");var resource=level.getServer().getResourceManager().getResource(path);if(resource.isEmpty())return Optional.empty();
  try(var reader=new java.io.InputStreamReader(resource.get().open(),java.nio.charset.StandardCharsets.UTF_8)){
   var j=JsonParser.parseReader(reader).getAsJsonObject();if(j.get("schema_version").getAsInt()!=1)return Optional.empty();var size=j.getAsJsonArray("size");return Optional.of(new PartDefinition(size.get(0).getAsInt(),size.get(1).getAsInt(),size.get(2).getAsInt(),j.get("category").getAsString()));
  }catch(Exception invalid){return Optional.empty();}
 }
}

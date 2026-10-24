package org.goldgom.chemicalelements.machine;
import java.io.InputStreamReader;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
public final class MachineGridSettings {
 private MachineGridSettings(){}
 public static int side(MinecraftServer server,MachineKind kind){
  var id=ResourceLocation.fromNamespaceAndPath("chemicalelements","chemicalelements/machine_grids/"+kind.id()+".json");
  var resource=server.getResourceManager().getResource(id);if(resource.isEmpty())return 5;
  try(var reader=new InputStreamReader(resource.get().open(),java.nio.charset.StandardCharsets.UTF_8)){
   var root=JsonParser.parseReader(reader).getAsJsonObject();int width=root.get("side").getAsInt();
   if(root.get("schema_version").getAsInt()!=1||width<1||width>MachineBlockEntity.MAX_SIDE)throw new IllegalStateException("Invalid machine grid "+id);return width;
  }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read machine grid "+id,failure);}
 }
}

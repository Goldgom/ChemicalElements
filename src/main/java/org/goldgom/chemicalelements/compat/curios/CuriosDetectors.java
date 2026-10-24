package org.goldgom.chemicalelements.compat.curios;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.compat.DetectorProfile;
import top.theillusivec4.curios.api.CuriosApi;
/** Class is resolved only after ModList reports Curios loaded. */
public final class CuriosDetectors {
 private CuriosDetectors(){}
 /** Local invalidation only: never grants a profile or exposes composition. Cosmetic slots excluded. */
 public static List<net.minecraft.world.item.ItemStack> equipmentSnapshot(net.minecraft.world.entity.LivingEntity player){
  var handler=CuriosApi.getCuriosInventory(player);if(handler.isEmpty())return List.of();
  var result=new ArrayList<net.minecraft.world.item.ItemStack>();
  for(var entry:new TreeMap<>(handler.get().getCurios()).entrySet()){
   var stacks=entry.getValue().getStacks();
   for(int i=0;i<stacks.getSlots();i++)result.add(handler.get().isSlotActive(entry.getKey(),i)?stacks.getStackInSlot(i).copy():net.minecraft.world.item.ItemStack.EMPTY);
  }
  return List.copyOf(result);
 }
 public static Optional<DetectorProfile> equipped(ServerPlayer player){
  var handler=CuriosApi.getCuriosInventory(player);if(handler.isEmpty())return Optional.empty();
  var profiles=new ArrayList<DetectorProfile>();
  for(var entry:handler.get().getCurios().entrySet()){
   var stacks=entry.getValue().getStacks();
   for(int i=0;i<stacks.getSlots();i++){
    if(!handler.get().isSlotActive(entry.getKey(),i))continue;
    var stack=stacks.getStackInSlot(i);if(stack.isEmpty())continue;
    var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
    if(id.getNamespace().equals("chemicalelements")&&id.getPath().startsWith("detector_"))DetectorProfile.load(player,id.getPath().substring(9)).ifPresent(profiles::add);
   }
  }
  return profiles.stream().sorted(Comparator.comparingInt(DetectorProfile::priority).reversed().thenComparing(DetectorProfile::id)).findFirst();
 }
}

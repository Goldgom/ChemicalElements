package org.goldgom.chemicalelements.compat;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
public final class DetectorAccess {
 private DetectorAccess(){}
 public static boolean available(){return ModList.get().isLoaded("curios");}
 public static Optional<DetectorProfile> equipped(ServerPlayer player){
  if(!available())return Optional.empty();
  return org.goldgom.chemicalelements.compat.curios.CuriosDetectors.equipped(player);
 }
}

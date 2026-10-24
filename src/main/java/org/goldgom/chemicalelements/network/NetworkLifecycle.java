package org.goldgom.chemicalelements.network;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
@EventBusSubscriber(modid="chemicalelements")
public final class NetworkLifecycle {
 private NetworkLifecycle(){}
 private static void invalidate(net.minecraft.world.entity.player.Player player){if(player.containerMenu instanceof org.goldgom.chemicalelements.machine.MachineMenu menu)menu.invalidateTransactionSession();}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player){ChemistryNetwork.tick(player);if(player.containerMenu instanceof org.goldgom.chemicalelements.machine.MachineMenu menu)menu.transactionSession(player);}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){invalidate(event.getEntity());ChemistryNetwork.forget(event.getEntity().getUUID());}
 @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event){invalidate(event.getEntity());ChemistryNetwork.forget(event.getEntity().getUUID());}
 @SubscribeEvent public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event){if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player){invalidate(player);ChemistryNetwork.forget(player.getUUID());}}
}

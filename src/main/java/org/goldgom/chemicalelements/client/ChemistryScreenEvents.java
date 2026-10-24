package org.goldgom.chemicalelements.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.goldgom.chemicalelements.client.screen.*;

@EventBusSubscriber(modid="chemicalelements",value=Dist.CLIENT)
public final class ChemistryScreenEvents {
 private ChemistryScreenEvents(){}
 @SubscribeEvent public static void loggedOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){
  org.goldgom.chemicalelements.compat.RecipeViews.clear();
 }
 @SubscribeEvent public static void init(ScreenEvent.Init.Post event){
  if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
  event.addListener(Button.builder(Component.literal("118"),b->Minecraft.getInstance().setScreen(new PeriodicTableScreen(screen))).bounds(4,4,38,20).build());
 }
 @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event){
  // C over a hovered slot opens a read-only paginated query to the actual server menu.
  if(event.getKeyCode()!=67||!(event.getScreen() instanceof AbstractContainerScreen<?> screen)||screen.getSlotUnderMouse()==null)return;
  Minecraft.getInstance().setScreen(new StackCompositionScreen(screen,screen.getMenu().containerId,screen.getSlotUnderMouse().index));event.setCanceled(true);
 }
}

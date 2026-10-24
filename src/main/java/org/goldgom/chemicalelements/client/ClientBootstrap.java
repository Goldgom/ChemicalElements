package org.goldgom.chemicalelements.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.goldgom.chemicalelements.machine.MachineBootstrap;
import org.goldgom.chemicalelements.client.screen.MachineScreen;
@EventBusSubscriber(modid="chemicalelements",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientBootstrap {
 private ClientBootstrap(){}
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(MachineBootstrap.MENU.get(),MachineScreen::new);}
}

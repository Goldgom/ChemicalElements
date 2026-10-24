package org.goldgom.chemicalelements.machine;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
@EventBusSubscriber(modid="chemicalelements",bus=EventBusSubscriber.Bus.MOD)
public final class MachineCapabilities {
 private MachineCapabilities(){}
 @SubscribeEvent public static void register(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,MachineBootstrap.CONTROLLER.get(),(machine,side)->machine.power);}
}

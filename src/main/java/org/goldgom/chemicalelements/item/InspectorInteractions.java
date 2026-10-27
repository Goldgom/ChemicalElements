package org.goldgom.chemicalelements.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.goldgom.chemicalelements.network.*;

@EventBusSubscriber(modid="chemicalelements")
public final class InspectorInteractions {
    private InspectorInteractions() {}
    /** Inspect interactive blocks such as chests before their normal menus consume right-click. */
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event){
        var player=event.getEntity();
        if(!ChemicalInspectorItem.authorized(player))return;
        if(player instanceof ServerPlayer server)BlockChemistryInspection.target(server).ifPresent(pos->InspectorNetwork.send(server,pos,0,true));
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));event.setCanceled(true);
    }
    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event){
        if(ChemicalInspectorItem.authorized(event.getEntity()))event.setCanceled(true);
    }
}

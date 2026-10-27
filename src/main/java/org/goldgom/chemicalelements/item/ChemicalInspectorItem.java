package org.goldgom.chemicalelements.item;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.goldgom.chemicalelements.network.BlockChemistryInspection;
import org.goldgom.chemicalelements.network.InspectorNetwork;

/** Creative inspection never samples, initializes or modifies the inspected inventory. */
public final class ChemicalInspectorItem extends Item {
    private static final Map<ServerPlayer,Long> lastHud=new WeakHashMap<>();
    public ChemicalInspectorItem(Properties properties){super(properties);}
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,Level level,BlockPos pos,Player player){return !authorized(player);}
    public static boolean authorized(Player player){
        boolean creative=player instanceof ServerPlayer server?server.gameMode.getGameModeForPlayer().isCreative():player.isCreative();
        return creative&&(player.getMainHandItem().getItem() instanceof ChemicalInspectorItem||player.getOffhandItem().getItem() instanceof ChemicalInspectorItem);
    }
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
        if(!(entity instanceof ServerPlayer player)||!authorized(player)||(!selected&&player.getOffhandItem()!=stack))return;
        long tick=level.getGameTime();if(tick%8!=0||Objects.equals(lastHud.put(player,tick),tick))return;
        BlockChemistryInspection.target(player).ifPresent(pos->InspectorNetwork.send(player,pos,0,false));
    }
    private void open(Player player){
        if(player instanceof ServerPlayer server&&authorized(player))BlockChemistryInspection.target(server).ifPresent(pos->InspectorNetwork.send(server,pos,0,true));
    }
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null||!authorized(player))return InteractionResult.PASS;
        open(player);return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!authorized(player))return InteractionResultHolder.pass(player.getItemInHand(hand));
        open(player);return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide());
    }
}

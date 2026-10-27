package org.goldgom.chemicalelements.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.goldgom.chemicalelements.client.screen.BlockChemistryScreen;
import org.goldgom.chemicalelements.item.ChemicalInspectorItem;
import org.goldgom.chemicalelements.network.*;

@EventBusSubscriber(modid="chemicalelements",value=Dist.CLIENT)
public final class InspectorClient {
    private static InspectorNetwork.Page observed;
    private static ResourceLocation dimension;
    private static long receivedTick;
    private InspectorClient() {}
    public static void install(){InspectorNetwork.receiver=InspectorClient::receive;}
    private static void receive(InspectorNetwork.Page page){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!ChemicalInspectorItem.authorized(mc.player))return;
        if(page.page()==0){observed=page;dimension=mc.level.dimension().location();receivedTick=mc.level.getGameTime();}
        if(page.open())mc.setScreen(new BlockChemistryScreen(page));
        else if(mc.screen instanceof BlockChemistryScreen screen)screen.accept(page);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!ChemicalInspectorItem.authorized(mc.player)){observed=null;dimension=null;return;}
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.player==null||mc.level==null||!ChemicalInspectorItem.authorized(mc.player)||observed==null||!mc.level.dimension().location().equals(dimension)||mc.level.getGameTime()-receivedTick>24)return;
        var hit=mc.player.pick(BlockChemistryInspection.RANGE,1,true);
        if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK||!block.getBlockPos().equals(observed.pos()))return;
        var graphics=event.getGuiGraphics();int width=Math.min(360,Math.max(120,graphics.guiWidth()/2)),y=36;
        var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        int selected=0,maxLines=Math.max(3,(graphics.guiHeight()-65)/10);
        for(int i=0;i<observed.lines().size()&&selected<9;i++){
            String text=observed.lines().get(i);
            if(i>=5&&!text.contains(" = ")&&!text.contains("Nominal material model")&&!text.contains("Actual block inventory"))continue;
            var wrapped=mc.font.split(net.minecraft.network.chat.Component.literal(text),width-12);if(lines.size()+wrapped.size()>=maxLines)break;
            lines.addAll(wrapped);selected++;
        }
        lines.addAll(mc.font.split(net.minecraft.network.chat.Component.translatable("gui.chemicalelements.inspector.hint"),width-12));
        graphics.fill(5,y-5,5+width,y+lines.size()*10+4,0xc8182028);
        for(var line:lines){graphics.drawString(mc.font,line,11,y,0xe4f4f1);y+=10;}
    }
}

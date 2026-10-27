package org.goldgom.chemicalelements.client.screen;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.goldgom.chemicalelements.item.ChemicalInspectorItem;
import org.goldgom.chemicalelements.network.InspectorNetwork;

public final class BlockChemistryScreen extends Screen {
    private InspectorNetwork.Page snapshot;
    private int scroll,ticks,requestedPage;
    public BlockChemistryScreen(InspectorNetwork.Page page){super(Component.translatable("gui.chemicalelements.inspector.title"));snapshot=page;requestedPage=page.page();}
    public void accept(InspectorNetwork.Page page){if(page.pos().equals(snapshot.pos())&&(page.page()==requestedPage||requestedPage>=page.pages())){snapshot=page;requestedPage=page.page();scroll=Math.min(scroll,Math.max(0,wrapped().size()-visibleLines()));}}
    @Override protected void init(){
        int cell=Math.max(45,Math.min(110,(width-44)/4)),x=(width-(cell*4+12))/2,y=height-27;
        addRenderableWidget(Button.builder(Component.literal("<"),b->{if(requestedPage>0){requestedPage--;scroll=0;request();}}).bounds(x,y,cell,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{if(requestedPage+1<snapshot.pages()){requestedPage++;scroll=0;request();}}).bounds(x+cell+4,y,cell,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chemicalelements.refresh"),b->request()).bounds(x+2*(cell+4),y,cell,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+3*(cell+4),y,cell,20).build());
    }
    private void request(){if(minecraft.getConnection()!=null)PacketDistributor.sendToServer(new InspectorNetwork.Query(snapshot.pos(),requestedPage));}
    private int visibleLines(){return Math.max(1,(height-77)/11);}
    private List<FormattedCharSequence> wrapped(){var lines=new ArrayList<FormattedCharSequence>();for(String line:snapshot.lines())lines.addAll(font.split(Component.literal(line),Math.max(80,width-32)));return lines;}
    @Override public void tick(){
        if(minecraft.player==null||!ChemicalInspectorItem.authorized(minecraft.player)||minecraft.player.distanceToSqr(snapshot.pos().getCenter())>100){onClose();return;}
        var hit=minecraft.player.pick(8,1,true);
        if(!(hit instanceof net.minecraft.world.phys.BlockHitResult block)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||!block.getBlockPos().equals(snapshot.pos())){onClose();return;}
        if(++ticks%20==0)request();
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){
        graphics.fill(0,0,width,height,0xf0141d26);super.render(graphics,x,y,partial);
        graphics.drawString(font,title,14,12,0x8de5cf);
        graphics.drawString(font,(requestedPage+1)+" / "+snapshot.pages()+" · "+snapshot.pos().toShortString(),14,27,0xdde9ef);
        var lines=wrapped();graphics.enableScissor(12,43,width-12,height-33);
        for(int i=0;i<visibleLines()&&i+scroll<lines.size();i++)graphics.drawString(font,lines.get(i+scroll),14,45+i*11,0xe1e8ed);
        graphics.disableScissor();
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(Math.max(0,wrapped().size()-visibleLines()),scroll-(int)vertical*3));return true;}
    @Override public boolean isPauseScreen(){return false;}
}

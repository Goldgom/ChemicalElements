package org.goldgom.chemicalelements.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.goldgom.chemicalelements.network.*;

public final class StackCompositionScreen extends Screen {
 private final Screen previous;private final int container,slot;private long revision=-1;private int instancePage,componentPage,scroll,authorizationTicks;
 private final PageSession session=new PageSession();
 private long sequence;
 private int selected=-1;
 private java.util.List<net.minecraft.world.item.ItemStack> equipment=java.util.List.of();
 private java.util.List<net.minecraft.world.item.ItemStack> localEquipment(){
  if(minecraft.player==null||!net.neoforged.fml.ModList.get().isLoaded("curios"))return java.util.List.of();
  return org.goldgom.chemicalelements.compat.curios.CuriosDetectors.equipmentSnapshot(minecraft.player);
 }
 private boolean equipmentChanged(){var next=localEquipment();boolean changed=next.size()!=equipment.size();if(!changed)for(int i=0;i<next.size();i++)if(!net.minecraft.world.item.ItemStack.matches(next.get(i),equipment.get(i))){changed=true;break;}equipment=next;return changed;}
 private net.minecraft.world.item.ItemStack observed=net.minecraft.world.item.ItemStack.EMPTY;
 private CompositionPages.Page page=new CompositionPages.Page("LOADING",-1,0,0,java.util.List.of());
 public StackCompositionScreen(Screen previous,int container,int slot){super(Component.literal("Composition / 逐件组成"));this.previous=previous;this.container=container;this.slot=slot;}
 @Override protected void init(){ChemistryNetwork.pageReceiver=this::receive;int y=height-28;int gap=4,cell=Math.max(1,(width-24-gap*4)/5);
  addRenderableWidget(Button.builder(Component.literal("< 件"),b->{if(instancePage>0){instancePage--;componentPage=0;request();}}).bounds(12,y,cell,20).build());
  addRenderableWidget(Button.builder(Component.literal("件 >"),b->{if((instancePage+1)*CompositionPages.PAGE_SIZE<page.count()){instancePage++;componentPage=0;request();}}).bounds(12+(cell+gap),y,cell,20).build());
  addRenderableWidget(Button.builder(Component.literal("< 组分"),b->{if(componentPage>0){componentPage--;request();}}).bounds(12+2*(cell+gap),y,cell,20).build());
  addRenderableWidget(Button.builder(Component.literal("组分 >"),b->{componentPage++;request();}).bounds(12+3*(cell+gap),y,cell,20).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.chemicalelements.refresh"),b->{revision=-1;request();}).bounds(12+4*(cell+gap),y,cell,20).build());request();}
 private void receive(ChemistryNetwork.Reply reply){if(!session.accepts(reply.session(),reply.sequence(),reply.emission()))return;page=reply.page();revision=page.revision();scroll=0;}
 @Override public void tick(){super.tick();if(minecraft.player==null||minecraft.player.containerMenu.containerId!=container){onClose();return;}var slots=minecraft.player.containerMenu.slots;if(slot<0||slot>=slots.size()){onClose();return;}var target=slots.get(slot).getItem();if(equipmentChanged()){resetTargetPages();request();}if(selected!=minecraft.player.getInventory().selected||!net.minecraft.world.item.ItemStack.matches(observed,target)){selected=minecraft.player.getInventory().selected;observed=target.copy();resetTargetPages();request();}if(++authorizationTicks>=10){authorizationTicks=0;request();}}
 @Override public void removed(){if(minecraft!=null&&minecraft.getConnection()!=null)PacketDistributor.sendToServer(new ChemistryNetwork.Close(session.id()));session.close();ChemistryNetwork.pageReceiver=p->{};page=new CompositionPages.Page("CLOSED",-1,0,0,java.util.List.of());super.removed();}
 private void resetTargetPages(){revision=-1;instancePage=0;componentPage=0;scroll=0;}
 private void request(){sequence=session.next();page=new CompositionPages.Page("VERIFYING_EQUIPMENT",-1,0,0,java.util.List.of());PacketDistributor.sendToServer(new ChemistryNetwork.Query(session.id(),sequence,new CompositionPages.Request(container,slot,revision,instancePage,componentPage)));}
 @Override public void render(GuiGraphics g,int x,int y,float partial){g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","textures/gui/detector_panel.png"),0,0,width,height,0,0,256,256,256,256);super.render(g,x,y,partial);g.drawString(font,title,12,10,0x80e8d0);g.drawString(font,page.status()+" | "+page.count()+" instances | revision "+page.revision(),12,25,0xffffff);int n=Math.max(0,(height-82)/11);for(int i=0;i<n&&i+scroll<page.lines().size();i++)g.drawString(font,page.lines().get(i+scroll),12,43+i*11,0xd3e0e5);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(Math.max(0,page.lines().size()-1),scroll-(int)vertical*3));return true;}
 @Override public void onClose(){ChemistryNetwork.pageReceiver=p->{};minecraft.setScreen(previous);}
 @Override public boolean isPauseScreen(){return false;}
}

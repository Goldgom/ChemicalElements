package org.goldgom.chemicalelements.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.goldgom.chemicalelements.machine.MachineMenu;

public final class MachineScreen extends AbstractContainerScreen<MachineMenu>{
 private int diagnosticOffset;private String lastDiagnostic="";
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){int sy=topPos+MachineMenu.playerInventoryY(menu.side())-20;if(x>=leftPos+8&&x<leftPos+172&&y>=sy&&y<sy+10){int count=font.split(Component.literal(menu.statusMessage()),Math.max(40,Math.min(240,width-24))).size();diagnosticOffset=Math.clamp(diagnosticOffset+(dy<0?1:-1),0,Math.max(0,count-8));return true;}return super.mouseScrolled(x,y,dx,dy);}
 public MachineScreen(MachineMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=242;imageHeight=MachineMenu.playerInventoryY(menu.side())+96;inventoryLabelY=MachineMenu.playerInventoryY(menu.side())-10;}
 @Override protected void init(){super.init();addRenderableWidget(Button.builder(Component.translatable("gui.chemicalelements.start"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+175,topPos+88,60,20).build());addRenderableWidget(Button.builder(Component.translatable("gui.chemicalelements.cancel"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1)).bounds(leftPos+175,topPos+111,60,20).build());}
 @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","textures/gui/machine_panel.png"),leftPos,topPos,imageWidth,imageHeight,0,0,256,256,256,256);for(var slot:menu.slots)g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","textures/gui/slot.png"),leftPos+slot.x-1,topPos+slot.y-1,0,0,18,18,18,18);int width=menu.duration()==0?0:(int)(56L*menu.elapsed()/menu.duration());g.fill(leftPos+177,topPos+77,leftPos+233,topPos+82,0xff304550);g.fill(leftPos+177,topPos+77,leftPos+177+width,topPos+82,0xff4fd7b2);int fill=menu.tankFillBasisPoints();int liquid=40*fill/10000;g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","textures/gui/tank.png"),leftPos+178,topPos+137,20,48,0,0,24,64,24,64);if(liquid>0)g.fill(leftPos+182,topPos+181-liquid,leftPos+194,topPos+181,0xff4bafa6);g.drawString(font,(fill/100)+"%",leftPos+201,topPos+156,0xffd3e0e5,false);}
 @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
  g.drawString(font,title,titleLabelX,titleLabelY,0xffd3e0e5,false);
  g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xffd3e0e5,false);
  String status=menu.statusMessage();int y=MachineMenu.playerInventoryY(menu.side())-20;
  String summary=font.plainSubstrByWidth(status,158);if(!summary.equals(status))summary=font.plainSubstrByWidth(status,150)+"…";
  g.drawString(font,summary,8,y,status.startsWith("Structure:")||status.startsWith("paused:")?0xffffbc84:0xff9cdbce,false);
 }
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);int sy=topPos+MachineMenu.playerInventoryY(menu.side())-20;
  String diagnostic=menu.statusMessage();if(!diagnostic.equals(lastDiagnostic)){diagnosticOffset=0;lastDiagnostic=diagnostic;}
  if(x>=leftPos+8&&x<leftPos+172&&y>=sy&&y<sy+10){var all=font.split(Component.literal(diagnostic),Math.max(40,Math.min(240,width-24)));int start=Math.min(diagnosticOffset,Math.max(0,all.size()-8));var shown=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>(all.subList(start,Math.min(start+8,all.size())));if(all.size()>8)shown.add(Component.literal((start+1)+"-"+Math.min(start+8,all.size())+" / "+all.size()+" · Scroll / 滚轮").getVisualOrderText());g.renderTooltip(font,shown,x,y);}
 }
}

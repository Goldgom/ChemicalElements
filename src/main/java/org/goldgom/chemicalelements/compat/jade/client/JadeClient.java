package org.goldgom.chemicalelements.compat.jade.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import org.goldgom.chemicalelements.machine.MachineBlock;

public final class JadeClient {
 private JadeClient(){}
 public static void register(IWailaClientRegistration registration){registration.registerBlockComponent(Tooltip.INSTANCE,MachineBlock.class);}
 enum Tooltip implements IBlockComponentProvider {INSTANCE;
  @Override public ResourceLocation getUid(){return ResourceLocation.fromNamespaceAndPath("chemicalelements","machine_status");}
  @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config){
   var root=accessor.getServerData();if(!root.contains("chemicalelements")){tooltip.add(Component.translatable("gui.chemicalelements.unsynchronized"));return;}
   var tag=root.getCompound("chemicalelements");var mc=net.minecraft.client.Minecraft.getInstance();
   int maxWidth=Math.max(40,Math.min(240,mc.getWindow().getGuiScaledWidth()-24));
   var rows=mc.font.split(Component.literal(tag.getString("status")),maxWidth);
   for(int i=0;i<Math.min(6,rows.size());i++)tooltip.add(new WrappedLine(rows.get(i),maxWidth));
   if(rows.size()>6)tooltip.add(Component.literal("… +"+(rows.size()-6)+" · Open GUI / 打开界面查看"));
   tooltip.add(new ProcessPanel(tag.getInt("elapsed"),tag.getInt("duration")));
  }
 }
 static final class WrappedLine extends snownee.jade.api.ui.Element {
  private final net.minecraft.util.FormattedCharSequence text;private final int width;
  WrappedLine(net.minecraft.util.FormattedCharSequence text,int maxWidth){this.text=text;this.width=Math.min(maxWidth,net.minecraft.client.Minecraft.getInstance().font.width(text));}
  @Override public net.minecraft.world.phys.Vec2 getSize(){return new net.minecraft.world.phys.Vec2(width,10);}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,float x,float y,float width,float height){g.drawString(net.minecraft.client.Minecraft.getInstance().font,text,(int)x,(int)y,0xffd3e0e5,false);}
 }
 /** Public Jade element API; values are supplied only by the server provider. */
 static final class ProcessPanel extends snownee.jade.api.ui.Element {
  private static final ResourceLocation PANEL=ResourceLocation.fromNamespaceAndPath("chemicalelements","textures/gui/jade_panel.png");
  private final int elapsed,duration;
  ProcessPanel(int elapsed,int duration){this.elapsed=Math.max(0,elapsed);this.duration=Math.max(0,duration);}
  @Override public net.minecraft.world.phys.Vec2 getSize(){return new net.minecraft.world.phys.Vec2(126,20);}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,float x,float y,float width,float height){
   int left=(int)x,top=(int)y;
   g.blit(PANEL,left,top,126,20,0,0,256,256,256,256);
   int progress=duration==0?0:(int)Math.min(118,118L*elapsed/duration);
   g.fill(left+4,top+14,left+122,top+17,0xff304550);
   g.fill(left+4,top+14,left+4+progress,top+17,0xff4fd7b2);
   if(duration>0)g.drawString(net.minecraft.client.Minecraft.getInstance().font,elapsed+" / "+duration+" ticks",left+5,top+3,0xffd3e0e5,false);
  }
 }
}

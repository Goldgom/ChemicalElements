package org.goldgom.chemicalelements.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.goldgom.chemicalelements.registry.ElementItems;

public final class PeriodicTableScreen extends Screen {
 private static final String[] SYMBOLS="H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og".split(" ");
 private final Screen previous;
 public PeriodicTableScreen(Screen previous){super(Component.translatable("gui.chemicalelements.periodic_table"));this.previous=previous;}
 private int[] cell(int z){if(z==1)return new int[]{0,0};if(z==2)return new int[]{17,0};if(z<=10)return new int[]{z<=4?z-3:z+7,1};if(z<=18)return new int[]{z<=12?z-11:z-1,2};if(z<=36)return new int[]{z-19,3};if(z<=54)return new int[]{z-37,4};if(z<=56)return new int[]{z-55,5};if(z<=71)return new int[]{z-55,7};if(z<=86)return new int[]{z-69,5};if(z<=88)return new int[]{z-87,6};if(z<=103)return new int[]{z-87,8};return new int[]{z-101,6};}
 private int familyColor(int z,int column){
  if(z>=57&&z<=71)return 0xffbe90d4;
  if(z>=89&&z<=103)return 0xffd785aa;
  if(column==17)return 0xff7dbddd;
  if(column==16)return 0xff87cc99;
  if(column==0&&z!=1)return 0xffdca16f;
  if(column==1)return 0xffd3bf78;
  if(column>=2&&column<=11)return 0xff7aafc5;
  return 0xff8fc4b0;
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","textures/gui/periodic_panel.png"),0,0,width,height,0,0,256,256,256,256);
  super.render(g,mx,my,partial);
  int w=Math.min(26,(width-16)/18),h=Math.min(29,(height-38)/9),left=(width-w*18)/2,top=27;
  g.drawString(font,title,left,10,0x90e8cc);
  Component tooltip=null;
  for(int z=1;z<=118;z++){
   var c=cell(z);int x=left+c[0]*w,y=top+c[1]*h;
   int accent=familyColor(z,c[0]);
   g.fill(x,y,x+w-2,y+h-2,0xff192e39);
   g.fill(x,y,x+w-2,y+1,accent);
   var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","element_"+ElementItems.NAMES.get(z-1)));
   // Common GUI scale gives h=22: the previous h>=24 gate silently hid every icon.
   // Fit the actual registered item above the symbol instead of dropping its texture.
   int iconSize=Math.max(1,Math.min(16,Math.min(w-4,h-12)));
   g.pose().pushPose();
   g.pose().translate(x+(w-2-iconSize)/2.0f,y+2,0);
   g.pose().scale(iconSize/16.0f,iconSize/16.0f,1);
   g.renderItem(new net.minecraft.world.item.ItemStack(item),0,0);
   g.pose().popPose();
   g.drawString(font,SYMBOLS[z-1],x+1,y+h-10,0xe4eeee);
   if(mx>=x&&mx<x+w&&my>=y&&my<y+h)tooltip=Component.literal(z+" "+ElementItems.NAMES.get(z-1)+" ("+SYMBOLS[z-1]+")");
  }
  // Render last so later cells cannot paint over a hovered element's tooltip.
  if(tooltip!=null)g.renderTooltip(font,tooltip,mx,my);
 }
 @Override public void onClose(){minecraft.setScreen(previous);}
}

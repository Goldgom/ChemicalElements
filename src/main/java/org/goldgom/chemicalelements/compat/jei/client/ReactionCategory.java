package org.goldgom.chemicalelements.compat.jei.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.goldgom.chemicalelements.machine.*;

final class ReactionCategory implements IRecipeCategory<ChemistryJeiPlugin.View> {
 private final IDrawable icon;
 ReactionCategory(IGuiHelper gui){icon=gui.createDrawableItemStack(MachineBootstrap.MACHINES.get(MachineKind.REACTOR).get().asItem().getDefaultInstance());}
 @Override public RecipeType<ChemistryJeiPlugin.View> getRecipeType(){return ChemistryJeiPlugin.TYPE;}
 @Override public Component getTitle(){return Component.literal("Chemical reaction rules / 反应规则");}
 @Override public IDrawable getIcon(){return icon;}
 @Override public int getWidth(){return 190;} @Override public int getHeight(){return 150;}
 @Override public void setRecipe(IRecipeLayoutBuilder builder,ChemistryJeiPlugin.View recipe,IFocusGroup focuses){}
 @Override public void draw(ChemistryJeiPlugin.View view,IRecipeSlotsView slots,GuiGraphics g,double mouseX,double mouseY){
  g.fill(0,0,190,150,0xff122a36);var font=Minecraft.getInstance().font;var p=view.value();if(p==null)return;
  int y=4;for(var text:font.split(Component.literal(p.id()),184)){g.drawString(font,text,3,y,0xffe3bc,false);y+=10;}
  for(String line:p.lines()){for(var text:font.split(Component.literal(line),184)){if(y>137)return;g.drawString(font,text,3,y,0xd3e5ea,false);y+=10;}}
 }
 @Override public java.util.List<Component> getTooltipStrings(ChemistryJeiPlugin.View view,IRecipeSlotsView slots,double x,double y){
  if(view.value()==null)return java.util.List.of();var result=new java.util.ArrayList<Component>();result.add(Component.literal(view.value().id()));for(String line:view.value().lines())result.add(Component.literal(line));return result;
 }
}

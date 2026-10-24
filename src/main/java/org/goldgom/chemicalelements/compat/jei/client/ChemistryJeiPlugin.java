package org.goldgom.chemicalelements.compat.jei.client;

import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import org.goldgom.chemicalelements.registry.ElementItems;
import org.goldgom.chemicalelements.machine.MachineBootstrap;
import org.goldgom.chemicalelements.compat.*;

@JeiPlugin
public final class ChemistryJeiPlugin implements IModPlugin,RecipeViews.Adapter {
 public static final class View {private ReactionDisplay value;View(ReactionDisplay value){this.value=value;}public ReactionDisplay value(){return value;}}
 public static final RecipeType<View> TYPE=RecipeType.create("chemicalelements","reaction",View.class);
 private IJeiRuntime runtime;
 private List<View> visible=List.of();
 private final List<View> slots=new ArrayList<>();
 @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("chemicalelements","chemistry");}
 @Override public void registerCategories(IRecipeCategoryRegistration registration){registration.addRecipeCategories(new ReactionCategory(registration.getJeiHelpers().getGuiHelper()));}
 @Override public void registerRecipes(IRecipeRegistration registration){
  int z=0;for(var name:ElementItems.NAMES){z++;registration.addItemStackInfo(ElementItems.ELEMENTS.get(name).get().getDefaultInstance(),Component.literal("Z = "+z+" | "+name),Component.literal("Reference element sample; actual instance mol, temperature and remaining composition are server-authoritative. Hover a slot and press C for details."));}
  slots.clear();for(var entry:RecipeViews.reactions())slots.add(new View(entry));visible=List.copyOf(slots);registration.addRecipes(TYPE,visible);
 }
 @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){for(var block:MachineBootstrap.MACHINES.values())registration.addRecipeCatalyst(block.get().asItem().getDefaultInstance(),TYPE);}
 @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;RecipeViews.attach(this);}
 @Override public void onRuntimeUnavailable(){RecipeViews.detach(viewerId());runtime=null;visible=List.of();slots.clear();}
 @Override public String viewerId(){return "jei";}
 @Override public void replaceSnapshot(String hash,List<ReactionDisplay> reactions){
  if(runtime==null)return;
  var manager=runtime.getRecipeManager();manager.hideRecipes(TYPE,visible);
  // JEI has no removeRecipes. Stable documentation identities cap retained records at 4096.
  int existing=slots.size();for(int i=existing;i<reactions.size();i++)slots.add(new View(reactions.get(i)));
  for(int i=0;i<reactions.size();i++)slots.get(i).value=reactions.get(i);
  if(slots.size()>existing)manager.addRecipes(TYPE,List.copyOf(slots.subList(existing,slots.size())));
  visible=List.copyOf(slots.subList(0,reactions.size()));manager.unhideRecipes(TYPE,visible);
 }
 @Override public void clear(){if(runtime!=null)runtime.getRecipeManager().hideRecipes(TYPE,visible);visible=List.of();for(var slot:slots)slot.value=null;}
}

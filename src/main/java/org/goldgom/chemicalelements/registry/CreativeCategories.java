package org.goldgom.chemicalelements.registry;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Central primary classification; callers register suppliers before the mod bus registration closes. */
public final class CreativeCategories {
 public enum Category {
  ELEMENTS("Elements",Items.IRON_INGOT), SPECIES("Compounds and species",Items.POTION),
  MATERIALS("Minerals and materials",Items.STONE), MACHINES("Machines",Items.FURNACE),
  PARTS("Multiblock parts",Items.IRON_BLOCK), CONTAINERS("Containers and tanks",Items.BUCKET),
  TOOLS("Tools and accessories",Items.COMPASS), REAGENTS("Reagents and solvents",Items.GLASS_BOTTLE),
  NUCLIDES("Nuclides and radioactivity",Items.GLOWSTONE_DUST);
  final String fallback; final Item icon;
  Category(String fallback,Item icon){this.fallback=fallback;this.icon=icon;}
 }
 private record Entry(Category category,Supplier<? extends Item> item){}
 private static final Map<ResourceLocation,Entry> ENTRIES=new TreeMap<>();
 private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"chemicalelements");
 private static boolean installed;
 static {for(var category:Category.values())TABS.register("category_"+category.name().toLowerCase(Locale.ROOT),()->CreativeModeTab.builder()
   .title(Component.translatableWithFallback("itemGroup.chemicalelements."+category.name().toLowerCase(Locale.ROOT),category.fallback))
   .icon(()->new ItemStack(category.icon)).displayItems((parameters,output)->{
      synchronized(ENTRIES){ENTRIES.entrySet().stream()
        .filter(e->e.getValue().category()==category)
        .sorted(Comparator.<Map.Entry<ResourceLocation,Entry>>comparingInt(e->displayOrder(category,e.getKey()))
          .thenComparing(Map.Entry::getKey))
        .forEach(e->output.accept(e.getValue().item().get()));}
   }).build());}
 /** Known elements use atomic-number order; extensions and other categories retain stable ID order. */
 private static int displayOrder(Category category,ResourceLocation id){
   if(category==Category.ELEMENTS && id.getNamespace().equals("chemicalelements") && id.getPath().startsWith("element_")){
     int index=ElementItems.NAMES.indexOf(id.getPath().substring("element_".length()));
     if(index>=0)return index;
   }
   return Integer.MAX_VALUE;
 }
 private CreativeCategories(){}
 public static void register(ResourceLocation id,Category category,Supplier<? extends Item> item){
   Objects.requireNonNull(id);Objects.requireNonNull(category);Objects.requireNonNull(item);
   synchronized(ENTRIES){if(ENTRIES.putIfAbsent(id,new Entry(category,item))!=null)throw new IllegalArgumentException("Duplicate primary creative category: "+id);}
 }
 public static Map<ResourceLocation,Category> classifications(){
   synchronized(ENTRIES){var result=new TreeMap<ResourceLocation,Category>();ENTRIES.forEach((id,e)->result.put(id,e.category()));return Collections.unmodifiableMap(result);}
 }
 public static synchronized void bootstrap(IEventBus bus){if(!installed){TABS.register(bus);installed=true;}}
}

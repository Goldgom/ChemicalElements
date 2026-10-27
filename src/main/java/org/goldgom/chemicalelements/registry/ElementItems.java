package org.goldgom.chemicalelements.registry;
import java.util.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.Item;
public final class ElementItems {
 public static final List<String> NAMES=List.of("hydrogen","helium","lithium","beryllium","boron","carbon","nitrogen","oxygen","fluorine","neon","sodium","magnesium","aluminium","silicon","phosphorus","sulfur","chlorine","argon","potassium","calcium","scandium","titanium","vanadium","chromium","manganese","iron","cobalt","nickel","copper","zinc","gallium","germanium","arsenic","selenium","bromine","krypton","rubidium","strontium","yttrium","zirconium","niobium","molybdenum","technetium","ruthenium","rhodium","palladium","silver","cadmium","indium","tin","antimony","tellurium","iodine","xenon","caesium","barium","lanthanum","cerium","praseodymium","neodymium","promethium","samarium","europium","gadolinium","terbium","dysprosium","holmium","erbium","thulium","ytterbium","lutetium","hafnium","tantalum","tungsten","rhenium","osmium","iridium","platinum","gold","mercury","thallium","lead","bismuth","polonium","astatine","radon","francium","radium","actinium","thorium","protactinium","uranium","neptunium","plutonium","americium","curium","berkelium","californium","einsteinium","fermium","mendelevium","nobelium","lawrencium","rutherfordium","dubnium","seaborgium","bohrium","hassium","meitnerium","darmstadtium","roentgenium","copernicium","nihonium","flerovium","moscovium","livermorium","tennessine","oganesson");
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("chemicalelements");
 /** Carrier only: actual matter is supplied exclusively by its instance ledger. */
 public static final DeferredItem<Item> MATERIAL_SAMPLE=ITEMS.registerSimpleItem("material_sample",new Item.Properties());
 public static final DeferredItem<org.goldgom.chemicalelements.item.PartialFluidBucketItem> PARTIAL_FLUID_BUCKET=ITEMS.register("partial_fluid_bucket",()->new org.goldgom.chemicalelements.item.PartialFluidBucketItem(new Item.Properties().stacksTo(1)));
 public static final DeferredItem<org.goldgom.chemicalelements.item.ChemicalInspectorItem> CHEMICAL_INSPECTOR=ITEMS.register("chemical_inspector",()->new org.goldgom.chemicalelements.item.ChemicalInspectorItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
 public static final Map<String,DeferredItem<Item>> ELEMENTS;
 static {var m=new LinkedHashMap<String,DeferredItem<Item>>(); for(String name:NAMES)m.put(name,ITEMS.registerSimpleItem("element_"+name,new Item.Properties())); ELEMENTS=Collections.unmodifiableMap(m);}
 public static void bootstrap(IEventBus bus){ITEMS.register(bus);ElementMaterials.bootstrap(bus);}
 private ElementItems(){}
}

package org.goldgom.chemicalelements.machine;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public final class MachineBootstrap {
 public static final String MODID="chemicalelements";
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MODID);
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MODID);
 public static final Map<MachineKind,DeferredBlock<MachineBlock>> MACHINES=new EnumMap<>(MachineKind.class);
 public static final Map<String,DeferredBlock<SizedPartBlock>> PARTS=new LinkedHashMap<>();
 static {for(String id:List.of("port","column_short","column_tall","core","assembly","tower")){var block=BLOCKS.register("part_"+id,()->new SizedPartBlock(id,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f)));PARTS.put(id,block);ITEMS.registerSimpleBlockItem("part_"+id,block);}}
 public static final Map<String,DeferredItem<net.minecraft.world.item.Item>> DETECTORS=new LinkedHashMap<>();
 static {for(String tier:List.of("basic","advanced","research"))DETECTORS.put(tier,ITEMS.registerSimpleItem("detector_"+tier,new net.minecraft.world.item.Item.Properties().stacksTo(1)));}
 private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MODID);
 private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,MODID);
 static {for(var k:MachineKind.values()){var block=BLOCKS.register(k.id(),()->new MachineBlock(k,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f).requiresCorrectToolForDrops()));MACHINES.put(k,block);ITEMS.registerSimpleBlockItem(k.id(),block);}}
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MachineBlockEntity>> CONTROLLER=ENTITIES.register("machine_controller",()->BlockEntityType.Builder.of(MachineBlockEntity::new,MACHINES.values().stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
 public static final DeferredHolder<MenuType<?>,MenuType<MachineMenu>> MENU=MENUS.register("machine",()->IMenuTypeExtension.create((id,inventory,buffer)->new MachineMenu(id,inventory,buffer.readBlockPos(),buffer.readVarInt())));
 private MachineBootstrap(){}
 public static void bootstrap(IEventBus bus){
  PARTS.forEach((name,block)->org.goldgom.chemicalelements.registry.CreativeCategories.register(block.getId(),org.goldgom.chemicalelements.registry.CreativeCategories.Category.PARTS,()->block.get().asItem()));
  DETECTORS.forEach((name,item)->org.goldgom.chemicalelements.registry.CreativeCategories.register(item.getId(),org.goldgom.chemicalelements.registry.CreativeCategories.Category.TOOLS,item));
  BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);
 }
}

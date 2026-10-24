package org.goldgom.chemicalelements;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Chemicalelements.MODID)
public class Chemicalelements {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "chemicalelements";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "chemicalelements" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "chemicalelements" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "chemicalelements" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "chemicalelements:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "chemicalelements:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "chemicalelements:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "chemicalelements:example_tab" for the example item, that is placed after the combat tab
    // Primary creative categories are centrally registered; no mixed example tab.

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Chemicalelements(IEventBus modEventBus, ModContainer modContainer) {
        org.goldgom.chemicalelements.persistence.ChemistryComponents.register(modEventBus);
        // Safe listeners only: legacy recovery, finite-water binding and production remain separate.
        org.goldgom.chemicalelements.instance.InstanceBootstrap.registerLifecycle();
        org.goldgom.chemicalelements.instance.InstanceBootstrap.registerReadServices();
        org.goldgom.chemicalelements.registry.ElementItems.bootstrap(modEventBus);
        org.goldgom.chemicalelements.machine.MachineBootstrap.bootstrap(modEventBus);
        org.goldgom.chemicalelements.world.SimulationBootstrap.bindModels(() -> {
            var bundle=org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle();
            return new org.goldgom.chemicalelements.world.SimulationBootstrap.Models(bundle.epoch(),bundle.reactions(),bundle.solubility());
        });
        org.goldgom.chemicalelements.world.SimulationBootstrap.bootstrap(modEventBus);
        org.goldgom.chemicalelements.registry.ElementItems.ELEMENTS.forEach((name,item) ->
            org.goldgom.chemicalelements.registry.CreativeCategories.register(item.getId(),org.goldgom.chemicalelements.registry.CreativeCategories.Category.ELEMENTS,item));
        org.goldgom.chemicalelements.machine.MachineBootstrap.MACHINES.forEach((kind,block) ->
            org.goldgom.chemicalelements.registry.CreativeCategories.register(block.getId(),switch(kind) {
                case STORAGE_TANK, PUMP, PIPE -> org.goldgom.chemicalelements.registry.CreativeCategories.Category.CONTAINERS;
                default -> org.goldgom.chemicalelements.registry.CreativeCategories.Category.MACHINES;
            },() -> block.get().asItem()));
        org.goldgom.chemicalelements.registry.CreativeCategories.register(EXAMPLE_ITEM.getId(),org.goldgom.chemicalelements.registry.CreativeCategories.Category.MATERIALS,EXAMPLE_ITEM);
        org.goldgom.chemicalelements.registry.CreativeCategories.register(EXAMPLE_BLOCK_ITEM.getId(),org.goldgom.chemicalelements.registry.CreativeCategories.Category.MATERIALS,EXAMPLE_BLOCK_ITEM);
        org.goldgom.chemicalelements.registry.CreativeCategories.register(org.goldgom.chemicalelements.registry.ElementItems.MATERIAL_SAMPLE.getId(),org.goldgom.chemicalelements.registry.CreativeCategories.Category.MATERIALS,org.goldgom.chemicalelements.registry.ElementItems.MATERIAL_SAMPLE);
        org.goldgom.chemicalelements.registry.CreativeCategories.bootstrap(modEventBus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent event) ->
                event.addListener(new org.goldgom.chemicalelements.data.ChemistryReloadListener()));
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Chemicalelements) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(EXAMPLE_BLOCK_ITEM);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        try {
            org.goldgom.chemicalelements.data.RuntimeCoverageAudit.write(java.nio.file.Path.of("a-audit"));
        } catch (java.io.IOException ex) {
            LOGGER.error("Unable to write chemistry coverage audit", ex);
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}

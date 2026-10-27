package org.goldgom.chemicalelements;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = Chemicalelements.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    private static final ModConfigSpec.DoubleValue MINIMUM_WATER_LITRES = BUILDER
            .comment("Free water below this volume evaporates; removed volume, solutes and heat are audited. A full block is 1000 L. Zero disables trace evaporation.")
            .defineInRange("minimumWaterLitres", 0.01, 0.0, 1000.0);
    private static final ModConfigSpec.DoubleValue MINIMUM_WATER_LEVEL_DIFFERENCE_LITRES = BUILDER
            .comment("Minimum volume difference between two horizontal free-water cells required for flow, in litres. A full block is 1000 L. Zero disables this deadband; gravity and absorption are unaffected.")
            .defineInRange("minimumWaterLevelDifferenceLitres", 50.0, 0.0, 1000.0);
    public static double minimumWaterLevelDifferenceLitres = 50.0;
    private static final ModConfigSpec.IntValue FLUID_UPDATE_RADIUS = BUILDER
            .comment("Maximum Manhattan distance of a fluid update from its original trigger. Propagated changes do not reset this radius.")
            .defineInRange("fluidUpdateRadius", 10, 1, 16);
    public static int fluidUpdateRadius = 10;
    private static final ModConfigSpec.IntValue FLUID_FLOW_INTERVAL_TICKS = BUILDER
            .comment("Delay between visible stages of the same fluid region, in game ticks. 10 ticks = 0.5 seconds at 20 TPS.")
            .defineInRange("fluidFlowIntervalTicks", 10, 1, 100);
    public static int fluidFlowIntervalTicks = 10;
    static final ModConfigSpec SPEC = BUILDER.build();
    public static double minimumWaterLitres = 0.01;

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if(event.getConfig().getSpec()!=SPEC)return;
        minimumWaterLitres = MINIMUM_WATER_LITRES.get();
        minimumWaterLevelDifferenceLitres = MINIMUM_WATER_LEVEL_DIFFERENCE_LITRES.get();
        fluidUpdateRadius = FLUID_UPDATE_RADIUS.get();
        fluidFlowIntervalTicks = FLUID_FLOW_INTERVAL_TICKS.get();
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemName)))
                .collect(Collectors.toSet());
    }
}

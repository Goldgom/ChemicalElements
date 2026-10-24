package org.goldgom.chemicalelements.registry;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import org.goldgom.chemicalelements.content.material.MaterialBlockItem;
import org.goldgom.chemicalelements.content.material.MaterialBlock;
import org.goldgom.chemicalelements.content.material.MaterialBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Content identities, not declarations of ambient physical phase or a matter mint. */
public final class ElementMaterials {
    public static final List<String> CARRIER_FORMS = List.of("solid", "liquid", "gas", "unresolved");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "chemicalelements");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("chemicalelements");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "chemicalelements");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaterialBlockEntity>> MATERIAL_BE =
            BLOCK_ENTITIES.register("material", () -> BlockEntityType.Builder.of(MaterialBlockEntity::new,
                    ElementMaterials.CONTENT.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));
    public static final Map<String, DeferredHolder<Block, MaterialBlock>> CONTENT;
    public static final Map<String, DeferredItem<MaterialBlockItem>> BLOCK_ITEMS;
    static {
        var blocks = new LinkedHashMap<String, DeferredHolder<Block, MaterialBlock>>();
        var items = new LinkedHashMap<String, DeferredItem<MaterialBlockItem>>();
        for (String name : ElementItems.NAMES) add("elemental_" + name, blocks, items);
        for (String form : CARRIER_FORMS) add("mixture_carrier_" + form, blocks, items);
        CONTENT = Collections.unmodifiableMap(blocks);
        BLOCK_ITEMS = Collections.unmodifiableMap(items);
    }
    private static void add(String path, Map<String, DeferredHolder<Block, MaterialBlock>> blocks,
            Map<String, DeferredItem<MaterialBlockItem>> items) {
        // Until durable world Owner pickup exists these candidates cannot be mined/exploded for matter.
        var block = BLOCKS.register(path, () -> new MaterialBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)));
        var item = ITEMS.register(path, () -> new MaterialBlockItem(block.get(), new Item.Properties(), id(path)));
        blocks.put(path, block); items.put(path, item);
        CreativeCategories.register(id(path), CreativeCategories.Category.MATERIALS, item);
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("chemicalelements", path); }
    public static void bootstrap(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); }
    private ElementMaterials() {}
}

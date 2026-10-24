package org.goldgom.chemicalelements.gametest.core;

import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.content.material.MaterialBlockItem;
import org.goldgom.chemicalelements.registry.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialRegistrationGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realRegistryAndStaticClassification(GameTestHelper h) {
        h.assertTrue(ElementItems.NAMES.size()==118 && ElementItems.ELEMENTS.size()==118,"Existing 118 reused");
        h.assertTrue(ElementMaterials.CONTENT.size()==122 && ElementMaterials.CARRIER_FORMS.size()==4,"Finite four carriers, not composition products");
        var blockTag=TagKey.create(Registries.BLOCK,ElementMaterials.id("material_content/candidates"));
        var itemTag=TagKey.create(Registries.ITEM,ElementMaterials.id("material_content/candidates"));
        for(var e:ElementMaterials.CONTENT.entrySet()) {
            var id=ElementMaterials.id(e.getKey()); var block=e.getValue().get();
            var item=ElementMaterials.BLOCK_ITEMS.get(e.getKey()).get();
            h.assertTrue(BuiltInRegistries.BLOCK.getKey(block).equals(id) && BuiltInRegistries.ITEM.getKey(item).equals(id),"Registered identity "+id);
            h.assertTrue(item instanceof MaterialBlockItem && item.getBlock()==block,"Guarded real BlockItem");
            h.assertTrue(block.defaultBlockState().is(blockTag) && new ItemStack(item).is(itemTag),"Loaded classification tags "+id);
            h.assertTrue(CreativeCategories.classifications().get(id)==CreativeCategories.Category.MATERIALS,"Creative classification");
            var loot=ElementMaterials.id("loot_table/blocks/"+e.getKey()+".json");
            h.assertTrue(h.getLevel().getServer().getResourceManager().getResource(loot).isPresent(),"No-template-drop resource exists");
        }
        for(String name:ElementItems.NAMES) {
            h.assertTrue(BuiltInRegistries.ITEM.getKey(ElementItems.ELEMENTS.get(name).get()).equals(ElementMaterials.id("element_"+name)),"Original item preserved");
            h.assertTrue(ElementMaterials.CONTENT.containsKey("elemental_"+name),"All elemental candidates present");
        }
        h.succeed();
    }
}

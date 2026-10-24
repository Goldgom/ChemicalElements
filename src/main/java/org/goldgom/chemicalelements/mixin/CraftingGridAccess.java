package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Internal read access to the original grid; never a permission to mutate it. */
@Mixin(TransientCraftingContainer.class)
public interface CraftingGridAccess {
    @Accessor("items") NonNullList<ItemStack> chemicalelements$items();
    @Accessor("menu") AbstractContainerMenu chemicalelements$menu();
}

package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Slot.safeInsert merges with the chemistry-free comparison; the enclosing menu scope settles instances. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @WrapOperation(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) {
        return StackLedgers.canMerge(a, b);
    }
}

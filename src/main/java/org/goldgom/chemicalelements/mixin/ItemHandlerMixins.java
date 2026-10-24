package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.goldgom.chemicalelements.instance.Settlement;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge item handler paths used by modded pipes/machines and by vanilla hooks. Insert settles the
 * handler's slots + incoming stack + returned remainder; extract settles slot vs returned stack. With
 * simulate=true no scope is opened: the returned simulation copies carry head slices only (copy hook)
 * and never own instances because they are never stored by a settled scope.
 */
public final class ItemHandlerMixins {
    private ItemHandlerMixins() {}

    @Mixin(ItemStackHandler.class)
    public abstract static class StackHandler {
        @WrapMethod(method = "insertItem")
        private ItemStack chemicalelements$insert(int slot, ItemStack stack, boolean simulate, Operation<ItemStack> original) {
            return org.goldgom.chemicalelements.instance.ItemHandlerSettlement.insert((IItemHandler) this, stack, simulate, () -> original.call(slot, stack, simulate));
        }

        @WrapMethod(method = "extractItem")
        private ItemStack chemicalelements$extract(int slot, int amount, boolean simulate, Operation<ItemStack> original) {
            return org.goldgom.chemicalelements.instance.ItemHandlerSettlement.extract((IItemHandler) this, slot, simulate, () -> original.call(slot, amount, simulate));
        }

        @WrapOperation(method = "insertItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
        private boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) { return StackLedgers.canMerge(a, b); }
    }

    @Mixin(InvWrapper.class)
    public abstract static class Wrapper {
        @WrapMethod(method = "insertItem")
        private ItemStack chemicalelements$insert(int slot, ItemStack stack, boolean simulate, Operation<ItemStack> original) {
            return org.goldgom.chemicalelements.instance.ItemHandlerSettlement.insert((IItemHandler) this, stack, simulate, () -> original.call(slot, stack, simulate));
        }

        @WrapMethod(method = "extractItem")
        private ItemStack chemicalelements$extract(int slot, int amount, boolean simulate, Operation<ItemStack> original) {
            return org.goldgom.chemicalelements.instance.ItemHandlerSettlement.extract((IItemHandler) this, slot, simulate, () -> original.call(slot, amount, simulate));
        }

        @WrapOperation(method = "insertItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
        private boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) { return StackLedgers.canMerge(a, b); }
    }

    @Mixin(SidedInvWrapper.class)
    public abstract static class Sided {
        @WrapMethod(method = "insertItem")
        private ItemStack chemicalelements$insert(int slot, ItemStack stack, boolean simulate, Operation<ItemStack> original) {
            return org.goldgom.chemicalelements.instance.ItemHandlerSettlement.insert((IItemHandler) this, stack, simulate, () -> original.call(slot, stack, simulate));
        }

        @WrapMethod(method = "extractItem")
        private ItemStack chemicalelements$extract(int slot, int amount, boolean simulate, Operation<ItemStack> original) {
            return org.goldgom.chemicalelements.instance.ItemHandlerSettlement.extract((IItemHandler) this, slot, simulate, () -> original.call(slot, amount, simulate));
        }

        @WrapOperation(method = "insertItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
        private boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) { return StackLedgers.canMerge(a, b); }
    }

    @Mixin(ItemHandlerHelper.class)
    public abstract static class Helper {
        @WrapOperation(method = "insertItemStacked", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
        private static boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) { return StackLedgers.canMerge(a, b); }
    }

    @Mixin(VanillaInventoryCodeHooks.class)
    public abstract static class Vanilla {
        @WrapOperation(method = {"lambda$extractHook$0", "insertStack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
        private static boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) { return StackLedgers.canMerge(a, b); }
    }
}

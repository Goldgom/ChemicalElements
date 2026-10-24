package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.Settlement;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.entity.player.Player;

/**
 * Inventory.add / placeItemBackInInventory are the funnels for pickup, give, crafting returns, container
 * close. Each top-level call is one scope over the whole inventory plus the incoming stack (nested calls
 * from an outer menu/pickup scope are inert). Slot search uses the chemistry-free comparison.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow @Final public Player player;

    @WrapOperation(method = {"hasRemainingSpaceForItem", "findSlotMatchingUnusedItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean chemicalelements$space(ItemStack a, ItemStack b, Operation<Boolean> original) {
        return StackLedgers.canMerge(a, b);
    }

    @WrapOperation(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(ILnet/minecraft/world/item/ItemStack;)Z"))
    private boolean chemicalelements$add(Inventory self, int slot, ItemStack stack, Operation<Boolean> original) {
        if (player.level().isClientSide) return original.call(self, slot, stack);
        var s = Settlement.begin("inventory_add", org.goldgom.chemicalelements.instance.InventoryParticipants.with(self, stack));
        if (s != null && player.hasInfiniteMaterials()) s.route(Settlement.Router.CREATIVE);
        boolean ok = false, r;
        try { r = original.call(self, slot, stack); ok = true; }
        finally { if (ok) Settlement.end(s, org.goldgom.chemicalelements.instance.InventoryParticipants.with(self, stack)); else Settlement.abort(s); }
        return r;
    }

    @WrapOperation(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V"))
    private void chemicalelements$placeBack(Inventory self, ItemStack stack, boolean notify, Operation<Void> original) {
        if (player.level().isClientSide) { original.call(self, stack, notify); return; }
        var s = Settlement.begin("inventory_place_back", org.goldgom.chemicalelements.instance.InventoryParticipants.with(self, stack));
        boolean ok = false;
        try { original.call(self, stack, notify); ok = true; }
        finally { if (ok) Settlement.end(s, org.goldgom.chemicalelements.instance.InventoryParticipants.with(self, stack)); else Settlement.abort(s); }
    }
}

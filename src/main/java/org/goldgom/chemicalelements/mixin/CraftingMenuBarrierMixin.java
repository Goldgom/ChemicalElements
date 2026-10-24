package org.goldgom.chemicalelements.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.CraftingExtractionBarrier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both vanilla menus declare the same private grid field and quickMoveStack signature. */
@Mixin({CraftingMenu.class, InventoryMenu.class})
public abstract class CraftingMenuBarrierMixin {
    @Shadow(remap = false) @Final private CraftingContainer craftSlots;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void chemicalelements$beforeQuickResult(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        // Runs for EVERY invocation in the vanilla QUICK_MOVE loop, and direct callers too.
        if (slot == 0 && player instanceof ServerPlayer server
                && CraftingExtractionBarrier.blocks(server, craftSlots)) cir.setReturnValue(ItemStack.EMPTY);
    }
}

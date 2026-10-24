package org.goldgom.chemicalelements.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.CraftingExtractionBarrier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class ResultSlotBarrierMixin {
    @Shadow @Final private CraftingContainer craftSlots;
    @Shadow @Final private Player player;

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void chemicalelements$beforeRemove(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayer server && CraftingExtractionBarrier.blocks(server, craftSlots))
            cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void chemicalelements$beforeConsume(Player actor, ItemStack output, CallbackInfo ci) {
        // Defence for a direct onTake caller. Normal extraction is blocked before moving output
        // by the menu and remove hooks, not by attempting an after-the-fact refund here.
        if (player instanceof ServerPlayer server && CraftingExtractionBarrier.blocks(server, craftSlots))
            ci.cancel();
    }
}

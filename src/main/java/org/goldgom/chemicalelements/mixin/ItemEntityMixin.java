package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.goldgom.chemicalelements.instance.Settlement;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    /** New dropped stacks inside an active scope (THROW, block drops, death) are outputs of that scope. */
    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;DDD)V", at = @At("RETURN"))
    private void chemicalelements$emitDrop(Level level, double x, double y, double z, ItemStack stack, double dx, double dy, double dz, CallbackInfo ci) {
        if (!level.isClientSide) Settlement.emit(((ItemEntity) (Object) this).getItem());
    }

    /** Item entity merge (also used by explosion drop aggregation): target keeps its instances, source's head appended. */
    @WrapOperation(method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack chemicalelements$mergeCopy(ItemStack target, int count, Operation<ItemStack> original, ItemStack ignoredTarget, ItemStack source, int max) {
        var s = Settlement.begin("item_entity_merge", List.of(target, source));
        var result = original.call(target, count);
        int moved = result.getCount() - target.getCount();
        source.shrink(moved);
        Settlement.end(s, List.of(result, source));
        source.grow(moved); // vanilla shrinks the source itself right after this call
        return result;
    }

    @WrapOperation(method = "areMergable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean chemicalelements$mergable(ItemStack a, ItemStack b, Operation<Boolean> original) {
        return StackLedgers.canMerge(a, b);
    }

    /** Pickup: entity stack + whole player inventory in one scope. */
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean chemicalelements$pickup(net.minecraft.world.entity.player.Inventory inv, ItemStack stack, Operation<Boolean> original) {
        var s = Settlement.begin("pickup", org.goldgom.chemicalelements.instance.InventoryParticipants.with(inv, stack));
        if (s != null && inv.player.hasInfiniteMaterials()) s.route(Settlement.Router.CREATIVE);
        boolean ok = false, r;
        try { r = original.call(inv, stack); ok = true; }
        finally { if (ok) Settlement.end(s, org.goldgom.chemicalelements.instance.InventoryParticipants.with(inv, stack)); else Settlement.abort(s); }
        return r;
    }
}

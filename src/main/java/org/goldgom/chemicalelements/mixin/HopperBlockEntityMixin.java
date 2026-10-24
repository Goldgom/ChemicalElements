package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.goldgom.chemicalelements.instance.Settlement;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hopper push (ejectItems), pull from container (tryTakeInItemFromSlot) and pull of item entities
 * (addItem(Container, ItemEntity)) each settle over both containers. Vanilla's removeItem(i,1) +
 * failure restore via setCount is preserved as-is; the scope sees only the net effect.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Shadow private static Container getAttachedContainer(Level level, BlockPos pos, HopperBlockEntity hopper) { throw new AssertionError(); }

    @WrapOperation(method = "canMergeItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean chemicalelements$mergeable(ItemStack a, ItemStack b, Operation<Boolean> original) {
        return StackLedgers.canMerge(a, b);
    }

    @WrapOperation(method = "ejectItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack chemicalelements$eject(Container from, Container to, ItemStack moving, Direction dir, Operation<ItemStack> original) {
        return settled("hopper_push", from, to, moving, () -> original.call(from, to, moving, dir));
    }

    @WrapOperation(method = "tryTakeInItemFromSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack chemicalelements$pull(Container from, Container to, ItemStack moving, Direction dir, Operation<ItemStack> original) {
        return settled("hopper_pull", from, to, moving, () -> original.call(from, to, moving, dir));
    }

    /**
     * Vanilla removed one item from {@code from} before this call; on failure it restores the count with
     * setCount. The pre-snapshot therefore includes the moving stack as an extra participant whose
     * instance (head of the source, split by removeItem) travels with it.
     */
    private static ItemStack settled(String reason, Container from, Container to, ItemStack moving, java.util.function.Supplier<ItemStack> call) {
        var pre = org.goldgom.chemicalelements.instance.InventoryParticipants.concat(org.goldgom.chemicalelements.instance.InventoryParticipants.concat(org.goldgom.chemicalelements.instance.InventoryParticipants.of(from), org.goldgom.chemicalelements.instance.InventoryParticipants.of(to)), List.of(moving));
        var s = Settlement.begin(reason, pre);
        boolean ok = false;
        ItemStack rest;
        try { rest = call.get(); ok = true; }
        finally {
            if (!ok) Settlement.abort(s);
        }
        Settlement.end(s, org.goldgom.chemicalelements.instance.InventoryParticipants.concat(org.goldgom.chemicalelements.instance.InventoryParticipants.concat(org.goldgom.chemicalelements.instance.InventoryParticipants.of(from), org.goldgom.chemicalelements.instance.InventoryParticipants.of(to)), List.of(moving)), List.of(rest));
        return rest;
    }

    @WrapOperation(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack chemicalelements$suckEntity(Container from, Container to, ItemStack copy, Direction dir, Operation<ItemStack> original, Container ignored, ItemEntity entity) {
        // vanilla passes entity.getItem().copy(); settle the entity's real stack against the container
        var real = entity.getItem();
        var pre = org.goldgom.chemicalelements.instance.InventoryParticipants.concat(org.goldgom.chemicalelements.instance.InventoryParticipants.of(to), List.of(real));
        var s = Settlement.begin("hopper_suck_entity", pre);
        ItemStack rest;
        boolean ok = false;
        try { rest = original.call(from, to, copy, dir); ok = true; }
        finally { if (!ok) Settlement.abort(s); }
        // vanilla then sets entity item to rest (or EMPTY); model that the real stack now has rest's count
        int restCount = rest.getCount();
        var post = org.goldgom.chemicalelements.instance.InventoryParticipants.concat(org.goldgom.chemicalelements.instance.InventoryParticipants.of(to), List.of(real));
        int before = real.getCount();
        real.setCount(restCount);
        Settlement.end(s, post, List.of());
        // hand the settled ledger to the remainder object vanilla will store
        if (!rest.isEmpty() && !real.isEmpty()) StackLedgers.get(real).ifPresent(l -> StackLedgers.set(rest, l));
        real.setCount(before);
        return rest;
    }
}

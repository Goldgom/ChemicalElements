package org.goldgom.chemicalelements.mixin;

import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A copy is a snapshot, never an ownership transfer: copyWithCount(n) keeps only the head n instances so
 * count and ledger agree. Whether the copy ends up owning those instances is decided by the enclosing
 * settlement scope (which de-duplicates instance ids), never by the copy itself. Equality/hash untouched.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements org.goldgom.chemicalelements.instance.MutationTracked {
    @org.spongepowered.asm.mixin.Unique private long chemicalelements$mutations;
    @Override public long chemicalelements$mutationRevision() { return chemicalelements$mutations; }
    @Inject(method = "getCount", at = @At("HEAD"))
    private void chemicalelements$observeCount(CallbackInfoReturnable<Integer> cir) {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
    }
    @Inject(method = "getComponents", at = @At("HEAD"))
    private void chemicalelements$observeComponents(CallbackInfoReturnable<net.minecraft.core.component.DataComponentMap> cir) {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
    }
    @org.spongepowered.asm.mixin.Unique private void chemicalelements$advance() {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
        chemicalelements$mutations = Math.incrementExact(chemicalelements$mutations);
    }
    @Inject(method = "setCount", at = @At("HEAD"))
    private void chemicalelements$count(int count, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        chemicalelements$advance();
    }
    @Inject(method = "set", at = @At("HEAD"))
    private void chemicalelements$component(net.minecraft.core.component.DataComponentType<?> type, Object value,
            CallbackInfoReturnable<Object> cir) { chemicalelements$advance(); }
    @Inject(method = "remove", at = @At("HEAD"))
    private void chemicalelements$removeComponent(net.minecraft.core.component.DataComponentType<?> type,
            CallbackInfoReturnable<Object> cir) { chemicalelements$advance(); }
    @Inject(method = {"applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponentsAndValidate"}, at = @At("HEAD"))
    private void chemicalelements$patch(net.minecraft.core.component.DataComponentPatch patch,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { chemicalelements$advance(); }
    @Inject(method = "applyComponents(Lnet/minecraft/core/component/DataComponentMap;)V", at = @At("HEAD"))
    private void chemicalelements$map(net.minecraft.core.component.DataComponentMap map,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) { chemicalelements$advance(); }

    @Inject(method = "copyWithCount", at = @At("RETURN"))
    private void chemicalelements$trimCopy(int count, CallbackInfoReturnable<ItemStack> cir) {
        Hooks.afterCopy(cir.getReturnValue());
    }

    /**
     * split(n) is an explicit move: the returned stack holds the head (via copyWithCount), the source keeps
     * the tail. Without this, loops like Containers.dropItemStack would hand the same head to every piece.
     */
    @Inject(method = "split", at = @At("RETURN"))
    private void chemicalelements$split(int count, CallbackInfoReturnable<ItemStack> cir) {
        Hooks.afterSplit((ItemStack) (Object) this, cir.getReturnValue());
    }
}

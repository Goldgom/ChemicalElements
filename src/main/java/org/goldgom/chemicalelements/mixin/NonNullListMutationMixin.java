package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.NonNullList;
import org.goldgom.chemicalelements.instance.MutationTracked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks mutation attempts, including A->B->A, even when the value compares equal afterwards. */
@Mixin(NonNullList.class)
public abstract class NonNullListMutationMixin implements MutationTracked,
        org.goldgom.chemicalelements.instance.materialtx.ReservedListAccess {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final @org.spongepowered.asm.mixin.Mutable
    private java.util.List<Object> list;
    @Override public Object[] chemicalelements$rawReferences(
            org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease) {
        lease.requireRoot(this);
        return list.toArray();
    }
    @Override public void chemicalelements$replaceBacking(
            org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease, Object[] detachedValues) {
        lease.requireRoot(this);
        if (detachedValues.length != list.size()) throw new IllegalArgumentException("fixed owner list layout");
        Object[] copy = detachedValues.clone();
        for (Object value : copy) java.util.Objects.requireNonNull(value);
        long next = Math.incrementExact(chemicalelements$mutations);
        // Arrays-backed fixed-size storage matches original Inventory NonNullList.withSize.
        // No setItem, hooks, codecs, event handlers or ordinary list accesses in this publication.
        list = java.util.Arrays.asList(copy);
        chemicalelements$mutations = next;
    }
    @Unique private long chemicalelements$mutations;
    @Override public long chemicalelements$mutationRevision() { return chemicalelements$mutations; }
    @Inject(method = "get(I)Ljava/lang/Object;", at = @At("HEAD"))
    private void chemicalelements$observe(int index, CallbackInfoReturnable<Object> cir) {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
    }
    @Unique private void chemicalelements$advance() {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
        chemicalelements$mutations = Math.incrementExact(chemicalelements$mutations);
    }
    @Inject(method = "set(ILjava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"))
    private void chemicalelements$set(int index, Object value, CallbackInfoReturnable<Object> cir) { chemicalelements$advance(); }
    @Inject(method = "remove(I)Ljava/lang/Object;", at = @At("HEAD"))
    private void chemicalelements$remove(int index, CallbackInfoReturnable<Object> cir) { chemicalelements$advance(); }
    @Inject(method = "add(ILjava/lang/Object;)V", at = @At("HEAD"))
    private void chemicalelements$add(int index, Object value, CallbackInfo ci) { chemicalelements$advance(); }
    @Inject(method = "clear", at = @At("HEAD"))
    private void chemicalelements$clear(CallbackInfo ci) { chemicalelements$advance(); }
}

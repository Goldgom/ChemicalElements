package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.component.*;
import org.goldgom.chemicalelements.instance.MutationTracked;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PatchedDataComponentMap.class)
public abstract class ComponentMapMutationMixin implements MutationTracked {
    @Unique private long chemicalelements$mutations;
    @Override public long chemicalelements$mutationRevision() { return chemicalelements$mutations; }
    @Inject(method="get", at=@At("HEAD"))
    private void chemicalelements$observe(DataComponentType<?> type, CallbackInfoReturnable<Object> cir) {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
    }
    @Unique private void chemicalelements$advance() {
        org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.requireObservable(this);
        chemicalelements$mutations = Math.incrementExact(chemicalelements$mutations);
    }
    @Inject(method="set", at=@At("HEAD"))
    private void chemicalelements$set(DataComponentType<?> type, Object value, CallbackInfoReturnable<Object> cir) { chemicalelements$advance(); }
    @Inject(method="remove", at=@At("HEAD"))
    private void chemicalelements$remove(DataComponentType<?> type, CallbackInfoReturnable<Object> cir) { chemicalelements$advance(); }
    @Inject(method={"applyPatch", "restorePatch"}, at=@At("HEAD"))
    private void chemicalelements$patch(DataComponentPatch patch, CallbackInfo ci) { chemicalelements$advance(); }
    @Inject(method="setAll", at=@At("HEAD"))
    private void chemicalelements$all(DataComponentMap map, CallbackInfo ci) { chemicalelements$advance(); }
}

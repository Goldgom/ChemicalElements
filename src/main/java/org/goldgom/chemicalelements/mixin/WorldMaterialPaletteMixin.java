package org.goldgom.chemicalelements.mixin;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.goldgom.chemicalelements.world.material.WorldChunkBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PalettedContainer.class)
public abstract class WorldMaterialPaletteMixin {
    @Inject(method = {"onResize", "getAndSet", "getAndSetUnchecked", "get", "pack", "getSerializedSize", "maybeHas", "copy"}, at = @At("HEAD"))
    private void ce$readOrExchange(CallbackInfoReturnable<?> cir) { WorldChunkBoundary.requireObservable(this); }
    @Inject(method = {"set", "getAll", "read", "write", "count"}, at = @At("HEAD"))
    private void ce$visitOrMutate(CallbackInfo ci) { WorldChunkBoundary.requireObservable(this); }
}

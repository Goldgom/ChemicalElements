package org.goldgom.chemicalelements.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.goldgom.chemicalelements.world.material.WorldChunkBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelChunkSection.class)
public abstract class WorldMaterialSectionMixin {
    @Inject(method = {"getBlockState", "getFluidState", "setBlockState", "hasOnlyAir", "isRandomlyTicking",
            "isRandomlyTickingBlocks", "isRandomlyTickingFluids", "getStates", "getSerializedSize", "maybeHas"}, at = @At("HEAD"))
    private void ce$readOrExchange(CallbackInfoReturnable<?> cir) { WorldChunkBoundary.requireObservable(this); }
    @Inject(method = {"recalcBlockCounts", "read", "write"}, at = @At("HEAD"))
    private void ce$mutate(CallbackInfo ci) { WorldChunkBoundary.requireObservable(this); }
    @Inject(method = "getStates", at = @At("RETURN"))
    private void ce$palette(CallbackInfoReturnable<PalettedContainer<BlockState>> cir) {
        WorldChunkBoundary.bindChild(cir.getReturnValue(), this);
    }
}

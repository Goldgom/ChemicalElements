package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.goldgom.chemicalelements.world.FiniteWaterRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All registered FlowingFluid implementations share the conservative scheduler. */
@Mixin(FlowingFluid.class)
public abstract class FiniteWaterMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void chemicalelements$finiteTick(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (level instanceof ServerLevel server && org.goldgom.chemicalelements.fluid.FiniteFluids.exposed(level.getBlockState(pos))) {
            // Projected cells are already owned by an immutable-origin region job. Vanilla
            // scheduled ticks must not turn propagated neighbors into fresh radius origins.
            var block=level.getBlockState(pos);
            if(!block.hasProperty(org.goldgom.chemicalelements.fluid.FiniteLiquidShape.VOLUME_STEP)
                    ||block.getValue(org.goldgom.chemicalelements.fluid.FiniteLiquidShape.VOLUME_STEP)==0)FiniteWaterRuntime.mark(server, pos);
            ci.cancel();
        }
    }
}

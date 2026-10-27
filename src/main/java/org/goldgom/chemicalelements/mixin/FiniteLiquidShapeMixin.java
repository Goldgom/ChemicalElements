package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.*;
import org.goldgom.chemicalelements.fluid.FiniteLiquidShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class FiniteLiquidShapeMixin {
    @Mixin(LiquidBlock.class)
    public abstract static class State {
        @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
        private void chemicalelements$volume(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci){builder.add(FiniteLiquidShape.VOLUME_STEP);}
    }
    @Mixin(FlowingFluid.class)
    public abstract static class Height {
        @Inject(method="getHeight",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$height(FluidState fluid,BlockGetter world,BlockPos pos,CallbackInfoReturnable<Float> cir){
            var state=world.getBlockState(pos);if(FiniteLiquidShape.managed(state))cir.setReturnValue(FiniteLiquidShape.height(state));
        }
        @Inject(method="getShape",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$shape(FluidState fluid,BlockGetter world,BlockPos pos,CallbackInfoReturnable<VoxelShape> cir){
            var state=world.getBlockState(pos);
            // Do not use vanilla's cache keyed only by FluidState: ten bands share eight fluid levels.
            if(FiniteLiquidShape.managed(state))cir.setReturnValue(Shapes.box(0,0,0,1,FiniteLiquidShape.height(state),1));
        }
    }
}

package org.goldgom.chemicalelements.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.goldgom.chemicalelements.fluid.FiniteLiquidShape;
import org.goldgom.chemicalelements.client.FiniteLiquidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public abstract class FiniteLiquidRendererMixin {
    @Inject(method="tesselate",at=@At("HEAD"),cancellable=true)
    private void chemicalelements$tenBands(BlockAndTintGetter world,BlockPos pos,VertexConsumer output,BlockState state,FluidState fluid,CallbackInfo ci){
        if(FiniteLiquidShape.managed(state)){FiniteLiquidRenderer.render(world,pos,output,state,fluid);ci.cancel();}
    }
}

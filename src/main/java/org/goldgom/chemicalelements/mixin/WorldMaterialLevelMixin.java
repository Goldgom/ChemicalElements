package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.goldgom.chemicalelements.world.material.WorldChunkBoundary;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Level.class)
public abstract class WorldMaterialLevelMixin {
    @Unique private void ce$position(BlockPos pos) {
        if ((Object)this instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) WorldChunkBoundary.requireObservable(chunk);
        }
    }
    @Inject(method = {"getBlockState", "getFluidState", "getBlockEntity"}, at = @At("HEAD"))
    private void ce$read(BlockPos pos, CallbackInfoReturnable<?> cir) { ce$position(pos); }
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void ce$set(BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags, int depth,
            CallbackInfoReturnable<Boolean> cir) { ce$position(pos); }
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void ce$destroy(BlockPos pos, boolean drops, net.minecraft.world.entity.Entity entity, int depth,
            CallbackInfoReturnable<Boolean> cir) { ce$position(pos); }
    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void ce$setEntity(BlockEntity be, CallbackInfo ci) { ce$position(be.getBlockPos()); }
    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void ce$remove(BlockPos pos, CallbackInfo ci) { ce$position(pos); }
}

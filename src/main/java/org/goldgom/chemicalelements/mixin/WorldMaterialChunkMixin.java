package org.goldgom.chemicalelements.mixin;

import net.minecraft.world.level.chunk.LevelChunk;
import org.goldgom.chemicalelements.world.material.WorldChunkBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LevelChunk.class)
public abstract class WorldMaterialChunkMixin {
    @org.spongepowered.asm.mixin.Shadow public abstract java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity> getBlockEntities();
    @Inject(method = "getBlockEntityNbtForSaving", at = @At("HEAD"), cancellable = true)
    private void ce$strictSave(net.minecraft.core.BlockPos pos, net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir) {
        WorldChunkBoundary.requireObservable(this);
        var be = getBlockEntities().get(pos);
        // Vanilla marks BEs removed before its final unload/save pass. A removed
        // entry is not a live root; preserve vanilla's pending-NBT fallback.
        if (be != null && !be.isRemoved()) {
            var tag = be.saveWithFullMetadata(registries);
            tag.putBoolean("keepPacked", false); cir.setReturnValue(tag);
        } else {
            var tag = ((net.minecraft.world.level.chunk.ChunkAccess)(Object)this).getBlockEntityNbt(pos);
            if (tag != null) { tag = tag.copy(); tag.putBoolean("keepPacked", true); }
            cir.setReturnValue(tag);
        }
    }
    @Inject(method = {"getBlockState", "getFluidState", "setBlockState", "getBlockEntity", "getBlockEntities"}, at = @At("HEAD"))
    private void ce$access(CallbackInfoReturnable<?> cir) { WorldChunkBoundary.requireObservable(this); }
    @Inject(method = {"addAndRegisterBlockEntity", "setBlockEntity", "removeBlockEntity", "runPostLoad",
            "registerTickContainerInLevel", "clearAllBlockEntities", "registerAllBlockEntitiesAfterLevelLoad"}, at = @At("HEAD"))
    private void ce$lifecycle(CallbackInfo ci) { WorldChunkBoundary.requireObservable(this); }
}

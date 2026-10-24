package org.goldgom.chemicalelements.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.*;
import org.goldgom.chemicalelements.world.material.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ChunkAccess.class)
public abstract class WorldMaterialChunkAccessMixin implements WorldChunkAccessBridge {
    @Shadow @Final @Mutable protected Map<BlockPos, CompoundTag> pendingBlockEntities;
    @Shadow @Final @Mutable protected Map<BlockPos, BlockEntity> blockEntities;
    @Shadow @Final protected LevelChunkSection[] sections;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ce$install(CallbackInfo ci) {
        pendingBlockEntities = new GuardedBlockEntityMap<>(this, pendingBlockEntities);
        blockEntities = new GuardedBlockEntityMap<>(this, blockEntities);
        for (LevelChunkSection section : sections) {
            WorldChunkBoundary.bind(section, this);
            WorldChunkBoundary.bind(section.getStates(), this);
        }
    }
    @Override public boolean worldContainsBlockEntity(BlockEntity expected) {
        if (expected == null || !((Object)this instanceof LevelChunk)) return false;
        LevelChunk chunk = (LevelChunk)(Object)this;
        return expected.getLevel() == chunk.getLevel()
                && ((GuardedBlockEntityMap<BlockPos, BlockEntity>)blockEntities)
                        .containsIdentity(expected.getBlockPos(), expected);
    }
    @Override public BlockEntity worldPeekBlockEntity(WorldPrimitivePermit permit,
            org.goldgom.chemicalelements.api.tx.OwnerKey.WorldBlock owner, java.util.UUID operation, String hash, BlockPos pos) {
        return ((GuardedBlockEntityMap<BlockPos, BlockEntity>)blockEntities).worldPeek(permit, owner, operation, hash, pos);
    }
    @Override public void worldReplaceBlockEntity(WorldPrimitivePermit permit,
            org.goldgom.chemicalelements.api.tx.OwnerKey.WorldBlock owner, java.util.UUID operation, String hash,
            BlockPos pos, BlockEntity expected, BlockEntity replacement) {
        ((GuardedBlockEntityMap<BlockPos, BlockEntity>)blockEntities).worldReplace(permit, owner, operation, hash, pos, expected, replacement);
    }
    @Inject(method = "getSections", at = @At("HEAD"), cancellable = true)
    private void ce$sections(CallbackInfoReturnable<LevelChunkSection[]> cir) {
        WorldChunkBoundary.requireObservable(this); cir.setReturnValue(sections.clone());
    }
    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true)
    private void ce$section(int index, CallbackInfoReturnable<LevelChunkSection> cir) {
        WorldChunkBoundary.requireObservable(this); cir.setReturnValue(sections[index]);
    }
    @Inject(method = "getBlockEntitiesPos", at = @At("HEAD"))
    private void ce$positions(CallbackInfoReturnable<java.util.Set<BlockPos>> cir) { WorldChunkBoundary.requireObservable(this); }
    @Inject(method = "setBlockEntityNbt", at = @At("HEAD"))
    private void ce$pending(CompoundTag tag, CallbackInfo ci) { WorldChunkBoundary.requireObservable(this); }
    @Inject(method = "getBlockEntityNbt", at = @At("RETURN"), cancellable = true)
    private void ce$nbt(BlockPos pos, CallbackInfoReturnable<CompoundTag> cir) {
        WorldChunkBoundary.requireObservable(this);
        if (cir.getReturnValue() != null) cir.setReturnValue(cir.getReturnValue().copy());
    }
}

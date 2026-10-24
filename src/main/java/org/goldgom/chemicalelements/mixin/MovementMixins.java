package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.goldgom.chemicalelements.instance.BlockMoves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class MovementMixins {
    private MovementMixins() {}

    /**
     * Piston: before vanilla moves blocks, the instances of all pushed positions are lifted out of the store
     * and re-inserted at their destination (pos + direction). Destroyed positions keep their instance so the
     * drop hook hands it to the drop. A failed resolve moves nothing.
     */
    @Mixin(PistonBaseBlock.class)
    public abstract static class Piston {
        @WrapOperation(method = "moveBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
        private boolean chemicalelements$move(PistonStructureResolver resolver, Operation<Boolean> original, Level level, BlockPos pistonPos, Direction facing, boolean extending) {
            boolean ok = original.call(resolver);
            if (ok && level instanceof ServerLevel sl) {
                Direction dir = extending ? facing : facing.getOpposite();
                BlockMoves.shift(sl, resolver.getToPush(), dir);
            }
            return ok;
        }
    }

    /** Falling block: instance travels in the entity's persistent data and lands at the final position. */
    @Mixin(FallingBlockEntity.class)
    public abstract static class Falling {
        @Inject(method = "fall", at = @At("RETURN"))
        private static void chemicalelements$lift(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FallingBlockEntity> cir) {
            if (level instanceof ServerLevel sl) BlockMoves.lift(sl, pos, cir.getReturnValue());
        }

        @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
        private boolean chemicalelements$land(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
            boolean ok = original.call(level, pos, state, flags);
            if (ok && level instanceof ServerLevel sl) BlockMoves.land(sl, pos, (FallingBlockEntity) (Object) this);
            return ok;
        }

        @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
        private net.minecraft.world.entity.item.ItemEntity chemicalelements$broke(FallingBlockEntity self, net.minecraft.world.level.ItemLike item, Operation<net.minecraft.world.entity.item.ItemEntity> original) {
            var e = original.call(self, item);
            if (e != null) BlockMoves.dropFallen(self, e.getItem());
            return e;
        }
    }
}

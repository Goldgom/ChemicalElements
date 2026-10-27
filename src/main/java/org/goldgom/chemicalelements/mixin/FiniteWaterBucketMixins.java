package org.goldgom.chemicalelements.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.goldgom.chemicalelements.instance.WaterStorePort;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class FiniteWaterBucketMixins {
    private FiniteWaterBucketMixins() {}
    @Mixin(LiquidBlock.class)
    public abstract static class Pickup {
        @Inject(method="pickupBlock",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$pickup(Player player, LevelAccessor level, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir) {
            if(level instanceof ServerLevel server&&org.goldgom.chemicalelements.fluid.FiniteFluids.exposed(state))cir.setReturnValue(WaterStorePort.pickup(server,pos));
        }
    }
    @Mixin(LiquidBlock.class)
    public abstract static class Contact {
        /** NeoForge replaces shouldSpreadLiquid with FluidInteractionRegistry in these hooks. */
        @Inject(method="onPlace",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$placed(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
            if(level instanceof ServerLevel server){
                if(!WaterStorePort.RENDERING.get())org.goldgom.chemicalelements.world.FiniteWaterRuntime.mark(server,pos);
                ci.cancel();
            }
        }
        @Inject(method="neighborChanged",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$neighbor(BlockState state,Level level,BlockPos pos,Block block,BlockPos other,boolean moving,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
            if(level instanceof ServerLevel server){
                if(!WaterStorePort.RENDERING.get())org.goldgom.chemicalelements.world.FiniteWaterRuntime.mark(server,pos);
                ci.cancel();
            }
        }
    }
    @Mixin(BucketItem.class)
    public abstract static class Place {
        @Shadow @Final private Fluid content;
        @Shadow protected abstract void playEmptySound(Player player,LevelAccessor level,BlockPos pos);
        @Inject(method="use",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$collectAnyDepth(Level level,Player player,net.minecraft.world.InteractionHand hand,
                CallbackInfoReturnable<net.minecraft.world.InteractionResultHolder<ItemStack>> cir){
            if(content!=net.minecraft.world.level.material.Fluids.EMPTY)return;
            var eye=player.getEyePosition();
            var hit=level.clip(new ClipContext(eye,eye.add(player.getViewVector(1).scale(player.blockInteractionRange())),ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,player));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||!org.goldgom.chemicalelements.fluid.FiniteFluids.exposed(level.getBlockState(hit.getBlockPos())))return;
            var held=player.getItemInHand(hand);var pos=hit.getBlockPos();
            if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos.relative(hit.getDirection()),hit.getDirection(),held)){
                cir.setReturnValue(net.minecraft.world.InteractionResultHolder.fail(held));return;
            }
            if(!(level instanceof ServerLevel server)){cir.setReturnValue(net.minecraft.world.InteractionResultHolder.sidedSuccess(held,true));return;}
            var filled=WaterStorePort.pickup(server,pos);
            if(filled.isEmpty()){cir.setReturnValue(net.minecraft.world.InteractionResultHolder.fail(held));return;}
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(Items.BUCKET));
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)net.minecraft.advancements.CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer,filled);
            player.playSound(net.minecraft.sounds.SoundEvents.BUCKET_FILL,1,1);
            level.gameEvent(player,net.minecraft.world.level.gameevent.GameEvent.FLUID_PICKUP,pos);
            cir.setReturnValue(net.minecraft.world.InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(held,player,filled),false));
        }
        @Inject(method="emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true)
        private void chemicalelements$place(Player player,Level level,BlockPos pos,BlockHitResult hit,ItemStack bucket,CallbackInfoReturnable<Boolean> cir) {
            if(!(level instanceof ServerLevel server)||!(content instanceof net.minecraft.world.level.material.FlowingFluid))return;
            boolean placed=WaterStorePort.placeBucket(server,pos,bucket,(net.minecraft.world.level.material.FlowingFluid)content);
            if(!placed&&hit!=null){var adjacent=hit.getBlockPos().relative(hit.getDirection());if(!adjacent.equals(pos)){pos=adjacent;placed=WaterStorePort.placeBucket(server,pos,bucket,(net.minecraft.world.level.material.FlowingFluid)content);}}
            if(placed)playEmptySound(player,level,pos);
            cir.setReturnValue(placed);
        }
    }
}

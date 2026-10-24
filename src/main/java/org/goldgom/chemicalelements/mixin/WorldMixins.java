package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.goldgom.chemicalelements.instance.Hooks;
import org.goldgom.chemicalelements.instance.Settlement;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class WorldMixins {
    private WorldMixins() {}

    /**
     * Furnace tick: fuel consumption and smelting are one scope over the three slots. Smelting output is a
     * conversion of the consumed input instance (exact partition, nominal-weighted); fuel leftovers are an
     * explicit "consumed:fuel" sink (combustion products are C/A's reaction model, not invented here).
     */
    @Mixin(AbstractFurnaceBlockEntity.class)
    public abstract static class Furnace {
        @WrapMethod(method = "serverTick")
        private static void chemicalelements$tick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be, Operation<Void> original) {
            var items = ((FurnaceAccess) be).chemicalelements$items();
            var before0 = items.get(0);
            int in0 = before0.getCount();
            var s = Settlement.begin("furnace", List.copyOf(items));
            if (s != null) s.route(Settlement.Router.CRAFT).sinkAs("consumed:fuel");
            boolean ok = false;
            try { original.call(level, pos, state, be); ok = true; }
            finally {
                if (ok) Settlement.end(s, () -> List.copyOf(items), List.of());
                else Settlement.abort(s);
            }
        }

        @WrapOperation(method = {"canBurn", "burn"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
        private static boolean chemicalelements$merge(ItemStack a, ItemStack b, Operation<Boolean> original) { return StackLedgers.canMerge(a, b); }
    }

    /** BlockItem.place: head instance becomes the persistent block instance; removed from the held stack on consume. */
    @Mixin(BlockItem.class)
    public abstract static class Place {
        @WrapOperation(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
        private void chemicalelements$consume(ItemStack held, int n, LivingEntity entity, Operation<Void> original, BlockPlaceContext ctx) {
            var level = ctx.getLevel();
            if (level instanceof ServerLevel sl) Hooks.placed(sl, ctx.getClickedPos(), level.getBlockState(ctx.getClickedPos()), held);
            int before = held.getCount();
            original.call(held, n, entity);
            Hooks.afterPlaceConsume(held, before);
        }
    }

    /**
     * All block drops (player break, dropResources, explosions, pistons destroying) are produced by
     * BlockStateBase.getDrops(LootParams.Builder). The persistent block instance at the origin, if any,
     * is taken out of the chunk store and becomes the instance of the first matching drop.
     */
    @Mixin(BlockBehaviour.BlockStateBase.class)
    public abstract static class Drops {
        @WrapMethod(method = "getDrops")
        private List<ItemStack> chemicalelements$drops(LootParams.Builder builder, Operation<List<ItemStack>> original) {
            var drops = original.call(builder);
            if (!StackLedgers.authoritative()) return drops;
            ServerLevel level = builder.getLevel();
            Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
            var state = (BlockState) (Object) this;
            var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            var placed = origin == null ? Optional.<org.goldgom.chemicalelements.persistence.ChunkChemistry.BlockInstance>empty()
                    : Hooks.takePlaced(level, BlockPos.containing(origin));
            boolean[] used = {false};
            for (var d : drops) Hooks.assignDrop(d, placed, blockId, used);
            if (placed.isPresent() && !used[0]) Hooks.unusedPlaced(placed.get(), blockId);
            return drops;
        }
    }

    /** Dropper into container: same accounting as the hopper (one item moved). */
    @Mixin(DropperBlock.class)
    public abstract static class Dropper {
        @WrapMethod(method = "dispenseFrom")
        private void chemicalelements$dispense(ServerLevel level, BlockState state, BlockPos pos, Operation<Void> original) {
            var be = level.getBlockEntity(pos);
            var target = be instanceof net.minecraft.world.Container c ? c : null;
            var dest = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, pos.relative(state.getValue(DropperBlock.FACING)));
            var pre = new ArrayList<ItemStack>();
            if (target != null) pre.addAll(org.goldgom.chemicalelements.instance.InventoryParticipants.of(target));
            if (dest != null) pre.addAll(org.goldgom.chemicalelements.instance.InventoryParticipants.of(dest));
            var s = Settlement.begin("dropper", pre);
            boolean ok = false;
            try { original.call(level, state, pos); ok = true; }
            finally {
                if (ok) Settlement.end(s, () -> {
                    var post = new ArrayList<ItemStack>();
                    if (target != null) post.addAll(org.goldgom.chemicalelements.instance.InventoryParticipants.of(target));
                    if (dest != null) post.addAll(org.goldgom.chemicalelements.instance.InventoryParticipants.of(dest));
                    return post;
                }, List.of());
                else Settlement.abort(s);
            }
        }
    }

    /** Crafter: consumed grid instances convert into the dispensed result(s) exactly. */
    @Mixin(CrafterBlock.class)
    public abstract static class Crafter {
        @WrapMethod(method = "dispenseFrom")
        private void chemicalelements$craft(BlockState state, ServerLevel level, BlockPos pos, Operation<Void> original) {
            var be = level.getBlockEntity(pos) instanceof CrafterBlockEntity c ? c : null;
            if (be == null) { original.call(state, level, pos); return; }
            var s = Settlement.begin("crafter", org.goldgom.chemicalelements.instance.InventoryParticipants.of(be));
            if (s != null) s.route(Settlement.Router.CRAFT).sinkAs("consumed:crafting");
            boolean ok = false;
            try { original.call(state, level, pos); ok = true; }
            finally { if (ok) Settlement.end(s, () -> org.goldgom.chemicalelements.instance.InventoryParticipants.of(be), List.of()); else Settlement.abort(s); }
        }
    }
}

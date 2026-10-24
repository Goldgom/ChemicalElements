package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.instance.Settlement;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every click type (PICKUP, QUICK_MOVE, QUICK_CRAFT drag, SWAP, CLONE, THROW, PICKUP_ALL) runs through
 * {@code doClick}; one settlement scope wraps the call. Participants: all menu slots, carried stack and
 * the player's inventory slots that the menu does not expose. Result slots (crafting, smithing, stonecutter,
 * loom, grindstone, cartography, trade) are display placeholders; taking from them routes the consumed
 * inputs through an exact conversion (or trade payment). Thrown stacks are captured as scope outputs.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();

    @WrapOperation(method = "clicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;doClick(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
    private void chemicalelements$settleClick(AbstractContainerMenu self, int slot, int button, ClickType type, Player player, Operation<Void> original) {
        if (player.level().isClientSide) { original.call(self, slot, button, type, player); return; }
        // Until atomic crafting commit exists, never normalize/settle a result click first.
        // Direct quickMoveStack/remove/onTake callers have separate pre-consumption barriers.
        if ((self instanceof CraftingMenu || self instanceof InventoryMenu) && slot == 0) return;
        var placeholders = new HashSet<Integer>();
        boolean[] trade = {false};
        var s = Settlement.begin("menu:" + type.name().toLowerCase(Locale.ROOT), participants(player, placeholders, trade), placeholders);
        boolean takingResult = slot >= 0 && slot < slots.size() && placeholders.contains(slot);
        if (s != null) {
            if (type == ClickType.CLONE) s.route(Settlement.Router.CREATIVE);
            else if (takingResult) s.route(trade[0] ? Settlement.Router.TRADE : Settlement.Router.CRAFT);
            if (takingResult) s.sinkAs(trade[0] ? "trade_payment:menu" : "consumed:crafting");
        }
        boolean ok = false;
        try {
            original.call(self, slot, button, type, player);
            ok = true;
        } finally {
            if (ok) Settlement.end(s, participants(player, new HashSet<>(), new boolean[1]));
            else Settlement.abort(s);
        }
    }

    private List<ItemStack> participants(Player player, Set<Integer> placeholders, boolean[] trade) {
        var list = new ArrayList<ItemStack>(slots.size() + 42);
        var inv = player.getInventory();
        var exposed = new HashSet<Integer>();
        for (int i = 0; i < slots.size(); i++) {
            var sl = slots.get(i);
            if (isResult(sl)) { placeholders.add(i); if (sl instanceof MerchantResultSlot) trade[0] = true; }
            list.add(sl.getItem());
            if (sl.container == inv) exposed.add(sl.getContainerSlot());
        }
        list.add(getCarried());
        for (int i = 0; i < inv.getContainerSize(); i++) list.add(exposed.contains(i) ? ItemStack.EMPTY : inv.getItem(i));
        return list;
    }

    private static boolean isResult(Slot sl) {
        // Loom output is a plain SimpleContainer slot whose mayPlace is false and whose take consumes inputs.
        return sl instanceof ResultSlot || sl instanceof MerchantResultSlot || sl.container instanceof ResultContainer
                || sl.isFake() || (sl.container.getContainerSize() == 1 && sl.getClass().getEnclosingClass() == LoomMenu.class);
    }

    // -------------------------------------------- normal stacking with different chemistry

    @WrapOperation(method = {"doClick", "moveItemStackTo"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean chemicalelements$mergeIgnoringChemistry(ItemStack a, ItemStack b, Operation<Boolean> original) {
        return StackLedgers.canMerge(a, b);
    }

    @WrapOperation(method = "canItemQuickReplace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean chemicalelements$quickReplaceIgnoringChemistry(ItemStack a, ItemStack b, Operation<Boolean> original) {
        return StackLedgers.canMerge(a, b);
    }
}

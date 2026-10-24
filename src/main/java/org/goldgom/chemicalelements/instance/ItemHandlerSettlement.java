package org.goldgom.chemicalelements.instance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
/** Runtime helpers must not live in the reserved Mixin package. */
public final class ItemHandlerSettlement {
 private ItemHandlerSettlement() {}
    public static List<ItemStack> slots(IItemHandler h) {
        var l = new ArrayList<ItemStack>(h.getSlots());
        for (int i = 0; i < h.getSlots(); i++) l.add(h.getStackInSlot(i));
        return l;
    }

    public static ItemStack insert(IItemHandler h, ItemStack stack, boolean simulate, java.util.function.Supplier<ItemStack> call) {
        if (simulate || stack.isEmpty()) return call.get();
        var s = Settlement.begin("item_handler_insert", InventoryParticipants.concat(slots(h), List.of(stack)));
        ItemStack rest;
        boolean ok = false;
        try { rest = call.get(); ok = true; } finally { if (!ok) Settlement.abort(s); }
        // IItemHandler never consumes the argument object. It is not final storage;
        // the caller owns only the returned remainder after this operation.
        Settlement.end(s, () -> InventoryParticipants.concat(slots(h), List.of(ItemStack.EMPTY)), List.of(rest));
        return rest;
    }

    public static ItemStack extract(IItemHandler h, int slot, boolean simulate, java.util.function.Supplier<ItemStack> call) {
        if (simulate) return call.get();
        var s = Settlement.begin("item_handler_extract", slots(h));
        ItemStack out;
        boolean ok = false;
        try { out = call.get(); ok = true; } finally { if (!ok) Settlement.abort(s); }
        Settlement.end(s, () -> slots(h), List.of(out));
        return out;
    }

}

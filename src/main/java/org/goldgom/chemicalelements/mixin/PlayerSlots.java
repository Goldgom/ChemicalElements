package org.goldgom.chemicalelements.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Participant lists for settlement scopes (position order is stable between pre and post). */
public final class PlayerSlots {
    private PlayerSlots() {}

    /** All player inventory slots followed by the extra stack (e.g. a picked up entity stack). */
    public static List<ItemStack> with(Inventory inv, ItemStack extra) {
        var l = new ArrayList<ItemStack>(inv.getContainerSize() + 1);
        for (int i = 0; i < inv.getContainerSize(); i++) l.add(inv.getItem(i));
        l.add(extra);
        return l;
    }

    public static List<ItemStack> of(Container c) {
        var l = new ArrayList<ItemStack>(c.getContainerSize());
        for (int i = 0; i < c.getContainerSize(); i++) l.add(c.getItem(i));
        return l;
    }

    public static List<ItemStack> concat(List<ItemStack> a, List<ItemStack> b) {
        var l = new ArrayList<ItemStack>(a.size() + b.size());
        l.addAll(a);
        l.addAll(b);
        return l;
    }
}

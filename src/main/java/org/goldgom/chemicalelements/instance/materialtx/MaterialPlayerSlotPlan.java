package org.goldgom.chemicalelements.instance.materialtx;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.instance.*;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Detached plans over an actual captured player snapshot. No setItem, no resolution/minting.
 * Returned shell must be retained in the world business root to preserve non-chemistry components
 * across pickup; a naked material ledger is insufficient for arbitrary named/damaged containers. */
public final class MaterialPlayerSlotPlan {
    private MaterialPlayerSlotPlan() {}
    public record Removed(MaterialOwnerSnapshot after, String unitStack, InstanceRecord instance) {}
    public static Removed removeHead(MaterialOwnerSnapshot before, int slot, HolderLookup.Provider registries) {
        var image = image(before, registries); var slots = new ArrayList<>(image.slots());
        int index = index(slots, slot); var source = slots.get(index); var stack = source.stack();
        if (stack.isEmpty()) throw new IllegalStateException("EMPTY_SOURCE_SLOT");
        var ledger = StackLedgers.get(stack).orElseThrow(() -> new IllegalStateException("UNRESOLVED_LEDGER"));
        var head = ledger.instances().getFirst();
        var unit = stack.copy(); unit.setCount(1);
        StackLedgers.set(unit, new StackLedger(StackLedger.CURRENT_SCHEMA, ledger.revision(), List.of(head)));
        String encoded = TransactionEncoding.canonical(ItemStack.CODEC.encodeStart(
                net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries), unit).getOrThrow());
        var remaining = List.copyOf(ledger.instances().subList(1, ledger.instances().size()));
        if (remaining.isEmpty()) stack = ItemStack.EMPTY;
        else {
            stack.setCount(remaining.size());
            StackLedgers.set(stack, new StackLedger(StackLedger.CURRENT_SCHEMA, Math.incrementExact(ledger.revision()), remaining));
        }
        slots.set(index, new SlotImage(slot, stack, remaining));
        return new Removed(after(before, image, slots, registries), encoded, head);
    }
    /** Unit stack comes from journal-bound original world root, NOT a fabricated item template. */
    public static MaterialOwnerSnapshot insertUnit(MaterialOwnerSnapshot before, int slot, String canonicalUnit,
                                                   HolderLookup.Provider registries) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        var unit = ItemStack.CODEC.parse(ops, JsonParser.parseString(canonicalUnit)).getOrThrow();
        if (unit.isEmpty() || unit.getCount() != 1 || !TransactionEncoding.canonical(
                ItemStack.CODEC.encodeStart(ops, unit).getOrThrow()).equals(canonicalUnit))
            throw new IllegalArgumentException("INVALID_OR_LOSSY_STORED_UNIT");
        var incoming = StackLedgers.get(unit).orElseThrow(() -> new IllegalStateException("UNRESOLVED_UNIT"));
        if (incoming.instances().size() != 1) throw new IllegalArgumentException("UNIT_FIFO_COUNT");
        var image = image(before, registries); var slots = new ArrayList<>(image.slots());
        int index = index(slots, slot); var destination = slots.get(index); var stack = destination.stack();
        var records = new ArrayList<>(destination.instances());
        if (stack.isEmpty()) stack = unit.copy();
        else {
            if (!StackLedgers.canMerge(stack, unit) || stack.getCount() >= stack.getMaxStackSize())
                throw new IllegalStateException("DESTINATION_FULL_OR_INCOMPATIBLE");
            stack.setCount(stack.getCount() + 1);
        }
        records.add(incoming.instances().getFirst());
        long revision = destination.stack().isEmpty() ? incoming.revision()
                : Math.incrementExact(StackLedgers.get(destination.stack()).orElseThrow().revision());
        StackLedgers.set(stack, new StackLedger(StackLedger.CURRENT_SCHEMA, revision, records));
        slots.set(index, new SlotImage(slot, stack, records));
        return after(before, image, slots, registries);
    }
    private static Mirror image(MaterialOwnerSnapshot before, HolderLookup.Provider registries) {
        if (!(before.owner() instanceof org.goldgom.chemicalelements.api.tx.OwnerKey.PlayerInventory))
            throw new IllegalArgumentException("player owner required");
        var root = JsonParser.parseString(before.canonicalRoot()).getAsJsonObject();
        var result = TransactionEncoding.decodeMirror(TransactionEncoding.canonical(root.get("inventory")), registries);
        if (result.revision() != before.revision()) throw new IllegalArgumentException("player revision mismatch");
        return result;
    }
    private static int index(List<SlotImage> slots, int slot) {
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).index() == slot) return i;
        throw new IllegalArgumentException("unknown authority slot");
    }
    private static MaterialOwnerSnapshot after(MaterialOwnerSnapshot before, Mirror image, List<SlotImage> slots,
                                                HolderLookup.Provider registries) {
        long revision = Math.incrementExact(before.revision());
        var updated = new Mirror(revision, slots, image.tanks(), image.energy(), image.control());
        var root = JsonParser.parseString(before.canonicalRoot()).getAsJsonObject(); root.addProperty("revision", revision);
        root.add("inventory", JsonParser.parseString(TransactionEncoding.mirror(updated, registries)));
        return MaterialOwnerSnapshot.of(before.owner(), before.liveGeneration(), revision, TransactionEncoding.canonical(root));
    }
}

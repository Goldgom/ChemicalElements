package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.goldgom.chemicalelements.api.instance.CreationContext;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.instance.Provenance;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;
import org.goldgom.chemicalelements.persistence.ChunkChemistry;
import org.goldgom.chemicalelements.persistence.WorldChemistryStore;

/**
 * Entry points used by mixins. Everything here is a no-op off the logical server or before the
 * chemistry component is registered, so vanilla behavior is untouched in those states.
 */
public final class Hooks {
    private Hooks() {}

    /** copy()/copyWithCount(): a snapshot keeps the head slice so its count and ledger agree. */
    public static void afterCopy(ItemStack copy) {
        if (!ChemistryComponents.ready() || copy.isEmpty() || !StackLedgers.hasLedger(copy) || StackLedgers.quarantined(copy)) return;
        var l = StackLedgers.get(copy).orElse(null);
        if (l != null && l.instances().size() > copy.getCount()) StackLedgers.set(copy, LedgerOps.head(l, copy.getCount()));
    }

    /** After split: source keeps the instances not taken by the returned head slice (matched by id). */
    public static void afterSplit(ItemStack source, ItemStack taken) {
        if (!ChemistryComponents.ready() || taken.isEmpty() || !StackLedgers.hasLedger(source) || StackLedgers.quarantined(source)) return;
        var l = StackLedgers.get(source).orElse(null);
        var t = StackLedgers.get(taken).orElse(null);
        if (l == null || t == null) return;
        var takenIds = new HashSet<UUID>();
        t.instances().forEach(r -> takenIds.add(r.id()));
        var rest = l.instances().stream().filter(r -> !takenIds.contains(r.id())).toList();
        if (source.isEmpty()) return; // empty stacks drop their components on next set; nothing to keep
        if (rest.size() != l.instances().size()) StackLedgers.set(source, LedgerOps.of(LedgerOps.next(l), rest));
    }

    // ------------------------------------------------------------ placed blocks

    private static final ThreadLocal<IdentityHashMap<ItemStack, UUID>> PENDING_PLACE = ThreadLocal.withInitial(IdentityHashMap::new);

    /** BlockItem placed one item: its head instance becomes the block's persistent instance. */
    public static void placed(ServerLevel level, BlockPos pos, BlockState state, ItemStack held) {
        if (!StackLedgers.authoritative() || held.isEmpty() || StackLedgers.quarantined(held)) return;
        StackLedgers.normalize(held, "place");
        var ledger = StackLedgers.resolveObserved(held, "place");
        if (ledger.instances().isEmpty()) return;
        var head = ledger.instances().getFirst();
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!WorldChemistryStore.putBlock(level, pos, new ChunkChemistry.BlockInstance(blockId, itemId, head, 0))) return;
        PENDING_PLACE.get().put(held, head.id());
    }

    /** After vanilla consume(1): drop the head that now lives in the world (infinite materials keep it). */
    public static void afterPlaceConsume(ItemStack held, int countBefore) {
        var id = PENDING_PLACE.get().remove(held);
        if (id == null || !StackLedgers.authoritative()) return;
        if (held.getCount() >= countBefore) { CREATIVE_COPIES.add(id); return; }
        if (held.isEmpty()) return;
        var l = StackLedgers.get(held);
        if (l.isEmpty()) return;
        StackLedgers.set(held, LedgerOps.of(LedgerOps.next(l.get()), l.get().instances().stream().filter(r -> !r.id().equals(id)).toList()));
        StackLedgers.normalize(held, "place");
    }

    /** Instance ids whose world copy was placed with infinite materials (creative); drops re-identify them. */
    static final Set<UUID> CREATIVE_COPIES = Collections.synchronizedSet(new HashSet<>());

    public static Optional<ChunkChemistry.BlockInstance> takePlaced(ServerLevel level, BlockPos pos) {
        if (!StackLedgers.authoritative()) return Optional.empty();
        return WorldChemistryStore.takeBlock(level, pos);
    }

    /** Placed instance whose block dropped nothing of its item (silk-less ore, glass, explosion decay): explicit sink. */
    public static void unusedPlaced(ChunkChemistry.BlockInstance placed, String blockId) {
        org.goldgom.chemicalelements.persistence.SinkLedger.depositGlobal("block_destroyed:" + blockId, List.of(placed.record()));
    }

    /**
     * Drop of a broken/exploded block. The persistent block instance (if any) becomes the first instance of
     * the first drop of the same item; other drops are new loot instances with explicit loot provenance.
     */
    public static void assignDrop(ItemStack drop, Optional<ChunkChemistry.BlockInstance> placed, String blockId, boolean[] used) {
        if (!StackLedgers.authoritative() || drop.isEmpty() || StackLedgers.hasLedger(drop)) return;
        var fresh = InstanceSampler.mint(drop, drop.getCount(), new CreationContext(Provenance.of("loot", blockId), Optional.empty(), false));
        if (placed.isPresent() && !used[0] && BuiltInRegistries.ITEM.getKey(drop.getItem()).toString().equals(placed.get().itemId())) {
            var list = new ArrayList<InstanceRecord>(fresh.instances());
            var rec = placed.get().record();
            if (CREATIVE_COPIES.remove(rec.id())) rec = new InstanceRecord(LedgerOps.derivedId("creative_copy", List.of(rec.id()), 0), rec.remaining(), rec.origin(), 0, rec.thermal(), rec.nuclides(), rec.poreSolution(), rec.adsorbed());
            list.set(0, rec);
            fresh = LedgerOps.of(0, list);
            used[0] = true;
        }
        StackLedgers.set(drop, fresh);
    }
}

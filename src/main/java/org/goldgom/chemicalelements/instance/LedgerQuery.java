package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.persistence.WorldChemistryStore;

/**
 * Read-only, server-authorized, paged views of instance ledgers for D's UI/network/Jade layer.
 * No mutation path exists here. Targets are limited to: a slot of the player's currently open menu,
 * the player's own inventory, or a loaded block within interaction range in the player's dimension.
 * A stale {@code expectedRevision} returns {@link Status#STALE} with the current revision instead of data.
 */
public final class LedgerQuery {
    public static final int MAX_PAGE = 64;
    public static final double MAX_BLOCK_DISTANCE_SQ = 64.0;

    public enum Status { OK, STALE, FORBIDDEN, NOT_FOUND, QUARANTINED, INVALID_PAGE }

    public record Page(Status status, long revision, int total, int page, int pageSize, List<InstanceRecord> instances, Optional<String> quarantineReason) {
        public Page { instances = List.copyOf(instances); }
        static Page of(Status s) { return new Page(s, -1, 0, 0, 0, List.of(), Optional.empty()); }
    }

    private LedgerQuery() {}

    public static Page menuSlot(ServerPlayer player, int containerId, int slot, long expectedRevision, int page, int pageSize) {
        var menu = player.containerMenu;
        if (menu.containerId != containerId || slot < 0 || slot >= menu.slots.size() || !menu.stillValid(player)) return Page.of(Status.FORBIDDEN);
        return stack(menu.getSlot(slot).getItem(), expectedRevision, page, pageSize);
    }

    public static Page ownInventory(ServerPlayer player, int index, long expectedRevision, int page, int pageSize) {
        var inv = player.getInventory();
        if (index < 0 || index >= inv.getContainerSize()) return Page.of(Status.FORBIDDEN);
        return stack(inv.getItem(index), expectedRevision, page, pageSize);
    }

    /** One placed block instance (Jade-style look-at). */
    public static Page block(ServerPlayer player, BlockPos pos, long expectedRevision) {
        var level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > MAX_BLOCK_DISTANCE_SQ || WorldChemistryStore.loadedChunk(level, pos).isEmpty()) return Page.of(Status.FORBIDDEN);
        var b = WorldChemistryStore.block(level, pos);
        if (b.isEmpty()) return Page.of(Status.NOT_FOUND);
        if (expectedRevision >= 0 && expectedRevision != b.get().revision()) return new Page(Status.STALE, b.get().revision(), 1, 0, 0, List.of(), Optional.empty());
        return new Page(Status.OK, b.get().revision(), 1, 0, 1, List.of(b.get().record()), Optional.empty());
    }

    static Page stack(net.minecraft.world.item.ItemStack s, long expectedRevision, int page, int pageSize) {
        if (s.isEmpty()) return Page.of(Status.NOT_FOUND);
        if (StackLedgers.quarantined(s)) return new Page(Status.QUARANTINED, -1, s.getCount(), 0, 0, List.of(), StackLedgers.get(s).isEmpty() ? Optional.of("unsupported ledger schema") : Optional.empty());
        var l = StackLedgers.get(s);
        if (l.isEmpty()) return Page.of(Status.NOT_FOUND);
        var ledger = l.get();
        if (expectedRevision >= 0 && expectedRevision != ledger.revision()) return new Page(Status.STALE, ledger.revision(), ledger.instances().size(), page, 0, List.of(), Optional.empty());
        int size = Math.max(1, Math.min(MAX_PAGE, pageSize));
        long start = (long) page * size;
        if (page < 0 || start > ledger.instances().size() || (start == ledger.instances().size() && start != 0)) return Page.of(Status.INVALID_PAGE);
        int end = (int) Math.min(ledger.instances().size(), start + size);
        return new Page(Status.OK, ledger.revision(), ledger.instances().size(), page, size, ledger.instances().subList((int) start, end), Optional.empty());
    }
}

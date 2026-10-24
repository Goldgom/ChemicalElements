package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import org.goldgom.chemicalelements.api.tx.*;
import org.goldgom.chemicalelements.machine.MachineMenu;
import org.goldgom.chemicalelements.machine.MachineTransactions.SlotImage;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Server-only schema3 admission through D's real menu resolver. No caller-supplied Containers.
 * Observations are deep copies, NOT CAS snapshots: no revision/generation is invented from a
 * digest, menu stateId, BE.revision, session or object identity. All owners remain unsupported for
 * successful projection until their complete mutation/ABA and durable recovery adapters exist.
 */
public final class MenuOwnerTransactions {
    private MenuOwnerTransactions() {}
    public record Observation(OwnerKey owner, List<SlotImage> slots) {
        public Observation { Objects.requireNonNull(owner); slots = List.copyOf(slots); }
    }

    private static MachineMenu menu(ServerPlayer actor, MenuAuthority authority) {
        Objects.requireNonNull(actor); Objects.requireNonNull(authority);
        if (!actor.server.isSameThread() || actor.hasDisconnected() || !actor.isAlive()
                || !(actor.containerMenu instanceof MachineMenu menu)
                || !menu.transactionAuthority(actor).filter(authority::equals).isPresent())
            throw new IllegalStateException("INVALID_AUTHORITY");
        return menu;
    }

    /** Server-authorized full player snapshot with real mutation evidence. This does not enable
     * successful commit: durable projection recovery and machine atomic ports remain separate gates. */
    public static PlayerOwnerSnapshot snapshotPlayer(ServerPlayer actor, MenuAuthority authority) {
        menu(actor, authority);
        var snapshot = PlayerOwnerSnapshot.capture(actor);
        menu(actor, authority);
        return snapshot;
    }

    /** Full raw Container slots, including unselected/hidden slots. Never resolve/mint missing ledgers. */
    public static List<Observation> observe(ServerPlayer actor, MenuAuthority authority, List<Integer> menuSlots) {
        var menu = menu(actor, authority);
        if (menuSlots.isEmpty() || menuSlots.size() > 126) throw new IllegalArgumentException("slot budget");
        var owners = new LinkedHashMap<OwnerKey, Container>();
        var unique = new HashSet<Integer>();
        for (int index : List.copyOf(menuSlots)) {
            if (!unique.add(index)) throw new IllegalArgumentException("duplicate menu slot");
            var slot = menu.resolveTransactionSlot(actor, index).orElseThrow(() -> new IllegalStateException("INVALID_SLOT"));
            if (slot.layoutVersion() != authority.layoutVersion()) throw new IllegalStateException("LAYOUT_CHANGED");
            var old = owners.putIfAbsent(slot.owner(), slot.container());
            if (old != null && old != slot.container()) throw new IllegalStateException("OWNER_CONTAINER_CHANGED");
        }
        var out = new ArrayList<Observation>();
        owners.forEach((owner, container) -> {
            var slots = new ArrayList<SlotImage>();
            if (container.getContainerSize() > 4096) throw new IllegalStateException("CONTAINER_BUDGET");
            for (int i = 0; i < container.getContainerSize(); i++) {
                var copy = container.getItem(i).copy();
                var instances = copy.isEmpty() ? List.<org.goldgom.chemicalelements.api.instance.InstanceRecord>of()
                        : StackLedgers.get(copy).orElseThrow(() -> new IllegalStateException("UNRESOLVED_LEDGER")).instances();
                slots.add(new SlotImage(i, copy, instances));
            }
            out.add(new Observation(owner, slots));
        });
        menu(actor, authority); // revalidate after reading; observation never grants a later write
        return List.copyOf(out);
    }

    /** Actual admission/commit entry; terminal rejection is durable, successful production is not enabled.
     * UUID routing happens before looking at current inventory: retries never clear/refund an input.
     * A known PREPARED/COMMITTED other-format entry remains a conflict, never aborted here.
     */
    public static MultiOwnerJournal.Receipt commit(ServerPlayer actor, MultiOwnerRequest request, List<Integer> menuSlots) {
        menu(actor, request.authority());
        var journal = SinkLedger.get(); var registries = actor.registryAccess();
        var prior = MultiOwnerJournal.query(journal, request, registries);
        if (prior.state() == MultiOwnerJournal.State.CONFLICT || prior.state() == MultiOwnerJournal.State.CORRUPT) return prior;
        if (prior.state() == MultiOwnerJournal.State.REJECTED)
            return MultiOwnerJournal.rejectDurably(journal, request, registries, prior.reason());
        try {
            OwnerTransferValidation.validate(request, registries);
        } catch (IllegalStateException invalidTransfer) {
            menu(actor, request.authority());
            return MultiOwnerJournal.rejectDurably(journal, request, registries, invalidTransfer.getMessage());
        }
        var observed = observe(actor, request.authority(), menuSlots);
        var requested = new HashSet<OwnerKey>(); request.owners().forEach(c -> requested.add(c.owner()));
        var resolved = new HashSet<OwnerKey>(); observed.forEach(o -> resolved.add(o.owner()));
        String reason = "UNSUPPORTED_OWNER_ABA";
        if (!requested.equals(resolved)) reason = "OWNER_SCOPE_MISMATCH";
        else {
            for (var observation : observed) {
                var change = request.owners().stream().filter(c -> c.owner().equals(observation.owner())).findFirst().orElseThrow();
                if (change.owner() instanceof OwnerKey.MachineInventory) {
                    // Inventory alone omits tanks/energy/control. Never synthesize empty ports or a CAS revision.
                    reason = "UNSUPPORTED_MACHINE_FULL_OWNER"; break;
                }
                if (!sameSlots(observation.slots(), change.before().slots())) { reason = "OWNER_BEFORE_MISMATCH"; break; }
            }
        }
        menu(actor, request.authority()); // commit-time authorization precedes even the rejection write
        return MultiOwnerJournal.rejectDurably(journal, request, registries, reason);
    }

    /** Cancellation cannot refund: this slice never reserves or moves material. Unknown UUID stays unknown. */
    public static MultiOwnerJournal.Receipt cancel(ServerPlayer actor, MultiOwnerRequest request) {
        menu(actor, request.authority());
        var journal = SinkLedger.get();
        var prior = MultiOwnerJournal.query(journal, request, actor.registryAccess());
        if (prior.state() == MultiOwnerJournal.State.REJECTED) journal.flushDurably();
        return prior;
    }

    private static boolean sameSlots(List<SlotImage> actual, List<SlotImage> expected) {
        if (actual.size() != expected.size()) return false;
        var byIndex = new HashMap<Integer, SlotImage>(); expected.forEach(s -> byIndex.put(s.index(), s));
        for (var slot : actual) {
            var other = byIndex.get(slot.index());
            if (other == null || !slot.instances().equals(other.instances())
                    || !net.minecraft.world.item.ItemStack.matches(slot.stack(), other.stack())) return false;
        }
        // InstanceRecord equality includes thermal, nuclides, poreSolution, adsorbed and revision.
        return true;
    }
}

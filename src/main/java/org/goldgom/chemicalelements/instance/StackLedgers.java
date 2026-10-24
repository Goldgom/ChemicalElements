package org.goldgom.chemicalelements.instance;

import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.util.thread.EffectiveSide;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;
import org.goldgom.chemicalelements.persistence.LedgerPayload;
import org.goldgom.chemicalelements.persistence.SinkLedger;
import org.slf4j.Logger;

/**
 * Single access point to the per-stack instance ledger. Invariant (server, stable state):
 * {@code ledger.instances().size() == stack.getCount()}; index 0 is the FIFO head.
 * Stacks without the component are "unresolved" (fresh vanilla creations) and are resolved
 * exactly once with an explicit {@link CreationContext}.
 */
public final class StackLedgers {
    private static final Logger LOG = LogUtils.getLogger();
    private StackLedgers() {}

    /** Authority gate: ledger mutation happens only on the logical server thread after registry freeze. */
    public static boolean authoritative() {
        return ChemistryComponents.ready() && EffectiveSide.get().isServer();
    }

    static DataComponentType<LedgerPayload> type() { return ChemistryComponents.LEDGER.get(); }

    public static boolean hasLedger(ItemStack s) { return ChemistryComponents.ready() && !s.isEmpty() && s.has(type()); }

    /** Present and decodable ledger; quarantined (unknown schema) payloads return empty. */
    public static Optional<StackLedger> get(ItemStack s) {
        if (!hasLedger(s)) return Optional.empty();
        return s.get(type()).ledger();
    }

    public static boolean quarantined(ItemStack s) {
        return hasLedger(s) && s.get(type()).quarantined();
    }

    /** Writes ledger; an empty stack never carries a ledger. Quarantined payloads are never overwritten. */
    public static void set(ItemStack s, StackLedger ledger) {
        if (s.isEmpty() && s.getCount() <= 0) return;
        if (quarantined(s)) throw new IllegalStateException("refusing to overwrite quarantined ledger on " + id(s));
        s.set(type(), LedgerPayload.of(ledger));
    }

    public static void clear(ItemStack s) {
        if (hasLedger(s) && !quarantined(s)) s.remove(type());
    }

    static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }

    /**
     * Vanilla stacking comparison without the chemistry component. Every other component (name,
     * enchantments, damage, custom data) still has to match exactly. Quarantined ledgers never merge.
     */
    public static boolean canMerge(ItemStack a, ItemStack b) {
        if (!a.is(b.getItem())) return false;
        if (a.isEmpty() && b.isEmpty()) return true;
        if (!ChemistryComponents.ready()) return ItemStack.isSameItemSameComponents(a, b);
        if (quarantined(a) || quarantined(b)) return ItemStack.isSameItemSameComponents(a, b);
        var t = type();
        return a.getComponentsPatch().forget(x -> x == t).equals(b.getComponentsPatch().forget(x -> x == t));
    }

    /** Ledger of the stack, resolving an unresolved stack with the given context (server only). */
    public static StackLedger resolve(ItemStack s, CreationContext context) {
        if (s.isEmpty()) return LedgerOps.empty();
        var existing = get(s);
        if (existing.isPresent()) return existing.get();
        if (quarantined(s)) throw new IllegalStateException("quarantined ledger cannot be resolved: " + id(s));
        var ledger = InstanceSampler.mint(s, s.getCount(), context);
        if (!context.simulation()) set(s, ledger);
        return ledger;
    }

    /** Resolve with a generic "observed fresh creation" context. */
    public static StackLedger resolveObserved(ItemStack s, String where) {
        return resolve(s, new CreationContext(Provenance.of("vanilla_creation", where), Optional.empty(), false));
    }

    /**
     * Inventory-boundary audit. Excess instances (head first, matching FIFO consumption) go to the
     * persistent sink ledger; a deficit is filled by explicit zero-matter "unaccounted" instances.
     * Never refills from a template. Returns true if a correction happened.
     */
    public static boolean normalize(ItemStack s, String reason) {
        if (!authoritative() || s.isEmpty() || quarantined(s)) return false;
        var l = get(s);
        if (l.isEmpty()) { resolveObserved(s, reason); return false; }
        var ledger = l.get();
        int size = ledger.instances().size(), count = s.getCount();
        if (size == count) return false;
        if (size > count) {
            var excess = LedgerOps.head(ledger, size - count);
            SinkLedger.depositGlobal("audit_excess:" + id(s) + ":" + reason, excess.instances());
            set(s, LedgerOps.tail(ledger, size - count));
        } else {
            var fill = InstanceSampler.unaccounted(s, count - size, ledger, reason);
            set(s, LedgerOps.append(ledger, fill));
        }
        LOG.debug("Ledger audit corrected {} at {}: {} instances vs count {}", id(s), reason, size, count);
        return true;
    }
}

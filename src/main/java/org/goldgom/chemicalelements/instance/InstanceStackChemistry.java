package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.tx.ChemistryTransaction;
import org.goldgom.chemicalelements.api.tx.TransactionResult;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Package B implementation of A's {@link StackChemistry}. All mutation is server-thread only. */
public final class InstanceStackChemistry implements StackChemistry {
    public static final InstanceStackChemistry INSTANCE = new InstanceStackChemistry();
    private final Set<UUID> committedConsumptions = Collections.synchronizedSet(new HashSet<>());

    private InstanceStackChemistry() {}

    @Override public StackLedger resolve(ItemStack stack, CreationContext context) { return StackLedgers.resolve(stack, context); }

    @Override public Optional<StackLedger> snapshot(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        var l = StackLedgers.get(stack);
        if (l.isPresent() && l.get().instances().size() == stack.getCount()) return l;
        if (l.isPresent()) return Optional.of(l.get().instances().size() > stack.getCount() ? LedgerOps.tail(l.get(), l.get().instances().size() - stack.getCount()) : l.get());
        return Optional.empty();
    }

    /** Head slice leaves with the returned stack; source keeps the tail. */
    @Override public ItemStack split(ItemStack source, int count) {
        if (!StackLedgers.authoritative()) return source.split(count);
        var s = Settlement.begin("split", List.of(source));
        ItemStack out;
        try {
            out = source.split(count);
        } catch (RuntimeException ex) { Settlement.abort(s); throw ex; }
        Settlement.end(s, List.of(source), List.of(out));
        return out;
    }

    @Override public boolean canMerge(ItemStack a, ItemStack b) { return StackLedgers.canMerge(a, b); }

    @Override
    public ChemistryTransaction transfer(ItemStack source, ItemStack target, int count, TransferContext context) {
        return new ChemistryTransaction() {
            TransactionResult check() {
                if (source == target || count <= 0) return TransactionResult.rejected("invalid", "invalid transfer");
                if (source.getCount() < count) return TransactionResult.rejected("source", "insufficient source count");
                if (!target.isEmpty() && !StackLedgers.canMerge(source, target)) return TransactionResult.rejected("incompatible", "non-chemistry components differ");
                if (!target.isEmpty() && target.getCount() + count > target.getMaxStackSize()) return TransactionResult.rejected("capacity", "target stack full");
                long sr = StackLedgers.get(source).map(StackLedger::revision).orElse(0L);
                long tr = StackLedgers.get(target).map(StackLedger::revision).orElse(0L);
                if (context.expectedSourceRevision() >= 0 && sr != context.expectedSourceRevision()) return TransactionResult.rejected("stale", "source revision changed");
                if (context.expectedTargetRevision() >= 0 && tr != context.expectedTargetRevision()) return TransactionResult.rejected("stale", "target revision changed");
                if (StackLedgers.quarantined(source) || StackLedgers.quarantined(target)) return TransactionResult.rejected("quarantined", "unknown ledger schema");
                return new TransactionResult(false, "valid", "");
            }
            @Override public TransactionResult preview() { return check(); }
            @Override public TransactionResult validate() { return check(); }
            @Override public TransactionResult commit() {
                var c = check();
                if (!c.code().equals("valid")) return c;
                if (context.simulation()) return new TransactionResult(false, "simulated", "");
                if (target.isEmpty()) return TransactionResult.rejected("target", "empty target object cannot receive in place; use split()");
                var s = Settlement.begin("transfer:" + context.reason(), List.of(target, source));
                source.shrink(count);
                target.grow(count);
                Settlement.end(s, List.of(target, source));
                return TransactionResult.success();
            }
        };
    }

    /** Consume selected instances (whole instances) into an explicit sink by reason. Idempotent per selection. */
    @Override
    public ChemistryTransaction consume(ItemStack source, Selection selection, ConsumptionReason reason) {
        return new ChemistryTransaction() {
            final UUID key = UUID.nameUUIDFromBytes((selection.instanceIds() + "|" + selection.expectedRevision() + "|" + reason).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            TransactionResult check() {
                if (committedConsumptions.contains(key)) return TransactionResult.rejected("duplicate", "already consumed");
                var l = StackLedgers.get(source);
                if (l.isEmpty()) return TransactionResult.rejected("missing", "no ledger");
                if (selection.expectedRevision() >= 0 && l.get().revision() != selection.expectedRevision()) return TransactionResult.rejected("stale", "ledger changed");
                var ids = new HashSet<UUID>();
                l.get().instances().forEach(r -> ids.add(r.id()));
                if (!ids.containsAll(selection.instanceIds())) return TransactionResult.rejected("missing", "selected instance absent");
                if (new HashSet<>(selection.instanceIds()).size() != selection.instanceIds().size()) return TransactionResult.rejected("invalid", "duplicate selection");
                return new TransactionResult(false, "valid", "");
            }
            @Override public TransactionResult preview() { return check(); }
            @Override public TransactionResult validate() { return check(); }
            @Override public TransactionResult commit() {
                var c = check();
                if (!c.code().equals("valid")) return c;
                var l = StackLedgers.get(source).orElseThrow();
                var take = new HashSet<>(selection.instanceIds());
                var keep = new ArrayList<InstanceRecord>();
                var gone = new ArrayList<InstanceRecord>();
                for (var r : l.instances()) (take.contains(r.id()) ? gone : keep).add(r);
                SinkLedger.depositGlobal("consumed:" + reason.name().toLowerCase(Locale.ROOT), gone);
                source.shrink(gone.size());
                if (!source.isEmpty()) StackLedgers.set(source, LedgerOps.of(LedgerOps.next(l), keep));
                committedConsumptions.add(key);
                return TransactionResult.success();
            }
        };
    }
}

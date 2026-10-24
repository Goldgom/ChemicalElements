package org.goldgom.chemicalelements.instance;

import java.util.*;
import java.util.function.Supplier;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.instance.CreationContext;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.instance.Provenance;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;
import org.goldgom.chemicalelements.persistence.SinkLedger;
import org.slf4j.Logger;

/**
 * Settlement scope around one vanilla operation (partition rules in {@link SettleMath}).
 * <pre>
 *   var s = Settlement.begin("hopper", pre);                 // null on clients / before registry freeze
 *   ...vanilla...
 *   Settlement.end(s, () -> post, returned);                 // post.get(i) = same storage position as pre.get(i)
 * </pre>
 * Nested scopes never settle on their own: they join the root scope, adding their storage positions
 * (new objects are snapshotted at join time; objects the root already snapshotted keep the root's
 * snapshot). Post suppliers are evaluated when the root ends, so the root settles the complete net
 * effect exactly once. Item entities created while a scope is active are root outputs.
 */
public final class Settlement {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ThreadLocal<ArrayDeque<Settlement>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath("chemicalelements", "water");

    /** Disposal of instances nobody received and counts nobody supplied (root scope only). */
    public enum Router { NONE, CRAFT, TRADE, CREATIVE }

    final String reason;
    final Settlement root;
    // root-only state
    final List<ItemStack> preObjs = new ArrayList<>();
    final List<SettleMath.Pre> pre = new ArrayList<>();
    final Set<Object> preIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<Integer> placeholders = new HashSet<>();
    final List<Join> joins = new ArrayList<>();
    final List<ItemStack> emitted = new ArrayList<>();
    Router router = Router.NONE;
    String sinkReason;
    long maxRevision;
    // child-only state
    final List<Integer> childPositions = new ArrayList<>();

    record Join(List<Integer> positions, Supplier<List<ItemStack>> post) {}

    private Settlement(String reason, Settlement root) { this.reason = reason; this.root = root == null ? this : root; }

    public static boolean active() { return !STACK.get().isEmpty(); }
    boolean isRoot() { return root == this; }

    static String key(ItemStack s) {
        if (s.isEmpty() || !ChemistryComponents.ready()) return "";
        var t = ChemistryComponents.LEDGER.get();
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + s.getComponentsPatch().forget(x -> x == t).hashCode();
    }

    static String item(ItemStack s) { return s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }

    public static Settlement begin(String reason, List<ItemStack> participants) { return begin(reason, participants, Set.of()); }

    /** {@code placeholderPositions}: display-only stacks (crafting/trade previews) never resolved or settled. */
    public static Settlement begin(String reason, List<ItemStack> participants, Set<Integer> placeholderPositions) {
        if (!StackLedgers.authoritative()) return null;
        var stack = STACK.get();
        var s = new Settlement(reason, stack.isEmpty() ? null : stack.peekLast());
        for (int i = 0; i < participants.size(); i++) s.root.addPre(participants.get(i), placeholderPositions.contains(i), s);
        stack.push(s);
        return s;
    }

    /**
     * Adds a storage position to the root. A ledger longer than the count (vanilla split/copy pattern:
     * the copy took the head) is snapshotted as its tail plus an orphan head contribution, so the head
     * is available exactly once to whoever holds it now. Mid-operation objects without a ledger are not
     * resolved (that would mint template matter); they enter with zero instances.
     */
    private void addPre(ItemStack o, boolean placeholder, Settlement owner) {
        int index = pre.size();
        preObjs.add(o);
        owner.childPositions.add(index);
        if (placeholder) placeholders.add(index);
        if (placeholder || o == null || o.isEmpty() || StackLedgers.quarantined(o) || preIdentity.contains(o)) { pre.add(null); return; }
        var existing = StackLedgers.get(o);
        StackLedger l = existing.isPresent() ? existing.get()
                : owner == this ? StackLedgers.resolveObserved(o, reason) : LedgerOps.empty();
        int size = l.instances().size(), count = o.getCount();
        if (size > count) {
            var head = l.instances().subList(0, size - count);
            orphans.add(new SettleMath.Pre(new Object(), head.size(), head, key(o), item(o)));
            l = LedgerOps.tail(l, size - count);
        }
        pre.add(new SettleMath.Pre(o, count, l.instances(), key(o), item(o)));
        preIdentity.add(o);
        maxRevision = Math.max(maxRevision, l.revision());
    }

    final List<SettleMath.Pre> orphans = new ArrayList<>();

    public Settlement route(Router r) { if (isRoot()) this.router = r; return this; }
    public Settlement sinkAs(String reason) { if (isRoot()) this.sinkReason = reason; return this; }

    /** Every ItemEntity created while a scope is active hands its stack to the root scope as output. */
    public static void emit(ItemStack stack) {
        var s = STACK.get().peekLast();
        if (s != null && stack != null && !stack.isEmpty()) s.emitted.add(stack);
    }

    public static void end(Settlement s, List<ItemStack> post) { end(s, () -> post, List.of()); }
    public static void end(Settlement s, List<ItemStack> post, List<ItemStack> returned) { end(s, () -> post, returned); }

    public static void end(Settlement s, Supplier<List<ItemStack>> post, List<ItemStack> returned) {
        if (s == null) return;
        var stack = STACK.get();
        if (stack.peek() != s) { LOG.error("Unbalanced chemistry settlement {}", s.reason); stack.remove(s); return; }
        stack.pop();
        if (!s.isRoot()) {
            // returned values of nested calls are transient: the root sees final storage only
            s.root.joins.add(new Join(List.copyOf(s.childPositions), post));
            return;
        }
        List<ItemStack> postList = post.get();
        try {
            settle(s, postList, returned);
        } catch (RuntimeException ex) {
            LOG.error("Chemistry settlement failed at {}; participants audited", s.reason, ex);
            for (var o : postList) if (o != null) StackLedgers.normalize(o, s.reason + ":recovery");
        }
    }

    /** Exception unwinding: discard the scope (and with a root, every joined child). */
    public static void abort(Settlement s) {
        if (s == null) return;
        var stack = STACK.get();
        if (s.isRoot()) { while (!stack.isEmpty() && stack.peekLast() == s.root) { if (stack.pop() == s) break; } }
        else stack.remove(s);
    }

    // ------------------------------------------------------------------ core

    static void settle(Settlement s, List<ItemStack> rootPost, List<ItemStack> returned) {
        int n = s.pre.size();
        var postAt = new ItemStack[n];
        for (int i = 0; i < Math.min(rootPost.size(), n); i++) postAt[i] = rootPost.get(i);
        // root's own positions were added first (indices 0..rootPost.size()-1); child joins map their lists
        for (var j : s.joins) {
            var list = j.post().get();
            for (int k = 0; k < Math.min(list.size(), j.positions().size()); k++) postAt[j.positions().get(k)] = list.get(k);
        }
        var positions = new ArrayList<SettleMath.Position>();
        var objs = new ArrayList<ItemStack>();
        var listed = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (int i = 0; i < n; i++) {
            var a = s.placeholders.contains(i) ? null : postAt[i];
            if (a != null && !listed.add(a)) a = null;
            positions.add(new SettleMath.Position(s.pre.get(i), post(a)));
            objs.add(a);
        }
        var extra = new ArrayList<ItemStack>(s.emitted);
        extra.addAll(returned);
        for (var e : extra) {
            if (e == null || e.isEmpty() || !listed.add(e)) continue;
            positions.add(new SettleMath.Position(null, post(e)));
            objs.add(e);
        }
        for (var o : s.orphans) { positions.add(new SettleMath.Position(o, null)); objs.add(null); }
        var result = SettleMath.settle(positions, Set.of(), s.router != Router.CRAFT);
        long rev = Math.addExact(s.maxRevision, 1);
        result.assigned().forEach((i, list) -> write(objs.get(i), list, rev));
        route(s, objs, result);
        for (var a : objs) if (a != null && !a.isEmpty()) StackLedgers.normalize(a, s.reason);
    }

    static SettleMath.Post post(ItemStack a) {
        return (a == null || a.isEmpty() || StackLedgers.quarantined(a)) ? null : new SettleMath.Post(a, a.getCount(), key(a), item(a));
    }

    /** Only writes when the instance list actually changed (keeps revisions and slot sync stable). */
    static void write(ItemStack obj, List<InstanceRecord> list, long rev) {
        if (obj == null || obj.isEmpty()) return;
        var cur = StackLedgers.get(obj);
        if (cur.isPresent() && cur.get().instances().equals(list)) return;
        StackLedgers.set(obj, LedgerOps.of(Math.max(rev, cur.map(l -> l.revision() + 1).orElse(0L)), list));
    }

    static void route(Settlement s, List<ItemStack> objs, SettleMath.Result r) {
        var leftovers = new ArrayList<InstanceRecord>();
        r.leftover().values().forEach(leftovers::addAll);
        var needs = r.needs();
        String sink = s.sinkReason != null ? s.sinkReason : "vanished:" + s.reason;
        switch (s.router) {
            case CRAFT -> {
                if (!needs.isEmpty() && !leftovers.isEmpty()) { convert(s, objs, needs, leftovers, "crafted:" + s.reason); return; }
            }
            case TRADE -> {
                mint(objs, needs, "trade", s.reason);
                SinkLedger.depositGlobal("trade_payment:" + s.reason, leftovers);
                return;
            }
            case CREATIVE -> {
                mint(objs, needs, "creative", s.reason);
                SinkLedger.depositGlobal("creative_void:" + s.reason, leftovers);
                return;
            }
            default -> {}
        }
        for (var need : needs) {
            var obj = objs.get(need.position());
            var cur = StackLedgers.get(obj).orElse(LedgerOps.empty());
            StackLedgers.set(obj, LedgerOps.append(cur, InstanceSampler.unaccounted(obj, need.missing(), cur, s.reason)));
            LOG.debug("Settlement {}: {} unaccounted instance(s) for {}", s.reason, need.missing(), need.item());
        }
        SinkLedger.depositGlobal(sink, leftovers);
    }

    static void mint(List<ItemStack> objs, List<SettleMath.Need> needs, String kind, String where) {
        for (var need : needs) {
            var obj = objs.get(need.position());
            var cur = StackLedgers.get(obj).orElse(LedgerOps.empty());
            var fresh = InstanceSampler.mint(obj, need.missing(), new CreationContext(Provenance.of(kind, where), Optional.empty(), false));
            StackLedgers.set(obj, LedgerOps.append(cur, fresh));
        }
    }

    /**
     * Exact conversion: all consumed instances are partitioned into the needed output instances.
     * Outputs that are the crafting remainder of a consumed item (buckets, bottles) are containers and
     * take their nominal composition first. Matter, nuclides and heat are conserved; unallocated matter
     * is recorded as an explicit residue sink.
     */
    static void convert(Settlement s, List<ItemStack> objs, List<SettleMath.Need> needs, List<InstanceRecord> consumed, String provenance) {
        var catalog = InstanceSampler.catalog();
        var remainders = new HashSet<String>();
        for (var x : s.preObjs) if (x != null && !x.isEmpty() && x.hasCraftingRemainingItem())
            remainders.add(BuiltInRegistries.ITEM.getKey(x.getCraftingRemainingItem().getItem()).toString());
        var specs = new ArrayList<LedgerOps.OutputSpec>();
        var slots = new ArrayList<Integer>();
        for (var need : needs) {
            var nominal = catalog.itemCompositions().getOrDefault(ResourceLocation.parse(need.item()), Composition.EMPTY);
            for (int k = 0; k < need.missing(); k++) { specs.add(new LedgerOps.OutputSpec(need.item(), nominal, remainders.contains(need.item()))); slots.add(need.position()); }
        }
        var origin = new Provenance("conversion", provenance, 0, catalog.epoch(), Map.of());
        var d = LedgerOps.derive(provenance, consumed, specs, origin, WATER);
        if (!LedgerOps.conserved(consumed, d.outputs(), d.unallocated(), WATER)) throw new IllegalStateException("conversion not conserved at " + s.reason);
        var per = new TreeMap<Integer, List<InstanceRecord>>();
        for (int k = 0; k < slots.size(); k++) per.computeIfAbsent(slots.get(k), x -> new ArrayList<>()).add(d.outputs().get(k));
        per.forEach((pos, list) -> {
            var obj = objs.get(pos);
            var cur = StackLedgers.get(obj).orElse(LedgerOps.empty());
            StackLedgers.set(obj, LedgerOps.append(cur, LedgerOps.of(0, list)));
        });
        if (!d.unallocated().amounts().isEmpty()) SinkLedger.get().depositMatter(s.sinkReason != null ? s.sinkReason : "conversion_residue:" + s.reason, d.unallocated());
        d.diagnostics().forEach(m -> LOG.debug("conversion {}: {}", s.reason, m));
    }
}

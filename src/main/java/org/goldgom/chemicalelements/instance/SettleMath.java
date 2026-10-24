package org.goldgom.chemicalelements.instance;

import java.util.*;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;

/**
 * Pure re-partition of instances after an arbitrary vanilla stack operation, from object identity and
 * counts only (no Minecraft types; unit-testable). Rules, in order:
 * <ol>
 *   <li>Retained object (same identity before and after): keeps its own instances. Shrunk → keeps its
 *       tail, the head enters the pool (FIFO: the moved part is the head). Grown → draws the difference.</li>
 *   <li>Replaced object at the same position with the same item: reclaims the old instances of that
 *       position (tail when smaller; head to pool) — covers vanilla "copyWithCount then set" idioms.</li>
 *   <li>Every other pre-existing instance enters the pool in position order.</li>
 *   <li>Needs are drained FIFO from the pool: exact stacking key first, then same item.</li>
 * </ol>
 * Instance identities are globally de-duplicated: an id already assigned or pooled is ignored at any
 * later position, so a vanilla copy carrying a duplicate ledger can never duplicate matter.
 * The output is a permutation of the distinct input instances plus explicit unmet needs and leftovers.
 */
public final class SettleMath {
    private SettleMath() {}

    public record Pre(Object obj, int count, List<InstanceRecord> ledger, String key, String item) {
        public Pre { ledger = List.copyOf(ledger); }
        boolean present() { return obj != null && count > 0; }
    }

    public record Post(Object obj, int count, String key, String item) {
        boolean present() { return obj != null && count > 0; }
    }

    /** pre or post may be null (post-only outputs / pre-only removed positions). */
    public record Position(Pre pre, Post post) {}

    public record Need(int position, int missing, String key, String item) {}

    public record Result(Map<Integer, List<InstanceRecord>> assigned, Map<String, List<InstanceRecord>> leftover,
                         Map<String, String> leftoverItems, List<Need> needs) {
        public int leftoverCount() { return leftover.values().stream().mapToInt(List::size).sum(); }
        public int neededCount() { return needs.stream().mapToInt(Need::missing).sum(); }
    }

    public static Result settle(List<Position> positions) { return settle(positions, Set.of(), true); }

    public static Result settle(List<Position> positions, Set<UUID> excluded) { return settle(positions, excluded, true); }

    /**
     * {@code excluded}: instance ids already accounted elsewhere. {@code drain=false}: needs are never
     * filled from the pool (conversion scopes: every output must come from an explicit conversion).
     */
    public static Result settle(List<Position> positions, Set<UUID> excluded, boolean drain) {
        int n = positions.size();
        var seen = new HashSet<UUID>(excluded);
        // pre de-duplication by object identity: first position wins
        var pre = new Pre[n];
        var preIndex = new IdentityHashMap<Object, Integer>();
        for (int i = 0; i < n; i++) {
            var p = positions.get(i).pre();
            if (p == null || !p.present() || preIndex.containsKey(p.obj())) continue;
            preIndex.put(p.obj(), i);
            pre[i] = p;
        }
        var post = new Post[n];
        var postSeen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (int i = 0; i < n; i++) {
            var q = positions.get(i).post();
            if (q == null || !q.present() || !postSeen.add(q.obj())) continue;
            post[i] = q;
        }
        var assigned = new TreeMap<Integer, List<InstanceRecord>>();
        var consumed = new boolean[n];
        record Contribution(int pos, String key, String item, List<InstanceRecord> list) {}
        var contributions = new ArrayList<Contribution>();
        var needs = new ArrayList<Need>();

        // 1. retained objects
        for (int i = 0; i < n; i++) {
            if (post[i] == null) continue;
            var j = preIndex.get(post[i].obj());
            if (j == null || consumed[j]) continue;
            consumed[j] = true;
            keep(i, post[i], pre[j], seen, assigned, needs, (k, it, l) -> contributions.add(new Contribution(j, k, it, l)));
        }
        // 2. replaced objects reclaim their position
        for (int i = 0; i < n; i++) {
            if (post[i] == null || assigned.containsKey(i)) continue;
            if (pre[i] != null && !consumed[i] && Objects.equals(pre[i].item(), post[i].item())) {
                consumed[i] = true;
                final int fi = i;
                keep(i, post[i], pre[i], seen, assigned, needs, (k, it, l) -> contributions.add(new Contribution(fi, k, it, l)));
            } else {
                assigned.put(i, new ArrayList<>());
                needs.add(new Need(i, post[i].count(), post[i].key(), post[i].item()));
            }
        }
        // 3. every other previous instance
        for (int i = 0; i < n; i++)
            if (pre[i] != null && !consumed[i]) contributions.add(new Contribution(i, pre[i].key(), pre[i].item(), pre[i].ledger()));
        contributions.sort(Comparator.comparingInt(Contribution::pos));
        var pool = new LinkedHashMap<String, ArrayDeque<InstanceRecord>>();
        var itemOfKey = new HashMap<String, String>();
        for (var c : contributions) {
            var q = pool.computeIfAbsent(c.key(), k -> new ArrayDeque<>());
            itemOfKey.put(c.key(), c.item());
            for (var r : c.list()) if (seen.add(r.id())) q.add(r);
        }
        // 4. drain
        var unmet = new ArrayList<Need>();
        needs.sort(Comparator.comparingInt(Need::position));
        for (var need : needs) {
            if (!drain) { unmet.add(need); continue; }
            var target = assigned.get(need.position());
            int missing = drain(pool.get(need.key()), target, need.missing());
            for (var e : pool.entrySet()) {
                if (missing == 0) break;
                if (!e.getKey().equals(need.key()) && Objects.equals(itemOfKey.get(e.getKey()), need.item()))
                    missing = drain(e.getValue(), target, missing);
            }
            if (missing > 0) unmet.add(new Need(need.position(), missing, need.key(), need.item()));
        }
        var leftover = new LinkedHashMap<String, List<InstanceRecord>>();
        var leftoverItems = new LinkedHashMap<String, String>();
        pool.forEach((k, q) -> { if (!q.isEmpty()) { leftover.put(k, List.copyOf(q)); leftoverItems.put(k, itemOfKey.get(k)); } });
        return new Result(assigned, leftover, leftoverItems, unmet);
    }

    private interface Spill { void accept(String key, String item, List<InstanceRecord> list); }

    /** Keep the tail of {@code p} (de-duplicated) for post object {@code q}; spill the head. */
    private static void keep(int i, Post q, Pre p, Set<UUID> seen, Map<Integer, List<InstanceRecord>> assigned, List<Need> needs, Spill spill) {
        var own = new ArrayList<InstanceRecord>();
        for (var r : p.ledger()) if (!seen.contains(r.id())) own.add(r);
        int c = q.count(), m = own.size();
        List<InstanceRecord> kept;
        if (c >= m) {
            kept = own;
            if (c > m) needs.add(new Need(i, c - m, q.key(), q.item()));
        } else {
            kept = new ArrayList<>(own.subList(m - c, m));
            spill.accept(p.key(), p.item(), new ArrayList<>(own.subList(0, m - c)));
        }
        for (var r : kept) seen.add(r.id());
        assigned.put(i, kept);
    }

    private static int drain(ArrayDeque<InstanceRecord> q, List<InstanceRecord> into, int missing) {
        if (q == null) return missing;
        while (missing > 0 && !q.isEmpty()) { into.add(q.pollFirst()); missing--; }
        return missing;
    }
}

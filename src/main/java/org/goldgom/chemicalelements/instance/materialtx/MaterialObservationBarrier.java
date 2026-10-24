package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;

/** In-process exclusion for ORIGINAL owner roots and their mutable child objects.
 * This is metadata, not an inventory. Adapters must instrument every ordinary read/write path;
 * merely acquiring this lease does NOT prove an adapter is safe or a transaction atomic.
 * All access (including same-thread callbacks) is rejected while reserved. Projection code must
 * use a direct, non-callback atomic root replacement, never temporarily disable the guard.
 * A failed/partial projection retains the lease until reconciliation; close is deliberately absent.
 * Durable isolation across restart is the responsibility of the owner pending marker + journal.
 */
public final class MaterialObservationBarrier {
    private MaterialObservationBarrier() {}
    private static final IdentityHashMap<Object, Lease> HELD = new IdentityHashMap<>();

    public static synchronized boolean hasReservations() { return !HELD.isEmpty(); }

    public static synchronized boolean isReservedFor(Object root, UUID operation, String globalHash) {
        Lease lease = HELD.get(root);
        return lease != null && !lease.released && lease.thread == Thread.currentThread()
                && lease.operation.equals(operation) && lease.globalHash.equals(globalHash);
    }

    /** Opaque lifetime identity for leaf capabilities. Reacquiring the same operation/hash
     * must not revive a capability issued under an already released lease. */
    public static synchronized Object reservationIdentity(Object root, UUID operation, String globalHash) {
        if (!isReservedFor(root, operation, globalHash)) throw new IllegalStateException("MATERIAL_ROOT_NOT_RESERVED");
        return HELD.get(root).identity;
    }
    public static synchronized boolean isReservedFor(Object root, UUID operation, String globalHash, Object identity) {
        return isReservedFor(root, operation, globalHash) && HELD.get(root).identity == identity;
    }

    public static synchronized void requireObservable(Object root) {
        Objects.requireNonNull(root);
        Lease lease = HELD.get(root);
        if (lease != null) throw new IllegalStateException("MATERIAL_OWNER_ISOLATED:" + lease.operation);
    }

    /** Whole-set acquisition is atomic. Roots must include the actual inventory/list/stack and
     * component objects reachable through public aliases, not snapshots or owner key values. */
    public static synchronized Lease reserve(UUID operation, String globalHash, Collection<?> roots) {
        Objects.requireNonNull(operation);
        if (globalHash == null || !globalHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("global transaction hash");
        var unique = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (Object root : roots) {
            Objects.requireNonNull(root);
            if (!unique.add(root)) continue;
            if (HELD.containsKey(root)) throw new IllegalStateException("MATERIAL_OWNER_RESERVED");
        }
        if (unique.isEmpty()) throw new IllegalArgumentException("empty owner reservation");
        var lease = new Lease(operation, globalHash, List.copyOf(unique), Thread.currentThread());
        for (Object root : unique) HELD.put(root, lease);
        return lease;
    }

    public static final class Lease {
        private final UUID operation;
        private final String globalHash;
        private final List<Object> roots;
        private final Thread thread;
        private final Object identity = new Object();
        private boolean released;
        private boolean projectionStarted;
        private Lease(UUID operation, String globalHash, List<Object> roots, Thread thread) {
            this.operation = operation; this.globalHash = globalHash; this.roots = new ArrayList<>(roots); this.thread = thread;
        }
        public UUID operation() { return operation; }
        public String globalHash() { return globalHash; }
        public void requireRoot(Object root) {
            synchronized (MaterialObservationBarrier.class) {
                requireHeld();
                if (HELD.get(root) != this) throw new IllegalStateException("FOREIGN_RESERVATION_ROOT");
            }
        }
        /** Add freshly decoded replacement children BEFORE publishing them. Original aliases
         * stay reserved too. Only the internal publisher may expand a lease; no guard callback
         * receives this capability and expansion never makes an existing root observable. */
        void reserveReplacementChildren(Collection<?> children) {
            synchronized (MaterialObservationBarrier.class) {
                requireHeld();
                var unique = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
                for (Object child : children) {
                    Objects.requireNonNull(child);
                    if (HELD.containsKey(child) && HELD.get(child) != this)
                        throw new IllegalStateException("REPLACEMENT_ALIAS_ALREADY_RESERVED");
                    unique.add(child);
                }
                for (Object child : unique) if (!HELD.containsKey(child)) {
                    HELD.put(child, this); roots.add(child);
                }
            }
        }
        public void beforeProjection() {
            synchronized (MaterialObservationBarrier.class) {
                requireHeld(); projectionStarted = true;
            }
        }
        /** Only before the first mutation, after every owner was revalidated BEFORE. */
        public void releaseUnprojected() {
            synchronized (MaterialObservationBarrier.class) {
                requireHeld();
                if (projectionStarted) throw new IllegalStateException("PARTIAL_REQUIRES_FORWARD_RECOVERY");
                release();
            }
        }
        /** Package-private: only the journal coordinator may release after durable global commit
         * and synchronous persistence of every owner with the same operation/hash marker. */
        void releaseCommitted() {
            synchronized (MaterialObservationBarrier.class) { requireHeld(); release(); }
        }
        private void requireHeld() {
            if (Thread.currentThread() != thread || released)
                throw new IllegalStateException("INVALID_RESERVATION_LIFETIME");
            for (Object root : roots) if (HELD.get(root) != this)
                throw new IllegalStateException("RESERVATION_LOST");
        }
        private void release() {
            for (Object root : roots) HELD.remove(root);
            released = true;
        }
    }
}

package org.goldgom.chemicalelements.world.material;

import java.util.*;
import java.lang.ref.*;
import org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier;

/** Identity-only bindings; no world lookup or lazy chunk creation in an observation check.
 * Aliases remain bound for the lifetime of their chunk, including replaced palettes. */
public final class WorldChunkBoundary {
    private static final ReferenceQueue<Object> COLLECTED = new ReferenceQueue<>();
    private static final Map<IdentityReference, Set<IdentityReference>> ROOTS = new HashMap<>();
    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;
        IdentityReference(Object value, ReferenceQueue<Object> queue) {
            super(value, queue); hash = System.identityHashCode(value);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            Object value = get();
            return value != null && other instanceof IdentityReference ref && value == ref.get();
        }
    }
    private static void clean() {
        Reference<?> reference;
        while ((reference = COLLECTED.poll()) != null) ROOTS.remove(reference);
    }
    private static List<Object> roots(Object alias) {
        clean();
        var references = ROOTS.get(new IdentityReference(alias, null));
        if (references == null) return List.of();
        references.removeIf(ref -> ref.get() == null);
        var result = new ArrayList<Object>();
        for (var ref : references) { var root = ref.get(); if (root != null) result.add(root); }
        return result;
    }
    private WorldChunkBoundary() {}
    public static void bind(Object alias, Object originalChunk) {
        Objects.requireNonNull(alias); Objects.requireNonNull(originalChunk);
        synchronized (ROOTS) {
            clean();
            ROOTS.computeIfAbsent(new IdentityReference(alias, COLLECTED), ignored -> new HashSet<>())
                    .add(new IdentityReference(originalChunk, null));
        }
    }
    public static void bindChild(Object child, Object parent) {
        synchronized (ROOTS) {
            for (Object root : roots(parent)) bind(child, root);
        }
    }
    public static void requireObservable(Object object) {
        if (!MaterialObservationBarrier.hasReservations()) return;
        MaterialObservationBarrier.requireObservable(object);
        List<Object> roots;
        synchronized (ROOTS) { roots = roots(object); }
        for (Object root : roots) MaterialObservationBarrier.requireObservable(root);
    }
}

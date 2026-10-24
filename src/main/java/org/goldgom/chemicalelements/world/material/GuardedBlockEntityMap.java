package org.goldgom.chemicalelements.world.material;

import java.util.*;
import org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier;

/** A map whose retained views, iterators and entries never expose the backing map.
 * Install before constructor escape. Default Map compute/merge methods dispatch through
 * guarded get/put/remove; callbacks cannot bypass a reservation acquired during the callback. */
public final class GuardedBlockEntityMap<K, V> extends AbstractMap<K, V> {
    private final Map<K, V> delegate = new HashMap<>();
    private final Object originalRoot;
    public GuardedBlockEntityMap(Object originalRoot, Map<? extends K, ? extends V> initial) {
        this.originalRoot = Objects.requireNonNull(originalRoot);
        delegate.putAll(initial);
    }
    private void check() { MaterialObservationBarrier.requireObservable(originalRoot); }
    public boolean containsIdentity(K key, V expected) {
        return expected != null && delegate.get(key) == expected;
    }
    public V worldPeek(WorldPrimitivePermit permit, org.goldgom.chemicalelements.api.tx.OwnerKey.WorldBlock owner,
            UUID operation, String hash, K key) {
        requirePosition(owner, key);
        permit.consume(owner, operation, hash, originalRoot, WorldPrimitivePermit.Primitive.READ_ROOT);
        return delegate.get(key);
    }
    public void worldReplace(WorldPrimitivePermit permit, org.goldgom.chemicalelements.api.tx.OwnerKey.WorldBlock owner,
            UUID operation, String hash, K key, V expected, V replacement) {
        requirePosition(owner, key);
        permit.consume(owner, operation, hash, originalRoot, WorldPrimitivePermit.Primitive.REPLACE_BE_MAP);
        if (delegate.get(key) != expected) throw new IllegalStateException("WORLD_BE_MAP_CAS_FAILED");
        if (replacement == null) delegate.remove(key); else delegate.put(key, replacement);
    }
    private void requirePosition(org.goldgom.chemicalelements.api.tx.OwnerKey.WorldBlock owner, K key) {
        if (!(key instanceof net.minecraft.core.BlockPos pos) || pos.asLong() != owner.blockPos())
            throw new IllegalArgumentException("foreign world primitive position");
    }
    @Override public V get(Object key) { check(); return delegate.get(key); }
    @Override public boolean containsKey(Object key) { check(); return delegate.containsKey(key); }
    @Override public int size() { check(); return delegate.size(); }
    @Override public V put(K key, V value) { check(); return delegate.put(key, value); }
    @Override public V remove(Object key) { check(); return delegate.remove(key); }
    @Override public void clear() { check(); delegate.clear(); }
    @Override public Set<Entry<K, V>> entrySet() {
        check();
        return new AbstractSet<>() {
            @Override public int size() { return GuardedBlockEntityMap.this.size(); }
            @Override public void clear() { GuardedBlockEntityMap.this.clear(); }
            @Override public Iterator<Entry<K, V>> iterator() {
                check(); var iterator = delegate.entrySet().iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { check(); return iterator.hasNext(); }
                    @Override public Entry<K, V> next() {
                        check(); Entry<K, V> e = iterator.next();
                        return new Entry<>() {
                            @Override public K getKey() { check(); return e.getKey(); }
                            @Override public V getValue() { check(); return e.getValue(); }
                            @Override public V setValue(V value) { check(); return e.setValue(value); }
                            @Override public boolean equals(Object other) {
                                check(); return other instanceof Entry<?, ?> that
                                        && Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
                            }
                            @Override public int hashCode() { check(); return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue()); }
                        };
                    }
                    @Override public void remove() { check(); iterator.remove(); }
                };
            }
        };
    }
}

package org.goldgom.chemicalelements.world.material;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier;
import static org.junit.jupiter.api.Assertions.*;

class WorldPrimitivePermitTest {
    private static final String HASH = "a".repeat(64);
    private static OwnerKey.WorldBlock owner() {
        return new OwnerKey.WorldBlock(ResourceLocation.withDefaultNamespace("overworld"), 0L, UUID.randomUUID());
    }
    @Test void releasedLeaseCannotBeRevivedByTheSameOperationAndHash() {
        var root = new Object(); var op = UUID.randomUUID(); var owner = owner();
        var original = MaterialObservationBarrier.reserve(op, HASH, List.of(root));
        var stale = new WorldPrimitivePermit(owner, op, HASH, root, root, WorldPrimitivePermit.Primitive.READ_ROOT);
        original.releaseUnprojected();
        var next = MaterialObservationBarrier.reserve(op, HASH, List.of(root));
        try {
            assertThrows(IllegalStateException.class, () -> stale.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.READ_ROOT));
            var current = new WorldPrimitivePermit(owner, op, HASH, root, root, WorldPrimitivePermit.Primitive.READ_ROOT);
            assertDoesNotThrow(() -> current.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.READ_ROOT));
        } finally { next.releaseUnprojected(); }
    }
    @Test void targetOperationAndPrimitiveAreExactAndSingleUse() {
        var root = new Object(); var op = UUID.randomUUID(); var owner = owner();
        var lease = MaterialObservationBarrier.reserve(op, HASH, List.of(root));
        try {
            var permit = new WorldPrimitivePermit(owner, op, HASH, root, root, WorldPrimitivePermit.Primitive.REPLACE_ROOT);
            assertThrows(IllegalStateException.class, () -> permit.consume(owner, UUID.randomUUID(), HASH, root, WorldPrimitivePermit.Primitive.REPLACE_ROOT));
            assertThrows(IllegalStateException.class, () -> permit.consume(owner, op, HASH, new Object(), WorldPrimitivePermit.Primitive.REPLACE_ROOT));
            assertThrows(IllegalStateException.class, () -> permit.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.READ_ROOT));
            permit.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.REPLACE_ROOT);
            assertThrows(IllegalStateException.class, () -> permit.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.REPLACE_ROOT));
        } finally { lease.releaseUnprojected(); }
    }
    @Test void anotherThreadCannotBorrowTheCapability() throws InterruptedException {
        var root = new Object(); var op = UUID.randomUUID(); var owner = owner();
        var lease = MaterialObservationBarrier.reserve(op, HASH, List.of(root));
        try {
            var permit = new WorldPrimitivePermit(owner, op, HASH, root, root, WorldPrimitivePermit.Primitive.READ_ROOT);
            var error = new AtomicReference<Throwable>();
            var thread = new Thread(() -> { try { permit.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.READ_ROOT); }
                catch (Throwable expected) { error.set(expected); } });
            thread.start(); thread.join(2000);
            assertFalse(thread.isAlive()); assertInstanceOf(IllegalStateException.class, error.get());
            permit.consume(owner, op, HASH, root, WorldPrimitivePermit.Primitive.READ_ROOT);
        } finally { lease.releaseUnprojected(); }
    }
}

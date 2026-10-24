package org.goldgom.chemicalelements.world.material;

import java.util.Objects;
import java.util.UUID;
import org.goldgom.chemicalelements.api.tx.OwnerKey;

/** Candidate internal primitive capability, NOT a transaction admission or a barrier bypass.
 * No ThreadLocal, ambient flag, callback or public minting API. The world bridge must mint only
 * after validating B's matching durable reservation and the currently installed original root.
 */
final class WorldPrimitivePermit {
    enum Primitive { REPLACE_ROOT, REPLACE_SECTION, REPLACE_BE_MAP, ENCODE_ROOT, WRITE_CHUNK }
    private final OwnerKey.WorldBlock owner;
    private final UUID operation;
    private final String hash;
    private final Object target;
    private final Primitive primitive;
    private final Thread thread;
    private boolean consumed;

    WorldPrimitivePermit(OwnerKey.WorldBlock owner, UUID operation, String hash,
                         Object target, Primitive primitive) {
        this.owner = Objects.requireNonNull(owner);
        this.operation = Objects.requireNonNull(operation);
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("global hash");
        this.hash = hash;
        this.target = Objects.requireNonNull(target);
        this.primitive = Objects.requireNonNull(primitive);
        this.thread = Thread.currentThread();
    }

    synchronized void consume(OwnerKey.WorldBlock expectedOwner, UUID expectedOperation,
                              String expectedHash, Object expectedTarget, Primitive expectedPrimitive) {
        if (consumed || thread != Thread.currentThread() || target != expectedTarget
                || primitive != expectedPrimitive || !owner.equals(expectedOwner)
                || !operation.equals(expectedOperation) || !hash.equals(expectedHash))
            throw new IllegalStateException("INVALID_WORLD_PRIMITIVE_CAPABILITY");
        consumed = true;
    }
}

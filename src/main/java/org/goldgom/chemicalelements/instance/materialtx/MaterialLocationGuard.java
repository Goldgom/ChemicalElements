package org.goldgom.chemicalelements.instance.materialtx;

import java.util.Objects;
import java.util.UUID;

/** Trusted server resolver evidence, NOT an owner adapter, permit or isolation bypass.
 * A guard must only inspect its original loaded physical root without callbacks, chunk loads,
 * public observation, mutation or temporary guard disabling. No lease is exposed to guards.
 * Each alias is retained even when several aliases share one physical owner/change.
 */
public interface MaterialLocationGuard {
    MaterialOwnerAccess physicalAccess();

    enum Boundary { PREPARE, ATTACH_RECOVERY, CLASSIFY_BEFORE, CLASSIFY_AFTER,
        ISOLATE_BEFORE, ISOLATE_AFTER, PROJECT_BEFORE, PROJECT_AFTER,
        PERSIST_BEFORE, PERSIST_AFTER, COMPLETE_BEFORE, COMPLETE_AFTER,
        CANCEL_BEFORE, CANCEL_AFTER }

    /** expected=null only before classification: accept exactly BEFORE or AFTER, not a third
     * image. After classification it is the actual image, including legitimate changed heat.
     * Implementations must bind op/hash and both full images, check loaded object identity,
     * incarnation, live generation, bundle and finite-heat constraints at EVERY invocation.
     * Recovery may bind historical journal generation, never revive an old loaded handle.
     */
    record Check(MaterialOwnerAccess physicalAccess, UUID operation, String globalHash,
                 MaterialOwnerSnapshot before, MaterialOwnerSnapshot after,
                 boolean recovery, MaterialOwnerAccess.Position expected, Boundary boundary) {
        public Check {
            Objects.requireNonNull(physicalAccess); Objects.requireNonNull(operation);
            Objects.requireNonNull(before); Objects.requireNonNull(after); Objects.requireNonNull(boundary);
            if (globalHash == null || !globalHash.matches("[0-9a-f]{64}")
                    || !physicalAccess.owner().equals(before.owner()) || !before.owner().equals(after.owner())
                    || (expected != null && expected != MaterialOwnerAccess.Position.BEFORE
                        && expected != MaterialOwnerAccess.Position.AFTER))
                throw new IllegalArgumentException("INVALID_LOCATION_GUARD_BINDING");
        }
    }
    /** Throw on mismatch. Returning is evidence only, never permission to observe/mutate. */
    void validate(Check check);
}

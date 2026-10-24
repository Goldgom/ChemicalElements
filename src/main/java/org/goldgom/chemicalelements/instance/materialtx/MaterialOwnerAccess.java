package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import org.goldgom.chemicalelements.api.tx.OwnerKey;

/** Contract for real authoritative owner adapters. No implementation here and no fallback store.
 * Implementations are provided by content (world), thermal (finite heat), and the player adapter.
 * A generic fake Port or in-memory inventory must never be registered as a production adapter.
 */
public interface MaterialOwnerAccess {
    enum Position { BEFORE, AFTER, CONFLICT, UNAVAILABLE }
    OwnerKey owner();
    /** Must not load a chunk or player. */
    boolean isLoaded();
    /** Must report a concrete unsupported read/write alias or lifecycle path, not pretend safety. */
    Optional<String> isolationGap();
    MaterialOwnerSnapshot snapshot();
    /** Identity roots including exposed mutable aliases; resolving does not reserve or mutate. */
    Collection<?> observationRoots();
    /** Caller has reserved all roots. Uses privileged direct inspection, without callbacks.
     * AFTER requires original operation + global hash AND full image/revision equality.
     * During initial commit liveGeneration must match; recovery may accept a newly loaded root
     * only with the same persistent incarnation and verified pending marker/journal lineage. */
    Position classify(UUID operation, String globalHash, MaterialOwnerSnapshot before,
                      MaterialOwnerSnapshot after, boolean recovery);
    /** Persist RESERVED isolation in the original root BEFORE any material projection.
     * Must preserve the complete before image; must not invoke ordinary callbacks. */
    void isolate(MaterialPendingMarker marker);
    /** Atomic original-root replacement + PROJECTED marker in the same persisted root.
     * Multiple setItem/setBlock calls with observer callbacks do not implement this method. */
    void project(MaterialOwnerSnapshot after, MaterialPendingMarker marker);
    /** Synchronous durable barrier for original root + marker (setChanged is insufficient). */
    void persist();
    /** Clear pending only after all images and journal are durably committed. Retain original
     * operation/global hash as the last projection marker for duplicate/recovery classification. */
    void complete(UUID operation, String globalHash);
    /** Cancel only after coordinator established ALL owners BEFORE. */
    void cancelIsolation(UUID operation, String globalHash);
    /** Called only after coherent commit and lease release, never for partial projection.
     * Must be idempotent; never mint items as a side effect of a repeated notification. */
    void notifyCommitted(UUID operation, String globalHash);
}

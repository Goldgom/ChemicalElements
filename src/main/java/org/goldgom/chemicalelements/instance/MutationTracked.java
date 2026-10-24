package org.goldgom.chemicalelements.instance;

/** Implemented on actual mutable game objects by required mixins, never on mirror copies.
 * This is a process-lifetime mutation clock, not a durable projection revision. */
public interface MutationTracked {
    long chemicalelements$mutationRevision();
}

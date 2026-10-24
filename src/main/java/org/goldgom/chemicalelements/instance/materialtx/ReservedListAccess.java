package org.goldgom.chemicalelements.instance.materialtx;

import java.util.List;

/** Required mixin bridge on the ORIGINAL NonNullList. The lease must own this exact list.
 * Only immutable/detached input may be supplied. No callbacks or ordinary getters run during swap.
 * This is an internal projection primitive, not authorization to start a material transaction. */
public interface ReservedListAccess {
    Object[] chemicalelements$rawReferences(MaterialObservationBarrier.Lease lease);
    void chemicalelements$replaceBacking(MaterialObservationBarrier.Lease lease, Object[] detachedValues);
}

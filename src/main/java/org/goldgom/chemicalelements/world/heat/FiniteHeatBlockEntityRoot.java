package org.goldgom.chemicalelements.world.heat;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.data.ChemistryBundle;
import org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerAccess;

/** Implement ONLY on the actual authoritative BE. This interface supplies no store or persistence.
 * Content owner must persist incarnation across loads, rotate it on replacement, and rotate
 * liveGeneration on each load independently of its persistent mutation revision.
 * All ordinary read/write aliases (including block replacement and exposed contents) must be
 * guarded by B's observation barrier and durable pending marker. setChanged is NOT persist().
 */
public interface FiniteHeatBlockEntityRoot {
    UUID persistentIncarnation();
    UUID liveGeneration();
    /** Stable canonical access for the WHOLE material/machine root; all thermal port aliases
     * return this same access and its original observation roots, never a new heat-only owner. */
    MaterialOwnerAccess materialAccess();
    /** Privileged detached evidence from the same root snapshot. No minting or initialization.
     * Its canonical form must occur as canonicalRoot.finite_heat. Unsupported ports reject. */
    FiniteHeatEvidence heatEvidence(ResourceLocation port, ChemistryBundle capturedBundle);
}

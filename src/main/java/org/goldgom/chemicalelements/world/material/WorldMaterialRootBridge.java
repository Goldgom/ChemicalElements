package org.goldgom.chemicalelements.world.material;

import java.util.UUID;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.content.material.tx.MaterialRootState;

/** Implemented by the ORIGINAL MaterialBlockEntity, not a second owner or ledger.
 * Each implementation must consume the explicit permit against `this` before touching root.
 * A replacement is one reference assignment without setChanged/onLoad/neighbour callbacks.
 * Adoption may assign position incarnation only before the new BE is installed in the raw map.
 * The world coordinator validates old/new state and the complete durable B operation first. */
public interface WorldMaterialRootBridge {
    MaterialRootState worldReadRoot(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner,
            UUID operation, String globalHash);
    void worldReplaceRoot(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner,
            UUID operation, String globalHash, MaterialRootState replacement);
}

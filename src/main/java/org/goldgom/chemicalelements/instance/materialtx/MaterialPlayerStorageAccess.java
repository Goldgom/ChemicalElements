package org.goldgom.chemicalelements.instance.materialtx;

import java.io.IOException;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/** Implemented on real PlayerDataStorage. Caller must own the exact player's lease.
 * No vanilla saving event is fired inside isolation. Complete entity serialization stays real.
 */
public interface MaterialPlayerStorageAccess {
    void chemicalelements$saveMaterialPlayer(ServerPlayer player, MaterialObservationBarrier.Lease lease,
                                             ListTag detachedInventory) throws IOException;
}

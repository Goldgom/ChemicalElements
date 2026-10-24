package org.goldgom.chemicalelements.instance.materialtx;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

public final class MaterialPlayerPersistence {
    private MaterialPlayerPersistence() {}
    public static void save(ServerPlayer player, MaterialObservationBarrier.Lease lease,
                            ListTag detachedInventory, Path originalPlayerDirectory) throws IOException {
        if (player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("AUTHORITATIVE_PLAYER_SAVE_THREAD");
        lease.requireRoot(player); lease.requireRoot(player.getInventory());
        var image = ReservedPlayerSerialization.encode(player, lease, detachedInventory);
        MaterialPlayerFilePublication.publish(originalPlayerDirectory, player.getUUID(), image,
                MaterialPlayerFilePublication.SYSTEM);
        lease.requireRoot(player);
    }
}

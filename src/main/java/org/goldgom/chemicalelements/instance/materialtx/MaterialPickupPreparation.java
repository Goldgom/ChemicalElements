package org.goldgom.chemicalelements.instance.materialtx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.content.material.MaterialBlockEntity;
import org.goldgom.chemicalelements.content.material.MaterialProjection;

/** Read-only real pickup admission; never drops an item, clears a BE, or writes a player slot.
 * World root must retain the original unit shell before a lossless destination plan can be built. */
public final class MaterialPickupPreparation {
    private MaterialPickupPreparation() {}
    public record Prepared(ServerPlayer player, BlockPos position, int destinationSlot,
                           MaterialPlayerSnapshot.Evidence playerBefore, MaterialOwnerSnapshot worldBefore,
                           MaterialProjection material) {}
    public static Prepared inspect(ServerPlayer player, BlockPos position, int destinationSlot) {
        var level = player.serverLevel();
        if (!level.getServer().isSameThread() || !player.isAlive() || player.hasDisconnected() || player.isSpectator())
            throw new IllegalStateException("NO_SERVER_AUTHORITY");
        if (destinationSlot < 0 || destinationSlot >= 36) throw new IllegalArgumentException("MAIN_INVENTORY_DESTINATION_REQUIRED");
        if (!level.mayInteract(player, position) || !level.getWorldBorder().isWithinBounds(position))
            throw new IllegalStateException("PICKUP_DENIED");
        // Conservative actual player range check; no client-supplied position can select a distant root.
        if (player.distanceToSqr(position.getX()+.5, position.getY()+.5, position.getZ()+.5) > 36)
            throw new IllegalStateException("PICKUP_OUT_OF_RANGE");
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) throw new IllegalStateException("TARGET_UNLOADED");
        if (!(chunk.getBlockEntities().get(position) instanceof MaterialBlockEntity block))
            throw new IllegalStateException("NOT_MATERIAL_ROOT");
        var world = block.materialAccess().snapshot();
        var material = MaterialProjection.decode(world.canonicalRoot());
        if (material.ledger().instances().isEmpty()) throw new IllegalStateException("EMPTY_MATERIAL_ROOT");
        var before = MaterialPlayerSnapshot.capture(player);
        return new Prepared(player, position.immutable(), destinationSlot, before, world, material);
    }
}

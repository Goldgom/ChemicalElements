package org.goldgom.chemicalelements.world.material;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.goldgom.chemicalelements.api.tx.OwnerKey;

/** Internal bridge: never a general raw getter. One permit is consumed for one exact target. */
public interface WorldChunkAccessBridge {
    /** Identity-only liveness check for an already held BE reference. Returns no root, state,
     * map or NBT and cannot instantiate a pending BE or load a chunk. Safe while reserved. */
    boolean worldContainsBlockEntity(BlockEntity expected);
    BlockEntity worldPeekBlockEntity(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner, UUID operation, String hash, BlockPos pos);
    void worldReplaceBlockEntity(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner, UUID operation, String hash,
            BlockPos pos, BlockEntity expected, BlockEntity replacement);
}

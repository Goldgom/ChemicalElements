package org.goldgom.chemicalelements.world.material;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;

/** Candidate low-level I/O fence only. NOT production-ready persist(): caller must provide
 * validated detached complete chunk NBT, exclude stale autosaves, and retain isolation.
 * Does NOT assert atomic sector/header writes or power-loss durability of external .mcc.
 */
final class ChunkWriteFence {
    private ChunkWriteFence() {}
    static void writeAndForce(ServerLevel level, ChunkPos pos, CompoundTag detached) {
        Objects.requireNonNull(level); Objects.requireNonNull(pos); Objects.requireNonNull(detached);
        if (!level.getServer().isSameThread()) throw new IllegalStateException("SERVER_THREAD_REQUIRED");
        if (detached.getInt("xPos") != pos.x || detached.getInt("zPos") != pos.z)
            throw new IllegalArgumentException("wrong chunk image");
        ChunkStorage storage = level.getChunkSource().chunkMap;
        // Drain pre-existing queued writes before enqueuing the exact image. Never pump the
        // server event loop while waiting: IOWorker uses its own mailbox executor.
        storage.flushWorker();
        // Keep this exact future: a later synchronize alone may miss an already-removed failed write.
        storage.write(pos, detached.copy()).join();
        storage.flushWorker();
        // Any CompletionException must escape to B; no dirty-flag/queue-success fallback.
    }
}

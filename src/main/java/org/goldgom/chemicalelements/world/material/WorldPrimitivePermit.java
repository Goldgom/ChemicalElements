package org.goldgom.chemicalelements.world.material;

import java.util.*;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier;

/** A leaf capability, not admission. Package-only construction requires a separately verified
 * durable B authority at its caller; the in-process reservation alone is deliberately insufficient.
 * There is no ambient bypass, callback execution, or publicly constructible authorization. */
public final class WorldPrimitivePermit {
    public enum Primitive { READ_ROOT, REPLACE_ROOT, REPLACE_SECTION, REPLACE_BE_MAP, ENCODE_ROOT, WRITE_CHUNK }
    private final OwnerKey.WorldBlock owner;
    private final UUID operation;
    private final String hash;
    private final Object originalRoot, target;
    private final Primitive primitive;
    private final Thread thread;
    private final Object reservationIdentity;
    private boolean consumed;
    WorldPrimitivePermit(OwnerKey.WorldBlock owner, UUID operation, String hash,
            Object originalRoot, Object target, Primitive primitive) {
        this.owner = Objects.requireNonNull(owner); this.operation = Objects.requireNonNull(operation);
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("global hash");
        this.hash = hash; this.originalRoot = Objects.requireNonNull(originalRoot);
        this.target = Objects.requireNonNull(target); this.primitive = Objects.requireNonNull(primitive);
        this.thread = Thread.currentThread();
        reservationIdentity = MaterialObservationBarrier.reservationIdentity(originalRoot, operation, hash);
    }
    public synchronized void consume(OwnerKey.WorldBlock expectedOwner, UUID expectedOperation,
            String expectedHash, Object expectedTarget, Primitive expectedPrimitive) {
        if (consumed || thread != Thread.currentThread() || target != expectedTarget || primitive != expectedPrimitive
                || !owner.equals(expectedOwner) || !operation.equals(expectedOperation) || !hash.equals(expectedHash)
                || !MaterialObservationBarrier.isReservedFor(originalRoot, operation, hash, reservationIdentity))
            throw new IllegalStateException("INVALID_WORLD_PRIMITIVE_CAPABILITY");
        consumed = true;
    }

    /** The BE bridge must bind the reservation to the actual root, not merely to an arbitrary
     * reserved object. A new, unpublished BE can inherit a position identity only from that
     * position's real loaded chunk. Neither path confers durable PREPARE authority. */
    public net.minecraft.server.level.ServerLevel consumeMaterialRoot(OwnerKey.WorldBlock expectedOwner,
            UUID expectedOperation, String expectedHash, net.minecraft.world.level.block.entity.BlockEntity root,
            Primitive expectedPrimitive) {
        consume(expectedOwner, expectedOperation, expectedHash, root, expectedPrimitive);
        if (expectedPrimitive != Primitive.READ_ROOT && expectedPrimitive != Primitive.REPLACE_ROOT)
            throw new IllegalArgumentException("NOT_A_MATERIAL_ROOT_PRIMITIVE");
        net.minecraft.world.level.chunk.LevelChunk chunk;
        if (root.getLevel() == null) {
            if (!(originalRoot instanceof net.minecraft.world.level.chunk.LevelChunk originalChunk))
                throw new IllegalStateException("UNPUBLISHED_ROOT_REQUIRES_ORIGINAL_CHUNK");
            chunk = originalChunk;
        } else {
            if (originalRoot != root || !(root.getLevel() instanceof net.minecraft.server.level.ServerLevel level))
                throw new IllegalStateException("FOREIGN_MATERIAL_ORIGINAL_ROOT");
            chunk = level.getChunkSource().getChunkNow(root.getBlockPos().getX() >> 4, root.getBlockPos().getZ() >> 4);
            if (!(chunk instanceof WorldChunkAccessBridge bridge) || !bridge.worldContainsBlockEntity(root))
                throw new IllegalStateException("MATERIAL_ROOT_NO_LONGER_INSTALLED");
        }
        if (!(chunk.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.getServer().isSameThread() || root.isRemoved()
                || !level.dimension().location().equals(expectedOwner.dimension())
                || root.getBlockPos().asLong() != expectedOwner.blockPos()
                || !chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(root.getBlockPos()))
                || !MaterialObservationBarrier.isReservedFor(chunk, expectedOperation, expectedHash, reservationIdentity)
                || !MaterialObservationBarrier.isReservedFor(root, expectedOperation, expectedHash, reservationIdentity))
            throw new IllegalStateException("INVALID_MATERIAL_ROOT_LOCATION_OR_LEASE");
        return level;
    }
}

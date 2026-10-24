package org.goldgom.chemicalelements.world.material;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Frozen full NBT image with independently captured encoding expectations. This validator
 * does not obtain roots using ordinary guarded getters and never catches an omitted BE as success.
 * The raw chunk encoder must supply expectations BEFORE invoking any fallible serializers. */
public final class DetachedChunkImage {
    private final CompoundTag image;
    public DetachedChunkImage(CompoundTag encoded, int chunkX, int chunkZ,
            Map<Long, CompoundTag> expectedBlockEntities, CompoundTag expectedAttachments,
            Map<Long, WorldPositionMetadata> positions) {
        CompoundTag copy = encoded.copy();
        if (!copy.contains("xPos", Tag.TAG_INT) || !copy.contains("zPos", Tag.TAG_INT)
                || copy.getInt("xPos") != chunkX || copy.getInt("zPos") != chunkZ)
            throw new IllegalStateException("MATERIAL_CHUNK_ENCODING_COORDINATES");
        if (!copy.contains("block_entities", Tag.TAG_LIST)) throw new IllegalStateException("MATERIAL_CHUNK_MISSING_BE_LIST");
        Tag listTag = copy.get("block_entities");
        if (!(listTag instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalStateException("MATERIAL_CHUNK_INVALID_BE_LIST");
        Map<Long, CompoundTag> actual = new HashMap<>();
        for (Tag value : list) {
            CompoundTag be = (CompoundTag)value;
            if (!be.contains("x", Tag.TAG_INT) || !be.contains("y", Tag.TAG_INT) || !be.contains("z", Tag.TAG_INT))
                throw new IllegalStateException("MATERIAL_CHUNK_INVALID_BE_POSITION");
            var pos = new BlockPos(be.getInt("x"), be.getInt("y"), be.getInt("z"));
            if ((pos.getX() >> 4) != chunkX || (pos.getZ() >> 4) != chunkZ || actual.put(pos.asLong(), be) != null)
                throw new IllegalStateException("MATERIAL_CHUNK_DUPLICATE_OR_FOREIGN_BE");
        }
        if (!actual.equals(expectedBlockEntities)) throw new IllegalStateException("MATERIAL_CHUNK_OMITTED_OR_CHANGED_BE");
        Tag actualAttachments = copy.get(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
        if (expectedAttachments == null ? actualAttachments != null : !expectedAttachments.equals(actualAttachments))
            throw new IllegalStateException("MATERIAL_CHUNK_ATTACHMENT_ENCODING_FAILED");
        var decoded = WorldPositionMetadata.decodeChunk(copy, chunkX, chunkZ);
        if (!decoded.equals(positions)) throw new IllegalStateException("MATERIAL_CHUNK_POSITION_METADATA_CHANGED");
        for (var metadata : positions.values()) {
            var be = actual.get(metadata.position());
            if (metadata.occupancy() == WorldPositionMetadata.Occupancy.AIR) {
                if (be != null) throw new IllegalStateException("AIR_POSITION_HAS_BLOCK_ENTITY");
            } else {
                if (be == null || !be.contains("material_root", Tag.TAG_COMPOUND))
                    throw new IllegalStateException("MATERIAL_POSITION_LOST_ROOT");
                var root = org.goldgom.chemicalelements.content.material.tx.MaterialRootState.decode(be.getCompound("material_root"));
                String hash = ExternalChunkPublication.digest(root.image().canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (!root.incarnation().equals(metadata.incarnation()) || root.image().revision() != metadata.revision()
                        || !hash.equals(metadata.rootHash())) throw new IllegalStateException("MATERIAL_ROOT_METADATA_MISMATCH");
            }
        }
        this.image = copy;
    }
    public CompoundTag copy() { return image.copy(); }
}

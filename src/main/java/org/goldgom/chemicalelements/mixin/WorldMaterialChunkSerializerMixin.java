package org.goldgom.chemicalelements.mixin;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Ordinary save validation outside vanilla catch blocks. Isolated saves require the separate
 * capability-bound raw encoder; this deliberately does not grant it an ambient bypass. */
@Mixin(ChunkSerializer.class)
public abstract class WorldMaterialChunkSerializerMixin {
    @Inject(method = "write", at = @At("RETURN"))
    private static void ce$strict(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag output = cir.getReturnValue();
        // Recompute through the actual attachment serializer outside its catch. Errors must escape.
        CompoundTag attachments = chunk.writeAttachmentsToNBT(level.registryAccess());
        Tag encoded = output.get(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
        if (attachments == null ? encoded != null : !attachments.equals(encoded))
            throw new IllegalStateException("CHUNK_ATTACHMENT_SERIALIZATION_OMITTED_OR_CHANGED");
        if (!output.contains("block_entities", Tag.TAG_LIST)) throw new IllegalStateException("CHUNK_BE_LIST_MISSING");
        ListTag list = output.getList("block_entities", Tag.TAG_COMPOUND);
        Set<BlockPos> expected = chunk.getBlockEntitiesPos(); Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag be = list.getCompound(i);
            if (!be.contains("x", Tag.TAG_INT) || !be.contains("y", Tag.TAG_INT) || !be.contains("z", Tag.TAG_INT))
                throw new IllegalStateException("CHUNK_BE_COORDINATES_MISSING");
            BlockPos pos = new BlockPos(be.getInt("x"), be.getInt("y"), be.getInt("z"));
            if (!expected.contains(pos) || !seen.add(pos)) throw new IllegalStateException("CHUNK_BE_DUPLICATE_OR_UNKNOWN");
        }
        if (!seen.equals(expected)) throw new IllegalStateException("CHUNK_BE_SERIALIZATION_OMITTED");
    }
}

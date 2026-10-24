package org.goldgom.chemicalelements.mixin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.goldgom.chemicalelements.world.material.WorldRegionStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Entire reader/writer pair is redirected: never mix new external refs with a vanilla reader.
 * Not registered until the complete lifecycle/integration batch is ready. */
@Mixin(RegionFileStorage.class)
public abstract class WorldMaterialRegionStorageMixin {
    @Shadow @Final private Path folder;
    @Unique private final Set<WorldRegionStorage> ce$regions = Collections.newSetFromMap(new IdentityHashMap<>());
    @Unique private synchronized WorldRegionStorage ce$region(ChunkPos pos) throws IOException {
        var region = WorldRegionStorage.at(folder, pos); ce$regions.add(region); return region;
    }
    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void ce$read(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) throws IOException {
        cir.setReturnValue(ce$region(pos).read(pos));
    }
    @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true)
    private void ce$scan(ChunkPos pos, StreamTagVisitor visitor, CallbackInfo ci) throws IOException {
        ce$region(pos).scan(pos, visitor); ci.cancel();
    }
    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void ce$write(ChunkPos pos, CompoundTag tag, CallbackInfo ci) throws IOException {
        ce$region(pos).write(pos, tag); ci.cancel();
    }
    @Inject(method = {"flush", "close"}, at = @At("HEAD"))
    private synchronized void ce$healthy(CallbackInfo ci) throws IOException {
        for (var region : ce$regions) region.requireHealthy();
    }
}

package org.goldgom.chemicalelements.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.goldgom.chemicalelements.world.material.WorldChunkBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class WorldMaterialChunkPacketMixin {
    @Inject(method = "calculateChunkSize", at = @At("HEAD"))
    private static void ce$size(LevelChunk chunk, CallbackInfoReturnable<Integer> cir) { WorldChunkBoundary.requireObservable(chunk); }
    @Inject(method = "extractChunkData", at = @At("HEAD"))
    private static void ce$extract(FriendlyByteBuf buffer, LevelChunk chunk, CallbackInfo ci) { WorldChunkBoundary.requireObservable(chunk); }
}

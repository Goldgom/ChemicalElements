package org.goldgom.chemicalelements.mixin;

import java.io.File;
import java.io.IOException;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.goldgom.chemicalelements.instance.materialtx.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerDataStorage.class)
public abstract class MaterialPlayerDataStorageMixin implements MaterialPlayerStorageAccess {
    @Shadow @Final private File playerDir;
    @Override public void chemicalelements$saveMaterialPlayer(ServerPlayer player,
            MaterialObservationBarrier.Lease lease, ListTag detachedInventory) throws IOException {
        MaterialPlayerPersistence.save(player, lease, detachedInventory, playerDir.toPath());
    }
    /** Outside vanilla's catch: ordinary save cannot silently encode an isolated player. */
    @Inject(method="save", at=@At("HEAD"))
    private void chemicalelements$rejectIsolatedOrdinarySave(Player player, CallbackInfo ci) {
        MaterialObservationBarrier.requireObservable(player);
        MaterialObservationBarrier.requireObservable(player.getInventory());
    }
}

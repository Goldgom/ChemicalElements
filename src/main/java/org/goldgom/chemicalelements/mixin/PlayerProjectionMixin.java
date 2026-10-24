package org.goldgom.chemicalelements.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.goldgom.chemicalelements.instance.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerProjectionMixin implements PlayerProjectionAccess {
    @Unique private final PlayerProjectionState chemicalelements$projection = new PlayerProjectionState();
    @Unique private org.goldgom.chemicalelements.instance.materialtx.ReservedPlayerSerialization chemicalelements$serializer;
    @Override public PlayerProjectionState chemicalelements$projectionState() { return chemicalelements$projection; }
    @Override public void chemicalelements$beginMaterialSerialization(
            org.goldgom.chemicalelements.instance.materialtx.ReservedPlayerSerialization capability) {
        capability.requirePlayer(this);
        if (chemicalelements$serializer != null) throw new IllegalStateException("REENTRANT_PLAYER_SERIALIZATION");
        chemicalelements$serializer = capability;
    }
    @Override public void chemicalelements$endMaterialSerialization(
            org.goldgom.chemicalelements.instance.materialtx.ReservedPlayerSerialization capability) {
        if (chemicalelements$serializer != capability) throw new IllegalStateException("PLAYER_SERIALIZATION_CAPABILITY_LOST");
        chemicalelements$serializer = null;
    }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="addAdditionalSaveData", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/player/Inventory;save(Lnet/minecraft/nbt/ListTag;)Lnet/minecraft/nbt/ListTag;"))
    private net.minecraft.nbt.ListTag chemicalelements$serializeInventory(net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.nbt.ListTag list, com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.nbt.ListTag> original,
            CompoundTag target) {
        var capability = chemicalelements$serializer;
        if (capability == null) return original.call(inventory, list);
        return capability.inventoryFor(this, inventory, target);
    }
    @Inject(method="readAdditionalSaveData", at=@At("RETURN"))
    private void chemicalelements$readProjection(CompoundTag tag, CallbackInfo ci) { chemicalelements$projection.read(tag); }
    @Inject(method="addAdditionalSaveData", at=@At("RETURN"))
    private void chemicalelements$writeProjection(CompoundTag tag, CallbackInfo ci) { chemicalelements$projection.write(tag); }
}

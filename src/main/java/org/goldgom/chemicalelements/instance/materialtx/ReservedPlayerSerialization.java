package org.goldgom.chemicalelements.instance.materialtx;

import java.util.Objects;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.instance.PlayerProjectionAccess;

/** A single-use, exact-target serialization capability. No ThreadLocal or ordinary-access bypass.
 * Inventory NBT is detached and encoded before reservation. All other player fields are encoded
 * through the real full saveWithoutId path; callbacks still encounter the original barriers.
 */
public final class ReservedPlayerSerialization {
    private final ServerPlayer player;
    private final MaterialObservationBarrier.Lease lease;
    private final CompoundTag target = new CompoundTag();
    private final ListTag inventory;
    private boolean consumed;
    private ReservedPlayerSerialization(ServerPlayer player, MaterialObservationBarrier.Lease lease, ListTag inventory) {
        this.player = player; this.lease = lease; this.inventory = inventory.copy();
    }
    static CompoundTag encode(ServerPlayer player, MaterialObservationBarrier.Lease lease, ListTag inventory) {
        lease.requireRoot(player); lease.requireRoot(player.getInventory());
        if (!((Object) player instanceof MaterialAttachmentEncodingAccess attachmentAudit))
            throw new IllegalStateException("STRICT_PLAYER_ATTACHMENT_ENCODING_BRIDGE_REQUIRED");
        var capability = new ReservedPlayerSerialization(player, lease, inventory);
        var bridge = (PlayerProjectionAccess) player;
        bridge.chemicalelements$beginMaterialSerialization(capability);
        try {
            attachmentAudit.chemicalelements$beginAttachmentAudit(capability);
            var result = player.saveWithoutId(capability.target);
            attachmentAudit.chemicalelements$finishAttachmentAudit(capability);
            if (result != capability.target || !capability.consumed
                    || !result.getList("Inventory", Tag.TAG_COMPOUND).equals(capability.inventory))
                throw new IllegalStateException("PLAYER_SERIALIZATION_IMAGE_MISMATCH");
            lease.requireRoot(player); lease.requireRoot(player.getInventory());
            return result.copy();
        } finally {
            try { attachmentAudit.chemicalelements$abortAttachmentAudit(capability); }
            finally { bridge.chemicalelements$endMaterialSerialization(capability); }
        }
    }
    /** Called only at the exact Player.addAdditionalSaveData inventory.save invocation.
     * Reentrant save to another tag cannot consume or reuse the capability. */
    public ListTag inventoryFor(Object actualPlayer, Object actualInventory, CompoundTag actualTarget) {
        if (actualPlayer != player || actualInventory != player.getInventory() || actualTarget != target || consumed)
            throw new IllegalStateException("FOREIGN_OR_REENTRANT_PLAYER_SERIALIZATION");
        lease.requireRoot(player); lease.requireRoot(actualInventory);
        consumed = true;
        return inventory.copy();
    }
    public void requirePlayer(Object actualPlayer) {
        if (actualPlayer != player) throw new IllegalArgumentException("FOREIGN_PLAYER_SERIALIZER");
        lease.requireRoot(player);
    }
}

package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.goldgom.chemicalelements.content.material.MaterialAdmission;
import org.goldgom.chemicalelements.content.material.MaterialBlockItem;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;

/** Real server entry preparation, not commit/permission to place. Never creates a block or shrinks
 * input. Rejects unobserved authority, unsupported models and unloaded positions with no mutation. */
public final class MaterialPlacementPreparation {
    private MaterialPlacementPreparation() {}
    public record Prepared(ServerPlayer player, ServerLevel level, BlockPos position, int slot,
                           MaterialAdmission.Inspection admission, MaterialPlayerSnapshot.Evidence playerBefore,
                           MaterialPlayerSlotPlan.Removed removal) {}
    public static Prepared inspect(BlockPlaceContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)
                || !level.getServer().isSameThread() || player.serverLevel() != level || !player.isAlive()
                || player.hasDisconnected() || player.isSpectator()) throw new IllegalStateException("NO_SERVER_AUTHORITY");
        var pos = context.getClickedPos();
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
            throw new IllegalStateException("TARGET_UNLOADED");
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())
                || !level.getWorldBorder().isWithinBounds(pos)) throw new IllegalStateException("PLACEMENT_DENIED");
        int slot = MaterialPlayerSnapshot.handSlot(player, context.getHand());
        var actual = player.getInventory().getItem(slot);
        if (actual != context.getItemInHand() || !(actual.getItem() instanceof MaterialBlockItem material))
            throw new IllegalStateException("FOREIGN_HAND_STACK");
        if (!context.canPlace()) throw new IllegalStateException("TARGET_OCCUPIED");
        var admission = MaterialAdmission.inspect(level, actual, material.target());
        if (admission.status() != MaterialAdmission.Status.TRANSACTION_REQUIRED)
            throw new IllegalStateException("MATERIAL_ADMISSION:" + admission.status());
        var before = MaterialPlayerSnapshot.capture(player);
        var removed = MaterialPlayerSlotPlan.removeHead(before.snapshot(), slot, player.registryAccess());
        if (!admission.matches(actual, AtomicChemistryCatalog.INSTANCE.bundle())
                || !org.goldgom.chemicalelements.instance.PlayerMutationVersion.matches(player.getInventory(), before.mutation()))
            throw new IllegalStateException("STALE_PLACEMENT_PREPARATION");
        return new Prepared(player, level, pos.immutable(), slot, admission, before, removed);
    }
    /** Repeat before acquiring the observation lease; no cached permission. AFTER PREPARE the
     * coordinator must use privileged direct owner classification plus separate permission checks,
     * not this ordinary inventory-reading method (which must remain blocked by the lease). */
    public static void revalidate(Prepared prepared, BlockPlaceContext context) {
        var fresh = inspect(context);
        if (fresh.player() != prepared.player() || fresh.level() != prepared.level()
                || !fresh.position().equals(prepared.position()) || fresh.slot() != prepared.slot()
                || fresh.admission().bundle() != prepared.admission().bundle()
                || !fresh.playerBefore().equals(prepared.playerBefore())
                || !fresh.removal().equals(prepared.removal())) throw new IllegalStateException("PLACEMENT_CHANGED");
    }
}

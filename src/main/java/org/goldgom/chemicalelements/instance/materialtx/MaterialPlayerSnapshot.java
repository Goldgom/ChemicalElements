package org.goldgom.chemicalelements.instance.materialtx;

import com.google.gson.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.instance.*;

/** Reads the actual authoritative inventory; no second inventory and no commit permission. */
public final class MaterialPlayerSnapshot {
    private MaterialPlayerSnapshot() {}
    public record Evidence(MaterialOwnerSnapshot snapshot, PlayerMutationVersion.Version mutation,
                           PlayerProjectionState.Marker projection) {}
    public static Evidence capture(ServerPlayer player) {
        if (!(player instanceof PlayerProjectionAccess access)) throw new IllegalStateException("PLAYER_PROJECTION_MIXIN_UNAVAILABLE");
        var state = access.chemicalelements$projectionState();
        if (state.readOnly() || state.materialIsolated()) throw new IllegalStateException("PLAYER_MATERIAL_ISOLATED");
        var persistent = state.marker();
        var live = PlayerOwnerSnapshot.capture(player);
        var root = new JsonObject(); root.addProperty("version", 1);
        root.addProperty("persistent_incarnation", persistent.generation().toString());
        root.addProperty("revision", persistent.revision());
        // Preserve inventory mirror including complete stack components and full ordered ledgers.
        // Live mutation revision is evidence for initial CAS, not durable identity or projection revision.
        var inventory = JsonParser.parseString(live.canonicalMirror()).getAsJsonObject();
        inventory.addProperty("revision", persistent.revision()); root.add("inventory", inventory);
        String canonical = TransactionEncoding.canonical(root);
        if (!persistent.equals(state.marker()) || state.materialIsolated()
                || !PlayerMutationVersion.matches(player.getInventory(), live.version()))
            throw new IllegalStateException("PLAYER_CHANGED_DURING_MATERIAL_SNAPSHOT");
        return new Evidence(MaterialOwnerSnapshot.of(live.owner(), live.version().generation(), persistent.revision(), canonical),
                live.version(), persistent);
    }
    /** Resolve the exact original authority slot; hand stack objects alone are not authority. */
    public static int handSlot(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (player.getServer() == null || !player.getServer().isSameThread() || player.hasDisconnected() || !player.isAlive())
            throw new IllegalStateException("PLAYER_UNAVAILABLE");
        int slot = hand == net.minecraft.world.InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) throw new IllegalStateException("INVALID_HAND_SLOT");
        return slot;
    }
}

package org.goldgom.chemicalelements.instance;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** A full, server-resolved player snapshot. It is not a journal receipt or permission to project.
 * Mirror.revision here is the live mutation revision, NOT the persisted player projection revision.
 * Encoding round-trip detaches component values as well as the stack shell; raw ItemStack.copy alone
 * is insufficient for third-party mutable component payloads. Missing/unreadable ledgers fail closed.
 */
public record PlayerOwnerSnapshot(OwnerKey.PlayerInventory owner, PlayerMutationVersion.Version version,
                                  String canonicalMirror, String digest) {
    public PlayerOwnerSnapshot {
        Objects.requireNonNull(owner); Objects.requireNonNull(version); Objects.requireNonNull(canonicalMirror);
        if (!TransactionEncoding.sha256(canonicalMirror).equals(digest)) throw new IllegalArgumentException("snapshot digest");
    }
    public static PlayerOwnerSnapshot capture(ServerPlayer player) {
        if (player.hasDisconnected() || !player.isAlive()) throw new IllegalStateException("PLAYER_UNAVAILABLE");
        var inventory = player.getInventory();
        var version = PlayerMutationVersion.capture(inventory);
        var slots = new ArrayList<SlotImage>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i).copy();
            var instances = stack.isEmpty() ? List.<org.goldgom.chemicalelements.api.instance.InstanceRecord>of()
                    : StackLedgers.get(stack).orElseThrow(() -> new IllegalStateException("UNRESOLVED_LEDGER")).instances();
            slots.add(new SlotImage(i, stack, instances));
        }
        var mirror = new Mirror(version.revision(), slots, List.of(),
                new EnergyImage(BigInteger.ZERO, BigInteger.ZERO, 0), new ControlImage("player_inventory", 0, 0, Map.of()));
        var encoded = TransactionEncoding.mirror(mirror, player.registryAccess());
        var decoded = TransactionEncoding.decodeMirror(encoded, player.registryAccess());
        if (!TransactionEncoding.mirror(decoded, player.registryAccess()).equals(encoded))
            throw new IllegalStateException("LOSSY_OWNER_CODEC");
        if (!PlayerMutationVersion.matches(inventory, version)) throw new IllegalStateException("OWNER_MUTATED_DURING_READ");
        return new PlayerOwnerSnapshot(new OwnerKey.PlayerInventory(player.getUUID()), version, encoded,
                TransactionEncoding.sha256(encoded));
    }
    /** Every access decodes an independent full image, so callers cannot mutate retained evidence. */
    public Mirror image(ServerPlayer player) {
        if (!owner.player().equals(player.getUUID())) throw new IllegalArgumentException("foreign player");
        return TransactionEncoding.decodeMirror(canonicalMirror, player.registryAccess());
    }
    public boolean stillCurrent(ServerPlayer player) {
        if (!owner.player().equals(player.getUUID()) || player.hasDisconnected() || !player.isAlive()) return false;
        var current = capture(player);
        return version.equals(current.version) && digest.equals(current.digest);
    }
}

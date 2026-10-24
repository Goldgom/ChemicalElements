package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import org.goldgom.chemicalelements.instance.PlayerProjectionAccess;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialPlayerSerializationGameTests {
    private MaterialPlayerSerializationGameTests() {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    @GameTest(template="core_empty")
    public static void swallowedAttachmentFailureRejectsFullImage(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var inventory = player.getInventory();
        var list = inventory.save(new ListTag());
        var type = org.goldgom.chemicalelements.persistence.ChemistryComponents.CHUNK.get();
        // Use a registered type with an intentionally wrong payload: its real serializer
        // throws ClassCastException inside NeoForge's catch-and-skip loop.
        player.setData((net.neoforged.neoforge.attachment.AttachmentType) type, new Object());
        var state = ((PlayerProjectionAccess) player).chemicalelements$projectionState();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "e".repeat(64),
                List.of(player, inventory, inventory.items, inventory.armor, inventory.offhand, state));
        try {
            boolean failed = false;
            try { ReservedPlayerSerialization.encode(player, lease, list); }
            catch (ClassCastException expected) { failed = true; }
            h.assertTrue(failed, "swallowed attachment exception must reject the entire image");
        } finally {
            lease.releaseUnprojected();
            player.removeData(type);
        }
        // The failed capability must be cleared so the next legitimate save can proceed.
        var next = MaterialObservationBarrier.reserve(UUID.randomUUID(), "f".repeat(64),
                List.of(player, inventory, inventory.items, inventory.armor, inventory.offhand, state));
        try { ReservedPlayerSerialization.encode(player, next, list); }
        finally { next.releaseUnprojected(); }
        h.succeed();
    }
    @GameTest(template="core_empty")
    public static void fullSerializationUsesExactInventoryBridge(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var inventory = player.getInventory();
        var expected = player.saveWithoutId(new CompoundTag());
        var list = inventory.save(new ListTag());
        var state = ((PlayerProjectionAccess) player).chemicalelements$projectionState();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "d".repeat(64),
                List.of(player, inventory, inventory.items, inventory.armor, inventory.offhand, state));
        try {
            var actual = ReservedPlayerSerialization.encode(player, lease, list);
            h.assertTrue(actual.equals(expected), "full player NBT fields preserved under inventory reservation");
            boolean blocked = false;
            try { inventory.getItem(0); } catch (IllegalStateException expectedFailure) { blocked = true; }
            h.assertTrue(blocked, "serialization must not open ordinary inventory observation");
            boolean ordinaryFailed = false;
            try { player.saveWithoutId(new CompoundTag()); } catch (RuntimeException expectedFailure) { ordinaryFailed = true; }
            h.assertTrue(ordinaryFailed, "ordinary reentrant serialization has no capability");
        } finally { lease.releaseUnprojected(); }
        h.succeed();
    }
}

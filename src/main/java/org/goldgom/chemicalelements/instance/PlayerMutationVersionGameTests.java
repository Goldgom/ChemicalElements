package org.goldgom.chemicalelements.instance;

import net.minecraft.gametest.framework.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/** Real Inventory and ItemStack mixins, not fake Owners. Source-only until integration executes it. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class PlayerMutationVersionGameTests {
    @GameTest(template="core_empty", timeoutTicks=40)
    public static void realPlayerListAndStackABA(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var inv = player.getInventory();
        inv.setItem(0, new ItemStack(Items.STONE, 2));
        var original = inv.getItem(0);
        var initial = PlayerMutationVersion.capture(inv);
        h.assertTrue(PlayerMutationVersion.matches(inv, initial), "unchanged inventory version stable");
        original.setCount(1); original.setCount(2);
        h.assertTrue(!PlayerMutationVersion.matches(inv, initial), "count ABA invalidates original snapshot");
        var count = PlayerMutationVersion.capture(inv);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("temporary"));
        original.remove(DataComponents.CUSTOM_NAME);
        h.assertTrue(!PlayerMutationVersion.matches(inv, count), "component ABA invalidates snapshot");
        var mapVersion = PlayerMutationVersion.capture(inv);
        var map = (net.minecraft.core.component.PatchedDataComponentMap) original.getComponents();
        map.set(DataComponents.CUSTOM_NAME, Component.literal("direct-map"));
        map.remove(DataComponents.CUSTOM_NAME);
        h.assertTrue(!PlayerMutationVersion.matches(inv, mapVersion), "direct component-map ABA invalidates snapshot");
        var component = PlayerMutationVersion.capture(inv);
        inv.items.set(0, new ItemStack(Items.DIRT)); inv.items.set(0, original);
        h.assertTrue(!PlayerMutationVersion.matches(inv, component), "public backing-list ABA invalidates snapshot");
        var list = PlayerMutationVersion.capture(inv);
        var copy = original.copy(); copy.setCount(1);
        h.assertTrue(PlayerMutationVersion.matches(inv, list), "detached copy mutation does not invalidate owner");
        inv.armor.set(0, new ItemStack(Items.IRON_HELMET)); inv.armor.set(0, ItemStack.EMPTY);
        h.assertTrue(!PlayerMutationVersion.matches(inv, list), "hidden armor ABA included");
        var other = h.makeMockServerPlayerInLevel();
        var snapshot = PlayerOwnerSnapshot.capture(other);
        h.assertTrue(snapshot.stillCurrent(other), "complete empty player snapshot current");
        h.assertTrue(snapshot.image(other).slots().size() == other.getInventory().getContainerSize(), "all player slots captured");
        other.getInventory().items.set(0, new ItemStack(Items.DIRT));
        other.getInventory().items.set(0, ItemStack.EMPTY);
        h.assertTrue(!snapshot.stillCurrent(other), "same full digest after ABA does not authorize snapshot");
        h.assertTrue(!PlayerMutationVersion.capture(other.getInventory()).generation().equals(initial.generation()),
                "different Inventory incarnation cannot reuse generation");
        h.assertTrue(inv.getItem(0) == original && original.getCount() == 2, "version reads do not project or consume input");
        var saved = new net.minecraft.nbt.CompoundTag();
        other.addAdditionalSaveData(saved);
        h.assertTrue(saved.contains(PlayerProjectionState.KEY), "real player save contains projection metadata");
        var metadata = new PlayerProjectionState(); metadata.read(saved);
        var resaved = new net.minecraft.nbt.CompoundTag(); metadata.write(resaved);
        h.assertTrue(saved.getCompound(PlayerProjectionState.KEY).equals(resaved.getCompound(PlayerProjectionState.KEY)),
                "projection lineage survives NBT roundtrip");
        var damaged = saved.copy(); damaged.getCompound(PlayerProjectionState.KEY).putLong("revision", -1);
        metadata.read(damaged); metadata.write(resaved);
        h.assertTrue(metadata.readOnly() && damaged.getCompound(PlayerProjectionState.KEY).equals(resaved.getCompound(PlayerProjectionState.KEY)),
                "bad projection metadata preserved read-only, never reset");
        h.succeed();
    }
}

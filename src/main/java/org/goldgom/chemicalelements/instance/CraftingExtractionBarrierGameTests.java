package org.goldgom.chemicalelements.instance;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** SOURCE ONLY: real vanilla menu entry tests; not success-CAS evidence. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class CraftingExtractionBarrierGameTests {
    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void inventoryResultCannotConsumeBeforeCommit(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var menu = player.inventoryMenu;
        player.containerMenu = menu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 2));
        var input = menu.getSlot(1).getItem().copy();
        var output = menu.getSlot(0).getItem().copy();
        h.assertTrue(!output.isEmpty(), "real registered log recipe produces preview");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "direct quick move blocked");
        h.assertTrue(menu.getSlot(0).remove(1).isEmpty(), "direct result remove blocked");
        menu.getSlot(0).onTake(player, output.copy());
        h.assertTrue(ItemStack.matches(input, menu.getSlot(1).getItem()), "all paths retain actual input");
        h.assertTrue(ItemStack.matches(output, menu.getSlot(0).getItem()), "actual preview retained");
        h.assertTrue(menu.getCarried().isEmpty() && player.getInventory().isEmpty(), "no delivered product");
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void actualGridTracksABAAndMenuExpiry(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var menu = player.inventoryMenu;
        player.containerMenu = menu;
        var grid = (net.minecraft.world.inventory.CraftingContainer) menu.getSlot(1).container;
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 2));
        var original = grid.getItem(0);
        var first = CraftingGridVersion.capture(player, grid);
        grid.setItem(0, ItemStack.EMPTY);
        grid.setItem(0, original);
        var second = CraftingGridVersion.capture(player, grid);
        h.assertTrue(first.generation().equals(second.generation()) && first.revision() != second.revision(),
                "original backing list detects replace-restore ABA");
        original.shrink(1); original.grow(1);
        var third = CraftingGridVersion.capture(player, grid);
        h.assertTrue(second.revision() != third.revision(), "in-place count ABA detected");
        player.containerMenu = new CraftingMenu(74, player.getInventory());
        boolean expired = false;
        try { CraftingGridVersion.capture(player, grid); } catch (IllegalStateException expected) { expired = true; }
        h.assertTrue(expired, "old grid rejected after menu identity changes");
        player.containerMenu = menu;
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void craftingTableQuickMoveCannotBypassBarrier(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var menu = new CraftingMenu(73, player.getInventory(),
                ContainerLevelAccess.create(h.getLevel(), h.absolutePos(net.minecraft.core.BlockPos.ZERO)));
        player.containerMenu = menu;
        // Bottom-right placement exercises trimmed recipe positioning, not assumed slot zero.
        menu.getSlot(9).set(new ItemStack(Items.OAK_LOG, 3));
        var input = menu.getSlot(9).getItem().copy();
        var output = menu.getSlot(0).getItem().copy();
        h.assertTrue(!output.isEmpty(), "real recipe preview in 3x3 menu");
        for (int i = 0; i < 3; i++)
            h.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "each direct extraction rejected");
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        h.assertTrue(ItemStack.matches(input, menu.getSlot(9).getItem()), "quick loop retains inputs");
        h.assertTrue(ItemStack.matches(output, menu.getSlot(0).getItem()), "no preview movement");
        h.assertTrue(player.getInventory().isEmpty(), "no product leaked to player");
        player.containerMenu = player.inventoryMenu;
        h.succeed();
    }
}

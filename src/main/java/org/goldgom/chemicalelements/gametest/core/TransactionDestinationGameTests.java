package org.goldgom.chemicalelements.gametest.core;

import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.tx.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class TransactionDestinationGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void destinationValues(GameTestHelper helper) {
        var player = new UUID(1, 2);
        var session = new UUID(3, 4);
        var type = ResourceLocation.parse("chemicalelements:crafting");
        var value = new PlayerMenuDestination(player, session, type, 0, Long.MAX_VALUE);
        helper.assertTrue(value.equals(new PlayerMenuDestination(player, session, type, 0, Long.MAX_VALUE)), "Stable value equality");
        helper.assertTrue(!value.equals(new PlayerMenuDestination(player, new UUID(3, 5), type, 0, Long.MAX_VALUE)), "Session binds identity");
        helper.assertTrue(!value.equals(new PlayerMenuDestination(player, session, type, 1, Long.MAX_VALUE)), "Layout binds identity");
        helper.assertTrue(!value.equals(new PlayerMenuDestination(player, session, type, 0, 1)), "Owner revision binds identity");
        int rejected = 0;
        try { new PlayerMenuDestination(null, session, type, 0, 0); } catch (NullPointerException expected) { rejected++; }
        try { new PlayerMenuDestination(player, null, type, 0, 0); } catch (NullPointerException expected) { rejected++; }
        try { new PlayerMenuDestination(player, session, null, 0, 0); } catch (NullPointerException expected) { rejected++; }
        try { new PlayerMenuDestination(player, session, type, -1, 0); } catch (IllegalArgumentException expected) { rejected++; }
        try { new PlayerMenuDestination(player, session, type, 0, -1); } catch (IllegalArgumentException expected) { rejected++; }
        helper.assertTrue(rejected == 5, "Invalid player destination rejected");
        TransactionDestination machine = new MachineDestination(ResourceLocation.parse("minecraft:overworld"), Long.MIN_VALUE, session, type);
        helper.assertTrue(!machine.equals(value), "Machine never impersonates player");
        helper.succeed();
    }
}

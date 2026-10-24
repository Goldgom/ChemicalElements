package org.goldgom.chemicalelements.gametest.instance;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.instance.*;
import org.goldgom.chemicalelements.machine.MachineServices;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class ServiceIntegrationGameTests {
    @GameTest(template="core_empty")
    public static void realLedgerQueriesInstalledAtStartup(GameTestHelper helper) {
        var services = MachineServices.current().orElseThrow();
        helper.assertTrue(services.stacks() == InstanceStackChemistry.INSTANCE, "Real instance service installed");
        helper.assertTrue(services.settlement() == ProductSettlement.INSTANCE, "Pure settlement preview installed");
        helper.assertTrue(services.catalog().get() == AtomicChemistryCatalog.INSTANCE.snapshot(), "Current atomic catalog");
        var stack = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(services.stacks().snapshot(stack).isEmpty(), "Read must not mint a nominal instance");
        var ledger = StackLedgers.resolveObserved(stack, "service_integration_test");
        helper.assertTrue(services.stacks().snapshot(stack).orElseThrow().equals(ledger), "Query reads real ledger");
        helper.succeed();
    }
}

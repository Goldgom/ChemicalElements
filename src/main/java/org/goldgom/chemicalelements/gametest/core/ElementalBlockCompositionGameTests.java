package org.goldgom.chemicalelements.gametest.core;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.instance.InstanceSampler;
import org.goldgom.chemicalelements.registry.ElementItems;
import org.goldgom.chemicalelements.registry.ElementMaterials;

/** Real loaded resources and real registered stacks; no fixture catalog or live reload mutation. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class ElementalBlockCompositionGameTests {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("chemicalelements", path);
    }

    private static CreationContext context(long epoch) {
        // Actual mint path, not simulation. Only creates test-local ledgers; never inserts inventory.
        return new CreationContext(new Provenance("gametest", "elemental-default-r23", 23L,
                epoch, Map.of()), Optional.empty(), false);
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void allRegisteredElementalBlocksMintExactAtomMol(GameTestHelper helper) {
        var catalog = AtomicChemistryCatalog.INSTANCE.snapshot();
        helper.assertTrue(catalog.elements().size() == 118 && ElementItems.NAMES.size() == 118,
                "Require all 118 loaded elements and registrations");
        var names = new HashSet<>(ElementItems.NAMES);
        helper.assertTrue(names.size() == 118 && names.equals(catalog.elements().keySet().stream()
                .map(ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet())), "Name coverage");
        var allIds = new HashSet<UUID>();
        for (String name : ElementItems.NAMES) {
            var itemId = id("elemental_" + name);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(itemId), "Registered item: " + itemId);
            var item = ElementMaterials.BLOCK_ITEMS.get("elemental_" + name).get();
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(item).equals(itemId), "Actual registry identity: " + name);
            helper.assertTrue(item instanceof BlockItem && ((BlockItem)item).getBlock() == ElementMaterials.CONTENT.get("elemental_" + name).get(), "Real BlockItem/block identity: " + name);
            var expectedSpecies = id(name.equals("hydrogen") || name.equals("oxygen") ? name + "_gas" : name);
            var expectedAmount = MolAmount.parseDecimal(name.equals("hydrogen") || name.equals("oxygen") ? "0.5" : "1");
            var expected = new Composition(Map.of(expectedSpecies, expectedAmount));
            helper.assertTrue(expected.equals(catalog.itemCompositions().get(itemId)), "Loaded default: " + name);
            for (int count : new int[]{1, 2, 64}) {
                var stack = new ItemStack(item, count);
                var before = stack.copy();
                // This overload obtains AtomicChemistryCatalog.INSTANCE internally.
                var ledger = InstanceSampler.mint(stack, stack.getCount(), context(catalog.epoch()));
                ledger.validateCount(count);
                helper.assertTrue(stack.getCount() == count && ItemStack.isSameItemSameComponents(stack, before),
                        "Sampling must not mutate stack: " + name);
                for (var record : ledger.instances()) {
                    helper.assertTrue(allIds.add(record.id()), "Unique minted identity: " + name);
                    helper.assertTrue(!record.remaining().equals(Composition.EMPTY) && record.remaining().equals(expected),
                            "Non-empty exact species quantity: " + name);
                    var inventory = record.remaining().inventory(catalog);
                    helper.assertTrue(inventory.atoms().equals(Map.of(id(name), MolAmount.ONE.quanta()))
                            && inventory.charge().signum() == 0, "Exactly one neutral atom-mol: " + name);
                    helper.assertTrue(record.origin().registryEpoch() == catalog.epoch()
                            && !record.origin().metadata().containsKey("composition"), "Modeled provenance: " + name);
                }
                var total = ledger.remaining().inventory(catalog);
                helper.assertTrue(total.atoms().equals(Map.of(id(name), MolAmount.ONE.quanta().multiply(BigInteger.valueOf(count))))
                        && total.charge().signum() == 0, "Complete stack atom inventory: " + name + "/" + count);
            }
        }
        helper.assertTrue(allIds.size() == 118 * 67, "Every instance checked across 1/2/64 stacks");
        helper.assertTrue(AtomicChemistryCatalog.INSTANCE.snapshot() == catalog, "Test must not replace live catalog");
        helper.succeed();
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void unmappedCarrierRemainsExplicitlyUnmodeled(GameTestHelper helper) {
        var catalog = AtomicChemistryCatalog.INSTANCE.snapshot();
        for (String form : ElementMaterials.CARRIER_FORMS) {
            var carrierId = id("mixture_carrier_" + form);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(carrierId), "Registered carrier: " + form);
            var carrier = ElementMaterials.BLOCK_ITEMS.get("mixture_carrier_" + form).get();
            helper.assertTrue(carrier instanceof BlockItem && BuiltInRegistries.ITEM.getKey(carrier).equals(carrierId), "Real carrier identity");
            helper.assertTrue(!catalog.itemCompositions().containsKey(BuiltInRegistries.ITEM.getKey(carrier)),
                    "Generic carrier must have no invented default matter");
            var stack = new ItemStack(carrier, 2);
            var ledger = InstanceSampler.mint(stack, stack.getCount(), context(catalog.epoch()));
            ledger.validateCount(2);
            for (var record : ledger.instances()) {
                helper.assertTrue(record.remaining().equals(Composition.EMPTY)
                        && "unmodeled".equals(record.origin().metadata().get("composition")), "Unknown is empty and explicit");
            }
            helper.assertTrue(ledger.remaining().inventory(catalog).atoms().isEmpty(), "No invented atoms");
            helper.assertTrue(InstanceSampler.mint(new ItemStack(carrier), 0, context(catalog.epoch())).instances().isEmpty(),
                    "Zero requested instances creates no matter");
            helper.assertTrue(stack.getCount() == 2 && AtomicChemistryCatalog.INSTANCE.snapshot() == catalog,
                    "Unknown sampling preserves stack and catalog");
        }
        helper.succeed();
    }
}

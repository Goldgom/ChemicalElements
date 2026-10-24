package org.goldgom.chemicalelements.gametest.core;

import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.core.VanillaCraftingAdmission;
import org.goldgom.chemicalelements.core.VanillaCraftingAdmission.Status;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.persistence.*;

/** Actual server RecipeManager + component inputs. Fixtures are explicit test sources,
 * not production minting; no successful CAS or full 1290 coverage is claimed here. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class VanillaCraftingAdmissionGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realPackingAdmissionLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = recipe(helper, "iron_block");
        var slots = new ArrayList<ItemStack>();
        for (int i = 0; i < 9; i++) slots.add(fixture(Items.IRON_INGOT, i + 1));
        var input = CraftingInput.of(3, 3, slots);
        var before = slots.stream().map(ItemStack::copy).toList();
        long epoch = AtomicChemistryCatalog.INSTANCE.snapshot().epoch();
        var a = VanillaCraftingAdmission.inspect(level, recipe, input, epoch);
        helper.assertTrue(a.status() == Status.MECHANICAL_CANDIDATE, "Real iron block recipe is cold mechanical candidate, not committed");
        helper.assertTrue(a.output().is(Items.IRON_BLOCK) && a.consumed().size() == 9, "Real assemble and nine actual FIFO records");
        helper.assertTrue(ItemStack.listMatches(before, slots), "Admission leaves live stacks and all components unchanged");
        var exposed = a.output(); exposed.setCount(0);
        a.inputs().getFirst().setCount(0);
        helper.assertTrue(a.output().getCount() == 1 && a.inputs().getFirst().getCount() == 1, "Preview accessors cannot mutate retained image");
        helper.assertTrue(a.revalidate(level, input) == Status.MECHANICAL_CANDIDATE, "Repeated validation does not consume");
        var retry = VanillaCraftingAdmission.inspect(level, recipe, input, epoch);
        helper.assertTrue(retry.consumed().equals(a.consumed()), "Retry reads same UUIDs; no minted instances");
        helper.assertTrue(VanillaCraftingAdmission.inspect(level, recipe, input, epoch + 1).status() == Status.STALE_CATALOG,
                "Wrong reload epoch rejects before any consumption");
        slots.getFirst().setCount(2);
        helper.assertTrue(a.revalidate(level, input) == Status.INPUT_CHANGED, "Owner input change invalidates preview");
        slots.getFirst().setCount(1);
        a.cancel(); a.cancel();
        helper.assertTrue(a.revalidate(level, input) == Status.CANCELLED && ItemStack.listMatches(before, slots),
                "Repeated cancellation is preview-only, never refund or mutation");
        helper.succeed();
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realCakeRemaindersPreservedAndRejected(GameTestHelper helper) {
        var items = List.of(Items.MILK_BUCKET, Items.MILK_BUCKET, Items.MILK_BUCKET,
                Items.SUGAR, Items.EGG, Items.SUGAR, Items.WHEAT, Items.WHEAT, Items.WHEAT);
        var stacks = new ArrayList<ItemStack>();
        for (int i = 0; i < items.size(); i++) stacks.add(fixture(items.get(i), 100 + i));
        var before = stacks.stream().map(ItemStack::copy).toList();
        var input = CraftingInput.of(3, 3, stacks);
        var a = VanillaCraftingAdmission.inspect(helper.getLevel(), recipe(helper, "cake"), input,
                AtomicChemistryCatalog.INSTANCE.snapshot().epoch());
        helper.assertTrue(a.status() == Status.PROCESS_MODEL_REQUIRED, "Cake is not authorized as an ambient mechanical reaction");
        helper.assertTrue(a.output().is(Items.CAKE) && a.remainders().size() == 9, "Real recipe shape and all nine remainder slots retained");
        helper.assertTrue(a.unchangedReturns().isEmpty(), "Milk-to-bucket transformation cannot return the full milk instance as an unchanged shell");
        for (int i = 0; i < 3; i++) helper.assertTrue(a.remainders().get(i).is(Items.BUCKET), "Milk bucket return remains explicit");
        helper.assertTrue(ItemStack.listMatches(before, stacks), "Condition rejection preserves every input");
        stacks.getFirst().remove(ChemistryComponents.LEDGER.get());
        helper.assertTrue(VanillaCraftingAdmission.inspect(helper.getLevel(), recipe(helper, "cake"), input,
                AtomicChemistryCatalog.INSTANCE.snapshot().epoch()).status() == Status.UNRESOLVED_LEDGER,
                "Missing real ledger rejects, never samples a template");
        helper.succeed();
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void dynamicRepairUsesRealComponents(GameTestHelper helper) {
        var first = fixture(Items.IRON_PICKAXE, 201);
        var second = fixture(Items.IRON_PICKAXE, 202);
        first.setDamageValue(200); second.setDamageValue(180);
        var input = CraftingInput.of(2, 1, List.of(first, second));
        var before = input.items().stream().map(ItemStack::copy).toList();
        var holder = recipe(helper, "repair_item");
        var expected = holder.value().assemble(CraftingInput.of(2, 1, before.stream().map(ItemStack::copy).toList()),
                helper.getLevel().registryAccess());
        var a = VanillaCraftingAdmission.inspect(helper.getLevel(), holder, input,
                AtomicChemistryCatalog.INSTANCE.snapshot().epoch());
        helper.assertTrue(a.status() == Status.PROCESS_MODEL_REQUIRED, "Dynamic repair needs explicit material/process settlement, not arbitrary cold conversion");
        helper.assertTrue(ItemStack.matches(a.output(), expected) && a.output().getDamageValue() < 180,
                "Real special-recipe assemble retains runtime damage semantics");
        helper.assertTrue(a.remainders().size() == 2 && a.remainders().stream().allMatch(ItemStack::isEmpty),
                "Actual dynamic remainder vector is retained, not guessed from item type");
        helper.assertTrue(ItemStack.listMatches(input.items(), before), "Dynamic evaluation leaves both original instances untouched");
        helper.succeed();
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void dynamicBookReturnDoesNotCloneMatter(GameTestHelper helper) {
        var original = fixture(Items.WRITTEN_BOOK, 301);
        original.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                net.minecraft.world.item.component.WrittenBookContent.EMPTY);
        var blank = fixture(Items.WRITABLE_BOOK, 302);
        var input = CraftingInput.of(2, 1, List.of(original, blank));
        var before = input.items().stream().map(ItemStack::copy).toList();
        var a = VanillaCraftingAdmission.inspect(helper.getLevel(), recipe(helper, "book_cloning"), input,
                AtomicChemistryCatalog.INSTANCE.snapshot().epoch());
        helper.assertTrue(a.status() == Status.PROCESS_MODEL_REQUIRED, "Copying book content is not permission to duplicate matter");
        helper.assertTrue(a.output().is(Items.WRITTEN_BOOK)
                && a.output().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).generation() == 1,
                "Real dynamic assemble advances copied book generation");
        helper.assertTrue(a.remainders().getFirst().is(Items.WRITTEN_BOOK)
                && a.remainders().getFirst().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).generation() == 0,
                "Original written book is returned with original content generation");
        helper.assertTrue(!a.output().has(ChemistryComponents.LEDGER.get())
                && !a.remainders().getFirst().has(ChemistryComponents.LEDGER.get()),
                "Neither shape preview copies original chemistry ownership; B must allocate/return actual instances");
        helper.assertTrue(a.unchangedReturns().size() == 1
                && a.unchangedReturns().get(0).equals(original.get(ChemistryComponents.LEDGER.get()).ledger().orElseThrow().instances().getFirst()),
                "Original book return binds the exact full FIFO record, never nominal matter or a new UUID");
        helper.assertTrue(a.consumed().size() == 2 && ItemStack.listMatches(before, input.items()),
                "All real consumed fields retained once and input books unchanged");
        helper.succeed();
    }

    @GameTest(template="core_empty", timeoutTicks=100)
    public static void duplicateAndHotInputsAreRejectedUnchanged(GameTestHelper helper) {
        var first = fixture(Items.IRON_INGOT, 401);
        var slots = new ArrayList<ItemStack>();
        slots.add(first); slots.add(first.copy());
        for (int i = 2; i < 9; i++) slots.add(fixture(Items.IRON_INGOT, 401 + i));
        var input = CraftingInput.of(3, 3, slots);
        var before = input.items().stream().map(ItemStack::copy).toList();
        long epoch = AtomicChemistryCatalog.INSTANCE.snapshot().epoch();
        helper.assertTrue(VanillaCraftingAdmission.inspect(helper.getLevel(), recipe(helper, "iron_block"), input, epoch)
                .status() == Status.DUPLICATE_INSTANCE && ItemStack.listMatches(before, input.items()),
                "Copied UUID fails closed without normalizing or consuming");
        slots.set(1, fixture(Items.IRON_INGOT, 402));
        var old = first.get(ChemistryComponents.LEDGER.get()).ledger().orElseThrow().instances().getFirst();
        var hot = new InstanceRecord(old.id(), old.remaining(), old.origin(), old.revision(),
                new org.goldgom.chemicalelements.api.thermal.ThermalState(new java.math.BigDecimal("50"),
                        new java.math.BigDecimal("900"), Map.of(), 1), old.nuclides(), old.poreSolution(), old.adsorbed());
        first.set(ChemistryComponents.LEDGER.get(), LedgerPayload.of(new StackLedger(StackLedger.CURRENT_SCHEMA, 1, List.of(hot))));
        var hotBefore = input.items().stream().map(ItemStack::copy).toList();
        helper.assertTrue(VanillaCraftingAdmission.inspect(helper.getLevel(), recipe(helper, "iron_block"), input, epoch)
                .status() == Status.NON_AMBIENT_INPUT && ItemStack.listMatches(hotBefore, input.items()),
                "Ambient workbench cannot silently cool hot material or consume it");
        helper.succeed();
    }

    private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper helper, String id) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace(id)).orElseThrow();
        if (!(holder.value() instanceof CraftingRecipe crafting)) throw new IllegalStateException("not crafting " + id);
        return new RecipeHolder<>(holder.id(), crafting);
    }
    private static ItemStack fixture(Item item, long id) {
        var stack = new ItemStack(item);
        // Explicit test-only source, deliberately not nominal chemistry. Admission must read it intact.
        var matter = new Composition(Map.of(ResourceLocation.parse("chemicalelements:iron"), MolAmount.parseDecimal("0.125")));
        var record = new InstanceRecord(new UUID(0xA22, id), matter, Provenance.of("test", "admission fixture"), 0);
        stack.set(ChemistryComponents.LEDGER.get(), LedgerPayload.of(new StackLedger(StackLedger.CURRENT_SCHEMA, 0, List.of(record))));
        return stack;
    }
}

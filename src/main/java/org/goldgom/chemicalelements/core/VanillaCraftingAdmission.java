package org.goldgom.chemicalelements.core;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;

/** Read-only, pre-consumption admission for the real vanilla crafting recipe.
 * NOT a reservation, transaction receipt, owner revision or permission to call vanilla onTake.
 * B must resolve/lock the real owners, revalidate, and settle ALL exits in its sole journal.
 * No stack resolution, sampling, inventory mutation, UUID generation or journal lives here.
 */
public final class VanillaCraftingAdmission {
    private VanillaCraftingAdmission() {}
    public enum Status {
        MECHANICAL_CANDIDATE, PROCESS_MODEL_REQUIRED, INVALID_THREAD, STALE_CATALOG,
        STALE_RECIPE, NO_MATCH, INVALID_GRID, UNRESOLVED_LEDGER, INVALID_LEDGER,
        DUPLICATE_INSTANCE, NON_AMBIENT_INPUT, INVALID_REMAINDERS, RECIPE_MUTATED_INPUT,
        RECIPE_ERROR, INPUT_CHANGED, OUTPUT_CHANGED, CANCELLED
    }
    // Explicit first slice: reversible cold packing, not an element-vector reaction predictor.
    // Neither entry enables production; full thermal/isotope/residue allocation is still B's job.
    private static final Set<ResourceLocation> COLD_PACKING = Set.of(
        ResourceLocation.withDefaultNamespace("iron_block"),
        ResourceLocation.withDefaultNamespace("iron_ingot_from_iron_block"));

    /** Private mutable stacks never escape. Full records include thermal/isotopes/pore/adsorbed. */
    public static final class Admission {
        private final ResourceLocation recipeId;
        private final CraftingRecipe recipeIdentity;
        private final long epoch;
        private final int width, height;
        private final List<ItemStack> input, remainders;
        private final ItemStack output;
        private final List<InstanceRecord> consumed;
        private final Status status;
        private boolean cancelled;
        private final net.minecraft.core.HolderLookup.Provider registries;
        private Admission(ServerLevel level, RecipeHolder<CraftingRecipe> recipe, long epoch, CraftingInput input,
                          ItemStack output, List<ItemStack> remainders,
                          List<InstanceRecord> consumed, Status status) {
            this.registries = level.registryAccess();
            this.recipeId = recipe.id(); this.recipeIdentity = recipe.value(); this.epoch = epoch;
            this.width = input.width(); this.height = input.height();
            this.input = copies(input.items(), registries); this.output = detached(output, registries);
            this.remainders = copies(remainders, registries); this.consumed = List.copyOf(consumed); this.status = status;
        }
        public ResourceLocation recipeId() { return recipeId; }
        public long epoch() { return epoch; }
        public Status status() { return cancelled ? Status.CANCELLED : status; }
        public ItemStack output() { return detached(output, registries); }
        public List<ItemStack> remainders() { return copies(remainders, registries); }
        public List<ItemStack> inputs() { return copies(input, registries); }
        public List<InstanceRecord> consumed() { return consumed; }
        /** Exact unchanged-shell returns (e.g. original written book/banner). This is an
         * allocation constraint, NOT ownership transfer: B must preserve these complete records
         * in the same transaction and exclude them from newly manufactured product material.
         * Changed containers (milk bucket -> bucket) intentionally remain unbound here. */
        public Map<Integer, InstanceRecord> unchangedReturns() {
            var bound = new TreeMap<Integer, InstanceRecord>();
            if (cancelled) return Map.of();
            for (int slot = 0; slot < remainders.size(); slot++) {
                var returned = remainders.get(slot);
                var source = input.get(slot);
                if (returned.isEmpty() || returned.getCount() != 1 || source.isEmpty()) continue;
                var shell = detached(source, registries);
                stripPreviewLedger(shell);
                if (ItemStack.isSameItemSameComponents(shell, returned)) {
                    var record = StackLedgers.get(source).orElseThrow().instances().getFirst();
                    bound.put(slot, record);
                }
            }
            return Collections.unmodifiableMap(bound);
        }
        /** Discards a preview only. Never refunds: no material was reserved or removed. */
        public void cancel() { cancelled = true; }
        /** Still not commit permission: only checks this preview against a newly resolved grid. */
        public Status revalidate(ServerLevel level, CraftingInput actual) {
            if (cancelled) return Status.CANCELLED;
            // Hard rejection deliberately retained no input image. It cannot be promoted by a
            // later revalidation; callers must perform a new inspection after fixing the cause.
            if (status != Status.MECHANICAL_CANDIDATE && status != Status.PROCESS_MODEL_REQUIRED) return status;
            if (!level.getServer().isSameThread()) return Status.INVALID_THREAD;
            if (AtomicChemistryCatalog.INSTANCE.snapshot().epoch() != epoch) return Status.STALE_CATALOG;
            var current = level.getRecipeManager().byKey(recipeId);
            if (current.isEmpty() || current.get().value() != recipeIdentity) return Status.STALE_RECIPE;
            if (width != actual.width() || height != actual.height()
                    || !ItemStack.listMatches(input, actual.items())) return Status.INPUT_CHANGED;
            var fresh = inspect(level, new RecipeHolder<>(recipeId, recipeIdentity), actual, epoch);
            if (fresh.status != status) return fresh.status;
            if (!ItemStack.matches(output, fresh.output)
                    || !ItemStack.listMatches(remainders, fresh.remainders)) return Status.OUTPUT_CHANGED;
            return status;
        }
    }

    public static Admission inspect(ServerLevel level, RecipeHolder<CraftingRecipe> recipe,
                                    CraftingInput supplied, long expectedEpoch) {
        Objects.requireNonNull(level); Objects.requireNonNull(recipe); Objects.requireNonNull(supplied);
        if (!level.getServer().isSameThread()) return rejected(level, recipe, expectedEpoch, supplied, Status.INVALID_THREAD);
        var catalog = AtomicChemistryCatalog.INSTANCE.snapshot();
        if (catalog.epoch() != expectedEpoch || expectedEpoch <= 0)
            return rejected(level, recipe, expectedEpoch, supplied, Status.STALE_CATALOG);
        if (supplied.width() < 1 || supplied.height() < 1 || supplied.width() > 3 || supplied.height() > 3
                || supplied.size() != supplied.width() * supplied.height())
            return rejected(level, recipe, expectedEpoch, supplied, Status.INVALID_GRID);
        var registered = level.getRecipeManager().byKey(recipe.id());
        if (registered.isEmpty() || registered.get().value() != recipe.value())
            return rejected(level, recipe, expectedEpoch, supplied, Status.STALE_RECIPE);
        // Recipes get independent deep copies for EACH call. Even a third-party implementation
        // that mutates its argument cannot mutate the live Container or another evaluation stage.
        final CraftingInput baseline;
        try { baseline = copy(supplied, level.registryAccess()); }
        catch (RuntimeException ex) { return rejected(level, recipe, expectedEpoch, supplied, Status.RECIPE_ERROR); }
        var consumed = new ArrayList<InstanceRecord>();
        var ids = new HashSet<UUID>();
        for (var stack : baseline.items()) {
            if (stack.isEmpty()) continue;
            var ledger = StackLedgers.get(stack);
            if (ledger.isEmpty()) return rejected(level, recipe, expectedEpoch, baseline, Status.UNRESOLVED_LEDGER);
            if (ledger.get().instances().size() != stack.getCount())
                return rejected(level, recipe, expectedEpoch, baseline, Status.INVALID_LEDGER);
            // Scan the entire observed stack, not just the consumed head, for duplicate ownership.
            for (var record : ledger.get().instances()) if (!ids.add(record.id()))
                return rejected(level, recipe, expectedEpoch, baseline, Status.DUPLICATE_INSTANCE);
            var head = ledger.get().instances().getFirst();
            if (head.thermal().temperatureK().compareTo(new java.math.BigDecimal("298.15")) != 0
                    || head.poreSolution().temperatureK().compareTo(new java.math.BigDecimal("298.15")) != 0)
                return rejected(level, recipe, expectedEpoch, baseline, Status.NON_AMBIENT_INPUT);
            consumed.add(head); // Real FIFO head, never the nominal item composition.
        }
        try {
            var matchInput = copy(baseline, level.registryAccess());
            boolean matches = recipe.value().matches(matchInput, level);
            if (!unchanged(baseline, matchInput)) return rejected(level, recipe, expectedEpoch, baseline, Status.RECIPE_MUTATED_INPUT);
            if (!matches) return rejected(level, recipe, expectedEpoch, baseline, Status.NO_MATCH);
            var assembleInput = copy(baseline, level.registryAccess());
            var output = recipe.value().assemble(assembleInput, level.registryAccess()).copy();
            if (!unchanged(baseline, assembleInput)) return rejected(level, recipe, expectedEpoch, baseline, Status.RECIPE_MUTATED_INPUT);
            var remainderInput = copy(baseline, level.registryAccess());
            var remaining = copies(recipe.value().getRemainingItems(remainderInput), level.registryAccess());
            if (!unchanged(baseline, remainderInput)) return rejected(level, recipe, expectedEpoch, baseline, Status.RECIPE_MUTATED_INPUT);
            if (remaining.size() != baseline.size() || output.isEmpty()
                    || output.getCount() > output.getMaxStackSize())
                return rejected(level, recipe, expectedEpoch, baseline, Status.INVALID_REMAINDERS);
            for (int i = 0; i < remaining.size(); i++) {
                var r = remaining.get(i);
                if (!r.isEmpty() && (baseline.getItem(i).isEmpty() || r.getCount() > r.getMaxStackSize()))
                    return rejected(level, recipe, expectedEpoch, baseline, Status.INVALID_REMAINDERS);
            }
            // Dynamic recipes copy names, dyes, maps, books, etc. Keep those exact components,
            // but NEVER copy an input chemistry UUID into a newly produced/remainder item.
            stripPreviewLedger(output); remaining.forEach(VanillaCraftingAdmission::stripPreviewLedger);
            if (AtomicChemistryCatalog.INSTANCE.snapshot() != catalog)
                return rejected(level, recipe, expectedEpoch, baseline, Status.STALE_CATALOG);
            if (level.getRecipeManager().byKey(recipe.id()).map(r -> r.value() != recipe.value()).orElse(true))
                return rejected(level, recipe, expectedEpoch, baseline, Status.STALE_RECIPE);
            return new Admission(level, recipe, expectedEpoch, baseline, output, remaining, consumed,
                    COLD_PACKING.contains(recipe.id()) ? Status.MECHANICAL_CANDIDATE : Status.PROCESS_MODEL_REQUIRED);
        } catch (RuntimeException ex) {
            return rejected(level, recipe, expectedEpoch, baseline, Status.RECIPE_ERROR);
        }
    }
    private static void stripPreviewLedger(ItemStack stack) {
        if (!stack.isEmpty() && ChemistryComponents.ready()) stack.remove(ChemistryComponents.LEDGER.get());
    }
    private static Admission rejected(ServerLevel level, RecipeHolder<CraftingRecipe> recipe, long epoch, CraftingInput input, Status status) {
        return new Admission(level, recipe, epoch, CraftingInput.EMPTY, ItemStack.EMPTY, List.of(), List.of(), status);
    }
    private static ItemStack detached(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var decoded = ItemStack.parse(registries, stack.save(registries)).orElseThrow(() -> new IllegalStateException("UNREADABLE_STACK_CODEC"));
        if (!ItemStack.matches(stack, decoded)) throw new IllegalStateException("LOSSY_STACK_CODEC");
        return decoded;
    }
    private static List<ItemStack> copies(List<ItemStack> input, net.minecraft.core.HolderLookup.Provider registries) {
        return input.stream().map(s -> detached(s, registries)).toList();
    }
    private static CraftingInput copy(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return CraftingInput.of(input.width(), input.height(), copies(input.items(), registries));
    }
    private static boolean unchanged(CraftingInput a, CraftingInput b) {
        return a.width() == b.width() && a.height() == b.height() && ItemStack.listMatches(a.items(), b.items());
    }
}

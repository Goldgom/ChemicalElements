package org.goldgom.chemicalelements.instance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import org.goldgom.chemicalelements.core.VanillaCraftingAdmission;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;

/** Production safety barrier, not a successful settlement implementation.
 * There is deliberately no caller-supplied permit: admission is only a preview.
 * Until the sole journal can commit the actual grid + destination atomically,
 * no server-side vanilla result extraction may bypass it.
 */
public final class CraftingExtractionBarrier {
    private CraftingExtractionBarrier() {}
    public record Decision(String reason, VanillaCraftingAdmission.Admission admission,
                           int left, int top, int gridWidth) {}

    public static Decision inspect(ServerPlayer player, CraftingContainer grid) {
        if (!player.serverLevel().getServer().isSameThread())
            return new Decision("INVALID_THREAD", null, 0, 0, grid.getWidth());
        try {
            var version = CraftingGridVersion.capture(player, grid);
            var positioned = grid.asPositionedCraftInput();
            var live = positioned.input();
            var detached = live.items().stream().map(stack -> {
                if (stack.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
                var copy = net.minecraft.world.item.ItemStack.parse(player.registryAccess(),
                        stack.save(player.registryAccess())).orElseThrow();
                if (!net.minecraft.world.item.ItemStack.matches(stack, copy))
                    throw new IllegalStateException("LOSSY_STACK_CODEC");
                return copy;
            }).toList();
            var input = net.minecraft.world.item.crafting.CraftingInput.of(live.width(), live.height(), detached);
            var recipe = player.serverLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, player.serverLevel());
            if (recipe.isEmpty()) return new Decision("NO_MATCH", null,
                    positioned.left(), positioned.top(), grid.getWidth());
            var admission = VanillaCraftingAdmission.inspect(player.serverLevel(), recipe.get(), input,
                    AtomicChemistryCatalog.INSTANCE.snapshot().epoch());
            String reason = switch (admission.status()) {
                case MECHANICAL_CANDIDATE, PROCESS_MODEL_REQUIRED -> "ATOMIC_CRAFTING_COMMIT_UNAVAILABLE";
                default -> admission.status().name();
            };
            if (!version.equals(CraftingGridVersion.capture(player, grid)))
                return new Decision("GRID_CHANGED", null, positioned.left(), positioned.top(), grid.getWidth());
            return new Decision(reason, admission, positioned.left(), positioned.top(), grid.getWidth());
        } catch (RuntimeException failure) {
            // No resolution, sampling, inventory writes or refund happen on this path.
            return new Decision("RECIPE_ERROR", null, 0, 0, grid.getWidth());
        }
    }

    /** Inspection never authorizes vanilla consumption, including mechanically balanced recipes. */
    public static boolean blocks(ServerPlayer player, CraftingContainer grid) {
        inspect(player, grid);
        return true;
    }
}

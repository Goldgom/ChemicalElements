package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Live optimistic version for the ORIGINAL Inventory. No inventory copy, no durable revision claim.
 * Public items/armor/offhand writes are observed through the backing list clocks; in-place stack
 * writes are observed through stack clocks. A relog/new Inventory gets a new generation.
 * Durable projection revision and journal recovery must be checked separately before any write.
 */
public final class PlayerMutationVersion {
    private PlayerMutationVersion() {}
    private static final Map<Inventory, State> STATES = new WeakHashMap<>();
    public record Version(UUID generation, long revision) {}
    private record Mark(Object identity, long clock) {}
    private static final class State {
        final UUID generation = UUID.randomUUID();
        long revision;
        List<Mark> observed;
    }
    public static Version capture(Inventory inventory) {
        if (inventory.player.level().isClientSide || inventory.player.getServer() == null
                || !inventory.player.getServer().isSameThread()) throw new IllegalStateException("SERVER_THREAD_REQUIRED");
        var marks = new ArrayList<Mark>();
        for (var list : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            marks.add(mark(list));
            for (ItemStack stack : list) {
                marks.add(mark(stack));
                if (!stack.isEmpty()) marks.add(mark(stack.getComponents()));
            }
        }
        synchronized (STATES) {
            var state = STATES.computeIfAbsent(inventory, ignored -> new State());
            if (state.observed != null && !same(state.observed, marks)) state.revision = Math.incrementExact(state.revision);
            state.observed = List.copyOf(marks);
            return new Version(state.generation, state.revision);
        }
    }
    public static boolean matches(Inventory inventory, Version expected) { return capture(inventory).equals(expected); }
    private static Mark mark(Object object) {
        if (!(object instanceof MutationTracked tracked)) throw new IllegalStateException("MUTATION_MIXIN_UNAVAILABLE");
        return new Mark(object, tracked.chemicalelements$mutationRevision());
    }
    private static boolean same(List<Mark> a, List<Mark> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).identity != b.get(i).identity || a.get(i).clock != b.get(i).clock) return false;
        return true;
    }
}

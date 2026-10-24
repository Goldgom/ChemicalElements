package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.goldgom.chemicalelements.mixin.CraftingGridAccess;

/** Live grid identity only. Not durable ownership or successful CAS permission. */
public final class CraftingGridVersion {
    private CraftingGridVersion() {}
    private static final Map<CraftingContainer, State> STATES = new WeakHashMap<>();
    public record Version(UUID generation, long revision) {}
    private record Mark(Object object, long revision) {}
    private static final class State {
        final UUID generation = UUID.randomUUID();
        List<Mark> previous;
        long revision;
    }
    public static Version capture(ServerPlayer player, CraftingContainer grid) {
        if (!player.serverLevel().getServer().isSameThread()) throw new IllegalStateException("SERVER_THREAD_REQUIRED");
        if (!player.isAlive() || player.hasDisconnected()) throw new IllegalStateException("PLAYER_UNAVAILABLE");
        if (!(grid instanceof CraftingGridAccess access)) throw new IllegalStateException("UNSUPPORTED_GRID");
        var menu = access.chemicalelements$menu();
        if (player.containerMenu != menu || !(menu instanceof CraftingMenu || menu instanceof InventoryMenu))
            throw new IllegalStateException("GRID_MENU_EXPIRED");
        if (menu.slots.stream().noneMatch(slot -> slot.container == grid))
            throw new IllegalStateException("GRID_NOT_IN_MENU");
        var marks = new ArrayList<Mark>();
        var items = access.chemicalelements$items();
        marks.add(mark(items));
        for (var stack : items) {
            marks.add(mark(stack));
            if (!stack.isEmpty()) marks.add(mark(stack.getComponents()));
        }
        synchronized (STATES) {
            var state = STATES.computeIfAbsent(grid, ignored -> new State());
            if (state.previous != null && !same(state.previous, marks))
                state.revision = Math.incrementExact(state.revision);
            state.previous = List.copyOf(marks);
            return new Version(state.generation, state.revision);
        }
    }
    private static Mark mark(Object object) {
        if (!(object instanceof MutationTracked tracked)) throw new IllegalStateException("MUTATION_MIXIN_UNAVAILABLE");
        return new Mark(object, tracked.chemicalelements$mutationRevision());
    }
    private static boolean same(List<Mark> a, List<Mark> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).object != b.get(i).object || a.get(i).revision != b.get(i).revision) return false;
        return true;
    }
}

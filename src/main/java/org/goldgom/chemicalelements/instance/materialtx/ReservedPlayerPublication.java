package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.machine.MachineTransactions.Mirror;

/** Internal live publication primitive over the ORIGINAL Inventory's three original list objects.
 * All reads/copy/codec work is completed before reservation; no ordinary getter is used in swap.
 * Not sufficient alone: the service must bind metadata, guard stack aliases, persist player root
 * and release only after the global journal decision. Failure retains the lease for forward repair.
 */
final class ReservedPlayerPublication {
    private ReservedPlayerPublication() {}
    record Prepared(Object[] main, Object[] armor, Object[] offhand) {
        Prepared { main=main.clone(); armor=armor.clone(); offhand=offhand.clone(); }
        @Override public Object[] main() { return main.clone(); }
        @Override public Object[] armor() { return armor.clone(); }
        @Override public Object[] offhand() { return offhand.clone(); }
    }
    static Prepared prepare(Mirror detached) {
        if (detached.slots().size()!=41 || !detached.tanks().isEmpty()) throw new IllegalArgumentException("PLAYER_SLOT_LAYOUT");
        Object[] all = new Object[41];
        for (var slot : detached.slots()) {
            if (slot.index()<0 || slot.index()>=41 || all[slot.index()]!=null) throw new IllegalArgumentException("PLAYER_SLOT_LAYOUT");
            all[slot.index()]=slot.stack();
        }
        for(Object stack:all) Objects.requireNonNull(stack);
        return new Prepared(Arrays.copyOfRange(all,0,36),Arrays.copyOfRange(all,36,40),Arrays.copyOfRange(all,40,41));
    }
    static void publish(Inventory original, Prepared detached, MaterialObservationBarrier.Lease lease,
                        org.goldgom.chemicalelements.instance.PlayerProjectionState state, MaterialPendingMarker marker) {
        if (!((Object) original.player instanceof org.goldgom.chemicalelements.instance.PlayerProjectionAccess access)
                || access.chemicalelements$projectionState() != state) throw new IllegalArgumentException("FOREIGN_PLAYER_METADATA");
        lease.requireRoot(original.player);
        state.validateMaterialProjection(lease, marker);
        publishInventory(original, detached, lease);
        // No codec, event or observer runs between original business replacement and metadata.
        state.projectMaterial(lease, marker);
    }
    private static void publishInventory(Inventory original, Prepared detached, MaterialObservationBarrier.Lease lease) {
        if (original.player.getServer()==null || !original.player.getServer().isSameThread())
            throw new IllegalStateException("SERVER_THREAD_REQUIRED");
        lease.requireRoot(original); lease.requireRoot(original.items); lease.requireRoot(original.armor); lease.requireRoot(original.offhand);
        var main = bridge(original.items); var armor = bridge(original.armor); var offhand = bridge(original.offhand);
        Object[] m=detached.main(), a=detached.armor(), o=detached.offhand();
        if(main.chemicalelements$rawReferences(lease).length!=m.length || armor.chemicalelements$rawReferences(lease).length!=a.length
                || offhand.chemicalelements$rawReferences(lease).length!=o.length) throw new IllegalStateException("PLAYER_LAYOUT_CHANGED");
        var children = new ArrayList<Object>();
        for (Object[] values : new Object[][] { m, a, o }) for (Object value : values) {
            var stack = (ItemStack) value;
            // EMPTY is globally shared; do not reserve a global singleton as a private owner.
            if (stack != ItemStack.EMPTY) { children.add(stack); children.add(stack.getComponents()); }
        }
        lease.reserveReplacementChildren(children);
        lease.beforeProjection();
        main.chemicalelements$replaceBacking(lease,m);
        armor.chemicalelements$replaceBacking(lease,a);
        offhand.chemicalelements$replaceBacking(lease,o);
        // No notifications here. Ordinary observers remain blocked across all three replacements.
    }
    private static ReservedListAccess bridge(Object value) {
        if (!(value instanceof ReservedListAccess access)) throw new IllegalStateException("PLAYER_LIST_BRIDGE_UNAVAILABLE");
        return access;
    }
}

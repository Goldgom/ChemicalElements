package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Strict first success-domain validator: relocation only, no chemical/thermal/nuclear transformation.
 * Rejects unsupported reactions rather than weakening conservation to element totals. All instances,
 * temperatures, phase fractions, nuclides, pore solution and adsorbed inventories must remain exact.
 * Returned containers are normal material-bearing stacks and cannot appear from nowhere.
 */
public final class OwnerTransferValidation {
    private OwnerTransferValidation() {}
    private record Carried(InstanceRecord record, String stackEncoding) {}
    public static void validate(MultiOwnerRequest request, HolderLookup.Provider registries) {
        var proposal = request.proposal();
        if (!proposal.consumed().amounts().isEmpty() || !proposal.products().amounts().isEmpty()
                || !proposal.byproducts().amounts().isEmpty() || !proposal.extent().isZero()
                || proposal.energyJ().signum() != 0 || proposal.durationSeconds().signum() != 0)
            throw new IllegalStateException("TRANSFORMATION_MODEL_REQUIRED");
        var before = new HashMap<UUID, Carried>(); var after = new HashMap<UUID, Carried>();
        for (var change : request.owners()) {
            var a = change.before(); var b = change.after();
            if (!a.energy().equals(b.energy()) || !a.control().equals(b.control()))
                throw new IllegalStateException("TRANSFER_CONTROL_OR_ENERGY_CHANGED");
            // Tank and slot relocation need different physical projection ports; do not silently
            // turn a tank parcel into a packaged stack without a container accounting model.
            if (!a.tanks().equals(b.tanks())) throw new IllegalStateException("TRANSFER_TANK_CHANGED");
            collect(a, before, registries); collect(b, after, registries);
        }
        if (!before.equals(after)) throw new IllegalStateException("FULL_INSTANCE_OR_CONTAINER_CONSERVATION");
    }
    private static void collect(Mirror mirror, Map<UUID, Carried> target, HolderLookup.Provider registries) {
        for (var slot : mirror.slots()) {
            ItemStack stack = slot.stack();
            if (stack.isEmpty()) continue;
            // Encode a one-unit shell without chemistry so a 64->32+32 split is legal, but item,
            // damage, names and nested container contents cannot be substituted in transit.
            var shell = stack.copy(); shell.setCount(1);
            shell.remove(org.goldgom.chemicalelements.persistence.ChemistryComponents.LEDGER.get());
            var encoded = ItemStack.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(
                    com.mojang.serialization.JsonOps.INSTANCE, registries), shell).getOrThrow();
            String canonical = TransactionEncoding.canonical(encoded);
            for (var record : slot.instances()) {
                if (target.putIfAbsent(record.id(), new Carried(record, canonical)) != null)
                    throw new IllegalStateException("DUPLICATE_OWNER_INSTANCE");
            }
        }
        for (var tank : mirror.tanks()) for (var record : tank.parcels())
            if (target.putIfAbsent(record.id(), new Carried(record, "tank:" + tank.identity())) != null)
                throw new IllegalStateException("DUPLICATE_OWNER_INSTANCE");
    }
}

package org.goldgom.chemicalelements.content.material;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.data.*;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.goldgom.chemicalelements.registry.ElementMaterials;
import org.goldgom.chemicalelements.thermal.PhaseModel;

/** Read-only server entry. A diagnostic is NOT a placement permit or proof of slot ownership.
 * Never resolve/mint a missing ledger, normalize a count, create an Owner, or infer phase from species.phase.
 */
public final class MaterialAdmission {
    public enum Status { NO_AUTHORITY, INVALID_TARGET, MISSING_LEDGER, QUARANTINED,
        COUNT_MISMATCH, STALE_CATALOG, EMPTY_MATTER, INVALID_COMPOSITION, IDENTITY_MISMATCH,
        UNKNOWN, UNMODELED, TRANSACTION_REQUIRED }
    public record Observation(InstanceRecord instance, MaterialBehaviorLibrary.Query behavior,
            Set<ResourceLocation> phaseModels) {
        public Observation { phaseModels = Set.copyOf(phaseModels); }
    }
    public record Inspection(Status status, ChemistryBundle bundle, StackLedger expected,
            List<Observation> observations) {
        public Inspection { observations = List.copyOf(observations); }
        /** Always false until a real B durable placement protocol is integrated. */
        public boolean mayPlace() { return false; }
        public boolean matches(ItemStack authoritativeStack, ChemistryBundle current) {
            return expected != null && current == bundle && authoritativeStack.getCount() == expected.instances().size()
                    && StackLedgers.get(authoritativeStack).filter(expected::equals).isPresent();
        }
    }
    public static Inspection inspect(ServerLevel level, ItemStack authoritativeStack, ResourceLocation target) {
        Objects.requireNonNull(level); Objects.requireNonNull(authoritativeStack); Objects.requireNonNull(target);
        var bundle = AtomicChemistryCatalog.INSTANCE.bundle();
        if (!level.getServer().isSameThread() || !StackLedgers.authoritative()) return result(Status.NO_AUTHORITY, bundle, null, List.of());
        if (!target.getNamespace().equals("chemicalelements") || !ElementMaterials.CONTENT.containsKey(target.getPath()))
            return result(Status.INVALID_TARGET, bundle, null, List.of());
        if (StackLedgers.quarantined(authoritativeStack)) return result(Status.QUARANTINED, bundle, null, List.of());
        var ledger = StackLedgers.get(authoritativeStack).orElse(null);
        if (ledger == null) return result(Status.MISSING_LEDGER, bundle, null, List.of());
        if (ledger.instances().size() != authoritativeStack.getCount() || ledger.instances().isEmpty())
            return result(Status.COUNT_MISMATCH, bundle, ledger, List.of());
        var observations = new ArrayList<Observation>();
        Status status = Status.TRANSACTION_REQUIRED;
        for (var instance : ledger.instances()) {
            if (instance.origin().registryEpoch() != bundle.epoch()) return result(Status.STALE_CATALOG, bundle, ledger, observations);
            if (instance.remaining().amounts().isEmpty()) return result(Status.EMPTY_MATTER, bundle, ledger, observations);
            try {
                var behavior = bundle.materials().query(instance.remaining());
                var models = new TreeSet<ResourceLocation>();
                boolean modeled = false, explicitlyUnmodeled = false;
                for (var model : bundle.phases().models().values()) {
                    if (instance.remaining().amounts().containsKey(model.subject())) {
                        models.add(model.id()); modeled |= model.hasEnthalpy();
                        explicitlyUnmodeled |= model.kind() == PhaseModel.Kind.UNMODELED;
                    }
                }
                observations.add(new Observation(instance, behavior, models));
                if (target.getPath().startsWith("elemental_")) {
                    var element = ElementMaterials.id(target.getPath().substring("elemental_".length()));
                    var atoms = instance.remaining().inventory(bundle.catalog());
                    if (!atoms.atoms().keySet().equals(Set.of(element)) || atoms.charge().signum() != 0)
                        return result(Status.IDENTITY_MISMATCH, bundle, ledger, observations);
                }
                // Multiple species, pore solution and adsorption cannot borrow a pure-material model.
                if (instance.remaining().amounts().size() != 1 || !instance.adsorbed().amounts().isEmpty()
                        || !instance.poreSolution().equals(org.goldgom.chemicalelements.api.fluid.SolutionState.empty())
                        || explicitlyUnmodeled) status = Status.UNMODELED;
                else if (!modeled && status != Status.UNMODELED) status = Status.UNKNOWN;
            } catch (IllegalArgumentException ex) {
                return result(Status.INVALID_COMPOSITION, bundle, ledger, observations);
            }
        }
        // Even a complete model needs authoritative pressure/reference, finite heat, capacity,
        // permissions, target occupancy, full-field conservation and joint Owner CAS before projection.
        return result(status, bundle, ledger, observations);
    }
    private static Inspection result(Status s, ChemistryBundle b, StackLedger l, List<Observation> o) {
        return new Inspection(s, b, l, o);
    }
    private MaterialAdmission() {}
}

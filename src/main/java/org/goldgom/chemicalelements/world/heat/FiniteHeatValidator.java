package org.goldgom.chemicalelements.world.heat;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;
import org.goldgom.chemicalelements.data.ChemistryBundle;
import org.goldgom.chemicalelements.instance.TransactionEncoding;
import org.goldgom.chemicalelements.instance.materialtx.MaterialOwnerSnapshot;
import org.goldgom.chemicalelements.thermal.EnthalpyPlanner;

/** Pure validation; zero-energy AMBIENT and unsupported mixtures never acquire spendable heat. */
public final class FiniteHeatValidator {
    private FiniteHeatValidator() {}
    public record Result(String status, String detail) {
        public boolean valid() { return status.equals("VALIDATED"); }
    }
    public static Result validate(MaterialOwnerSnapshot snapshot, FiniteHeatEvidence heat, ChemistryBundle bundle) {
        if (heat.epoch() != bundle.epoch() || !heat.catalogHash().equals(bundle.catalog().contentHash()))
            return no("STALE_BUNDLE", "Captured catalog changed");
        var root = JsonParser.parseString(snapshot.canonicalRoot()).getAsJsonObject();
        if (!root.has("finite_heat") || !TransactionEncoding.canonical(root.get("finite_heat")).equals(heat.canonical()))
            return no("ROOT_MISMATCH", "Full FIFO/capacity/reference evidence not bound to authoritative root");
        var model = bundle.phases().get(heat.model());
        if (model == null || !model.hasEnthalpy()) return no("UNMODELED", "No complete production enthalpy model");
        if (!FiniteHeatEvidence.modelHash(model).equals(heat.modelHash())) return no("STALE_MODEL", "Model content changed");
        if (!model.energyReference().equals(heat.reference())) return no("REFERENCE_MISMATCH", "No implicit energy migration");
        if (heat.occupiedVolumeM3().compareTo(heat.maxVolumeM3()) > 0) return no("CAPACITY", "Geometric capacity exceeded");
        BigDecimal energy = BigDecimal.ZERO, mol = BigDecimal.ZERO, cp = BigDecimal.ZERO;
        var ids = new HashSet<UUID>();
        for (var instance : heat.instances()) {
            if (!ids.add(instance.id())) return no("DUPLICATE_INSTANCE", "Repeated FIFO identity");
            if (instance.origin().registryEpoch() != bundle.epoch()) return no("STALE_INSTANCE", "Instance epoch differs");
            // Same-temperature plan runs full reference energy / composition / nuclide / pore checks first.
            var check = EnthalpyPlanner.plan(instance, new EnthalpyPlanner.Request(heat.model(), heat.epoch(),
                    heat.reference(), heat.pressurePa(), instance.thermal().temperatureK(), BigDecimal.ZERO, BigDecimal.ZERO), bundle.phases());
            if (!check.status().equals("NO_CHANGE")) return no(check.status(), check.reason());
            // A latent plateau cannot be represented by a single sensible J/K capacity.
            if (instance.remaining().amounts().size() != 1 || instance.thermal().temperatureK().compareTo(model.transitionK()) == 0)
                return no("UNMODELED", "Latent plateau requires joint enthalpy plan, not constant-capacity exchange");
            var amount = instance.remaining().totalMol().decimal();
            boolean high = instance.remaining().amounts().containsKey(model.highSpecies());
            cp = cp.add(amount.multiply(high ? model.cpHigh() : model.cpLow()));
            energy = energy.add(instance.thermal().energyJ()); mol = mol.add(amount);
        }
        if (energy.signum() < 0 || energy.compareTo(heat.maxEnergyJ()) > 0 || mol.compareTo(heat.maxMol()) > 0)
            return no("CAPACITY", "Stored energy or material exceeds independent root capacity");
        if (cp.compareTo(heat.joulesPerKelvin()) != 0) return no("CAPACITY_MISMATCH", "J/K differs from exact model and complete FIFO");
        return no("VALIDATED", "Read-only complete root evidence; requires B joint durable transaction");
    }
    private static Result no(String status, String detail) { return new Result(status, detail); }
}

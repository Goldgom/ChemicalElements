package org.goldgom.chemicalelements.instance.materialtx;

import java.math.BigDecimal;
import java.util.*;
import com.google.gson.JsonParser;
import org.goldgom.chemicalelements.data.ChemistryBundle;
import org.goldgom.chemicalelements.world.heat.FiniteHeatEvidence;
import org.goldgom.chemicalelements.world.heat.FiniteHeatValidator;

/** Phase gate for finite heat roots, using C's real full-root evidence. This is a necessary gate,
 * not a complete phase planner or replacement for cross-owner matter/nuclear conservation. */
final class MaterialHeatValidation {
    private MaterialHeatValidation() {}
    static void validate(MaterialTransaction tx, ChemistryBundle bundle) {
        if (tx.bundleEpoch() != bundle.epoch() || !tx.bundleHash().equals(bundle.catalog().contentHash()))
            throw new IllegalStateException("STALE_MATERIAL_BUNDLE");
        int heatRoots = 0;
        BigDecimal beforeEnergy = BigDecimal.ZERO, afterEnergy = BigDecimal.ZERO;
        for (var change : tx.changes()) {
            boolean a = JsonParser.parseString(change.before().canonicalRoot()).getAsJsonObject().has("finite_heat");
            boolean b = JsonParser.parseString(change.after().canonicalRoot()).getAsJsonObject().has("finite_heat");
            if (a != b) throw new IllegalStateException("HEAT_ROOT_LAYOUT_CHANGED");
            if (!a) continue;
            heatRoots++;
            var before = FiniteHeatEvidence.fromRoot(change.before().canonicalRoot());
            var after = FiniteHeatEvidence.fromRoot(change.after().canonicalRoot());
            var av = FiniteHeatValidator.validate(change.before(), before, bundle);
            var bv = FiniteHeatValidator.validate(change.after(), after, bundle);
            if (!av.valid()) throw new IllegalStateException("HEAT_BEFORE:" + av.status());
            if (!bv.valid()) throw new IllegalStateException("HEAT_AFTER:" + bv.status());
            if (!before.reference().equals(tx.thermalReference()) || !after.reference().equals(tx.thermalReference()))
                throw new IllegalStateException("HEAT_TRANSACTION_REFERENCE_MISMATCH");
            if (!before.model().equals(after.model()) || !before.modelHash().equals(after.modelHash())
                    || before.maxEnergyJ().compareTo(after.maxEnergyJ()) != 0
                    || before.maxMol().compareTo(after.maxMol()) != 0
                    || before.maxVolumeM3().compareTo(after.maxVolumeM3()) != 0)
                throw new IllegalStateException("HEAT_CAPACITY_OR_MODEL_SUBSTITUTED");
            for (var i : before.instances()) beforeEnergy = beforeEnergy.add(i.thermal().energyJ());
            for (var i : after.instances()) afterEnergy = afterEnergy.add(i.thermal().energyJ());
        }
        if (tx.action() == MaterialTransaction.Action.PHASE && heatRoots == 0)
            throw new IllegalStateException("FINITE_HEAT_ROOT_REQUIRED");
        // Energy balance with non-heat material roots is verified by the full phase planner;
        // don't pretend these subtotals are global conservation when such roots are present.
        if (heatRoots == tx.changes().size() && beforeEnergy.compareTo(afterEnergy) != 0)
            throw new IllegalStateException("CLOSED_HEAT_ROOT_ENERGY_IMBALANCE");
    }
}

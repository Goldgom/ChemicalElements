package org.goldgom.chemicalelements.data;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine;
import org.goldgom.chemicalelements.reaction.PhaseEquilibrium;

/** Capture once per operation: all chemistry definitions belong to catalog.epoch(). */
public record ChemistryBundle(RegistrySnapshot catalog, ModeledSynthesisEngine.Library reactions,
                              List<PhaseEquilibrium.Salt> solubility,
                              Map<ResourceLocation,String> sourcePacks,
                              org.goldgom.chemicalelements.thermal.PhaseLibrary phases,
                              MaterialBehaviorLibrary materials) {
    public ChemistryBundle {
        solubility = List.copyOf(solubility);
        sourcePacks = Map.copyOf(sourcePacks);
        if(phases.catalog()!=catalog || materials.catalog()!=catalog || materials.phases()!=phases)
            throw new IllegalArgumentException("Bundle libraries must share catalog identity");
    }
    public ChemistryBundle(RegistrySnapshot catalog, ModeledSynthesisEngine.Library reactions,
                           List<PhaseEquilibrium.Salt> solubility, Map<ResourceLocation,String> sourcePacks) {
        this(catalog,reactions,solubility,sourcePacks,
                org.goldgom.chemicalelements.thermal.PhaseLibrary.load(catalog,Map.of()));
    }
    private ChemistryBundle(RegistrySnapshot catalog, ModeledSynthesisEngine.Library reactions,
                           List<PhaseEquilibrium.Salt> solubility, Map<ResourceLocation,String> sourcePacks,
                           org.goldgom.chemicalelements.thermal.PhaseLibrary phases) {
        this(catalog,reactions,solubility,sourcePacks,phases,MaterialBehaviorLibrary.load(catalog,phases,Map.of()));
    }
    public ChemistryBundle(RegistrySnapshot catalog, ModeledSynthesisEngine.Library reactions,
                           List<PhaseEquilibrium.Salt> solubility) {
        this(catalog,reactions,solubility,Map.of());
    }
    public long epoch() { return catalog.epoch(); }
}

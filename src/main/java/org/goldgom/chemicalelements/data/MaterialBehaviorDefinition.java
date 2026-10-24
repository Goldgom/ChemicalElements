package org.goldgom.chemicalelements.data;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Server data classification only: never inventory, thermodynamic state or a transaction permit. */
public record MaterialBehaviorDefinition(ResourceLocation id, CompositionKind compositionKind,
        Set<ResourceLocation> allSpecies, Set<ResourceLocation> tags,
        Optional<ResourceLocation> phaseModel, String basis, String source, String limitations) {
    public enum CompositionKind { ANY, PURE, MIXTURE }
    public MaterialBehaviorDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(compositionKind);
        allSpecies=Set.copyOf(allSpecies); tags=Set.copyOf(tags);
        Objects.requireNonNull(phaseModel); Objects.requireNonNull(basis);
        Objects.requireNonNull(source); Objects.requireNonNull(limitations);
    }
}

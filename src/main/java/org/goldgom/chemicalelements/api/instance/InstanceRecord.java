package org.goldgom.chemicalelements.api.instance;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.api.nuclear.NuclideInventory;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
public record InstanceRecord(UUID id,Composition remaining,Provenance origin,long revision,ThermalState thermal,NuclideInventory nuclides,SolutionState poreSolution,Composition adsorbed) {
 public InstanceRecord {Objects.requireNonNull(id);Objects.requireNonNull(remaining);Objects.requireNonNull(origin);Objects.requireNonNull(thermal);Objects.requireNonNull(nuclides);Objects.requireNonNull(poreSolution);Objects.requireNonNull(adsorbed);if(revision<0)throw new IllegalArgumentException("negative revision");}
 public InstanceRecord(UUID id,Composition remaining,Provenance origin,long revision){this(id,remaining,origin,revision,ThermalState.AMBIENT,NuclideInventory.EMPTY,SolutionState.empty(),Composition.EMPTY);}
}
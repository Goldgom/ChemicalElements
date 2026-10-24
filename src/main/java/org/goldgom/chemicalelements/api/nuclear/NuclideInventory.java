package org.goldgom.chemicalelements.api.nuclear;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.MolAmount;
/** Isotope resolution of existing atoms, never extra chemical inventory. */
public record NuclideInventory(Map<NuclideId,MolAmount> amounts) {
 public static final NuclideInventory EMPTY=new NuclideInventory(Map.of());
 public NuclideInventory {amounts=Collections.unmodifiableMap(new TreeMap<>(amounts));}
}
package org.goldgom.chemicalelements.api.thermal;
import java.math.BigDecimal;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.Phase;
public record ThermalState(BigDecimal energyJ,BigDecimal temperatureK,Map<Phase,BigDecimal> phaseFractions,long revision) {
 public static final ThermalState AMBIENT=new ThermalState(BigDecimal.ZERO,new BigDecimal("298.15"),Map.of(),0);
 public ThermalState { Objects.requireNonNull(energyJ); if(temperatureK.signum()<=0||revision<0)throw new IllegalArgumentException("invalid thermal state");phaseFractions=Map.copyOf(phaseFractions); if(phaseFractions.values().stream().anyMatch(v->v.signum()<0||v.compareTo(BigDecimal.ONE)>0))throw new IllegalArgumentException("invalid phase fraction"); if(!phaseFractions.isEmpty()&&phaseFractions.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("phase fractions must sum to one"); }
}
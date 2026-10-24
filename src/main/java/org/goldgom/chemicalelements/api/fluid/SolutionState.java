package org.goldgom.chemicalelements.api.fluid;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.Provenance;
import java.math.BigDecimal;
import java.util.Objects;
public record SolutionState(MolAmount waterMol,Composition dissolved,Composition suspended,BigDecimal temperatureK,BigDecimal volumeLitres,Provenance origin,long revision) {
 public SolutionState {Objects.requireNonNull(waterMol);Objects.requireNonNull(dissolved);Objects.requireNonNull(suspended);Objects.requireNonNull(origin);if(temperatureK.signum()<=0||volumeLitres.signum()<0||revision<0)throw new IllegalArgumentException("invalid solution"); if(volumeLitres.signum()==0&&(!waterMol.isZero()||!dissolved.amounts().isEmpty()||!suspended.amounts().isEmpty()))throw new IllegalArgumentException("nonempty zero-volume solution");}
 public static SolutionState empty(){return new SolutionState(MolAmount.ZERO,Composition.EMPTY,Composition.EMPTY,new BigDecimal("298.15"),BigDecimal.ZERO,Provenance.of("empty","none"),0);}
}
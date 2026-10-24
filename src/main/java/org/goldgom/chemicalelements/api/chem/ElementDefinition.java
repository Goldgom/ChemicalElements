package org.goldgom.chemicalelements.api.chem;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.Objects;
public record ElementDefinition(ResourceLocation id,int atomicNumber,String symbol,BigDecimal molarMass,DataBasis basis,String source) {
 public ElementDefinition { Objects.requireNonNull(id); Objects.requireNonNull(symbol); Objects.requireNonNull(basis); Objects.requireNonNull(source); if(atomicNumber<1||atomicNumber>118||molarMass.signum()<=0)throw new IllegalArgumentException("invalid element"); }
}
package org.goldgom.chemicalelements.api.reaction;
import org.goldgom.chemicalelements.api.chem.*;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.*;
/** Species and phase are retained; element totals alone cannot predict chemistry. */
public record ReactionContext(Composition inputs,BigDecimal temperatureK,BigDecimal pressurePa,BigDecimal availableEnergyJ,Set<ResourceLocation> catalysts,Set<String> capabilities,BigDecimal elapsedSeconds) {
 public ReactionContext {Objects.requireNonNull(inputs); catalysts=Set.copyOf(catalysts);capabilities=Set.copyOf(capabilities);if(temperatureK.signum()<=0||pressurePa.signum()<=0||availableEnergyJ.signum()<0||elapsedSeconds.signum()<0)throw new IllegalArgumentException("invalid reaction conditions");}
 public static ReactionContext workbench(Composition inputs){return new ReactionContext(inputs,new BigDecimal("298.15"),new BigDecimal("101325"),BigDecimal.ZERO,Set.of(),Set.of("ambient_workbench"),BigDecimal.ZERO);}
}
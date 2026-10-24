package org.goldgom.chemicalelements.api.reaction;
import org.goldgom.chemicalelements.api.chem.*;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.*;
public record ReactionProposal(ResourceLocation rule,Composition consumed,Composition products,Composition byproducts,MolAmount extent,BigDecimal energyJ,BigDecimal durationSeconds,long registryEpoch,String basis,List<String> diagnostics) {
 public ReactionProposal {diagnostics=List.copyOf(diagnostics);if(durationSeconds.signum()<0||registryEpoch<0)throw new IllegalArgumentException("invalid proposal");}
}
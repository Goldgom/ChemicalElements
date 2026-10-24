package org.goldgom.chemicalelements.api.fluid;
import net.minecraft.resources.ResourceLocation;
public record SolutionLocation(ResourceLocation dimension,long packedPosition,String port) {}
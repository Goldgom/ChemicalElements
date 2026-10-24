package org.goldgom.chemicalelements.api.world;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
public record EnvironmentSample(ResourceLocation dimension,ResourceLocation biome,int y,String geology,long seed,Map<String,String> surroundings) {public EnvironmentSample{surroundings=Map.copyOf(surroundings);}}
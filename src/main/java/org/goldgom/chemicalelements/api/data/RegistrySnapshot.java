package org.goldgom.chemicalelements.api.data;
import org.goldgom.chemicalelements.api.chem.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public record RegistrySnapshot(long epoch,String contentHash,Map<ResourceLocation,ElementDefinition> elements,Map<ResourceLocation,SpeciesDefinition> species,Map<ResourceLocation,Composition> itemCompositions) {
 public RegistrySnapshot { if(epoch<0)throw new IllegalArgumentException("negative epoch"); Objects.requireNonNull(contentHash); elements=Map.copyOf(elements);species=Map.copyOf(species);itemCompositions=Map.copyOf(itemCompositions); for(var s:species.values())for(var a:s.atoms().keySet())if(!elements.containsKey(a))throw new IllegalArgumentException("unknown element "+a); for(var c:itemCompositions.values())for(var id:c.amounts().keySet())if(!species.containsKey(id))throw new IllegalArgumentException("unknown species "+id); }
 public SpeciesDefinition requireSpecies(ResourceLocation id){var d=species.get(id);if(d==null)throw new IllegalArgumentException("unmodeled species "+id);return d;}
}
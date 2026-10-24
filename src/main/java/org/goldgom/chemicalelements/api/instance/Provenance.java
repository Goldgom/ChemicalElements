package org.goldgom.chemicalelements.api.instance;
import java.util.*;
public record Provenance(String kind,String source,long seed,long registryEpoch,Map<String,String> metadata) {
 public Provenance {Objects.requireNonNull(kind);Objects.requireNonNull(source);if(registryEpoch<0)throw new IllegalArgumentException("negative epoch");metadata=Map.copyOf(metadata);}
 public static Provenance of(String kind,String source){return new Provenance(kind,source,0,0,Map.of());}
}
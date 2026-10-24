package org.goldgom.chemicalelements.api.instance;
import org.goldgom.chemicalelements.api.world.EnvironmentSample;
import java.util.Optional;
public record CreationContext(Provenance origin,Optional<EnvironmentSample> environment,boolean simulation) {}
package org.goldgom.chemicalelements.api.instance;
import java.util.*;
public record Selection(List<UUID> instanceIds,long expectedRevision) {public Selection{instanceIds=List.copyOf(instanceIds);}}
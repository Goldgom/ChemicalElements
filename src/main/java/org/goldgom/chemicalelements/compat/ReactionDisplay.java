package org.goldgom.chemicalelements.compat;

import java.util.List;
import java.util.Objects;

/** Public rule documentation, never an executable proposal or an inventory snapshot. */
public record ReactionDisplay(String id, List<String> lines) {
 public ReactionDisplay {
  Objects.requireNonNull(id);lines=List.copyOf(lines);
  if(id.length()>256||lines.size()>32||lines.stream().anyMatch(s->s.length()>1024))throw new IllegalArgumentException("Oversized reaction documentation");
 }
}

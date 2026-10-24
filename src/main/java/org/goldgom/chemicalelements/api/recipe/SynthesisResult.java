package org.goldgom.chemicalelements.api.recipe;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import java.util.*;
public record SynthesisResult(Status status,Optional<ReactionProposal> proposal,List<String> reasons) {
 public enum Status { READY, CONDITIONS_NOT_MET, UNMODELED, NO_SOLUTION, STALE_REGISTRY }
 public SynthesisResult {reasons=List.copyOf(reasons);if((status==Status.READY)!=proposal.isPresent())throw new IllegalArgumentException("status/proposal mismatch");}
}
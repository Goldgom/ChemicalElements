package org.goldgom.chemicalelements.api.reaction;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import java.util.List;
public interface ReactionEngine { List<ReactionProposal> evaluate(ReactionContext context,RegistrySnapshot snapshot); }
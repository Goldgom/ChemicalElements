package org.goldgom.chemicalelements.api.ui;
import java.util.List;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
/** Optional viewer integrations implement this without core depending on JEI/NEI classes. */
public interface RecipeDisplayAdapter {String viewerId();void replaceSnapshot(RegistrySnapshot snapshot,List<ReactionProposal> reactions);void clear();}
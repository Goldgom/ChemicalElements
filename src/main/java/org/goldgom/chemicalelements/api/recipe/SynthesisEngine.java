package org.goldgom.chemicalelements.api.recipe;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
public interface SynthesisEngine {
 SynthesisResult solve(SynthesisRequest request,RegistrySnapshot snapshot);
 default SynthesisResult evaluate(SynthesisRequest request,RegistrySnapshot snapshot){return solve(request,snapshot);}
}
package org.goldgom.chemicalelements.reaction;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.ReactionContext;
import org.goldgom.chemicalelements.api.recipe.*;
import java.math.*;
import java.util.*;
public final class SynthesisServiceTest {
 static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("test",s);}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] ignored){
  var element=new ElementDefinition(id("h"),1,"H",BigDecimal.ONE,DataBasis.GAME_CONVENTION,"synthetic fixture");
  Map<ResourceLocation,SpeciesDefinition> species=new HashMap<>();for(String s:List.of("a","b","c"))species.put(id(s),new SpeciesDefinition(id(s),Map.of(id("h"),1),0,BigDecimal.ONE,Phase.SOLID,DataBasis.GAME_CONVENTION,"synthetic isomer fixture"));
  var snapshot=new RegistrySnapshot(1,"fixture",Map.of(id("h"),element),species,Map.of());
  var domain=new CandidateSolver.Domain(new BigDecimal("290"),new BigDecimal("310"),new BigDecimal("100000"),new BigDecimal("103000"),Set.of());
  var ab=new CandidateSolver.Rule("test:ab","isomerization",Map.of("test:a",1),Map.of("test:b",1),domain,new BigDecimal("-1"),20,"fixture","synthetic Gibbs fixture, not real physical data");
  var bc=new CandidateSolver.Rule("test:bc","isomerization",Map.of("test:b",1),Map.of("test:c",1),domain,new BigDecimal("-2"),20,"fixture","synthetic Gibbs fixture, not real physical data");
  var input=new Composition(Map.of(id("a"),MolAmount.ONE));var request=new SynthesisRequest(List.of(input),1,1,ReactionContext.workbench(input),1);
  var engine=new ModeledSynthesisEngine(()->new ModeledSynthesisEngine.Library("fixture",List.of(new ModeledSynthesisEngine.Pathway(bc,Set.of(),BigDecimal.ZERO,0),new ModeledSynthesisEngine.Pathway(ab,Set.of(),BigDecimal.ZERO,0)),8,100));
  var answer=engine.solve(request,snapshot);check(answer.status()==SynthesisResult.Status.READY);check(answer.proposal().orElseThrow().products().amounts().containsKey(id("c")));check(input.amounts().get(id("a")).equals(MolAmount.ONE));check(answer.proposal().orElseThrow().durationSeconds().equals(new BigDecimal("2")));
  var reversed=new ModeledSynthesisEngine(()->new ModeledSynthesisEngine.Library("fixture",List.of(new ModeledSynthesisEngine.Pathway(ab,Set.of(),BigDecimal.ZERO,0),new ModeledSynthesisEngine.Pathway(bc,Set.of(),BigDecimal.ZERO,0)),8,100));check(reversed.solve(request,snapshot).equals(answer));
  var electricDomain=new CandidateSolver.Domain(domain.minK(),domain.maxK(),domain.minPa(),domain.maxPa(),Set.of("electricity"));var electric=new CandidateSolver.Rule("test:electric","isomerization",ab.reactants(),ab.products(),electricDomain,null,20,"fixture","fixture");
  var restricted=new ModeledSynthesisEngine(()->new ModeledSynthesisEngine.Library("e",List.of(new ModeledSynthesisEngine.Pathway(electric,Set.of(),BigDecimal.ONE,0)),8,100));check(restricted.solve(request,snapshot).status()==SynthesisResult.Status.CONDITIONS_NOT_MET);
  var powered=new ReactionContext(input,new BigDecimal("298.15"),new BigDecimal("101325"),new BigDecimal("0.5"),Set.of(),Set.of("electricity"),BigDecimal.ZERO);
  var partial=restricted.solve(new SynthesisRequest(List.of(input),1,1,powered,1),snapshot);check(partial.status()==SynthesisResult.Status.READY);check(partial.proposal().orElseThrow().products().amounts().get(id("b")).equals(MolAmount.parseDecimal("0.5")));check(partial.proposal().orElseThrow().products().amounts().get(id("a")).equals(MolAmount.parseDecimal("0.5")));
  check(engine.solve(new SynthesisRequest(List.of(input),1,1,ReactionContext.workbench(input),2),snapshot).status()==SynthesisResult.Status.STALE_REGISTRY);
  final int[] captures={0};var atomic=ModeledSynthesisEngine.atomic(()->{captures[0]++;return new ModeledSynthesisEngine.VersionedLibrary(1,new ModeledSynthesisEngine.Library("fixture",List.of(new ModeledSynthesisEngine.Pathway(ab,Set.of(),BigDecimal.ZERO,0)),8,100));});
  check(atomic.solve(request,snapshot).status()==SynthesisResult.Status.READY);check(captures[0]==1);
  var wrongEpoch=new ModeledSynthesisEngine(()->new ModeledSynthesisEngine.Library("fixture",List.of(new ModeledSynthesisEngine.Pathway(ab,Set.of(),BigDecimal.ZERO,0)),8,100),()->2);
  check(wrongEpoch.solve(request,snapshot).status()==SynthesisResult.Status.STALE_REGISTRY);
  final long[] epochRead={0};var racingEpoch=new ModeledSynthesisEngine(()->new ModeledSynthesisEngine.Library("fixture",List.of(new ModeledSynthesisEngine.Pathway(ab,Set.of(),BigDecimal.ZERO,0)),8,100),()->++epochRead[0]);
  check(racingEpoch.solve(request,snapshot).status()==SynthesisResult.Status.STALE_REGISTRY);
  System.out.println("PASS: unified model epoch mismatch/race rejection; actual SynthesisEngine API; multistep reachable product; Gibbs preference; reversed-library determinism; duration; unchanged input; workbench rejection; stale epoch");
 }
}

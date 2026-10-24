package org.goldgom.chemicalelements.reaction;

import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.*;
import org.goldgom.chemicalelements.api.recipe.*;
import java.math.*;
import java.util.*;
import java.util.function.Supplier;

/** Searches reachable product inventories in a finite modeled reaction network, never crafting patterns.
 * Unknown thermodynamics stay unknown. Explicit game priorities only break ties among unknown states. */
public final class ModeledSynthesisEngine implements SynthesisEngine, ReactionEngine {
    public record Pathway(CandidateSolver.Rule rule,Set<String> catalysts,BigDecimal joulesPerMol,int priority){
        public Pathway {catalysts=Set.copyOf(catalysts);Objects.requireNonNull(joulesPerMol);}
    }
    public record Library(String hash,List<Pathway> pathways,int maxDepth,int maxStates){
        public Library {pathways=List.copyOf(pathways);if(maxDepth<1||maxStates<1)throw new IllegalArgumentException("invalid search budget");}
    }
    public record VersionedLibrary(long epoch,Library library) {}
    private Supplier<VersionedLibrary> versioned;
    public static ModeledSynthesisEngine atomic(Supplier<VersionedLibrary> source){
        var engine=new ModeledSynthesisEngine(()->{throw new IllegalStateException("Atomic source required");});
        engine.versioned=Objects.requireNonNull(source);return engine;
    }
    private final Supplier<Library> library;
    private final java.util.function.LongSupplier epoch;
    public ModeledSynthesisEngine(Supplier<Library> library){this(library,()->-1);}
    public ModeledSynthesisEngine(Supplier<Library> library,java.util.function.LongSupplier epoch){this.library=library;this.epoch=epoch;}
    private record Node(Composition inventory,List<String> path,BigDecimal gibbs,boolean known,BigDecimal energy,BigDecimal duration,int priority) {}
    @Override public SynthesisResult solve(SynthesisRequest request,RegistrySnapshot snapshot){
        if(request.registryEpoch()!=snapshot.epoch())return failure(SynthesisResult.Status.STALE_REGISTRY,"Registry changed; inputs were not consumed");
        return search(request.conditions(),snapshot);
    }
    @Override public List<ReactionProposal> evaluate(ReactionContext context,RegistrySnapshot snapshot){return search(context,snapshot).proposal().stream().toList();}
    private SynthesisResult search(ReactionContext context,RegistrySnapshot snapshot){
        final Library lib;
        if(versioned!=null){
            var captured=versioned.get();
            if(captured.epoch()!=snapshot.epoch())return failure(SynthesisResult.Status.STALE_REGISTRY,"Species and reaction model epochs differ; inputs unchanged");
            lib=captured.library();
        }else{
            long beforeEpoch=epoch.getAsLong();
            if(beforeEpoch>=0&&beforeEpoch!=snapshot.epoch())return failure(SynthesisResult.Status.STALE_REGISTRY,"Species and reaction model epochs differ; inputs unchanged");
            lib=library.get();
            if(epoch.getAsLong()!=beforeEpoch)return failure(SynthesisResult.Status.STALE_REGISTRY,"Model changed during snapshot read; retry without consuming inputs");
        }
        if(lib.pathways.isEmpty())return failure(SynthesisResult.Status.UNMODELED,"No reaction library is loaded; inputs unchanged");
        final ElementInventory initial;
        try{initial=context.inputs().inventory(snapshot);}catch(IllegalArgumentException ex){return failure(SynthesisResult.Status.UNMODELED,ex.getMessage());}
        TreeMap<String,CandidateSolver.Species> species=new TreeMap<>();snapshot.species().forEach((id,s)->{TreeMap<String,Integer> atoms=new TreeMap<>();s.atoms().forEach((e,n)->atoms.put(e.toString(),n));species.put(id.toString(),new CandidateSolver.Species(atoms,s.charge(),s.phase().name()));});
        List<Pathway> modeled=lib.pathways.stream().filter(p->species.keySet().containsAll(p.rule.reactants().keySet())&&species.keySet().containsAll(p.rule.products().keySet())).sorted(Comparator.comparing(p->p.rule.id())).toList();
        CandidateSolver solver;
        try{solver=new CandidateSolver(species,modeled.stream().map(Pathway::rule).toList());}catch(IllegalArgumentException ex){return failure(SynthesisResult.Status.UNMODELED,"Invalid library: "+ex.getMessage());}
        Map<String,Pathway> byId=new HashMap<>();modeled.forEach(p->byId.put(p.rule.id(),p));
        Node root=new Node(context.inputs(),List.of(),BigDecimal.ZERO,true,BigDecimal.ZERO,BigDecimal.ZERO,0);
        ArrayDeque<Node> queue=new ArrayDeque<>();queue.add(root);Set<String> seen=new HashSet<>();seen.add(key(root.inventory));List<Node> candidates=new ArrayList<>();TreeSet<String> failures=new TreeSet<>();boolean truncated=false;
        var conditions=new CandidateSolver.Conditions(context.temperatureK(),context.pressurePa(),context.capabilities());
        Set<String> catalysts=new HashSet<>();context.catalysts().forEach(c->catalysts.add(c.toString()));
        while(!queue.isEmpty()){
            Node node=queue.removeFirst();if(node.path.size()>=lib.maxDepth){truncated=true;continue;}
            var result=solver.solve(raw(node.inventory),conditions);
            if(result.status().equals("CONDITIONS_UNMET"))failures.add(result.explanation());
            for(var proposal:result.candidates()){
                Pathway p=byId.get(proposal.rule().id());if(!catalysts.containsAll(p.catalysts)){failures.add(p.rule.id()+": missing catalysts "+p.catalysts);continue;}
                BigInteger extent=proposal.extent();
                if(p.joulesPerMol.signum()>0){
                    BigDecimal remainingEnergy=context.availableEnergyJ().subtract(node.energy).max(BigDecimal.ZERO);
                    BigInteger energyLimit=remainingEnergy.movePointRight(MolAmount.SCALE).divideToIntegralValue(p.joulesPerMol).toBigInteger();
                    extent=extent.min(energyLimit);
                }
                if(extent.signum()==0){failures.add(p.rule.id()+": insufficient supplied energy");continue;}
                for(BigInteger selectedExtent:CompetingExtents.candidates(p.rule,extent,raw(node.inventory),modeled.stream().map(Pathway::rule).toList())){
                extent=selectedExtent;
                BigDecimal mol=new BigDecimal(extent,MolAmount.SCALE);BigDecimal energy=node.energy.add(p.joulesPerMol.multiply(mol));
                Composition next=node.inventory.subtract(stoichiometry(p.rule.reactants(),extent)).add(stoichiometry(p.rule.products(),extent));
                if(!initial.equals(next.inventory(snapshot)))throw new IllegalStateException("solver violated exact element/charge invariant");
                String key=key(next);if(seen.contains(key))continue;
                if(seen.size()>=lib.maxStates){truncated=true;continue;}seen.add(key);
                List<String> path=new ArrayList<>(node.path);path.add(p.rule.id());
                boolean known=node.known&&p.rule.deltaG()!=null;
                BigDecimal gibbs=known?node.gibbs.add(p.rule.deltaG().multiply(mol)):BigDecimal.ZERO;
                Node child=new Node(next,List.copyOf(path),gibbs,known,energy,node.duration.add(BigDecimal.valueOf(p.rule.durationTicks()).divide(BigDecimal.valueOf(20))),node.priority+p.priority);
                candidates.add(child);queue.addLast(child);
                }
            }
        }
        // A truncated search cannot honestly claim the most stable reachable candidate.
        if(truncated)return failure(SynthesisResult.Status.NO_SOLUTION,"Modeled candidate search budget exhausted; no inputs consumed (increase configured search budget)");
        if(candidates.isEmpty())return failure(failures.isEmpty()?SynthesisResult.Status.UNMODELED:SynthesisResult.Status.CONDITIONS_NOT_MET,failures.isEmpty()?"No modeled pathway for these species and phases":String.join("; ",failures));
        candidates.sort(Comparator.<Node,Boolean>comparing(n->!n.known).thenComparing(n->n.gibbs).thenComparingInt(n->n.priority).thenComparing(n->String.join("/",n.path)));
        Node selected=candidates.getFirst();TreeMap<ResourceLocation,MolAmount> gas=new TreeMap<>(),product=new TreeMap<>();
        // All input is reserved; unreacted inventory is explicitly returned, not silently discarded.
        selected.inventory.amounts().forEach((id,n)->{if(snapshot.requireSpecies(id).phase()==Phase.GAS)gas.put(id,n);else product.put(id,n);});
        var proposal=new ReactionProposal(ResourceLocation.fromNamespaceAndPath("chemicalelements","network_synthesis"),context.inputs(),new Composition(product),new Composition(gas),MolAmount.ONE,selected.energy,selected.duration,snapshot.epoch(),selected.known?"modeled_thermodynamics":"explicit_game_priority_no_gibbs_claim",List.of("library="+lib.hash,"path="+String.join(" -> ",selected.path),"All unreacted material is returned in products/byproducts; gas output must be captured", "Inputs retain species and phases; balance alone never enables a path", "Finite candidate extent search includes pairwise competition vertices; not a claim of global nonlinear equilibrium"));
        return new SynthesisResult(SynthesisResult.Status.READY,Optional.of(proposal),proposal.diagnostics());
    }
    private static String key(Composition c){return c.amounts().toString();}
    private static Map<String,BigInteger> raw(Composition c){TreeMap<String,BigInteger> result=new TreeMap<>();c.amounts().forEach((id,n)->result.put(id.toString(),n.quanta()));return result;}
    private static Composition stoichiometry(Map<String,Integer> terms,BigInteger extent){TreeMap<String,BigInteger> amounts=new TreeMap<>();terms.forEach((id,n)->amounts.put(id,extent.multiply(BigInteger.valueOf(n))));return composition(amounts);}
    private static Composition composition(Map<String,BigInteger> c){TreeMap<ResourceLocation,MolAmount> result=new TreeMap<>();c.forEach((id,n)->result.put(ResourceLocation.parse(id),new MolAmount(n)));return new Composition(result);}
    private static SynthesisResult failure(SynthesisResult.Status status,String why){return new SynthesisResult(status,Optional.empty(),List.of(why));}
}

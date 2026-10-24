from pathlib import Path
root=Path(__file__).resolve().parents[2]/'src/main/java/org/goldgom/chemicalelements'
def put(name, body):
 p=root/(name+'.java'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text('package org.goldgom.chemicalelements.'+name.rsplit('/',1)[0].replace('/','.')+';\n'+body,encoding='utf-8')
put('api/chem/MolAmount', '''import java.math.*;
import java.util.Objects;
/** Nonnegative fixed point amount. Scale is a persistent protocol invariant. */
public record MolAmount(BigInteger quanta) implements Comparable<MolAmount> {
 public static final int SCALE=18;
 public static final MolAmount ZERO=new MolAmount(BigInteger.ZERO);
 public static final MolAmount ONE=parseDecimal("1");
 public MolAmount { Objects.requireNonNull(quanta); if(quanta.signum()<0) throw new IllegalArgumentException("negative mol"); }
 public static MolAmount parseDecimal(String s) { return new MolAmount(new BigDecimal(s).movePointRight(SCALE).toBigIntegerExact()); }
 public static MolAmount readBounded(String s) { if(s.length()>80) throw new IllegalArgumentException("amount encoding too long"); var a=parseDecimal(s); if(a.quanta.bitLength()>128) throw new IllegalArgumentException("amount exceeds input limit"); return a; }
 public MolAmount add(MolAmount b){return new MolAmount(quanta.add(b.quanta));}
 public MolAmount subtract(MolAmount b){return new MolAmount(quanta.subtract(b.quanta));}
 public MolAmount multiply(BigInteger n){return new MolAmount(quanta.multiply(n));}
 public boolean isZero(){return quanta.signum()==0;}
 public BigDecimal decimal(){return new BigDecimal(quanta,SCALE);}
 public int compareTo(MolAmount b){return quanta.compareTo(b.quanta);}
 public String toString(){return decimal().toPlainString();}
}''')
put('api/chem/Phase','public enum Phase { SOLID, LIQUID, GAS, AQUEOUS, PLASMA, SPECIAL }')
put('api/chem/DataBasis','public enum DataBasis { EMPIRICAL, APPROXIMATION, GAME_CONVENTION }')
put('api/chem/ElementDefinition','''import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.Objects;
public record ElementDefinition(ResourceLocation id,int atomicNumber,String symbol,BigDecimal molarMass,DataBasis basis,String source) {
 public ElementDefinition { Objects.requireNonNull(id); Objects.requireNonNull(symbol); Objects.requireNonNull(basis); Objects.requireNonNull(source); if(atomicNumber<1||atomicNumber>118||molarMass.signum()<=0)throw new IllegalArgumentException("invalid element"); }
}''')
put('api/chem/SpeciesDefinition','''import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.*;
public record SpeciesDefinition(ResourceLocation id,Map<ResourceLocation,Integer> atoms,int charge,BigDecimal molarMass,Phase phase,DataBasis basis,String source) {
 public SpeciesDefinition { Objects.requireNonNull(id); Objects.requireNonNull(phase); Objects.requireNonNull(basis); Objects.requireNonNull(source); atoms=Collections.unmodifiableMap(new TreeMap<>(atoms)); if(atoms.values().stream().anyMatch(n->n<=0)||molarMass.signum()<0)throw new IllegalArgumentException("invalid species"); if(atoms.isEmpty()&&phase!=Phase.SPECIAL)throw new IllegalArgumentException("empty species requires special carrier"); }
}''')
put('api/chem/ElementInventory','''import net.minecraft.resources.ResourceLocation;
import java.math.BigInteger;
import java.util.*;
/** Atom-mol quanta and signed elementary-charge-mol quanta. */
public record ElementInventory(Map<ResourceLocation,BigInteger> atoms,BigInteger charge) {
 public ElementInventory { var m=new TreeMap<ResourceLocation,BigInteger>(); atoms.forEach((k,v)->{if(v.signum()<0)throw new IllegalArgumentException("negative atoms"); if(v.signum()!=0)m.put(k,v);}); atoms=Collections.unmodifiableMap(m); Objects.requireNonNull(charge); }
}''')
put('api/chem/Composition','''import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import java.math.*;
import java.util.*;
public record Composition(Map<ResourceLocation,MolAmount> amounts) {
 public static final Composition EMPTY=new Composition(Map.of());
 public Composition { var m=new TreeMap<ResourceLocation,MolAmount>(); amounts.forEach((k,v)->{Objects.requireNonNull(k); if(!v.isZero())m.put(k,v);}); amounts=Collections.unmodifiableMap(m); }
 public MolAmount totalMol(){return amounts.values().stream().reduce(MolAmount.ZERO,MolAmount::add);}
 public Composition add(Composition b){var m=new TreeMap<>(amounts); b.amounts.forEach((k,v)->m.merge(k,v,MolAmount::add)); return new Composition(m);}
 public Composition subtract(Composition b){var m=new TreeMap<>(amounts); b.amounts.forEach((k,v)->m.put(k,m.getOrDefault(k,MolAmount.ZERO).subtract(v))); return new Composition(m);}
 public Composition multiply(BigInteger n){var m=new TreeMap<ResourceLocation,MolAmount>(); amounts.forEach((k,v)->m.put(k,v.multiply(n)));return new Composition(m);}
 public ElementInventory inventory(RegistrySnapshot s){var m=new TreeMap<ResourceLocation,BigInteger>(); BigInteger charge=BigInteger.ZERO; for(var e:amounts.entrySet()){var d=s.requireSpecies(e.getKey()); for(var atom:d.atoms().entrySet())m.merge(atom.getKey(),e.getValue().quanta().multiply(BigInteger.valueOf(atom.getValue())),BigInteger::add); charge=charge.add(e.getValue().quanta().multiply(BigInteger.valueOf(d.charge())));} return new ElementInventory(m,charge);}
 public BigDecimal mass(RegistrySnapshot s){BigDecimal mass=BigDecimal.ZERO; for(var e:amounts.entrySet()) mass=mass.add(e.getValue().decimal().multiply(s.requireSpecies(e.getKey()).molarMass()));return mass;}
 public Map<ResourceLocation,BigDecimal> massFractions(RegistrySnapshot s){var m=new TreeMap<ResourceLocation,BigDecimal>(); var total=mass(s); if(total.signum()!=0)amounts.forEach((k,v)->m.put(k,v.decimal().multiply(s.requireSpecies(k).molarMass()).divide(total,MathContext.DECIMAL128)));return Collections.unmodifiableMap(m);}
 public Optional<BigDecimal> meanMolarMass(RegistrySnapshot s){return totalMol().isZero()?Optional.empty():Optional.of(mass(s).divide(totalMol().decimal(),MathContext.DECIMAL128));}
}''')
put('api/data/RegistrySnapshot','''import org.goldgom.chemicalelements.api.chem.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public record RegistrySnapshot(long epoch,String contentHash,Map<ResourceLocation,ElementDefinition> elements,Map<ResourceLocation,SpeciesDefinition> species,Map<ResourceLocation,Composition> itemCompositions) {
 public RegistrySnapshot { if(epoch<0)throw new IllegalArgumentException("negative epoch"); Objects.requireNonNull(contentHash); elements=Map.copyOf(elements);species=Map.copyOf(species);itemCompositions=Map.copyOf(itemCompositions); for(var s:species.values())for(var a:s.atoms().keySet())if(!elements.containsKey(a))throw new IllegalArgumentException("unknown element "+a); for(var c:itemCompositions.values())for(var id:c.amounts().keySet())if(!species.containsKey(id))throw new IllegalArgumentException("unknown species "+id); }
 public SpeciesDefinition requireSpecies(ResourceLocation id){var d=species.get(id);if(d==null)throw new IllegalArgumentException("unmodeled species "+id);return d;}
}''')
put('api/data/ChemistryCatalog','public interface ChemistryCatalog { RegistrySnapshot snapshot(); }')
put('api/thermal/ThermalState','''import java.math.BigDecimal;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.Phase;
public record ThermalState(BigDecimal energyJ,BigDecimal temperatureK,Map<Phase,BigDecimal> phaseFractions,long revision) {
 public static final ThermalState AMBIENT=new ThermalState(BigDecimal.ZERO,new BigDecimal("298.15"),Map.of(),0);
 public ThermalState { Objects.requireNonNull(energyJ); if(temperatureK.signum()<=0||revision<0)throw new IllegalArgumentException("invalid thermal state");phaseFractions=Map.copyOf(phaseFractions); if(phaseFractions.values().stream().anyMatch(v->v.signum()<0||v.compareTo(BigDecimal.ONE)>0))throw new IllegalArgumentException("invalid phase fraction"); if(!phaseFractions.isEmpty()&&phaseFractions.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("phase fractions must sum to one"); }
}''')
put('api/nuclear/NuclideId','''public record NuclideId(int z,int a,int isomer) implements Comparable<NuclideId> {
 public NuclideId {if(z<0||z>118||a<1||a<z||isomer<0)throw new IllegalArgumentException("invalid nuclide");}
 public int compareTo(NuclideId o){int c=Integer.compare(z,o.z);if(c==0)c=Integer.compare(a,o.a);if(c==0)c=Integer.compare(isomer,o.isomer);return c;}
}''')
put('api/nuclear/NuclideInventory','''import java.util.*;
import org.goldgom.chemicalelements.api.chem.MolAmount;
/** Isotope resolution of existing atoms, never extra chemical inventory. */
public record NuclideInventory(Map<NuclideId,MolAmount> amounts) {
 public static final NuclideInventory EMPTY=new NuclideInventory(Map.of());
 public NuclideInventory {amounts=Collections.unmodifiableMap(new TreeMap<>(amounts));}
}''')
put('api/instance/Provenance','''import java.util.*;
public record Provenance(String kind,String source,long seed,long registryEpoch,Map<String,String> metadata) {
 public Provenance {Objects.requireNonNull(kind);Objects.requireNonNull(source);if(registryEpoch<0)throw new IllegalArgumentException("negative epoch");metadata=Map.copyOf(metadata);}
 public static Provenance of(String kind,String source){return new Provenance(kind,source,0,0,Map.of());}
}''')
put('api/fluid/SolutionState','''import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.Provenance;
import java.math.BigDecimal;
import java.util.Objects;
public record SolutionState(MolAmount waterMol,Composition dissolved,Composition suspended,BigDecimal temperatureK,BigDecimal volumeLitres,Provenance origin,long revision) {
 public SolutionState {Objects.requireNonNull(waterMol);Objects.requireNonNull(dissolved);Objects.requireNonNull(suspended);Objects.requireNonNull(origin);if(temperatureK.signum()<=0||volumeLitres.signum()<0||revision<0)throw new IllegalArgumentException("invalid solution"); if(volumeLitres.signum()==0&&(!waterMol.isZero()||!dissolved.amounts().isEmpty()||!suspended.amounts().isEmpty()))throw new IllegalArgumentException("nonempty zero-volume solution");}
 public static SolutionState empty(){return new SolutionState(MolAmount.ZERO,Composition.EMPTY,Composition.EMPTY,new BigDecimal("298.15"),BigDecimal.ZERO,Provenance.of("empty","none"),0);}
}''')
put('api/instance/InstanceRecord','''import java.util.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.api.nuclear.NuclideInventory;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
public record InstanceRecord(UUID id,Composition remaining,Provenance origin,long revision,ThermalState thermal,NuclideInventory nuclides,SolutionState poreSolution,Composition adsorbed) {
 public InstanceRecord {Objects.requireNonNull(id);Objects.requireNonNull(remaining);Objects.requireNonNull(origin);Objects.requireNonNull(thermal);Objects.requireNonNull(nuclides);Objects.requireNonNull(poreSolution);Objects.requireNonNull(adsorbed);if(revision<0)throw new IllegalArgumentException("negative revision");}
 public InstanceRecord(UUID id,Composition remaining,Provenance origin,long revision){this(id,remaining,origin,revision,ThermalState.AMBIENT,NuclideInventory.EMPTY,SolutionState.empty(),Composition.EMPTY);}
}''')
put('api/instance/StackLedger','''import java.util.*;
import org.goldgom.chemicalelements.api.chem.Composition;
public record StackLedger(int schemaVersion,long revision,List<InstanceRecord> instances) {
 public static final int CURRENT_SCHEMA=1;
 public StackLedger {if(schemaVersion!=CURRENT_SCHEMA||revision<0)throw new IllegalArgumentException("unsupported ledger schema/revision");instances=List.copyOf(instances);var ids=new HashSet<UUID>();for(var i:instances)if(!ids.add(i.id()))throw new IllegalArgumentException("duplicate instance identity");}
 public void validateCount(int count){if(instances.size()!=count)throw new IllegalStateException("ledger/count mismatch");}
 public Composition remaining(){return instances.stream().map(InstanceRecord::remaining).reduce(Composition.EMPTY,Composition::add);}
 public Split split(int count){if(count<0||count>instances.size())throw new IllegalArgumentException("invalid split");return new Split(new StackLedger(schemaVersion,Math.addExact(revision,1),instances.subList(0,count)),new StackLedger(schemaVersion,Math.addExact(revision,1),instances.subList(count,instances.size())));}
 public StackLedger append(StackLedger other){var all=new ArrayList<>(instances);all.addAll(other.instances);return new StackLedger(schemaVersion,Math.addExact(revision,1),all);}
 public record Split(StackLedger taken,StackLedger remainder){}
}''')
put('api/world/EnvironmentSample','''import net.minecraft.resources.ResourceLocation;
import java.util.Map;
public record EnvironmentSample(ResourceLocation dimension,ResourceLocation biome,int y,String geology,long seed,Map<String,String> surroundings) {public EnvironmentSample{surroundings=Map.copyOf(surroundings);}}''')
put('api/instance/CreationContext','''import org.goldgom.chemicalelements.api.world.EnvironmentSample;
import java.util.Optional;
public record CreationContext(Provenance origin,Optional<EnvironmentSample> environment,boolean simulation) {}''')
put('api/instance/TransferContext','public record TransferContext(String reason,boolean simulation,long expectedSourceRevision,long expectedTargetRevision) {}')
put('api/instance/Selection','''import java.util.*;
public record Selection(List<UUID> instanceIds,long expectedRevision) {public Selection{instanceIds=List.copyOf(instanceIds);}}''')
put('api/instance/ConsumptionReason','public enum ConsumptionReason { REACTION, DISSOLUTION, CRAFTING, FUEL, EXPLICIT_SINK }')
put('api/tx/TransactionResult','''public record TransactionResult(boolean committed,String code,String detail) {
 public static TransactionResult rejected(String code,String detail){return new TransactionResult(false,code,detail);}
 public static TransactionResult success(){return new TransactionResult(true,"committed","");}
}''')
put('api/tx/ChemistryTransaction','''public interface ChemistryTransaction {
 /** Pure simulation; must not allocate identities, advance RNG or dirty persistence. */
 TransactionResult preview();
 TransactionResult validate();
 TransactionResult commit();
}''')
put('api/instance/StackChemistry','''import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import org.goldgom.chemicalelements.api.tx.ChemistryTransaction;
public interface StackChemistry {
 StackLedger resolve(ItemStack stack,CreationContext context);
 Optional<StackLedger> snapshot(ItemStack stack);
 ItemStack split(ItemStack source,int count);
 ChemistryTransaction transfer(ItemStack source,ItemStack target,int count,TransferContext context);
 ChemistryTransaction consume(ItemStack source,Selection selection,ConsumptionReason reason);
 boolean canMerge(ItemStack a,ItemStack b);
}''')
put('api/fluid/SolutionLocation','''import net.minecraft.resources.ResourceLocation;
public record SolutionLocation(ResourceLocation dimension,long packedPosition,String port) {}''')
put('api/fluid/SolutionAccess','''import java.math.BigDecimal;
import java.util.Optional;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.tx.*;
public interface SolutionAccess {
 Optional<SolutionState> snapshot(SolutionLocation location);
 ChemistryTransaction previewTransfer(SolutionLocation from,SolutionLocation to,BigDecimal litres);
 default TransactionResult commit(ChemistryTransaction plan){return plan.commit();}
 ChemistryTransaction dissolve(SolutionLocation target,Composition solid,BigDecimal seconds);
}''')
put('api/reaction/ReactionContext','''import org.goldgom.chemicalelements.api.chem.*;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.*;
/** Species and phase are retained; element totals alone cannot predict chemistry. */
public record ReactionContext(Composition inputs,BigDecimal temperatureK,BigDecimal pressurePa,BigDecimal availableEnergyJ,Set<ResourceLocation> catalysts,Set<String> capabilities,BigDecimal elapsedSeconds) {
 public ReactionContext {Objects.requireNonNull(inputs); catalysts=Set.copyOf(catalysts);capabilities=Set.copyOf(capabilities);if(temperatureK.signum()<=0||pressurePa.signum()<=0||availableEnergyJ.signum()<0||elapsedSeconds.signum()<0)throw new IllegalArgumentException("invalid reaction conditions");}
 public static ReactionContext workbench(Composition inputs){return new ReactionContext(inputs,new BigDecimal("298.15"),new BigDecimal("101325"),BigDecimal.ZERO,Set.of(),Set.of("ambient_workbench"),BigDecimal.ZERO);}
}''')
put('api/reaction/ReactionProposal','''import org.goldgom.chemicalelements.api.chem.*;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.*;
public record ReactionProposal(ResourceLocation rule,Composition consumed,Composition products,Composition byproducts,MolAmount extent,BigDecimal energyJ,BigDecimal durationSeconds,long registryEpoch,String basis,List<String> diagnostics) {
 public ReactionProposal {diagnostics=List.copyOf(diagnostics);if(durationSeconds.signum()<0||registryEpoch<0)throw new IllegalArgumentException("invalid proposal");}
}''')
put('api/reaction/ReactionEngine','''import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import java.util.List;
public interface ReactionEngine { List<ReactionProposal> evaluate(ReactionContext context,RegistrySnapshot snapshot); }''')
put('api/recipe/SynthesisRequest','''import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.ReactionContext;
import java.util.*;
public record SynthesisRequest(List<Composition> slots,int width,int height,ReactionContext conditions,long registryEpoch) {
 public SynthesisRequest {slots=List.copyOf(slots);if(width<1||height<1||Math.multiplyExact(width,height)!=slots.size()||registryEpoch<0)throw new IllegalArgumentException("invalid grid");if(!aggregate(slots).equals(conditions.inputs()))throw new IllegalArgumentException("conditions input mismatch");}
 private static Composition aggregate(List<Composition> slots){return slots.stream().reduce(Composition.EMPTY,Composition::add);}
 public Composition species(){return aggregate(slots);}
 public ElementInventory elements(RegistrySnapshot snapshot){if(snapshot.epoch()!=registryEpoch)throw new IllegalStateException("stale registry");return species().inventory(snapshot);}
}''')
put('api/recipe/SynthesisResult','''import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import java.util.*;
public record SynthesisResult(Status status,Optional<ReactionProposal> proposal,List<String> reasons) {
 public enum Status { READY, CONDITIONS_NOT_MET, UNMODELED, NO_SOLUTION, STALE_REGISTRY }
 public SynthesisResult {reasons=List.copyOf(reasons);if((status==Status.READY)!=proposal.isPresent())throw new IllegalArgumentException("status/proposal mismatch");}
}''')
put('api/recipe/SynthesisEngine','''import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
public interface SynthesisEngine { SynthesisResult solve(SynthesisRequest request,RegistrySnapshot snapshot); }''')
put('api/ui/RecipeDisplayAdapter','''import java.util.List;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
/** Optional viewer integrations implement this without core depending on JEI/NEI classes. */
public interface RecipeDisplayAdapter {String viewerId();void replaceSnapshot(RegistrySnapshot snapshot,List<ReactionProposal> reactions);void clear();}''')
put('core/ExactAllocator','''import java.math.BigInteger;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.MolAmount;
/** Largest remainder apportionment. Equal remainders use original destination index. */
public final class ExactAllocator {
 private ExactAllocator(){}
 public static List<MolAmount> allocate(MolAmount amount,List<BigInteger> weights){
 if(weights.isEmpty()||weights.stream().anyMatch(w->w.signum()<0))throw new IllegalArgumentException("invalid weights");
 var sum=weights.stream().reduce(BigInteger.ZERO,BigInteger::add);if(sum.signum()==0)throw new IllegalArgumentException("zero weight sum");
 var q=new ArrayList<BigInteger>();var r=new ArrayList<BigInteger>();var used=BigInteger.ZERO;
 for(var w:weights){var qr=amount.quanta().multiply(w).divideAndRemainder(sum);q.add(qr[0]);r.add(qr[1]);used=used.add(qr[0]);}
 var order=new ArrayList<Integer>();for(int i=0;i<weights.size();i++)order.add(i);order.sort((a,b)->{int c=r.get(b).compareTo(r.get(a));return c==0?Integer.compare(a,b):c;});
 int left=amount.quanta().subtract(used).intValueExact();for(int i=0;i<left;i++){int k=order.get(i);q.set(k,q.get(k).add(BigInteger.ONE));}return q.stream().map(MolAmount::new).toList();
 }
}''')
put('core/AtomicLedgerStore','''import java.util.*;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.api.tx.*;
/** Single authority, synchronized atomic replacement. Persistence adapters snapshot after success. */
public final class AtomicLedgerStore {
 private Map<String,StackLedger> ledgers=Map.of();
 private final Set<UUID> committed=new HashSet<>();
 public synchronized void initialize(String key,StackLedger value){if(ledgers.containsKey(key))throw new IllegalStateException("already initialized");var next=new HashMap<>(ledgers);next.put(key,value);ledgers=Map.copyOf(next);}
 public synchronized Map<String,StackLedger> snapshot(){return ledgers;}
 public ChemistryTransaction transfer(UUID transactionId,String from,String to,int count,int capacity){
 final Map<String,StackLedger> before=snapshot();
 return new ChemistryTransaction(){
 private TransactionResult check(){if(committed.contains(transactionId))return TransactionResult.rejected("duplicate","already committed");if(from.equals(to)||count<=0)return TransactionResult.rejected("invalid","invalid transfer");var a=before.get(from);var b=before.get(to);if(a==null||b==null)return TransactionResult.rejected("missing","ledger absent");if(ledgers.get(from)!=a||ledgers.get(to)!=b)return TransactionResult.rejected("stale","ledger changed");if(count>a.instances().size()||(long)b.instances().size()+count>capacity)return TransactionResult.rejected("capacity","insufficient capacity or source");return new TransactionResult(false,"valid","");}
 public TransactionResult preview(){synchronized(AtomicLedgerStore.this){return check();}}
 public TransactionResult validate(){return preview();}
 public TransactionResult commit(){synchronized(AtomicLedgerStore.this){var result=check();if(!result.code().equals("valid"))return result;var split=before.get(from).split(count);var target=before.get(to).append(split.taken());var next=new HashMap<>(ledgers);next.put(from,split.remainder());next.put(to,target);var frozen=Map.copyOf(next);committed.add(transactionId);ledgers=frozen;return TransactionResult.success();}}
 };}
}''')
print('API files generated')

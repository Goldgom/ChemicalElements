from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]
rows='''hydrogen H 1.008
helium He 4.002602
lithium Li 6.94
beryllium Be 9.0121831
boron B 10.81
carbon C 12.011
nitrogen N 14.007
oxygen O 15.999
fluorine F 18.998403163
neon Ne 20.1797
sodium Na 22.98976928
magnesium Mg 24.305
aluminium Al 26.9815385
silicon Si 28.085
phosphorus P 30.973761998
sulfur S 32.06
chlorine Cl 35.45
argon Ar 39.948
potassium K 39.0983
calcium Ca 40.078
scandium Sc 44.955908
titanium Ti 47.867
vanadium V 50.9415
chromium Cr 51.9961
manganese Mn 54.938044
iron Fe 55.845
cobalt Co 58.933194
nickel Ni 58.6934
copper Cu 63.546
zinc Zn 65.38
gallium Ga 69.723
germanium Ge 72.630
arsenic As 74.921595
selenium Se 78.971
bromine Br 79.904
krypton Kr 83.798
rubidium Rb 85.4678
strontium Sr 87.62
yttrium Y 88.90584
zirconium Zr 91.224
niobium Nb 92.90637
molybdenum Mo 95.95
technetium Tc 98
ruthenium Ru 101.07
rhodium Rh 102.90550
palladium Pd 106.42
silver Ag 107.8682
cadmium Cd 112.414
indium In 114.818
tin Sn 118.710
antimony Sb 121.760
tellurium Te 127.60
iodine I 126.90447
xenon Xe 131.293
caesium Cs 132.90545196
barium Ba 137.327
lanthanum La 138.90547
cerium Ce 140.116
praseodymium Pr 140.90766
neodymium Nd 144.242
promethium Pm 145
samarium Sm 150.36
europium Eu 151.964
gadolinium Gd 157.25
terbium Tb 158.92535
dysprosium Dy 162.500
holmium Ho 164.93033
erbium Er 167.259
thulium Tm 168.93422
ytterbium Yb 173.045
lutetium Lu 174.9668
hafnium Hf 178.49
tantalum Ta 180.94788
tungsten W 183.84
rhenium Re 186.207
osmium Os 190.23
iridium Ir 192.217
platinum Pt 195.084
gold Au 196.966569
mercury Hg 200.592
thallium Tl 204.38
lead Pb 207.2
bismuth Bi 208.98040
polonium Po 209
astatine At 210
radon Rn 222
francium Fr 223
radium Ra 226
actinium Ac 227
thorium Th 232.0377
protactinium Pa 231.03588
uranium U 238.02891
neptunium Np 237
plutonium Pu 244
americium Am 243
curium Cm 247
berkelium Bk 247
californium Cf 251
einsteinium Es 252
fermium Fm 257
mendelevium Md 258
nobelium No 259
lawrencium Lr 266
rutherfordium Rf 267
dubnium Db 268
seaborgium Sg 269
bohrium Bh 270
hassium Hs 277
meitnerium Mt 278
darmstadtium Ds 281
roentgenium Rg 282
copernicium Cn 285
nihonium Nh 286
flerovium Fl 289
moscovium Mc 290
livermorium Lv 293
tennessine Ts 294
oganesson Og 294'''.splitlines()
assert len(rows)==118
base=root/'src/main/resources/data/chemicalelements/chemicalelements'
for n,line in enumerate(rows,1):
 name,symbol,mass=line.split(); approx=n in (43,61) or n>=84 and n not in (90,91,92)
 data=dict(schema_version=1,atomic_number=n,symbol=symbol,molar_mass_g_mol=mass,basis='approximation' if approx else 'empirical',source='https://iupac.org/what-we-do/periodic-table-of-elements/',notes='Representative isotope mass number, not standard atomic weight; approximate g/mol.' if approx else 'Conventional abridged atomic weight; natural isotopic composition varies.')
 p=base/'elements'/f'{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n')
 # Atomic component species is an explicit accounting convention, not a claim monatomic hydrogen gas is stable.
 d=dict(schema_version=1,atoms={f'chemicalelements:{name}':1},charge=0,phase='solid',molar_mass_g_mol=mass,basis='game_convention',source='ChemicalElements atomic-component bookkeeping v1',notes='One mol of atoms in an elemental sample; actual molecular/allotropic phase requires a separate species.')
 p=base/'species'/f'{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,indent=2)+'\n')
for name,atoms,charge,phase,mass in [('water',{'hydrogen':2,'oxygen':1},0,'liquid','18.015'),('sodium_ion',{'sodium':1},1,'aqueous','22.98976928'),('chloride_ion',{'chlorine':1},-1,'aqueous','35.45'),('hydrogen_ion',{'hydrogen':1},1,'aqueous','1.008'),('hydroxide_ion',{'oxygen':1,'hydrogen':1},-1,'aqueous','17.007'),('sodium_chloride',{'sodium':1,'chlorine':1},0,'solid','58.43976928'),('silica',{'silicon':1,'oxygen':2},0,'solid','60.083'),('cellulose',{'carbon':6,'hydrogen':10,'oxygen':5},0,'solid','162.141')]:
 d=dict(schema_version=1,atoms={f'chemicalelements:{k}':v for k,v in atoms.items()},charge=charge,phase=phase,molar_mass_g_mol=mass,basis='approximation',source='Stoichiometric formula and sum of bundled abridged atomic weights; ion electron mass neglected')
 (base/'species'/f'{name}.json').write_text(json.dumps(d,indent=2)+'\n')
java=root/'src/main/java/org/goldgom/chemicalelements'
def put(name,body):
 p=java/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('package org.goldgom.chemicalelements.'+name.rsplit('/',1)[0].replace('/','.')+';\n'+body,encoding='utf-8')
names=','.join('"'+line.split()[0]+'"' for line in rows)
put('registry/ElementItems','''import java.util.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.Item;
public final class ElementItems {
 public static final List<String> NAMES=List.of('''+names+''');
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("chemicalelements");
 public static final Map<String,DeferredItem<Item>> ELEMENTS;
 static {var m=new LinkedHashMap<String,DeferredItem<Item>>(); for(String name:NAMES)m.put(name,ITEMS.registerSimpleItem("element_"+name,new Item.Properties())); ELEMENTS=Collections.unmodifiableMap(m);}
 public static void bootstrap(IEventBus bus){ITEMS.register(bus);}
 private ElementItems(){}
}''')
put('data/MigrationChain','''import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.UnaryOperator;
/** Copy before migration: failed/newer schemas never become empty reconstructed inventories. */
public final class MigrationChain {
 private final int current; private final Map<Integer,UnaryOperator<JsonObject>> steps;
 public MigrationChain(int current,Map<Integer,UnaryOperator<JsonObject>> steps){if(current<1)throw new IllegalArgumentException();this.current=current;this.steps=Map.copyOf(steps);}
 public JsonObject migrate(JsonObject input){var result=input.deepCopy();int version=result.get("schema_version").getAsInt();if(version<1||version>current)throw new IllegalArgumentException("unsupported schema "+version);while(version<current){var step=steps.get(version);if(step==null)throw new IllegalArgumentException("missing migration "+version);result=Objects.requireNonNull(step.apply(result)).deepCopy();if(result.get("schema_version").getAsInt()!=version+1)throw new IllegalArgumentException("migration must advance one version");version++;}return result;}
}''')
put('data/CatalogParser','''import com.google.gson.*;
import java.util.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
public final class CatalogParser {
 private CatalogParser(){}
 private static final MigrationChain MIGRATION=new MigrationChain(1,Map.of());
 public static RegistrySnapshot parse(long epoch,Map<ResourceLocation,JsonElement> files){
 var elements=new TreeMap<ResourceLocation,ElementDefinition>();var species=new TreeMap<ResourceLocation,SpeciesDefinition>();var items=new TreeMap<ResourceLocation,Composition>();var priorities=new HashMap<ResourceLocation,Integer>();var ordered=new TreeMap<>(files);
 for(var e:ordered.entrySet())if(e.getKey().getPath().startsWith("elements/")){var d=object(e);var id=id(e.getKey(),"elements/");elements.put(id,new ElementDefinition(id,d.get("atomic_number").getAsInt(),d.get("symbol").getAsString(),decimal(d,"molar_mass_g_mol"),basis(d),d.get("source").getAsString()));}
 var numbers=new HashSet<Integer>();for(var d:elements.values())if(!numbers.add(d.atomicNumber()))throw new IllegalArgumentException("duplicate atomic number "+d.atomicNumber());if(numbers.size()!=118)throw new IllegalArgumentException("expected all 118 elements, got "+numbers.size());
 for(var e:ordered.entrySet())if(e.getKey().getPath().startsWith("species/")){var d=object(e);var id=id(e.getKey(),"species/");var atoms=new TreeMap<ResourceLocation,Integer>();d.getAsJsonObject("atoms").entrySet().forEach(a->atoms.put(ResourceLocation.parse(a.getKey()),a.getValue().getAsInt()));species.put(id,new SpeciesDefinition(id,atoms,d.get("charge").getAsInt(),decimal(d,"molar_mass_g_mol"),Phase.valueOf(d.get("phase").getAsString().toUpperCase(Locale.ROOT)),basis(d),d.get("source").getAsString()));}
 for(var e:ordered.entrySet())if(e.getKey().getPath().startsWith("item_compositions/")){var d=object(e);basis(d);var m=new TreeMap<ResourceLocation,MolAmount>();d.getAsJsonObject("per_instance").entrySet().forEach(a->m.put(ResourceLocation.parse(a.getKey()),MolAmount.readBounded(a.getValue().getAsString())));var c=new Composition(m);int priority=d.get("priority").getAsInt();for(var value:d.getAsJsonObject("match").getAsJsonArray("items")){var id=ResourceLocation.parse(value.getAsString());var old=priorities.get(id);if(old!=null&&old==priority)throw new IllegalArgumentException("conflicting composition "+id+" at "+e.getKey());if(old==null||priority>old){priorities.put(id,priority);items.put(id,c);}}}
 try{var digest=MessageDigest.getInstance("SHA-256");ordered.forEach((k,v)->{digest.update(k.toString().getBytes(StandardCharsets.UTF_8));digest.update(v.toString().getBytes(StandardCharsets.UTF_8));});return new RegistrySnapshot(epoch,HexFormat.of().formatHex(digest.digest()),elements,species,items);}catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}
 }
 private static JsonObject object(Map.Entry<ResourceLocation,JsonElement> e){try{return MIGRATION.migrate(e.getValue().getAsJsonObject());}catch(RuntimeException ex){throw new IllegalArgumentException(e.getKey()+": "+ex.getMessage(),ex);}}
 private static ResourceLocation id(ResourceLocation file,String prefix){return ResourceLocation.fromNamespaceAndPath(file.getNamespace(),file.getPath().substring(prefix.length()));}
 private static BigDecimal decimal(JsonObject d,String key){if(!d.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be a decimal string");return new BigDecimal(d.get(key).getAsString());}
 private static DataBasis basis(JsonObject d){return DataBasis.valueOf(d.get("basis").getAsString().toUpperCase(Locale.ROOT));}
}''')
put('data/AtomicChemistryCatalog','''import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.data.*;
import org.goldgom.chemicalelements.api.chem.SpeciesDefinition;
public final class AtomicChemistryCatalog implements ChemistryCatalog {
 public static final AtomicChemistryCatalog INSTANCE=new AtomicChemistryCatalog();
 private final AtomicReference<RegistrySnapshot> current=new AtomicReference<>(new RegistrySnapshot(0,"uninitialized",Map.of(),Map.of(),Map.of()));
 private final Map<ResourceLocation,SpeciesDefinition> tombstones=new HashMap<>();
 public RegistrySnapshot snapshot(){return current.get();}
 public synchronized RegistrySnapshot reload(Map<ResourceLocation,JsonElement> files){var old=current.get();var next=CatalogParser.parse(Math.addExact(old.epoch(),1),files);old.species().forEach((id,d)->{if(!next.species().containsKey(id))tombstones.put(id,d);});current.set(next);return next;}
 public synchronized Optional<SpeciesDefinition> orphaned(ResourceLocation id){return Optional.ofNullable(tombstones.get(id));}
}''')
put('data/ChemistryReloadListener','''import com.google.gson.*;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import com.mojang.logging.LogUtils;
public final class ChemistryReloadListener extends SimpleJsonResourceReloadListener {
 public ChemistryReloadListener(){super(new Gson(),"chemicalelements");}
 protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
 try{var next=AtomicChemistryCatalog.INSTANCE.reload(files);LogUtils.getLogger().info("Chemical catalog epoch {}: {} elements, {} species, {} item compositions",next.epoch(),next.elements().size(),next.species().size(),next.itemCompositions().size());}
 catch(RuntimeException ex){LogUtils.getLogger().error("Chemistry reload rejected; previous catalog preserved",ex);throw ex;}
 }
}''')
print('118 elements, 126 species, registry and reload classes written')

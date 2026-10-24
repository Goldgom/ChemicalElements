package org.goldgom.chemicalelements.core;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.recipe.*;
import org.goldgom.chemicalelements.api.reaction.*;
import org.goldgom.chemicalelements.data.*;
import java.math.*;
import java.nio.file.*;
import java.util.*;

/** Executable assertion suite without network-resolved test frameworks. */
public final class CoreVerification {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static void rejects(Runnable run,String message){checks++;try{run.run();}catch(RuntimeException expected){return;}throw new AssertionError(message);}
 private static ResourceLocation id(String path){return ResourceLocation.parse("chemicalelements:"+path);}
 public static void main(String[] args)throws Exception{
  check(MolAmount.parseDecimal("0.000000000000000001").quanta().equals(BigInteger.ONE),"quantum");
  rejects(()->MolAmount.parseDecimal("-1"),"negative");rejects(()->MolAmount.parseDecimal("1e-19"),"precision");
  rejects(()->MolAmount.ZERO.subtract(MolAmount.ONE),"underflow");
  var huge=new MolAmount(BigInteger.ONE.shiftLeft(1024));check(huge.add(huge).subtract(huge).equals(huge),"unbounded arithmetic");
  rejects(()->MolAmount.readBounded("1e1000"),"bounded input");
  check(MolAmount.parseDecimal(huge.toString()).equals(huge),"decimal serialization");
  var random=new Random(91817);
  for(int t=0;t<1000;t++){var n=new MolAmount(new BigInteger(160,random));var weights=List.of(BigInteger.valueOf(random.nextInt(100)+1),BigInteger.valueOf(random.nextInt(100)+1),BigInteger.valueOf(random.nextInt(100)+1));var shares=ExactAllocator.allocate(n,weights);check(shares.stream().reduce(MolAmount.ZERO,MolAmount::add).equals(n),"allocation conservation");check(shares.equals(ExactAllocator.allocate(n,weights)),"allocation deterministic");}
  check(ExactAllocator.allocate(new MolAmount(BigInteger.ONE),List.of(BigInteger.ONE,BigInteger.ONE)).getFirst().quanta().equals(BigInteger.ONE),"tie order");
  var resources=Path.of("src/main/resources/data/chemicalelements/chemicalelements");var files=new TreeMap<ResourceLocation,JsonElement>();try(var paths=Files.walk(resources)){paths.filter(p->p.toString().endsWith(".json")).forEach(p->{try{var name=resources.relativize(p).toString().replace('\\','/');files.put(id(name.substring(0,name.length()-5)),JsonParser.parseString(Files.readString(p)));}catch(Exception e){throw new RuntimeException(e);}});}
  var catalog=new AtomicChemistryCatalog();var snapshot=catalog.reload(files);check(snapshot.elements().size()==118,"118 elements");check(snapshot.species().size()>=126,"species");
  var water=new Composition(Map.of(id("water"),MolAmount.ONE));var inventory=water.inventory(snapshot);check(inventory.atoms().get(id("hydrogen")).equals(MolAmount.ONE.quanta().multiply(BigInteger.TWO)),"water H balance");check(water.mass(snapshot).compareTo(new BigDecimal("18.015"))==0,"water mass");check(water.massFractions(snapshot).get(id("water")).compareTo(BigDecimal.ONE)==0,"mass fraction");
  var salt=new Composition(Map.of(id("sodium_ion"),MolAmount.ONE,id("chloride_ion"),MolAmount.ONE));check(salt.inventory(snapshot).charge().signum()==0,"charge balance");
  rejects(()->water.amounts().put(id("carbon"),MolAmount.ONE),"immutable composition");rejects(()->water.subtract(new Composition(Map.of(id("water"),MolAmount.parseDecimal("2")))),"negative composition");
  var bad=new TreeMap<>(files);var json=bad.get(id("elements/hydrogen")).deepCopy().getAsJsonObject();json.addProperty("atomic_number",2);bad.put(id("elements/hydrogen"),json);rejects(()->catalog.reload(bad),"duplicate atomic number");check(catalog.snapshot()==snapshot,"failed reload preserves identity");
  var bundle=catalog.bundle();
  check(bundle.sourcePacks().size()==files.size(),"every resource has explicit programmatic provenance");
  rejects(()->bundle.sourcePacks().clear(),"source provenance immutable");
  var sourcedCatalog=new AtomicChemistryCatalog();var sourcePacks=new TreeMap<ResourceLocation,String>();
  files.keySet().forEach(key->sourcePacks.put(key,"test:base"));
  sourcedCatalog.reload(files,sourcePacks);var sourcedBundle=sourcedCatalog.bundle();
  sourcePacks.replaceAll((key,value)->"test:override");
  check(sourcedBundle.sourcePacks().values().stream().allMatch("test:base"::equals),"caller mutation cannot change published provenance");
  var missingSource=new TreeMap<>(sourcePacks);missingSource.pollFirstEntry();
  rejects(()->sourcedCatalog.reload(files,missingSource),"incomplete provenance rejected");
  check(sourcedCatalog.bundle()==sourcedBundle,"provenance failure preserves entire bundle");
  rejects(()->sourcedCatalog.reload(bad,sourcePacks),"invalid definitions with new provenance rejected");
  check(sourcedCatalog.bundle()==sourcedBundle,"invalid definitions cannot publish new provenance");
  sourcedCatalog.reload(files,sourcePacks);
  check(sourcedCatalog.bundle().epoch()==sourcedBundle.epoch()+1&&sourcedCatalog.bundle().sourcePacks().values().stream().allMatch("test:override"::equals),"selected pack provenance changes with successful epoch only");
  check(bundle.epoch()==snapshot.epoch()&&!bundle.reactions().pathways().isEmpty()&&!bundle.solubility().isEmpty(),"one epoch includes production reaction and solubility data");
  var badReaction=new TreeMap<>(files);var reactionId=files.keySet().stream().filter(k->k.getPath().startsWith("reactions/")).findFirst().orElseThrow();
  var reaction=files.get(reactionId).deepCopy().getAsJsonObject();reaction.getAsJsonObject("products").addProperty("chemicalelements:water",999);badReaction.put(reactionId,reaction);
  rejects(()->catalog.reload(badReaction),"unbalanced reaction rejected before publish");check(catalog.bundle()==bundle,"bad reaction preserves entire bundle identity");
  var badSolubility=new TreeMap<>(files);var saltId=files.keySet().stream().filter(k->k.getPath().startsWith("solubility/")).findFirst().orElseThrow();
  var solubility=files.get(saltId).deepCopy().getAsJsonObject();solubility.getAsJsonObject("ions").addProperty("chemicalelements:hydrogen_ion",999);badSolubility.put(saltId,solubility);
  rejects(()->catalog.reload(badSolubility),"unbalanced solubility rejected before publish");check(catalog.bundle()==bundle,"bad solubility preserves entire bundle identity");
  var migration=new MigrationChain(1,Map.of());var future=JsonParser.parseString("{\"schema_version\":2}").getAsJsonObject();rejects(()->migration.migrate(future),"future schema");
  for(String malformed:List.of("1.5","1e0","4294967297","\"1\"","null","0","-1")){var invalid=JsonParser.parseString("{\"schema_version\":"+malformed+"}").getAsJsonObject();rejects(()->migration.migrate(invalid),"exact migration schema "+malformed);}
  var original=JsonParser.parseString("{\"schema_version\":1,\"payload\":{\"matter\":\"9007199254740993\"}}").getAsJsonObject();var beforeMigration=original.deepCopy();
  var failingMigration=new MigrationChain(2,Map.of(1,j->{j.addProperty("schema_version",2.5);j.remove("payload");return j;}));rejects(()->failingMigration.migrate(original),"fractional step schema rejected");check(original.equals(beforeMigration),"failed migration preserves original payload");
  var validMigration=new MigrationChain(2,Map.of(1,j->{j.addProperty("schema_version",2);return j;}));var migrated=validMigration.migrate(original);check(migrated.get("payload").equals(original.get("payload")),"migration preserves exact nested payload");check(original.equals(beforeMigration),"successful migration copies original");
  var list=new ArrayList<InstanceRecord>();for(int i=0;i<64;i++)list.add(new InstanceRecord(new UUID(0,i+1),water.multiply(BigInteger.valueOf(i+1)),Provenance.of("test","fixture"),0));
  var ledger=new StackLedger(1,0,list);var split=ledger.split(17);check(split.taken().instances().size()==17,"split count");check(split.taken().remaining().add(split.remainder().remaining()).equals(ledger.remaining()),"split conservation");check(split.taken().append(split.remainder()).instances().equals(ledger.instances()),"ordered merge");rejects(()->ledger.append(ledger),"duplicate IDs");
  check(LedgerJson.decode(LedgerJson.encode(ledger)).equals(ledger),"full ledger JSON roundtrip");
  var hugeLedger=new StackLedger(1,0,List.of(new InstanceRecord(new UUID(9,9),new Composition(Map.of(id("water"),huge)),Provenance.of("test","large inventory"),0)));
  check(LedgerJson.decode(LedgerJson.encode(hugeLedger)).equals(hugeLedger),"1025-bit inventory persistence roundtrip");
  var richAmount=new Composition(Map.of(id("water"),huge));
  var richOrigin=new Provenance("test","rich fixture",17,snapshot.epoch(),Map.of("biome","minecraft:river"));
  var richThermal=new org.goldgom.chemicalelements.api.thermal.ThermalState(new BigDecimal("123.456789"),new BigDecimal("320.125"),Map.of(Phase.LIQUID,BigDecimal.ONE),7);
  var richNuclear=new org.goldgom.chemicalelements.api.nuclear.NuclideInventory(Map.of(new org.goldgom.chemicalelements.api.nuclear.NuclideId(1,2,0),huge));
  var richSolution=new org.goldgom.chemicalelements.api.fluid.SolutionState(huge,salt,richAmount,new BigDecimal("320.125"),new BigDecimal("19.75"),richOrigin,8);
  var richLedger=new StackLedger(1,9,List.of(new InstanceRecord(new UUID(10,10),richAmount,richOrigin,6,richThermal,richNuclear,richSolution,richAmount)));
  check(LedgerJson.decode(LedgerJson.encode(richLedger)).equals(richLedger),"1025-bit all nonempty fields JSON roundtrip");
  var malicious=LedgerJson.encode(richLedger);malicious.getAsJsonArray("instances").get(0).getAsJsonObject().getAsJsonObject("remaining").addProperty("chemicalelements:water","1".repeat(LedgerJson.MAX_STORED_AMOUNT_CHARACTERS+1));
  rejects(()->LedgerJson.decode(malicious),"oversized stored number rejected before BigInteger allocation");
  var futureLedger=LedgerJson.encode(ledger);futureLedger.addProperty("schema_version",99);rejects(()->LedgerJson.decode(futureLedger),"future ledger schema rejected");
  for(String invalid:List.of("1.5","4294967297","1e0")){
   var malformed=LedgerJson.encode(richLedger);malformed.add("schema_version",JsonParser.parseString(invalid));
   String beforeDecode=malformed.toString();rejects(()->LedgerJson.decode(malformed),"nonexact schema rejected: "+invalid);
   check(malformed.toString().equals(beforeDecode),"decode rejection keeps original JSON");
  }
  for(String invalid:List.of("0.5","18446744073709551616")){
   var malformed=LedgerJson.encode(richLedger);malformed.add("revision",JsonParser.parseString(invalid));
   rejects(()->LedgerJson.decode(malformed),"nonexact revision rejected: "+invalid);
  }
  var invalidNuclide=LedgerJson.encode(richLedger);invalidNuclide.getAsJsonArray("instances").get(0).getAsJsonObject().getAsJsonArray("nuclides").get(0).getAsJsonObject().addProperty("z",4294967297L);
  rejects(()->LedgerJson.decode(invalidNuclide),"nuclide id may not wrap into valid Z");
  rejects(()->MolAmount.readBounded("1e2147483647"),"extreme exponent rejected before allocation");
  var store=new AtomicLedgerStore();store.initialize("a",ledger);store.initialize("b",new StackLedger(1,0,List.of()));var before=store.snapshot();var tx=store.transfer(new UUID(1,1),"a","b",10,64);check(tx.preview().code().equals("valid"),"preview");check(store.snapshot()==before,"preview side effect free");check(tx.commit().committed(),"commit");check(!tx.commit().committed(),"repeated commit denied");
  var beforeFailure=store.snapshot();var full=store.transfer(new UUID(1,2),"a","b",10,10);check(!full.commit().committed(),"capacity denied");check(store.snapshot()==beforeFailure,"failure atomic");
  var stale=store.transfer(new UUID(1,3),"a","b",1,64);check(store.transfer(new UUID(1,4),"a","b",1,64).commit().committed(),"concurrent first");check(stale.commit().code().equals("stale"),"stale revision denied");
  var slots=Collections.nCopies(25,water);var context=ReactionContext.workbench(water.multiply(BigInteger.valueOf(25)));var request=new SynthesisRequest(slots,5,5,context,snapshot.epoch());check(request.species().equals(context.inputs()),"5x5 aggregation");check(context.availableEnergyJ().signum()==0&&context.catalysts().isEmpty(),"workbench no hidden conditions");
  var craft=ConservativeCrafting.rewrite(richAmount.add(salt),List.of(salt),Collections.nCopies(4,water),snapshot);
  check(craft.exits().equals(richAmount.add(salt)),"craft rewrite preserves exact species and containers");
  check(craft.products().size()==4&&craft.containers().equals(salt),"craft output count and container reservation");
  check(craft.products().stream().reduce(Composition.EMPTY,Composition::add).equals(richAmount),"craft 1025bit remainder distribution");
  rejects(()->ConservativeCrafting.rewrite(water,List.of(salt),List.of(water),snapshot),"unavailable dynamic container rejected");
  rejects(()->ConservativeCrafting.rewrite(water,List.of(),List.of(water,Composition.EMPTY),snapshot),"mixed unmodeled output rejected");
  rejects(()->ConservativeCrafting.rewrite(water,List.of(),List.of(),snapshot),"empty product list rejected");
  var quantumWater=new Composition(Map.of(id("water"),new MolAmount(BigInteger.ONE)));
  var quantumCraft=ConservativeCrafting.rewrite(quantumWater,List.of(),List.of(water,water),snapshot);
  check(quantumCraft.exits().equals(quantumWater),"one quantum recipe exits exact");
  check(quantumCraft.products().getFirst().equals(quantumWater),"one quantum allocation stable slot order");
  check(quantumCraft.epoch()==snapshot.epoch(),"craft binds captured epoch");
  var ambient=ReactionContext.workbench(water);
  var goodPlan=new ConservativeRecipePlan(water,water,Composition.EMPTY,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,snapshot.epoch(),List.of());
  goodPlan.validate(snapshot,ambient);check(true,"balanced ambient plan accepted");
  rejects(()->new ConservativeRecipePlan(water,Composition.EMPTY,Composition.EMPTY,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,snapshot.epoch(),List.of()).validate(snapshot,ambient),"matter disappearance rejected");
  rejects(()->new ConservativeRecipePlan(water,water,Composition.EMPTY,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ONE,snapshot.epoch(),List.of()).validate(snapshot,ambient),"reaction time required");
  rejects(()->new ConservativeRecipePlan(water,water,Composition.EMPTY,Composition.EMPTY,BigDecimal.ONE,BigDecimal.ZERO,snapshot.epoch(),List.of()).validate(snapshot,ambient),"workbench energy missing");
  rejects(()->new ConservativeRecipePlan(water,water,Composition.EMPTY,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,snapshot.epoch(),List.of("electrolysis")).validate(snapshot,ambient),"workbench capability missing");
  System.out.println("CORE VERIFICATION PASSED checks="+checks+" elements="+snapshot.elements().size()+" species="+snapshot.species().size()+" catalogSha256="+snapshot.contentHash());
 }
}

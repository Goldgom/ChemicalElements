package org.goldgom.chemicalelements.data;

import com.google.gson.*;
import java.util.*;
import java.math.BigDecimal;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.api.nuclear.*;

/** Explicit wire schema: numbers representing matter are decimal strings, never JSON doubles. */
public final class LedgerJson {
 private LedgerJson(){}
 public static JsonObject encode(StackLedger ledger){
  var root=new JsonObject();root.addProperty("schema_version",ledger.schemaVersion());root.addProperty("revision",ledger.revision());var instances=new JsonArray();
  for(var i:ledger.instances()){
   var row=new JsonObject();row.addProperty("id",i.id().toString());row.addProperty("revision",i.revision());row.add("remaining",composition(i.remaining()));row.add("origin",origin(i.origin()));row.add("adsorbed",composition(i.adsorbed()));
   var heat=new JsonObject();heat.addProperty("energy_j",i.thermal().energyJ().toPlainString());heat.addProperty("temperature_k",i.thermal().temperatureK().toPlainString());heat.addProperty("revision",i.thermal().revision());var phases=new JsonObject();i.thermal().phaseFractions().forEach((k,v)->phases.addProperty(k.name(),v.toPlainString()));heat.add("phases",phases);row.add("thermal",heat);
   var nuclides=new JsonArray();i.nuclides().amounts().forEach((k,v)->{var n=new JsonObject();n.addProperty("z",k.z());n.addProperty("a",k.a());n.addProperty("isomer",k.isomer());n.addProperty("mol",v.toString());nuclides.add(n);});row.add("nuclides",nuclides);
   var p=i.poreSolution();var pore=new JsonObject();pore.addProperty("water_mol",p.waterMol().toString());pore.add("dissolved",composition(p.dissolved()));pore.add("suspended",composition(p.suspended()));pore.addProperty("temperature_k",p.temperatureK().toPlainString());pore.addProperty("volume_l",p.volumeLitres().toPlainString());pore.add("origin",origin(p.origin()));pore.addProperty("revision",p.revision());row.add("pore_solution",pore);instances.add(row);
  }root.add("instances",instances);return root;
 }
 /** Transport/storage safety budget, not the 128-bit user-input amount limit. */
 public static final int MAX_STORED_AMOUNT_CHARACTERS=16384;
 public static StackLedger decode(JsonObject root){
  int schema=integer(root.get("schema_version"));if(schema!=StackLedger.CURRENT_SCHEMA)throw new IllegalArgumentException("unsupported ledger schema "+schema);
  var records=new ArrayList<InstanceRecord>();var array=root.getAsJsonArray("instances");if(array.size()>4096)throw new IllegalArgumentException("oversized ledger");
  for(var element:array){var row=element.getAsJsonObject();var h=row.getAsJsonObject("thermal");var phases=new EnumMap<Phase,BigDecimal>(Phase.class);h.getAsJsonObject("phases").entrySet().forEach(e->phases.put(Phase.valueOf(e.getKey()),decimal(e.getValue())));var thermal=new ThermalState(decimal(h.get("energy_j")),decimal(h.get("temperature_k")),phases,revision(h.get("revision")));
   var nuclides=new TreeMap<NuclideId,MolAmount>();for(var n:row.getAsJsonArray("nuclides")){var d=n.getAsJsonObject();var id=new NuclideId(integer(d.get("z")),integer(d.get("a")),integer(d.get("isomer")));if(nuclides.put(id,amount(d.get("mol")))!=null)throw new IllegalArgumentException("duplicate nuclide");}
   var p=row.getAsJsonObject("pore_solution");var pore=new SolutionState(amount(p.get("water_mol")),composition(p.getAsJsonObject("dissolved")),composition(p.getAsJsonObject("suspended")),decimal(p.get("temperature_k")),decimal(p.get("volume_l")),origin(p.getAsJsonObject("origin")),revision(p.get("revision")));
   records.add(new InstanceRecord(UUID.fromString(row.get("id").getAsString()),composition(row.getAsJsonObject("remaining")),origin(row.getAsJsonObject("origin")),revision(row.get("revision")),thermal,new NuclideInventory(nuclides),pore,composition(row.getAsJsonObject("adsorbed"))));
  }return new StackLedger(schema,revision(root.get("revision")),records);
 }
 private static JsonObject composition(Composition c){var j=new JsonObject();c.amounts().forEach((k,v)->j.addProperty(k.toString(),v.toString()));return j;}
 private static Composition composition(JsonObject j){if(j.size()>4096)throw new IllegalArgumentException("oversized composition");var m=new TreeMap<ResourceLocation,MolAmount>();j.entrySet().forEach(e->m.put(ResourceLocation.parse(e.getKey()),amount(e.getValue())));return new Composition(m);}
 private static JsonObject origin(Provenance p){var j=new JsonObject();j.addProperty("kind",p.kind());j.addProperty("source",p.source());j.addProperty("seed",p.seed());j.addProperty("registry_epoch",p.registryEpoch());var m=new JsonObject();p.metadata().forEach(m::addProperty);j.add("metadata",m);return j;}
 private static Provenance origin(JsonObject j){var m=new TreeMap<String,String>();j.getAsJsonObject("metadata").entrySet().forEach(e->m.put(e.getKey(),e.getValue().getAsString()));return new Provenance(j.get("kind").getAsString(),j.get("source").getAsString(),revision(j.get("seed")),revision(j.get("registry_epoch")),m);}
 private static MolAmount amount(JsonElement e){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("mol must be string");String text=e.getAsString();if(text.length()>MAX_STORED_AMOUNT_CHARACTERS)throw new IllegalArgumentException("stored mol encoding too long");if(!text.matches("[0-9]+(?:\\.[0-9]{1,18})?"))throw new IllegalArgumentException("stored mol must be nonnegative plain decimal");return MolAmount.parseDecimal(text);}
 /** Integer metadata must never wrap or truncate: it participates in CAS and schema selection. */
 private static long revision(JsonElement e){return exactInteger(e).longValueExact();}
 private static int integer(JsonElement e){return exactInteger(e).intValueExact();}
 private static java.math.BigInteger exactInteger(JsonElement e){
  if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("integer metadata must be JSON number");
  String text=e.getAsString();if(text.length()>32||!text.matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException("invalid integer metadata");
  return new java.math.BigInteger(text);
 }
 private static BigDecimal decimal(JsonElement e){if(!e.getAsJsonPrimitive().isString()||e.getAsString().length()>100)throw new IllegalArgumentException("invalid decimal string");var value=new BigDecimal(e.getAsString());if(Math.abs((long)value.scale())>1000||value.precision()>128)throw new IllegalArgumentException("decimal magnitude limit");return value;}
}

package org.goldgom.chemicalelements.instance;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.data.LedgerJson;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Versioned canonical request encoding. Pure: does not reserve or create inventory.
 * Object keys and destination lists are sorted; instance and diagnostic order are significant.
 * Full ItemStack codec output binds every component, not just item/count or chemistry.
 */
public final class TransactionEncoding {
 private TransactionEncoding() {}
 public static String proposal(ReactionProposal p) {
  var j=new JsonObject();j.addProperty("rule",p.rule().toString());
  j.add("consumed",composition(p.consumed()));j.add("products",composition(p.products()));j.add("byproducts",composition(p.byproducts()));
  j.addProperty("extent",p.extent().quanta().toString());j.addProperty("energy_j",p.energyJ().toPlainString());
  j.addProperty("duration_seconds",p.durationSeconds().toPlainString());j.addProperty("epoch",p.registryEpoch());j.addProperty("basis",p.basis());
  var d=new JsonArray();p.diagnostics().forEach(d::add);j.add("diagnostics",d);return canonical(j);
 }
 public static String request(Request r, HolderLookup.Provider registries) {
  if(!proposal(r.proposal()).equals(r.canonicalProposal()))throw new IllegalArgumentException("proposal encoding mismatch");
  if(r.catalog().epoch()!=r.proposal().registryEpoch())throw new IllegalArgumentException("proposal catalog mismatch");
  var j=new JsonObject();j.addProperty("schema",1);j.addProperty("operation",r.key().operation().toString());
  var d=r.key().destination();var dest=new JsonObject();dest.addProperty("dimension",d.dimension().toString());dest.addProperty("position",d.controllerPos());dest.addProperty("incarnation",d.incarnation().toString());dest.addProperty("type",d.machineType().toString());j.add("destination",dest);
  j.addProperty("catalog_epoch",r.catalog().epoch());j.addProperty("catalog_sha256",r.catalog().sha256());j.add("proposal",JsonParser.parseString(r.canonicalProposal()));
  j.add("before",mirrorTree(r.before(),registries));j.add("after",mirrorTree(r.after(),registries));return canonical(j);
 }
 public static String checkedRequest(Request r,HolderLookup.Provider registries) {
  String encoded=request(r,registries);if(!sha256(encoded).equals(r.key().payloadSha256()))throw new IllegalArgumentException("request payload hash mismatch");return encoded;
 }
 public static String mirror(Mirror m,HolderLookup.Provider registries){return canonical(mirrorTree(m,registries));}
 /** Decode a stored canonical mirror; never projects it into an inventory. */
 public static Mirror decodeMirror(String encoded,HolderLookup.Provider registries){
  Objects.requireNonNull(encoded);Objects.requireNonNull(registries);
  if(encoded.length()>16*1024*1024)throw new IllegalArgumentException("mirror encoding too large");
  var j=JsonParser.parseString(encoded).getAsJsonObject();
  var slots=new ArrayList<SlotImage>();var tanks=new ArrayList<TankImage>();
  if(j.getAsJsonArray("slots").size()>4096||j.getAsJsonArray("tanks").size()>4096)throw new IllegalArgumentException("mirror layout too large");
  for(var value:j.getAsJsonArray("slots")){var s=value.getAsJsonObject();var stack=s.get("stack").isJsonNull()?ItemStack.EMPTY:ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,registries),s.get("stack")).getOrThrow();
   slots.add(new SlotImage(Math.toIntExact(exactLong(s,"index")),stack,LedgerJson.decode(s.getAsJsonObject("instances")).instances()));}
  for(var value:j.getAsJsonArray("tanks")){var t=value.getAsJsonObject();
   tanks.add(new TankImage(t.get("port").getAsString(),UUID.fromString(t.get("identity").getAsString()),new org.goldgom.chemicalelements.api.chem.MolAmount(storedInteger(t,"capacity")),exactLong(t,"revision"),LedgerJson.decode(t.getAsJsonObject("parcels")).instances()));}
  var e=j.getAsJsonObject("energy");var c=j.getAsJsonObject("control");var attributes=new TreeMap<String,String>();
  c.getAsJsonObject("attributes").entrySet().forEach(x->attributes.put(x.getKey(),x.getValue().getAsString()));
  var result=new Mirror(exactLong(j,"revision"),slots,tanks,new EnergyImage(storedInteger(e,"stored"),storedInteger(e,"capacity"),exactLong(e,"revision")),new ControlImage(c.get("phase").getAsString(),exactLong(c,"progress"),exactLong(c,"required"),attributes));
  // Round trip rejects unknown fields, duplicate keys, truncation, noncanonical numbers and codec defaults.
  if(!mirror(result,registries).equals(encoded))throw new IllegalArgumentException("noncanonical or lossy mirror encoding");
  return result;
 }
 private static long exactLong(JsonObject j,String key){
  var p=j.get(key).getAsJsonPrimitive();if(!p.isNumber()||!p.getAsString().matches("0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException("invalid mirror integer");return new java.math.BigInteger(p.getAsString()).longValueExact();
 }
 private static java.math.BigInteger storedInteger(JsonObject j,String key){
  var p=j.get(key).getAsJsonPrimitive();String s=p.getAsString();if(!p.isString()||s.length()>LedgerJson.MAX_STORED_AMOUNT_CHARACTERS||!s.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("invalid stored integer");return new java.math.BigInteger(s);
 }
 private static JsonObject mirrorTree(Mirror m,HolderLookup.Provider registries){
  var j=new JsonObject();j.addProperty("revision",m.revision());var slots=new JsonArray();
  m.slots().stream().sorted(Comparator.comparingInt(SlotImage::index)).forEach(s->{
   var x=new JsonObject();x.addProperty("index",s.index());var stack=s.stack();
   x.add("stack",stack.isEmpty()?JsonNull.INSTANCE:ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,registries),stack).getOrThrow());
   x.add("instances",LedgerJson.encode(new StackLedger(StackLedger.CURRENT_SCHEMA,0,s.instances())));slots.add(x);
  });j.add("slots",slots);var tanks=new JsonArray();
  m.tanks().stream().sorted(Comparator.comparing(TankImage::port)).forEach(t->{var x=new JsonObject();x.addProperty("port",t.port());x.addProperty("identity",t.identity().toString());x.addProperty("capacity",t.capacity().quanta().toString());x.addProperty("revision",t.revision());x.add("parcels",LedgerJson.encode(new StackLedger(StackLedger.CURRENT_SCHEMA,0,t.parcels())));tanks.add(x);});j.add("tanks",tanks);
  var e=new JsonObject();e.addProperty("stored",m.energy().stored().toString());e.addProperty("capacity",m.energy().capacity().toString());e.addProperty("revision",m.energy().revision());j.add("energy",e);
  var c=new JsonObject();c.addProperty("phase",m.control().phase());c.addProperty("progress",m.control().progressTicks());c.addProperty("required",m.control().requiredTicks());var a=new JsonObject();m.control().attributes().forEach(a::addProperty);c.add("attributes",a);j.add("control",c);return j;
 }
 private static JsonObject composition(Composition c){var j=new JsonObject();c.amounts().forEach((k,v)->j.addProperty(k.toString(),v.quanta().toString()));return j;}
 public static String canonical(JsonElement value){return sorted(value).toString();}
 private static JsonElement sorted(JsonElement value){
  if(value.isJsonObject()){var out=new JsonObject();new TreeSet<>(value.getAsJsonObject().keySet()).forEach(k->out.add(k,sorted(value.getAsJsonObject().get(k))));return out;}
  if(value.isJsonArray()){var out=new JsonArray();value.getAsJsonArray().forEach(v->out.add(sorted(v)));return out;}
  return value.deepCopy();
 }
 public static String sha256(String encoded){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}

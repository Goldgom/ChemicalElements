package org.goldgom.chemicalelements.instance;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import org.goldgom.chemicalelements.api.tx.*;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.machine.MachineTransactions;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Shared journal payload, not a reservation or proof of authority. Never allocates matter.
 * The eventual service must resolve player/session/slot mapping from server state before use.
 * Operation UUIDs use the existing journal namespace, NOT a per-destination namespace.
 * Schema 2 is separate from the existing machine schema 1; adapting an already recorded request
 * must not replace its stored payload/hash. */
public record TransactionRequest(UUID operation, TransactionDestination destination,
 CatalogVersion catalog, ReactionProposal proposal, Mirror before, Mirror after) {
 public TransactionRequest {
  Objects.requireNonNull(operation);Objects.requireNonNull(destination);Objects.requireNonNull(catalog);
  Objects.requireNonNull(proposal);Objects.requireNonNull(before);Objects.requireNonNull(after);
  if(catalog.epoch()!=proposal.registryEpoch())throw new IllegalArgumentException("proposal catalog mismatch");
  var a=new TreeSet<Integer>();var b=new TreeSet<Integer>();before.slots().forEach(s->a.add(s.index()));after.slots().forEach(s->b.add(s.index()));
  if(!a.equals(b))throw new IllegalArgumentException("slot layout changed");
  var x=new TreeMap<String,UUID>();var y=new TreeMap<String,UUID>();before.tanks().forEach(t->x.put(t.port(),t.identity()));after.tanks().forEach(t->y.put(t.port(),t.identity()));
  if(!x.equals(y))throw new IllegalArgumentException("port identity changed");
  if(destination instanceof PlayerMenuDestination p){
   if(p.ownerRevision()!=before.revision())throw new IllegalArgumentException("player owner revision mismatch");
   if(!before.tanks().isEmpty()||!after.tanks().isEmpty())throw new IllegalArgumentException("player menu cannot own machine tanks");
  }
 }
 /** Value conversion only. The old key's payload hash remains schema 1 and is not rewritten. */
 public static TransactionRequest fromMachine(Request r, HolderLookup.Provider registries){
  TransactionEncoding.checkedRequest(r,registries);
  var d=r.key().destination();
  return new TransactionRequest(r.key().operation(),new MachineDestination(d.dimension(),d.controllerPos(),d.incarnation(),d.machineType()),r.catalog(),r.proposal(),r.before(),r.after());
 }
 public String encode(HolderLookup.Provider registries){
  var j=new JsonObject();j.addProperty("schema",2);j.addProperty("operation",operation.toString());j.add("destination",destinationTree(destination));
  j.addProperty("catalog_epoch",catalog.epoch());j.addProperty("catalog_sha256",catalog.sha256());
  j.add("proposal",JsonParser.parseString(TransactionEncoding.proposal(proposal)));
  j.add("before",JsonParser.parseString(TransactionEncoding.mirror(before,registries)));j.add("after",JsonParser.parseString(TransactionEncoding.mirror(after,registries)));
  return TransactionEncoding.canonical(j);
 }
 public String payloadSha256(HolderLookup.Provider registries){return TransactionEncoding.sha256(encode(registries));}
 public String checkedEncoding(String expectedSha256,HolderLookup.Provider registries){
  String encoded=encode(registries);if(!TransactionEncoding.sha256(encoded).equals(expectedSha256))throw new IllegalArgumentException("request payload hash mismatch");return encoded;
 }
 private static JsonObject destinationTree(TransactionDestination destination){
  var d=new JsonObject();
  switch(destination){
   case PlayerMenuDestination p -> {d.addProperty("kind","player_menu");d.addProperty("player",p.player().toString());d.addProperty("session",p.session().toString());d.addProperty("menu_type",p.menuType().toString());d.addProperty("layout_version",p.layoutVersion());d.addProperty("owner_revision",p.ownerRevision());}
   case MachineDestination m -> {d.addProperty("kind","machine");d.addProperty("dimension",m.dimension().toString());d.addProperty("position",m.controllerPos());d.addProperty("incarnation",m.incarnation().toString());d.addProperty("type",m.machineType().toString());}
  }
  return d;
 }
}

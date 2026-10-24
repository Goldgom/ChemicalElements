package org.goldgom.chemicalelements.reaction;
import org.goldgom.chemicalelements.nuclear.*;
import java.math.*;import java.util.*;
public final class NuclearTransactionTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static final class Fixture implements NuclearReactionTransaction.Owner {
  NuclearReactionTransaction.Snapshot stock;Map<UUID,NuclearReactionTransaction.Outcome> receipts=new HashMap<>();int writes;
  Fixture(NuclearReactionTransaction.Snapshot s){stock=s;}
  public NuclearReactionTransaction.Snapshot snapshot(){return stock;}
  public Optional<NuclearReactionTransaction.Outcome> receipt(UUID id){return Optional.ofNullable(receipts.get(id));}
  public NuclearReactionTransaction.Outcome commit(UUID id,NuclearReactionTransaction.Snapshot expected,BranchingDecay.State after,BigDecimal debit){
   if(receipts.containsKey(id))return receipts.get(id);
   if(!stock.equals(expected))return new NuclearReactionTransaction.Outcome(false,"STALE","");
   stock=new NuclearReactionTransaction.Snapshot(stock.identity(),stock.revision()+1,stock.modelEpoch(),after,stock.availableEnergy().subtract(debit),stock.capabilities());writes++;
   var r=new NuclearReactionTransaction.Outcome(true,"COMMITTED","");receipts.put(id,r);return r;
  }
 }
 public static void main(String[] args){
  var c13=new DecayIntegrator.Nuclide(6,13,0);var c14=new DecayIntegrator.Nuclide(6,14,0);
  var rule=new NuclearReaction.Rule("fixture",Map.of(c13,1),Map.of(c14,1),List.of(new DecayIntegrator.Emission("neutron",1,0,1)),List.of(),new BigDecimal("-2"),Set.of("facility"),"fixture","synthetic test only");
  var state=new BranchingDecay.State(new DecayIntegrator.State(Map.of(c13,BigInteger.TEN),Map.of(),Map.of("neutron",BigInteger.TEN),BigDecimal.ZERO),Map.of());
  var initial=new NuclearReactionTransaction.Snapshot("test",0,1,state,new BigDecimal("6"),Set.of("facility"));var owner=new Fixture(initial);var id=UUID.randomUUID();
  var tx=new NuclearReactionTransaction(owner,id,rule);check(tx.extent().equals(BigInteger.valueOf(3)));check(tx.commit().committed());check(tx.commit().committed());check(owner.writes==1);check(owner.stock.availableEnergy().signum()==0);
  check(new NuclearReactionTransaction(owner,id,rule).commit().committed());check(owner.writes==1);
  owner=new Fixture(initial);tx=new NuclearReactionTransaction(owner,UUID.randomUUID(),rule);
  owner.stock=new NuclearReactionTransaction.Snapshot("test",0,2,state,new BigDecimal("6"),Set.of("facility"));check(tx.commit().code().equals("STALE_SOURCE"));check(owner.writes==0);
  owner=new Fixture(new NuclearReactionTransaction.Snapshot("test",0,1,state,new BigDecimal("6"),Set.of()));check(new NuclearReactionTransaction(owner,UUID.randomUUID(),rule).commit().code().equals("NO_REACTION"));check(owner.writes==0);
  System.out.println("PASS: nuclear coordinator energy debit; repeat/recreated receipt; epoch race; missing facility retains input (fixture owner, not B persistence proof)");
 }
}

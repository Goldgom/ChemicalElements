package org.goldgom.chemicalelements.reaction;
import org.goldgom.chemicalelements.fluid.*;
import java.math.BigInteger;
import java.util.*;
/** Adapter contract checks only; this in-memory test owner is not a production inventory. */
public final class WaterLifecycleTransactionTest {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 static final class TestOwner implements WaterLifecycleTransaction.Owner {
  Map<UUID,WaterLifecycleTransaction.Receipt> receipts=new HashMap<>();
  FiniteVolume.Parcel a=new FiniteVolume.Parcel(BigInteger.TEN,Map.of("salt",BigInteger.valueOf(7)),BigInteger.valueOf(13));
  FiniteVolume.Parcel b=new FiniteVolume.Parcel(BigInteger.ZERO,Map.of(),BigInteger.ZERO);
  long revision;int commits;boolean loaded=true,fail;
  public Optional<WaterLifecycleTransaction.Receipt> receipt(UUID id){return Optional.ofNullable(receipts.get(id));}
  public WaterLifecycleTransaction.Snapshot snapshot(WaterLifecycleTransaction.Request r){return new WaterLifecycleTransaction.Snapshot(new FiniteContainerTransfer.Endpoint("water",revision,a,BigInteger.TEN,loaded),new FiniteContainerTransfer.Endpoint("container",revision,b,BigInteger.TEN,true),"state:"+revision);}
  public WaterLifecycleTransaction.Outcome commit(UUID id,WaterLifecycleTransaction.Request r,WaterLifecycleTransaction.Snapshot expected,FiniteContainerTransfer.Plan plan){
   if(fail||!snapshot(r).equals(expected))return new WaterLifecycleTransaction.Outcome(false,"STALE");
   a=plan.sourceAfter();b=plan.destinationAfter();revision++;commits++;
   var result=new WaterLifecycleTransaction.Outcome(true,"COMMITTED");receipts.put(id,new WaterLifecycleTransaction.Receipt(r,result));return result;
  }
 }
 public static void main(String[] args){
  var owner=new TestOwner();var tx=new WaterLifecycleTransaction(owner);var id=UUID.randomUUID();
  var r=new WaterLifecycleTransaction.Request(WaterLifecycleTransaction.Entry.BOTTLE,"water","container",BigInteger.valueOf(3),true);
  var original=owner.a;check(tx.execute(id,r).committed());check(owner.a.plus(owner.b).equals(original));check(owner.commits==1);
  check(new WaterLifecycleTransaction(owner).execute(id,r).committed());check(owner.commits==1);
  var changed=new WaterLifecycleTransaction.Request(WaterLifecycleTransaction.Entry.BOTTLE,"water","container",BigInteger.ONE,true);
  check(tx.execute(id,changed).code().equals("TRANSACTION_ID_REUSED"));check(owner.commits==1);
  owner.loaded=false;check(tx.execute(UUID.randomUUID(),r).code().equals("UNLOADED"));check(owner.commits==1);
  owner.loaded=true;owner.fail=true;var before=owner.a;check(!tx.execute(UUID.randomUUID(),r).committed());check(owner.a.equals(before));
  owner.fail=false;var all=new WaterLifecycleTransaction.Request(WaterLifecycleTransaction.Entry.BUCKET,"water","container",BigInteger.TEN,true);
  check(tx.execute(UUID.randomUUID(),all).code().equals("INSUFFICIENT_WATER_OR_CAPACITY"));check(owner.a.equals(before));
  boolean rejected=false;try{new WaterLifecycleTransaction.Request(WaterLifecycleTransaction.Entry.BUCKET,"water","container",BigInteger.ONE,false);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  System.out.println("WaterLifecycleTransactionTest PASS "+checks+" adapter-only checks; not production owner evidence");
 }
}

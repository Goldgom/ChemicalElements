package org.goldgom.chemicalelements.core;
import java.util.*;
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
}
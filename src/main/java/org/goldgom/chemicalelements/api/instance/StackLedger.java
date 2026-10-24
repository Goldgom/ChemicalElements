package org.goldgom.chemicalelements.api.instance;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.Composition;
public record StackLedger(int schemaVersion,long revision,List<InstanceRecord> instances) {
 public static final int CURRENT_SCHEMA=1;
 public StackLedger {if(schemaVersion!=CURRENT_SCHEMA||revision<0)throw new IllegalArgumentException("unsupported ledger schema/revision");instances=List.copyOf(instances);var ids=new HashSet<UUID>();for(var i:instances)if(!ids.add(i.id()))throw new IllegalArgumentException("duplicate instance identity");}
 public void validateCount(int count){if(instances.size()!=count)throw new IllegalStateException("ledger/count mismatch");}
 public Composition remaining(){return instances.stream().map(InstanceRecord::remaining).reduce(Composition.EMPTY,Composition::add);}
 public Split split(int count){if(count<0||count>instances.size())throw new IllegalArgumentException("invalid split");return new Split(new StackLedger(schemaVersion,Math.addExact(revision,1),instances.subList(0,count)),new StackLedger(schemaVersion,Math.addExact(revision,1),instances.subList(count,instances.size())));}
 public StackLedger append(StackLedger other){var all=new ArrayList<>(instances);all.addAll(other.instances);return new StackLedger(schemaVersion,Math.addExact(revision,1),all);}
 public record Split(StackLedger taken,StackLedger remainder){}
}
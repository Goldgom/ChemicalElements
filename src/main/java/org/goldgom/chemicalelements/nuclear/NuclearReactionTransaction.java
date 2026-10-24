package org.goldgom.chemicalelements.nuclear;

import java.math.*;
import java.util.*;

/** Pure coordinator, never an inventory. Owner must be the same B ledger used by machines. */
public final class NuclearReactionTransaction {
 public record Snapshot(String identity,long revision,long modelEpoch,BranchingDecay.State state,
                        BigDecimal availableEnergy,Set<String> capabilities) {
  public Snapshot { Objects.requireNonNull(identity);Objects.requireNonNull(state);Objects.requireNonNull(availableEnergy);
   capabilities=Set.copyOf(capabilities);if(identity.isBlank()||revision<0||modelEpoch<0||availableEnergy.signum()<0)throw new IllegalArgumentException("invalid nuclear transaction snapshot"); }
 }
 public record Outcome(boolean committed,String code,String reason) {}
 public interface Owner {
  Snapshot snapshot();
  /** Durable receipt lookup, including after restart. Empty means no committed receipt. */
  Optional<Outcome> receipt(UUID id);
  /** One CAS includes state, particles, released energy, chemical projection and external energy debit.
   * Must compare modelEpoch, capabilities and revision and persist receipt atomically. Never turn all nuclear energy into heat. */
  Outcome commit(UUID id,Snapshot expected,BranchingDecay.State after,BigDecimal energyDebit);
 }
 private final Owner owner;private final UUID id;private final Snapshot before;
 private final NuclearReaction.Result result;private Outcome settled;
 public NuclearReactionTransaction(Owner owner,UUID id,NuclearReaction.Rule rule) {
  this.owner=Objects.requireNonNull(owner);this.id=Objects.requireNonNull(id);before=owner.snapshot();
  result=NuclearReaction.apply(before.state.inventory(),rule,before.capabilities,before.availableEnergy);
 }
 public BigInteger extent(){return result.extent();}
 public Outcome preview(){return result.extent().signum()==0?new Outcome(false,"NO_REACTION",result.reason()):new Outcome(false,"READY","Nuclear state and energy require atomic owner settlement");}
 public synchronized Outcome commit(){
  if(settled!=null)return settled;
  var prior=owner.receipt(id);if(prior.isPresent()){settled=prior.get();return settled;}
  if(result.extent().signum()==0)return preview();
  if(!before.equals(owner.snapshot()))return new Outcome(false,"STALE_SOURCE","Inventory, model epoch, energy or facility conditions changed; input retained");
  var after=new BranchingDecay.State(result.state(),before.state.branchDebt());
  var outcome=owner.commit(id,before,after,result.consumedEnergy());
  if(outcome.committed())settled=outcome;
  return outcome;
 }
}

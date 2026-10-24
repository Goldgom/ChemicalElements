package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stateless lifecycle adapter. B owns endpoints, complete instance state and durable receipts.
 * A parcel is only the liquid projection: it must never replace an entire InstanceRecord. */
public final class WaterLifecycleTransaction {
 public enum Entry { BUCKET, BOTTLE, PIPE, WATERLOGGING, DRAIN }
 public record Request(Entry entry,String source,String destination,BigInteger quantity,boolean exact) {
  public Request {Objects.requireNonNull(entry);Objects.requireNonNull(source);Objects.requireNonNull(destination);Objects.requireNonNull(quantity);
   if(source.isBlank()||destination.isBlank()||quantity.signum()<=0)throw new IllegalArgumentException("invalid transfer request");
   if((entry==Entry.BUCKET||entry==Entry.BOTTLE)&&!exact)throw new IllegalArgumentException("containers require exact transfer");}
 }
 /** Generation token covers every state field, capacity, loading and container/visual preconditions. */
 public record Snapshot(FiniteContainerTransfer.Endpoint source,FiniteContainerTransfer.Endpoint destination,String generation) {
  public Snapshot {Objects.requireNonNull(source);Objects.requireNonNull(destination);Objects.requireNonNull(generation);if(generation.isBlank())throw new IllegalArgumentException("missing full-state generation");}
 }
 public record Outcome(boolean committed,String code) {public Outcome{Objects.requireNonNull(code);}}
 public record Receipt(Request request,Outcome outcome) {public Receipt{Objects.requireNonNull(request);Objects.requireNonNull(outcome);}}
 public interface Owner {
  /** Must not initialize natural salts, allocate identities, load chunks or mutate inventory. */
  Snapshot snapshot(Request request);
  Optional<Receipt> receipt(UUID transaction);
  /** Atomic CAS of BOTH endpoints and generation, preserving nuclear/debt/adsorbed/provenance fields.
   * Liquid species, volume and energy delta, container replacement, initialization tombstones and
   * world projection intent are one settlement. Persist request-bound receipt in the same commit.
   * Recheck loaded status; reject without effects on any failure. Never call shrink independently.
   * Visual recovery must replay projection only, never material transfer. */
  Outcome commit(UUID transaction,Request request,Snapshot expected,FiniteContainerTransfer.Plan plan);
 }
 private final Owner owner;
 public WaterLifecycleTransaction(Owner owner){this.owner=Objects.requireNonNull(owner);}
 public Outcome execute(UUID transaction,Request request){
  Objects.requireNonNull(transaction);Objects.requireNonNull(request);
  var receipt=owner.receipt(transaction);
  if(receipt.isPresent())return receipt.get().request().equals(request)?receipt.get().outcome():new Outcome(false,"TRANSACTION_ID_REUSED");
  var before=Objects.requireNonNull(owner.snapshot(request));
  if(!before.source().identity().equals(request.source())||!before.destination().identity().equals(request.destination()))return new Outcome(false,"OWNER_IDENTITY_MISMATCH");
  var plan=FiniteContainerTransfer.plan(before.source(),before.destination(),request.quantity(),request.exact());
  if(!plan.ready())return new Outcome(false,plan.reason());
  // Owner performs the definitive CAS; no second mutable store or speculative container change.
  return Objects.requireNonNull(owner.commit(transaction,request,before,plan));
 }
}

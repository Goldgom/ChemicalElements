package org.goldgom.chemicalelements.fluid;
import java.math.BigInteger;
import java.util.*;
/** Shared bucket/bottle/pipe quantity planner. B commits both identities and visual/container changes atomically. */
public final class FiniteContainerTransfer {
 private FiniteContainerTransfer(){}
 public record Endpoint(String identity,long revision,FiniteVolume.Parcel parcel,BigInteger capacity,boolean loaded){
  public Endpoint {Objects.requireNonNull(identity);Objects.requireNonNull(parcel);Objects.requireNonNull(capacity);if(identity.isBlank()||revision<0||capacity.signum()<0||parcel.volume().compareTo(capacity)>0)throw new IllegalArgumentException("invalid endpoint");}
 }
 public record Plan(Endpoint source,Endpoint destination,FiniteVolume.Parcel sourceAfter,FiniteVolume.Parcel destinationAfter,BigInteger moved,String reason){public boolean ready(){return moved.signum()>0;}}
 /** Whole-container operations require exact=true; partial pipes and draining use false.
  * Requested and capacity use the same scale-18 litre units as finite water. No implicit external source. */
 public static Plan plan(Endpoint source,Endpoint destination,BigInteger requested,boolean exact){
  if(requested.signum()<0)throw new IllegalArgumentException("negative request");
  if(source.identity.equals(destination.identity))return reject(source,destination,"SAME_OWNER");
  if(!source.loaded||!destination.loaded)return reject(source,destination,"UNLOADED");
  BigInteger moved=requested.min(source.parcel.volume()).min(destination.capacity.subtract(destination.parcel.volume()));
  if(exact&&!moved.equals(requested))return reject(source,destination,"INSUFFICIENT_WATER_OR_CAPACITY");
  if(moved.signum()==0)return reject(source,destination,"NO_TRANSFER");
  var split=FiniteVolume.take(source.parcel,moved);
  return new Plan(source,destination,split.remainder(),destination.parcel.plus(split.moved()),moved,"READY");
 }
 private static Plan reject(Endpoint source,Endpoint destination,String reason){return new Plan(source,destination,source.parcel,destination.parcel,BigInteger.ZERO,reason);}
}

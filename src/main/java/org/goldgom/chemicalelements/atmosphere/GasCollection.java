package org.goldgom.chemicalelements.atmosphere;

import java.math.*;
import java.util.*;

/** Pure finite collection/deposition plans. The caller commits BOTH sides and heat in B's transaction. */
public final class GasCollection {
 private GasCollection(){}
 public record Plan(AtmosphereTransport.Cell atmosphere,AtmosphereTransport.Cell captured,Map<String,BigInteger> moved,String mode){public Plan{moved=Map.copyOf(moved);}}
 public static Plan collect(AtmosphereTransport.Cell atmosphere,AtmosphereTransport.Cell tank,Set<String> filter,BigInteger capacity,BigInteger flux){
  if(capacity.signum()<0||flux.signum()<0)throw new IllegalArgumentException("negative gas budget");
  BigInteger occupied=tank.gases().values().stream().reduce(BigInteger.ZERO,BigInteger::add);
  if(occupied.compareTo(capacity)>0)throw new IllegalArgumentException("tank exceeds capacity");
  BigInteger limit=capacity.subtract(occupied).min(flux);
  TreeMap<String,BigInteger> available=new TreeMap<>();
  atmosphere.gases().forEach((id,n)->{if(filter.contains(id)&&n.signum()>0)available.put(id,n);});
  BigInteger total=available.values().stream().reduce(BigInteger.ZERO,BigInteger::add);
  limit=limit.min(total);TreeMap<String,BigInteger> moved=new TreeMap<>();
  if(total.signum()>0){
   // Floors remain at source: no independently rounded species and no hidden rounding inventory.
   BigInteger used=BigInteger.ZERO;TreeMap<String,BigInteger> remainder=new TreeMap<>();
   for(var e:available.entrySet()){var qr=e.getValue().multiply(limit).divideAndRemainder(total);moved.put(e.getKey(),qr[0]);remainder.put(e.getKey(),qr[1]);used=used.add(qr[0]);}
   var order=new ArrayList<>(available.keySet());order.sort(Comparator.<String,BigInteger>comparing(remainder::get).reversed().thenComparing(Comparator.naturalOrder()));
   int extras=limit.subtract(used).intValueExact();for(int i=0;i<extras;i++)moved.merge(order.get(i),BigInteger.ONE,BigInteger::add);
  }
  var transfer=AtmosphereTransport.transfer(atmosphere,tank,moved);
  return new Plan(transfer.source(),transfer.destination(),moved,"collection");
 }
 /** Deposition is identity-preserving capture. Chemical precipitation uses PhaseEquilibrium separately. */
 public static Plan deposit(AtmosphereTransport.Cell atmosphere,AtmosphereTransport.Cell surface,Set<String> modeledDepositable,BigInteger capacity,BigInteger flux){
  var p=collect(atmosphere,surface,modeledDepositable,capacity,flux);
  return new Plan(p.atmosphere,p.captured,p.moved,"deposition");
 }
}

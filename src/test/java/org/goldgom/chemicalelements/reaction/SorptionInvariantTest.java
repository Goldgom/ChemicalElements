package org.goldgom.chemicalelements.reaction;
import org.goldgom.chemicalelements.fluid.*;import java.math.*;import java.util.*;
public final class SorptionInvariantTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var total=new FiniteVolume.Parcel(BigInteger.valueOf(1000),Map.of("water",BigInteger.valueOf(997),"salt",BigInteger.valueOf(31)),BigInteger.valueOf(153));
  var water=total;var pores=FiniteVolume.Parcel.empty();
  var material=new SorptionExchange.Material(BigInteger.valueOf(250),new BigDecimal("0.9"),BigInteger.valueOf(17),"fixture","synthetic");
  for(int i=0;i<100;i++){var r=SorptionExchange.absorb(water,pores,material);water=r.water();pores=r.pores();check(water.plus(pores).equals(total));check(pores.volume().compareTo(material.poreCapacity())<=0);}
  check(pores.volume().equals(material.poreCapacity()));
  var dense=new SorptionExchange.Material(BigInteger.ZERO,BigDecimal.ZERO,BigInteger.ZERO,"game_convention","dense zero-connected-porosity class");
  boolean rejected=false;try{SorptionExchange.absorb(water,pores,dense);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  var released=SorptionExchange.release(water,pores,pores.volume());check(released.water().equals(total));check(released.pores().equals(FiniteVolume.Parcel.empty()));
  var none=SorptionExchange.absorb(total,FiniteVolume.Parcel.empty(),dense);check(none.water().equals(total));check(none.pores().equals(FiniteVolume.Parcel.empty()));
  System.out.println("PASS: 100 sorption ticks species/volume/energy conserved; saturation; release recovery; zero-porosity no-op; capacity decrease rejects without loss");
 }
}

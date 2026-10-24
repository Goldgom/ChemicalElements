package org.goldgom.chemicalelements.reaction;
import org.goldgom.chemicalelements.fluid.*;import java.math.*;import java.util.*;
public final class FiniteContainerTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var inventory=new FiniteVolume.Parcel(BigInteger.valueOf(1000),Map.of("water",BigInteger.valueOf(3001),"salt",BigInteger.valueOf(17)),BigInteger.valueOf(7001));
  var world=new FiniteContainerTransfer.Endpoint("world",0,inventory,BigInteger.valueOf(1000),true);
  var bottle=new FiniteContainerTransfer.Endpoint("bottle",0,FiniteVolume.Parcel.empty(),BigInteger.valueOf(333),true);
  var fill=FiniteContainerTransfer.plan(world,bottle,BigInteger.valueOf(333),true);check(fill.ready());check(fill.sourceAfter().plus(fill.destinationAfter()).equals(inventory));
  var bottleFull=new FiniteContainerTransfer.Endpoint("bottle",1,fill.destinationAfter(),bottle.capacity(),true);
  var worldRest=new FiniteContainerTransfer.Endpoint("world",1,fill.sourceAfter(),world.capacity(),true);
  var empty=FiniteContainerTransfer.plan(bottleFull,worldRest,BigInteger.valueOf(333),true);check(empty.destinationAfter().equals(inventory));check(empty.sourceAfter().equals(FiniteVolume.Parcel.empty()));
  check(!FiniteContainerTransfer.plan(world,bottle,BigInteger.valueOf(1000),true).ready());
  check(!FiniteContainerTransfer.plan(world,world,BigInteger.ONE,false).ready());
  var offline=new FiniteContainerTransfer.Endpoint("offline",0,FiniteVolume.Parcel.empty(),BigInteger.TEN,false);check(!FiniteContainerTransfer.plan(world,offline,BigInteger.ONE,false).ready());
  check(FiniteContainerTransfer.plan(world,bottle,BigInteger.valueOf(1000),false).moved().equals(BigInteger.valueOf(333)));
  System.out.println("PASS: bottle fill/empty exact species-energy-volume round trip; whole bucket insufficient capacity preserves input; partial capacity; same-owner and unloaded reject (planner, not player path)");
 }
}

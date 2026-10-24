package org.goldgom.chemicalelements.gametest.machine;
import java.util.*;
import java.math.BigInteger;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;
public final class MachineTransactionsTest{
 static int checks;
 static void ok(boolean b){checks++;if(!b)throw new AssertionError();}
 static void rejects(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("accepted invalid state");}
 public static void main(String[] args){
  // Nonempty ItemStack/component tests require A's real NeoForge GameTest bootstrap.
  var tanks=new ArrayList<TankImage>();
  var tank=new TankImage("gas",UUID.randomUUID(),org.goldgom.chemicalelements.api.chem.MolAmount.parseDecimal("10"),0,List.of());tanks.add(tank);
  var attrs=new HashMap<String,String>();attrs.put("schema","1");
  var control=new ControlImage("idle",0,0,attrs);attrs.put("schema","2");ok(control.attributes().get("schema").equals("1"));
  var energy=new EnergyImage(BigInteger.ONE.shiftLeft(1025),BigInteger.ONE.shiftLeft(1026),0);
  var mirror=new Mirror(0,List.of(),tanks,energy,control);tanks.clear();ok(mirror.tanks().size()==1);ok(mirror.tanks().getFirst().port().equals("gas"));
  rejects(()->new Mirror(0,List.of(),List.of(tank,tank),energy,control));
  rejects(()->new EnergyImage(BigInteger.TEN,BigInteger.ONE,0));rejects(()->new EnergyImage(BigInteger.ONE.negate(),BigInteger.TEN,0));
  rejects(()->new ControlImage("idle",2,1,Map.of()));rejects(()->new CatalogVersion(-1,"0".repeat(64)));rejects(()->new CatalogVersion(0,"bad"));
  rejects(()->new ObservedMirror(1,"0".repeat(64),Optional.empty(),mirror));
  ok(energy.stored().bitLength()==1026);ok(Arrays.stream(Receipt.class.getRecordComponents()).noneMatch(x->x.getName().equals("outputs")));
  System.out.println("MachineTransactionsTest "+checks+" assertions passed; types only, no durable transaction or game lifecycle proof");
 }
}

package org.goldgom.chemicalelements.gametest.machine;
import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.machine.*;
public final class MachineStateTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  var water=ResourceLocation.parse("chemicalelements:water");var salt=ResourceLocation.parse("chemicalelements:sodium_chloride");
  var a=new MaterialTank(MolAmount.parseDecimal("100"));var b=new MaterialTank(MolAmount.parseDecimal("3"));
  var all=new Composition(Map.of(water,MolAmount.parseDecimal("8"),salt,MolAmount.parseDecimal("2")));a.restore(all,0);
  check(a.fillBasisPoints()==1000);check(b.fillBasisPoints()==0);check(a.revision()==0);check(a.contents().equals(all));
  var thirds=new MaterialTank(MolAmount.parseDecimal("3"));thirds.restore(new Composition(Map.of(water,MolAmount.parseDecimal("1"))),7);check(thirds.fillBasisPoints()==3333);check(thirds.revision()==7);
  var zero=new MaterialTank(MolAmount.ZERO);check(zero.fillBasisPoints()==0);
  var plan=a.preview(b,MolAmount.parseDecimal("5"),id->true);check(a.contents().equals(all));check(b.contents().equals(Composition.EMPTY));check(plan.material().totalMol().equals(MolAmount.parseDecimal("3")));
  check(a.commit(b,plan));check(b.fillBasisPoints()==10000);check(a.contents().add(b.contents()).equals(all));check(!a.commit(b,plan));check(a.contents().add(b.contents()).equals(all));
  var roundtrip=new MaterialTank(MolAmount.parseDecimal("100"));roundtrip.load(a.save());check(roundtrip.contents().equals(a.contents()));check(roundtrip.revision()==a.revision());
  var stale=a.preview(roundtrip,MolAmount.parseDecimal("1"),id->id.equals(water));roundtrip.restore(Composition.EMPTY,roundtrip.revision()+1);check(!a.commit(roundtrip,stale));
  var power=new MachinePower(()->{});check(power.temperature().compareTo(new BigDecimal("298.15"))==0);check(power.receiveEnergy(1000,true)==1000);check(power.getEnergyStored()==0);power.receiveEnergy(1000,false);power.tick(MachineKind.EVAPORATOR);check(power.getEnergyStored()==0);check(power.temperature().compareTo(new BigDecimal("299.15"))==0);check(!power.spend(1));
  var saved=new CompoundTag();power.save(saved);var clone=new MachinePower(()->{});clone.load(saved);check(clone.temperature().equals(power.temperature()));
  var bench=new MachinePower(()->{});bench.receiveEnergy(1000,false);bench.tick(MachineKind.AMBIENT_WORKBENCH);check(bench.temperature().compareTo(new BigDecimal("298.15"))==0);check(bench.getEnergyStored()==1000);
  System.out.println(checks+" machine tank/energy/menu-message assertions passed");
 }
}

package org.goldgom.chemicalelements.machine;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.persistence.*;
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MachineTransactionGameTests {
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void slotImageDefensiveReturn(GameTestHelper h){
  var record=record(1);var item=stack(record);var slot=new MachineTransactions.SlotImage(0,item,List.of(record));
  var returned=slot.stack();returned.setCount(2);returned.set(DataComponents.CUSTOM_NAME,Component.literal("mutated return"));returned.remove(ChemistryComponents.LEDGER.get());
  var actual=slot.stack();
  h.assertTrue(actual.getCount()==1,"Returned stack count mutation cannot modify mirror");
  h.assertTrue(!actual.has(DataComponents.CUSTOM_NAME),"Returned stack components cannot modify mirror");
  h.assertTrue(actual.get(ChemistryComponents.LEDGER.get()).ledger().orElseThrow().instances().equals(List.of(record)),"Returned stack removal cannot erase authoritative snapshot component");
  h.assertTrue(slot.stack()!=slot.stack(),"Each accessor returns a distinct defensive stack");h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void slotImageRejectsMismatches(GameTestHelper h){
  var a=record(2);var b=record(3);var item=stack(a);
  rejects(h,()->new MachineTransactions.SlotImage(0,item,List.of(b)),"Different instance UUID rejected despite equal count/composition");
  var count=item.copy();count.setCount(2);rejects(h,()->new MachineTransactions.SlotImage(0,count,List.of(a)),"Count mismatch rejected");
  var missing=item.copy();missing.remove(ChemistryComponents.LEDGER.get());rejects(h,()->new MachineTransactions.SlotImage(0,missing,List.of(a)),"Missing component rejected");
  var corrupt=item.copy();corrupt.set(ChemistryComponents.LEDGER.get(),new LedgerPayload("not-json"));rejects(h,()->new MachineTransactions.SlotImage(0,corrupt,List.of(a)),"Undecodable component rejected");
  h.assertTrue(item.getCount()==1&&item.get(ChemistryComponents.LEDGER.get()).ledger().orElseThrow().instances().equals(List.of(a)),"Rejected snapshots never mutate caller");h.succeed();
 }
 private static InstanceRecord record(long n){return new InstanceRecord(new UUID(0xD33,n),Composition.EMPTY,Provenance.of("test","D33-slot-component"),0);}
 private static ItemStack stack(InstanceRecord record){var x=new ItemStack(Items.IRON_INGOT);x.set(ChemistryComponents.LEDGER.get(),LedgerPayload.of(new StackLedger(StackLedger.CURRENT_SCHEMA,0,List.of(record))));return x;}
 private static void rejects(GameTestHelper h,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,message);}
}

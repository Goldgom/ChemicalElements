package org.goldgom.chemicalelements.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Real server menu/BE tests. These do not claim durable CAS or transaction commit coverage. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MachineMenuAuthorityGameTests {
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void machineMenuAuthoritySlots(GameTestHelper h){
  var p=h.makeMockServerPlayerInLevel();var pos=h.absolutePos(BlockPos.ZERO);
  h.getLevel().setBlock(pos,MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(),3);
  var be=(MachineBlockEntity)h.getLevel().getBlockEntity(pos);p.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
  var menu=new MachineMenu(19,p.getInventory(),be);p.containerMenu=menu;
  h.assertTrue(menu.transactionSession(p).isPresent(),"Server creates active session");
  var authority=menu.transactionAuthority(p).orElseThrow();
  h.assertTrue(authority.player().equals(p.getUUID())&&authority.session().equals(menu.transactionSession(p).orElseThrow()),"Authority is generated for server actor/session");
  h.assertTrue(authority.layoutVersion()==menu.layoutVersion(),"Authority binds fixed layout");
  int n=25;
  for(int m=0;m<n+45;m++){
   var resolved=menu.resolveAuthoritativeSlot(p,m).orElseThrow();
   int expected=m<n?m:m<n+9?81+m-n:m<n+36?m-n:m-(n+36);
   h.assertTrue(resolved.containerSlot()==expected,"raw slot mapping "+m);
   h.assertTrue(resolved.container()==(m<n+9?be.inventory():p.getInventory()),"actual container reference "+m);
   h.assertTrue(resolved.machineOwner()==(m<n+9),"owner boundary "+m);
   var bound=menu.resolveTransactionSlot(p,m).orElseThrow();
   h.assertTrue(bound.container()==resolved.container()&&bound.containerSlot()==expected,"Public locator preserves original slot "+m);
   if(m<n+9)h.assertTrue(bound.owner().equals(new org.goldgom.chemicalelements.api.tx.OwnerKey.MachineInventory(p.level().dimension().location(),pos.asLong(),be.incarnation(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements",be.kind().id()))),"Persistent BE owner "+m);
   else h.assertTrue(bound.owner().equals(new org.goldgom.chemicalelements.api.tx.OwnerKey.PlayerInventory(p.getUUID())),"Player owner, not menu identity "+m);
  }
  h.assertTrue(menu.resolveAuthoritativeSlot(p,-1).isEmpty()&&menu.resolveAuthoritativeSlot(p,n+45).isEmpty(),"Unmodeled slots rejected");
  var original=menu.transactionSession(p).orElseThrow();menu.removed(p);
  h.assertTrue(menu.transactionSession(p).isEmpty()&&menu.transactionAuthority(p).isEmpty()&&menu.resolveTransactionSlot(p,0).isEmpty(),"removed invalidates session and all public authority");
  var reopened=new MachineMenu(20,p.getInventory(),be);p.containerMenu=reopened;
  h.assertTrue(!reopened.transactionSession(p).orElseThrow().equals(original),"Reopen uses fresh session");
  be.configureGrid(1);
  h.assertTrue(reopened.transactionSession(p).isEmpty(),"Grid mutation irrevocably invalidates old layout");
  for(int side=1;side<=9;side++){
   be.configureGrid(side);var sized=new MachineMenu(30+side,p.getInventory(),be);p.containerMenu=sized;
   int total=side*side;
   h.assertTrue(sized.layoutSide()==side&&sized.transactionSession(p).isPresent(),"Fresh layout "+side);
   h.assertTrue(sized.resolveAuthoritativeSlot(p,total-1).orElseThrow().containerSlot()==total-1,"Last input "+side);
   h.assertTrue(sized.resolveAuthoritativeSlot(p,total).orElseThrow().containerSlot()==81,"Output start "+side);
   h.assertTrue(sized.resolveAuthoritativeSlot(p,total+9).orElseThrow().containerSlot()==9,"Player start "+side);
   h.assertTrue(sized.resolveAuthoritativeSlot(p,total+44).orElseThrow().containerSlot()==8,"Last hotbar "+side);
   sized.removed(p);
  }
  var tag=new net.minecraft.nbt.CompoundTag();be.saveAdditional(tag,h.getLevel().registryAccess());
  var copy=new MachineBlockEntity(pos,be.getBlockState());copy.loadAdditional(tag,h.getLevel().registryAccess());
  h.assertTrue(copy.incarnation().equals(be.incarnation()),"Owner incarnation survives NBT roundtrip");
  var replacement=new MachineBlockEntity(pos,be.getBlockState());h.assertTrue(!replacement.incarnation().equals(be.incarnation()),"Replacement BE has new incarnation");
  p.containerMenu=p.inventoryMenu;h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void machineMenuUnloadRevokes(GameTestHelper h){
  var p=h.makeMockServerPlayerInLevel();var pos=h.absolutePos(BlockPos.ZERO);
  h.getLevel().setBlock(pos,MachineBootstrap.MACHINES.values().iterator().next().get().defaultBlockState(),3);
  var be=(MachineBlockEntity)h.getLevel().getBlockEntity(pos);p.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
  var menu=new MachineMenu(21,p.getInventory(),be);p.containerMenu=menu;
  h.assertTrue(menu.transactionSession(p).isPresent(),"Session starts valid");
  be.onChunkUnloaded();h.assertTrue(menu.transactionSession(p).isEmpty(),"BE unload invalidates without refund");
  var second=new MachineMenu(22,p.getInventory(),be);p.containerMenu=second;
  be.setRemoved();h.assertTrue(second.resolveAuthoritativeSlot(p,0).isEmpty(),"Removed BE rejects old slot mapping");
  p.containerMenu=p.inventoryMenu;h.succeed();
 }
}

package org.goldgom.chemicalelements.machine;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MachineGameTests {
 @GameTest(template="core_empty",timeoutTicks=100)
 public static void sizedPartWorldLifecycle(GameTestHelper h){
  var level=h.getLevel();var anchor=h.absolutePos(new BlockPos(1,1,1));
  var block=MachineBootstrap.PARTS.get("column_tall").get();var definition=PartDefinition.load(level,"column_tall").orElseThrow();
  h.assertTrue(definition.height()==3,"Real datapack part must be 1x3x1");
  var state=block.defaultBlockState().setValue(SizedPartBlock.FACING,Direction.NORTH);
  level.setBlock(anchor,state,3);block.setPlacedBy(level,anchor,state,null,new ItemStack(block));
  h.assertTrue(block.complete(level,anchor,state),"All occupied cells share one anchor");
  var center=anchor.above();h.assertTrue(level.getBlockState(center).is(block),"Tall part occupies middle cell");
  level.destroyBlock(center,true);
  h.assertTrue(!level.getBlockState(anchor).is(block)&&!level.getBlockState(anchor.above(2)).is(block),"Breaking any cell removes the whole part");
  var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(anchor).inflate(2));
  int count=drops.stream().filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
  h.assertTrue(count==1,"A three-cell part drops one material item, found "+count);h.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=100)
 public static void strictStructureStopsMissing(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));var block=MachineBootstrap.MACHINES.get(MachineKind.ANALYZER).get();
  level.setBlock(pos,block.defaultBlockState(),3);
  var report=MultiblockValidator.inspect(level,pos,MachineKind.ANALYZER,0);
  h.assertTrue(report.state()==MultiblockValidator.State.INVALID&&!report.diagnostics().isEmpty(),"Missing real parts must yield explicit diagnostics");
  var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.start();
  h.assertTrue(!machine.running()&&machine.message().contains("Missing"),"Missing parts prevent running without consuming input");h.succeed();
 }
}

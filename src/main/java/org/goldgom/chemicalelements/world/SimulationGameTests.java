package org.goldgom.chemicalelements.world;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.recipe.*;
import org.goldgom.chemicalelements.api.reaction.ReactionContext;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.nuclear.*;
import java.math.*;
import java.util.*;
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class SimulationGameTests {
 /** Production queue against real ServerLevel chunks, without fake Owner or inventory. */
 @GameTest(template="core_empty",timeoutTicks=100)
 public static void unloadedWaterWorkDoesNotLoadChunks(GameTestHelper helper){
  var level=helper.getLevel();int queued=FiniteWaterRuntime.queuedPositions(level),checked=0;
  var base=helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
  for(int i=0;i<64&&checked<32;i++){
   int cx=(base.getX()>>4)+4096+i*17,cz=(base.getZ()>>4)+4096+i*19;
   if(level.getChunkSource().getChunkNow(cx,cz)!=null)continue;
   var position=new net.minecraft.core.BlockPos(cx*16,level.getSeaLevel(),cz*16);
   FiniteWaterRuntime.mark(level,position);
   helper.assertTrue(level.getChunkSource().getChunkNow(cx,cz)==null,"mark must not force-load untouched ocean chunk");
   helper.assertTrue(FiniteWaterRuntime.queuedPositions(level)==queued,"unloaded coordinates must not accumulate in global queue");
   checked++;
  }
  helper.assertTrue(checked==32,"require 32 genuinely unloaded boundary candidates");
  helper.succeed();
 }
 /** Detached chunks are not installed in the world and contain no test inventory or fake Owner. */
 @GameTest(template="core_empty",timeoutTicks=100)
 public static void admissionRejectsUnpublishedChunk(GameTestHelper helper){
  var level=helper.getLevel();
  var origin=helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
  var pos=new net.minecraft.world.level.ChunkPos(origin);
  var published=level.getChunkSource().getChunkNow(pos.x,pos.z);
  helper.assertTrue(published!=null,"test structure chunk must be published");
  int queued=ChunkWaterInitializationRuntime.queuedChunks(level);
  var detached=new net.minecraft.world.level.chunk.LevelChunk(level,pos);
  ChunkWaterInitializationRuntime.enqueue(level,detached);
  helper.assertTrue(level.getChunkSource().getChunkNow(pos.x,pos.z)==published,"enqueue must not replace current chunk incarnation");
  helper.assertTrue(ChunkWaterInitializationRuntime.queuedChunks(level)==queued,"unpublished incarnation must not enter admission queue");
  helper.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=100)
 public static void productionModelsLoad(GameTestHelper helper){
  var bundle=AtomicChemistryCatalog.INSTANCE.bundle();
  helper.assertTrue(SimulationBootstrap.models().epoch()==bundle.epoch(),"Catalog and simulation must share epoch");
  helper.assertTrue(!SimulationBootstrap.REACTIONS.snapshot().pathways().isEmpty(),"Real reaction resources loaded");
  helper.assertTrue(!SimulationBootstrap.SOLUBILITY.snapshot().isEmpty(),"Real solubility resources loaded");
  var rules=DecayReloadListener.INSTANCE.snapshot();helper.assertTrue(!rules.isEmpty(),"Real nuclear resources loaded");
  var carbon=new DecayIntegrator.Nuclide(6,14,0);
  var initial=new BranchingDecay.State(new DecayIntegrator.State(Map.of(carbon,BigInteger.TEN.pow(18)),Map.of(),Map.of(),BigDecimal.ZERO),Map.of());
  var result=BranchingDecay.tick(initial,rules,1,true);
  helper.assertTrue(result.inventory().nuclides().get(carbon).compareTo(BigInteger.TEN.pow(18))<0,"Production C14 must decay");
  helper.assertTrue(result.inventory().nuclides().values().stream().reduce(BigInteger.ZERO,BigInteger::add).equals(BigInteger.TEN.pow(18)),"Beta decay preserves total nuclide count");
  var water=new Composition(Map.of(ResourceLocation.parse("chemicalelements:water"),MolAmount.ONE));
  var response=SimulationBootstrap.SYNTHESIS.solve(new SynthesisRequest(List.of(water),1,1,ReactionContext.workbench(water),bundle.epoch()),bundle.catalog());
  helper.assertTrue(response.status()!=SynthesisResult.Status.READY,"Workbench cannot electrolyze water without supplied work");
  helper.assertTrue(water.totalMol().equals(MolAmount.ONE),"Rejected input unchanged");
  helper.succeed();
 }
 @GameTest(template="core_empty",timeoutTicks=100)
 public static void productionSorptionConserves(GameTestHelper helper){
  var soil=org.goldgom.chemicalelements.fluid.SorptionLibrary.INSTANCE.material(ResourceLocation.parse("minecraft:dirt")).orElseThrow();
  var dense=org.goldgom.chemicalelements.fluid.SorptionLibrary.INSTANCE.material(ResourceLocation.parse("minecraft:iron_block")).orElseThrow();
  var water=new org.goldgom.chemicalelements.fluid.FiniteVolume.Parcel(BigInteger.TEN.pow(18),Map.of("chemicalelements:water",BigInteger.TEN.pow(18),"chemicalelements:sodium_ion",BigInteger.TEN.pow(12),"chemicalelements:chloride_ion",BigInteger.TEN.pow(12)),BigInteger.TEN.pow(15));
  var empty=org.goldgom.chemicalelements.fluid.FiniteVolume.Parcel.empty();
  var absorbed=org.goldgom.chemicalelements.fluid.SorptionExchange.absorb(water,empty,soil);
  helper.assertTrue(absorbed.pores().volume().signum()>0,"Production soil absorbs finite water");
  helper.assertTrue(absorbed.water().plus(absorbed.pores()).equals(water),"Production soil species/heat/volume conserved");
  var released=org.goldgom.chemicalelements.fluid.SorptionExchange.release(absorbed.water(),absorbed.pores(),absorbed.pores().volume());
  helper.assertTrue(released.water().equals(water)&&released.pores().equals(empty),"Production pore release recovers all inventory");
  var blocked=org.goldgom.chemicalelements.fluid.SorptionExchange.absorb(water,empty,dense);
  helper.assertTrue(blocked.pores().volume().signum()>0&&blocked.water().plus(blocked.pores()).equals(water),"Production dense material has finite surface wetting and conserves inventory");
  for(var block:net.minecraft.core.registries.BuiltInRegistries.BLOCK)helper.assertTrue(org.goldgom.chemicalelements.fluid.SorptionLibrary.INSTANCE.materialForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)).poreCapacity().signum()>0,"Every registered block has an explicit or default finite wetting capacity");
  helper.assertTrue(org.goldgom.chemicalelements.fluid.SorptionLibrary.INSTANCE.material(ResourceLocation.parse("unknown:material")).isEmpty(),"Unknown material is not silently assigned porosity");
  helper.succeed();
 }
}

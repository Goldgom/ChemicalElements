package org.goldgom.chemicalelements.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.fluid.WaterTransport;
import java.math.BigInteger;
import java.util.*;

/** Event-driven finite-water scheduler. B owns the sole durable cells; this class owns no inventories. */
public final class FiniteWaterRuntime {
 public interface StorePort {
  /** Return only loaded cells, with dry receiving neighbors represented explicitly. Never load chunks. */
  Map<WaterTransport.Position,WaterTransport.Cell> neighborhood(ServerLevel level,BlockPos center);
  /** All changed cells + transaction receipt must persist atomically; false means no mutations. */
  boolean commit(ServerLevel level,UUID transaction,WaterTransport.Plan plan);
  /** Revision checked visual operation; must not mint fluid inventory. */
  void render(ServerLevel level,WaterTransport.Position position,int levelFromOneToEight);
 }
 private static StorePort store;
 private static final Map<ServerLevel,LinkedHashSet<BlockPos>> dirty=new WeakHashMap<>();
 private static final int CENTERS_PER_TICK=256;
 private static final BigInteger TICK_FLUX=new BigInteger("125000000000000000"); // 0.125 L/tick game hydraulic calibration, volume scale 18.
 private FiniteWaterRuntime(){}
 public static synchronized void bind(StorePort port){if(store!=null&&store!=port)throw new IllegalStateException("water store already bound");store=Objects.requireNonNull(port);}
 public static boolean ready(){return store!=null;}
 public static void mark(ServerLevel level,BlockPos pos){if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)dirty.computeIfAbsent(level,k->new LinkedHashSet<>()).add(pos.immutable());}
 /** B re-enqueues persisted active cells on load. Unloaded coordinates never stay in a global queue. */
 public static void unload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event){
  if(event.getLevel() instanceof ServerLevel level){var queue=dirty.get(level);if(queue==null)return;var chunk=event.getChunk().getPos();queue.removeIf(p->(p.getX()>>4)==chunk.x&&(p.getZ()>>4)==chunk.z);if(queue.isEmpty())dirty.remove(level);}
 }
 public static void unloadLevel(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)dirty.remove(level);}
 public static int queuedPositions(ServerLevel level){var queue=dirty.get(level);return queue==null?0:queue.size();}
 public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level)||store==null)return;
  LinkedHashSet<BlockPos> queue=dirty.get(level);if(queue==null||queue.isEmpty())return;
  List<BlockPos> batch=new ArrayList<>();
  var pending=queue.iterator();while(pending.hasNext()&&batch.size()<CENTERS_PER_TICK){batch.add(pending.next());pending.remove();}
  batch.sort(Comparator.<BlockPos>comparingInt(p->p.getY()).thenComparingInt(p->p.getX()).thenComparingInt(p->p.getZ()));
  // Read the union before any settlement. Overlapping dirty neighborhoods must not spend
  // the same tick flux repeatedly, nor make the result depend on event insertion order.
  Map<WaterTransport.Position,WaterTransport.Cell> cells=new TreeMap<>();
  for(BlockPos center:batch){if(level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)==null)continue;
   for(var entry:store.neighborhood(level,center).entrySet()){
    var p=entry.getKey();if(!level.hasChunkAt(new BlockPos(p.x(),p.y(),p.z()))||!entry.getValue().loaded())continue;
    var previous=cells.putIfAbsent(p,entry.getValue());
    if(previous!=null&&!previous.equals(entry.getValue())){batch.forEach(pos->mark(level,pos));return;}
   }
  }
  List<WaterTransport.Edge> edges=new ArrayList<>();
  for(var from:cells.keySet())for(int[] delta:new int[][]{{0,-1,0},{-1,0,0},{1,0,0},{0,0,-1},{0,0,1}}){var to=new WaterTransport.Position(from.x()+delta[0],from.y()+delta[1],from.z()+delta[2]);if(cells.containsKey(to))edges.add(new WaterTransport.Edge(from,to));}
  var plan=WaterTransport.plan(cells,edges,TICK_FLUX);if(plan.replacements().isEmpty())return;
  if(!store.commit(level,UUID.randomUUID(),plan)){batch.forEach(pos->mark(level,pos));return;}
  plan.replacements().forEach((pos,parcel)->{BigInteger capacity=cells.get(pos).capacity();int height=parcel.volume().signum()==0?0:parcel.volume().multiply(BigInteger.valueOf(8)).add(capacity).subtract(BigInteger.ONE).divide(capacity).intValueExact();store.render(level,pos,height);mark(level,new BlockPos(pos.x(),pos.y(),pos.z()));});
 }
 /** Vanilla-height projection only; actual volume stays in B store. Call under B's fluid-write guard. */
 public static void project(ServerLevel level,WaterTransport.Position pos,int height){
  BlockPos block=new BlockPos(pos.x(),pos.y(),pos.z());if(!level.hasChunkAt(block))return;
  var old=level.getBlockState(block);if(!old.isAir()&&!old.is(Blocks.WATER))return; // waterlogged/pore visuals need their own adapter.
  if(height==0){if(old.is(Blocks.WATER))level.setBlock(block,Blocks.AIR.defaultBlockState(),2);}
  else if(height>=1&&height<=8)level.setBlock(block,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,height==8?0:8-height),2);
  else throw new IllegalArgumentException("invalid water height");
 }
}

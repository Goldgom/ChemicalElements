package org.goldgom.chemicalelements.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.fluid.WaterTransport;
import org.goldgom.chemicalelements.fluid.WaterLevelProjection;
import java.math.BigInteger;
import java.util.*;

/** Event-driven finite-water scheduler. B owns the sole durable cells; this class owns no inventories. */
public final class FiniteWaterRuntime {
 public interface StorePort {
  /** Admit newly observed exposed water in one bounded transaction before reading the snapshot union. */
  default boolean prepare(ServerLevel level,List<BlockPos> centers){return true;}
  /** Return only loaded cells, with dry receiving neighbors represented explicitly. Never load chunks. */
  Map<WaterTransport.Position,WaterTransport.Cell> neighborhood(ServerLevel level,BlockPos center);
  default Map<WaterTransport.Position,WaterTransport.Cell> gravityNeighborhood(ServerLevel level,BlockPos source){return Map.of();}
  default boolean hasFallSpace(ServerLevel level,WaterTransport.Position position){return false;}
  /** All changed cells + transaction receipt must persist atomically; false means no mutations. */
  boolean commit(ServerLevel level,UUID transaction,WaterTransport.Plan plan);
  /** Revision checked visual operation; must not mint fluid inventory. */
  void render(ServerLevel level,WaterTransport.Position position,int volumeStepFromOneToTen);
 }
 private static StorePort store;
 private static final Map<ServerLevel,LinkedHashSet<BlockPos>> dirty=new WeakHashMap<>();
 private static final int CENTERS_PER_TICK=1; // One connected-region solve; no overlapping per-cell neighborhoods.
 public static final int FLOW_INTERVAL_TICKS=10;
 private static final Map<ServerLevel,Map<BlockPos,Long>> due=new WeakHashMap<>();
 private static final Map<ServerLevel,Set<BlockPos>> gravity=new WeakHashMap<>();
 private static final Map<ServerLevel,Set<BlockPos>> active=new WeakHashMap<>();
 private static final Map<ServerLevel,Map<BlockPos,Set<BlockPos>>> resumes=new WeakHashMap<>();
 public record Timing(long events,long lastGameTime,long completedSteps,long lastStepNanos) {}
 private static final Map<ServerLevel,Timing> timing=new WeakHashMap<>();
 public static Timing timing(ServerLevel level){return timing.getOrDefault(level,new Timing(0,-1,0,0));}
 private static final BigInteger TICK_FLUX=new BigInteger("1000000000000000000000"); // A full 1000 L cell may fall each flow step.
 private FiniteWaterRuntime(){}
 public static synchronized void bind(StorePort port){if(store!=null&&store!=port)throw new IllegalStateException("water store already bound");store=Objects.requireNonNull(port);}
 public static boolean ready(){return store!=null;}
 public static void mark(ServerLevel level,BlockPos pos){if(!level.isOutsideBuildHeight(pos)&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)dirty.computeIfAbsent(level,k->new LinkedHashSet<>()).add(pos.immutable());}
 public static void markUrgent(ServerLevel level,BlockPos pos){mark(level,pos);var queue=dirty.get(level);if(queue!=null&&queue.contains(pos)){queue.addFirst(pos.immutable());active.computeIfAbsent(level,k->new HashSet<>()).add(pos.immutable());}var deadlines=due.get(level);if(deadlines!=null)deadlines.remove(pos);var falling=gravity.get(level);if(falling!=null)falling.remove(pos);}
 /** B re-enqueues persisted active cells on load. Unloaded coordinates never stay in a global queue. */
 public static void unload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event){
  if(event.getLevel() instanceof ServerLevel level){
   var chunk=event.getChunk().getPos();java.util.function.Predicate<BlockPos> inside=p->(p.getX()>>4)==chunk.x&&(p.getZ()>>4)==chunk.z;
   var queue=dirty.get(level);if(queue!=null){queue.removeIf(inside);if(queue.isEmpty())dirty.remove(level);}
   var d=due.get(level);if(d!=null)d.keySet().removeIf(inside);
   var g=gravity.get(level);if(g!=null)g.removeIf(inside);
   var a=active.get(level);if(a!=null)a.removeIf(inside);
   var r=resumes.get(level);if(r!=null){r.keySet().removeIf(inside);r.values().forEach(origins->origins.removeIf(inside));}
  }
 }
 public static void unloadLevel(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){dirty.remove(level);timing.remove(level);due.remove(level);gravity.remove(level);active.remove(level);resumes.remove(level);}}
 public static int queuedPositions(ServerLevel level){var queue=dirty.get(level);return queue==null?0:queue.size();}
 public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level)||store==null)return;
  var previousTiming=timing(level);
  if(previousTiming.lastGameTime()==level.getGameTime())return;
  timing.put(level,new Timing(previousTiming.events()+1,level.getGameTime(),previousTiming.completedSteps(),previousTiming.lastStepNanos()));
  // Small two-cell continuations have their own bounded budget. They must not wait
  // behind whole ocean-region solves; each still respects its individual stage deadline.
  for(int i=0;i<4;i++)if(!step(level,true))break;
  step(level,false);
 }
 private static boolean step(ServerLevel level,boolean onlyGravity){
  LinkedHashSet<BlockPos> queue=dirty.get(level);if(queue==null||queue.isEmpty())return false;
  var deadlines=due.computeIfAbsent(level,k->new HashMap<>());var falling=gravity.computeIfAbsent(level,k->new HashSet<>());
  var foreground=active.computeIfAbsent(level,k->new HashSet<>());
  long started=System.nanoTime();
  List<BlockPos> batch=new ArrayList<>();
  // Interactive work keeps its priority for every stage. Reserve one tick in four for
  // discovery so a large natural shoreline cannot starve digging, buckets or waterfalls.
  boolean preferForeground=level.getGameTime()%4!=0;
  for(int pass=0;pass<2&&batch.isEmpty();pass++){
   var pending=queue.iterator();while(pending.hasNext()&&batch.size()<CENTERS_PER_TICK){var p=pending.next();
    if(falling.contains(p)!=onlyGravity||deadlines.getOrDefault(p,0L)>level.getGameTime()||pass==0&&foreground.contains(p)!=preferForeground)continue;
    batch.add(p);pending.remove();
   }
  }
  if(batch.isEmpty())return false;
  boolean gravityOnly=falling.remove(batch.getFirst());
  boolean wasActive=foreground.remove(batch.getFirst())||!gravityOnly&&queue.stream().anyMatch(p->foreground.contains(p)&&batch.getFirst().distManhattan(p)<=org.goldgom.chemicalelements.Config.fluidUpdateRadius);
  var waiting=resumes.computeIfAbsent(level,k->new HashMap<>());
  var owners=waiting.remove(batch.getFirst());
  if(owners==null)owners=new HashSet<>();
  if(!gravityOnly||owners.isEmpty())owners.addAll(batch);
  final Set<BlockPos> continuationOwners=owners;
  int radius=org.goldgom.chemicalelements.Config.fluidUpdateRadius;
  batch.sort(Comparator.<BlockPos>comparingInt(p->p.getY()).thenComparingInt(p->p.getX()).thenComparingInt(p->p.getZ()));
  if(!gravityOnly&&!store.prepare(level,List.copyOf(batch))){batch.forEach(pos->{mark(level,pos);if(wasActive)foreground.add(pos);});waiting.put(batch.getFirst(),new HashSet<>(owners));return false;}
  // Read the union before any settlement. Overlapping dirty neighborhoods must not spend
  // the same tick flux repeatedly, nor make the result depend on event insertion order.
  Map<WaterTransport.Position,WaterTransport.Cell> cells=new TreeMap<>();
  for(BlockPos center:batch){if(level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)==null)continue;
   for(var entry:(gravityOnly?store.gravityNeighborhood(level,center):store.neighborhood(level,center)).entrySet()){
    var p=entry.getKey();if(!level.hasChunkAt(new BlockPos(p.x(),p.y(),p.z()))||!entry.getValue().loaded())continue;
    var previous=cells.putIfAbsent(p,entry.getValue());
    if(previous!=null&&!previous.equals(entry.getValue())){batch.forEach(pos->mark(level,pos));return false;}
   }
  }
  // All these pending cells are covered by this immutable-origin region. A propagated change
  // must not become a new origin and march the same update across an entire ocean.
  queue.removeIf(p->{if(!gravityOnly&&!falling.contains(p)&&!waiting.containsKey(p)&&batch.stream().anyMatch(center->center.distManhattan(p)<=radius)){deadlines.remove(p);foreground.remove(p);return true;}return false;});
  List<WaterTransport.Edge> edges=new ArrayList<>();
  for(var from:cells.keySet())for(int[] delta:new int[][]{{0,-1,0},{0,1,0},{-1,0,0},{1,0,0},{0,0,-1},{0,0,1}}){var to=new WaterTransport.Position(from.x()+delta[0],from.y()+delta[1],from.z()+delta[2]);if(cells.containsKey(to))edges.add(new WaterTransport.Edge(from,to));}
  // Also restore visuals after a wet solid is broken or a saved projection is loaded.
  for(var center:batch){var pos=new WaterTransport.Position(center.getX(),center.getY(),center.getZ());var cell=cells.get(pos);if(cell!=null&&cell.revision()>=0&&cell.absorptionRate()==null)store.render(level,pos,height(cell.parcel().volume(),cell.capacity()));}
  var threshold=java.math.BigDecimal.valueOf(org.goldgom.chemicalelements.Config.minimumWaterLitres).movePointRight(18).toBigInteger();
  var minimumDifference=java.math.BigDecimal.valueOf(org.goldgom.chemicalelements.Config.minimumWaterLevelDifferenceLitres).movePointRight(18).toBigInteger();
  var plan=WaterTransport.evaporateTraces(cells,WaterTransport.stage(cells,edges,TICK_FLUX,minimumDifference),threshold);
  if(plan.replacements().isEmpty()){batch.forEach(deadlines::remove);continueBoundaryFalls(level,cells,continuationOwners);return true;}
  if(!store.commit(level,UUID.randomUUID(),plan)){batch.forEach(pos->{mark(level,pos);if(gravityOnly)falling.add(pos);if(wasActive)foreground.add(pos);});waiting.put(batch.getFirst(),new HashSet<>(owners));return false;}
  plan.replacements().forEach((pos,parcel)->{if(cells.get(pos).absorptionRate()==null)store.render(level,pos,height(parcel.volume(),cells.get(pos).capacity()));});
  if(!gravityOnly)batch.forEach(pos->{mark(level,pos);if(wasActive)foreground.add(pos);deadlines.put(pos,level.getGameTime()+org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks);});
  else batch.forEach(deadlines::remove);
  for(var origin:owners){
   if(origin.equals(batch.getFirst()))continue;
   if(plan.replacements().keySet().stream().noneMatch(p->origin.distManhattan(new BlockPos(p.x(),p.y(),p.z()))<=radius))continue;
   mark(level,origin);if(queue.contains(origin)){
    foreground.add(origin);falling.remove(origin);
    deadlines.putIfAbsent(origin,level.getGameTime()+org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks);
   }
  }
  continueBoundaryFalls(level,cells,continuationOwners);
  var observed=timing(level);timing.put(level,new Timing(observed.events(),observed.lastGameTime(),observed.completedSteps()+1,System.nanoTime()-started));
  return true;
 }
 private static void continueBoundaryFalls(ServerLevel level,Map<WaterTransport.Position,WaterTransport.Cell> cells,Set<BlockPos> owners){
  for(var p:cells.keySet())if(cells.get(p).absorptionRate()==null){
   if(!cells.containsKey(new WaterTransport.Position(p.x(),p.y()-1,p.z()))&&store.hasFallSpace(level,p))queueBoundaryFall(level,p,owners);
   // A clipped underwater column must refill from its actual upper neighbor, not leave
   // a false free surface under the sea. Each continuation is still a single downward edge.
   var up=new WaterTransport.Position(p.x(),p.y()+1,p.z());
   // Empty cells have no fluid identity yet, so check the actual upper supply even when
   // snapshot metadata could not classify it (for example, an empty cap under lava).
   if(!cells.containsKey(up)&&store.hasFallSpace(level,up))queueBoundaryFall(level,up,owners);
  }
 }
 private static void queueBoundaryFall(ServerLevel level,WaterTransport.Position p,Set<BlockPos> owners){
  var pos=new BlockPos(p.x(),p.y(),p.z());
  var queue=dirty.get(level);var falling=gravity.computeIfAbsent(level,k->new HashSet<>());
  resumes.computeIfAbsent(level,k->new HashMap<>()).computeIfAbsent(pos,k->new HashSet<>()).addAll(owners);
  active.computeIfAbsent(level,k->new HashSet<>()).add(pos);
  if(queue!=null&&queue.contains(pos)&&!falling.contains(pos))return; // Never downgrade a full-region job.
  mark(level,pos);
  if(gravity.computeIfAbsent(level,k->new HashSet<>()).add(pos))due.computeIfAbsent(level,k->new HashMap<>()).put(pos,level.getGameTime()+org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks);
 }
 private static int height(BigInteger volume,BigInteger capacity){return volume.signum()==0?0:WaterLevelProjection.height(volume,capacity);}
 /** Ten-band geometry metadata; actual volume stays in B store. Call under B's fluid-write guard. */
 public static void project(ServerLevel level,WaterTransport.Position pos,int height){
  BlockPos block=new BlockPos(pos.x(),pos.y(),pos.z());if(!level.hasChunkAt(block))return;
  var old=level.getBlockState(block);if(!org.goldgom.chemicalelements.instance.WaterStorePort.freeSpace(level,block))return; // waterlogged/pore visuals need their own adapter.
  if(height==0){if(org.goldgom.chemicalelements.fluid.FiniteFluids.exposed(old))level.setBlock(block,Blocks.AIR.defaultBlockState(),2);}
  else if(height>=1&&height<=10){
   var cell=org.goldgom.chemicalelements.persistence.WorldChemistryStore.water(level,block);
   var fluid=org.goldgom.chemicalelements.fluid.FiniteFluids.resolve(cell.map(org.goldgom.chemicalelements.persistence.ChunkChemistry.WaterCell::fluidId).orElse("minecraft:water"));
   boolean full=cell.map(c->c.volume().compareTo(org.goldgom.chemicalelements.instance.WaterStorePort.BLOCK_CAPACITY)>=0).orElse(false);
   int vanillaLevel=WaterLevelProjection.vanillaLevel(height,full);
   var visual=(full?fluid.getSource(false):fluid.getFlowing(8-vanillaLevel,false)).createLegacyBlock();
   if(visual.hasProperty(org.goldgom.chemicalelements.fluid.FiniteLiquidShape.VOLUME_STEP))visual=visual.setValue(org.goldgom.chemicalelements.fluid.FiniteLiquidShape.VOLUME_STEP,height);
   if(!old.equals(visual))level.setBlock(block,visual,2);
  }
  else throw new IllegalArgumentException("invalid water height");
 }
}

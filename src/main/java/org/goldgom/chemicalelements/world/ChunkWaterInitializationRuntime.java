package org.goldgom.chemicalelements.world;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Loaded-chunk work queue only, never an inventory or a world-coordinate index.
 * B owns provenance, persisted scan cursor, initial admission and tombstones in its sole journal.
 * Cold, unvisited world is not counted as an already initialized infinite reservoir. */
public final class ChunkWaterInitializationRuntime {
 public static final int POSITIONS_PER_TICK = 2048;
 public static final int POSITIONS_PER_SLICE = 256;
 /** Includes sealed, unavailable and provenance-pending chunks, not only scanned positions. */
 public static final int CHUNKS_PER_TICK = 8;
 public record Snapshot(long generation, int cursor, boolean complete, boolean provenanceReady) {
  public Snapshot { if(generation<0||cursor<0)throw new IllegalArgumentException("invalid initialization snapshot"); }
 }
 public record Observed(BlockPos position, BlockState state) {
  public Observed { position=position.immutable();Objects.requireNonNull(state); }
 }
 public record Slice(int start, int end, boolean complete, List<Observed> observed) {
  public Slice { observed=List.copyOf(observed);if(start<0||end<=start||end-start!=observed.size())throw new IllegalArgumentException("invalid scan slice"); }
 }
 public interface Owner {
  /** Read-only. A missing provenance proof must report provenanceReady=false, not inhabitedTime==0.
   * Persisted complete means never resample, including an exhausted chunk. No strong loads. */
  Snapshot snapshot(ServerLevel level, LevelChunk chunk);
  /** Recheck same loaded chunk incarnation, full generation, cursor and every observed block state.
   * In ONE durable B transaction: initialize only proven generated water, record initial admission,
   * persist water + per-position exhaustion markers + scan cursor; final slice seals chunk marker.
   * Already owned/placed water must be preserved, never resampled. While admission is incomplete,
   * B must gate interactions/flow through the same owner, so a bucket cannot outrun the scan.
   * False means no changes. Persist request-bound receipt; recovery advances cursor exactly once.
   * No second journal, no per-call salt minting, no independent tombstone write after water commit. */
  boolean commit(ServerLevel level, LevelChunk chunk, UUID operation, Snapshot expected, Slice slice);
 }
 private static volatile Owner owner;
 private static final Map<ServerLevel,LinkedHashSet<Long>> pending=new WeakHashMap<>();
 private ChunkWaterInitializationRuntime() {}
 public static synchronized void bind(Owner value) {
  Objects.requireNonNull(value);if(owner!=null&&owner!=value)throw new IllegalStateException("initialization owner already bound");owner=value;
 }
 public static boolean ready(){return owner!=null;}
 public static void load(ChunkEvent.Load event){
  // Load notification may precede publication through getChunkNow; tick resolves it without loading.
  if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
   pending.computeIfAbsent(level,k->new LinkedHashSet<>()).add(chunk.getPos().toLong());
 }
 /** B may enqueue an already loaded chunk after installing the verified owner; never loads it.
  * Unlike the lifecycle notification, explicit callers must hold the currently published instance. */
 public static void enqueue(ServerLevel level,LevelChunk chunk){
  var pos=chunk.getPos();
  if(level.getChunkSource().getChunkNow(pos.x,pos.z)!=chunk)return;
  pending.computeIfAbsent(level,k->new LinkedHashSet<>()).add(pos.toLong());
 }
 public static void unload(ChunkEvent.Unload event){
  if(event.getLevel() instanceof ServerLevel level){var queue=pending.get(level);if(queue!=null){queue.remove(event.getChunk().getPos().toLong());if(queue.isEmpty())pending.remove(level);}}
 }
 public static void unloadLevel(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)pending.remove(level);}
 public static int queuedChunks(ServerLevel level){var queue=pending.get(level);return queue==null?0:queue.size();}
 public static void tick(LevelTickEvent.Post event){
  var store=owner;if(store==null||!(event.getLevel() instanceof ServerLevel level))return;
  var queue=pending.get(level);if(queue==null||queue.isEmpty())return;
  int budget=POSITIONS_PER_TICK;
  // Independent visit budget also bounds snapshot I/O when no provenance is ready.
  // Requeued chunks rotate to the tail, so blocked chunks cannot starve later arrivals.
  int visits=Math.min(queue.size(),CHUNKS_PER_TICK);
  while(budget>0&&visits-->0&&!queue.isEmpty()){
   var iterator=queue.iterator();long key=iterator.next();iterator.remove();
   var chunk=level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key));
   if(chunk==null)continue; // Unloaded work is discarded; B cursor resumes on the next load event.
   var before=store.snapshot(level,chunk);
   if(before.complete())continue;
   if(!before.provenanceReady()){queue.add(key);continue;}
   int height=level.getMaxBuildHeight()-level.getMinBuildHeight();
   int total=Math.multiplyExact(height,256);
   if(before.cursor()>=total)throw new IllegalStateException("unsealed or invalid water initialization cursor");
   int end=Math.min(total,before.cursor()+Math.min(budget,POSITIONS_PER_SLICE));
   var observations=new ArrayList<Observed>(end-before.cursor());
   for(int index=before.cursor();index<end;index++){
    int y=level.getMinBuildHeight()+index/256,local=index%256;
    var pos=new BlockPos(chunk.getPos().getMinBlockX()+(local&15),y,chunk.getPos().getMinBlockZ()+(local>>>4));
    observations.add(new Observed(pos,chunk.getBlockState(pos)));
   }
   budget-=observations.size();
   var slice=new Slice(before.cursor(),end,end==total,observations);
   var operation=UUID.nameUUIDFromBytes((level.dimension().location()+"/water-admission/"+key+"/"+before.generation()+"/"+before.cursor()+"/"+end).getBytes(StandardCharsets.UTF_8));
   boolean committed=store.commit(level,chunk,operation,before,slice);
   if(!committed||!slice.complete())queue.add(key);
  }
  if(queue.isEmpty())pending.remove(level);
 }
}

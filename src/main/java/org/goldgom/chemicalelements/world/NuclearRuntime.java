package org.goldgom.chemicalelements.world;

import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.nuclear.BranchingDecay;

/** No inventory is owned here. B persists full state, branch debts and last processed game tick. */
public final class NuclearRuntime {
 /** activeSinceTick is the current continuous loaded/active window start, supplied by B.
  * It resets on load/restart; lastTick alone is not proof of time spent loaded. */
 public record Snapshot(String identity,long revision,long lastTick,long activeSinceTick,BranchingDecay.State state){
  public Snapshot{Objects.requireNonNull(identity);Objects.requireNonNull(state);if(identity.isBlank()||revision<0||lastTick<0||activeSinceTick<0)throw new IllegalArgumentException("invalid nuclear activity snapshot");}
 }
 public interface Port {
  /** Loaded instances only, including blocks/items/machines; identities must be unique. */
  Collection<Snapshot> loaded(ServerLevel level);
  List<BranchingDecay.Rule> rules();
  /** CAS and receipt atomic with nuclear/chemical projection, emitted particles and heat. */
  boolean commit(ServerLevel level,UUID receipt,Snapshot expected,BranchingDecay.State after,long tick);
  void diagnostic(ServerLevel level,String identity,String reason);
 }
 private static volatile Port port;
 private NuclearRuntime(){}
 public static synchronized void bind(Port value){if(port!=null&&port!=value)throw new IllegalStateException("nuclear owner already bound");port=Objects.requireNonNull(value);}
 public static boolean ready(){return port!=null;}
 public static void tick(LevelTickEvent.Post event){
  var owner=port;if(owner==null||!(event.getLevel() instanceof ServerLevel level))return;
  long tick=level.getGameTime();if(tick%20!=0)return;
  var rules=List.copyOf(owner.rules());Set<String> seen=new HashSet<>();
  for(var before:owner.loaded(level).stream().sorted(Comparator.comparing(Snapshot::identity)).toList()){
   if(!seen.add(before.identity)){owner.diagnostic(level,before.identity,"Duplicate owner identity; skipped");continue;}
   long start=Math.max(before.lastTick,before.activeSinceTick);
   if(start>=tick)continue;
   // Never count even a partial offline second after reload; B's active window is authoritative.
   double seconds=Math.min(20L,tick-start)/20.0;
   try{
    var next=BranchingDecay.tick(before.state,rules,seconds,true);
    UUID receipt=UUID.nameUUIDFromBytes((level.dimension().location()+"/nuclear/"+before.identity+"/"+before.revision+"/"+tick).getBytes(StandardCharsets.UTF_8));
    if(!owner.commit(level,receipt,before,next,tick))owner.diagnostic(level,before.identity,"Nuclear CAS rejected; original inventory retained");
   }catch(IllegalArgumentException invalid){owner.diagnostic(level,before.identity,"UNMODELED: "+invalid.getMessage());}
  }
 }
}

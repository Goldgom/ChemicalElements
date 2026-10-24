package org.goldgom.chemicalelements.machine;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
/** Structural metadata only, never material inventory. Rebuilt on validation after world load. Server thread only. */
public final class StructureClaims {
 private static final Map<Level,Map<BlockPos,BlockPos>> LEVELS=new WeakHashMap<>();
 private StructureClaims(){}
 public static Optional<BlockPos> claim(Level level,BlockPos controller,Set<BlockPos> cells){
  var claims=LEVELS.computeIfAbsent(level,l->new HashMap<>());
  for(var cell:cells){var owner=claims.get(cell);if(owner!=null&&!owner.equals(controller)){
   if(level.hasChunkAt(owner)&&!(level.getBlockEntity(owner) instanceof MachineBlockEntity)){claims.values().removeIf(owner::equals);continue;}
   return Optional.of(owner);
  }}
  claims.entrySet().removeIf(e->e.getValue().equals(controller)&&!cells.contains(e.getKey()));
  cells.forEach(cell->claims.put(cell.immutable(),controller.immutable()));return Optional.empty();
 }
 public static void release(Level level,BlockPos controller){var claims=LEVELS.get(level);if(claims!=null)claims.values().removeIf(controller::equals);}
}

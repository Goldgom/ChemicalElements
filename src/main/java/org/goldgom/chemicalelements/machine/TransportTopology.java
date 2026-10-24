package org.goldgom.chemicalelements.machine;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Read-only routing geometry. Does not load chunks, reserve matter, or commit transfers.
 * Prepared for the durable shared settlement port; not installed in MachineTransport.tick. */
public final class TransportTopology {
 private TransportTopology(){}
 public record Route(BlockPos controller,BlockPos source,BlockPos destination){
  public Route{
   controller=Objects.requireNonNull(controller).immutable();
   source=Objects.requireNonNull(source).immutable();
   destination=Objects.requireNonNull(destination).immutable();
   if(controller.equals(source)||controller.equals(destination)||source.equals(destination))throw new IllegalArgumentException("route participants overlap");
  }
  /** Controller participates even if both material endpoints happen to share another chunk. */
  public List<BlockPos> participants(){return List.of(controller,source,destination);}
  public boolean sameChunk(){return chunk(controller,source)&&chunk(controller,destination);}
  /** Caller supplies Level.hasChunkAt, never getChunk: this predicate must not load chunks. */
  public boolean allLoaded(Predicate<BlockPos> loaded){Objects.requireNonNull(loaded);return participants().stream().allMatch(loaded);}
 }
 /** The facing face is the outlet; its opposite is the inlet. All six axes are supported. */
 public static Route directed(BlockPos controller,Direction outlet){
  Objects.requireNonNull(outlet);return new Route(controller,controller.relative(outlet.getOpposite()),controller.relative(outlet));
 }
 /** Existing vertical behavior, for comparison and migration without silently changing ports. */
 public static Route legacyVertical(BlockPos controller){return directed(controller,Direction.DOWN);}
 private static boolean chunk(BlockPos a,BlockPos b){return (a.getX()>>4)==(b.getX()>>4)&&(a.getZ()>>4)==(b.getZ()>>4);}
}

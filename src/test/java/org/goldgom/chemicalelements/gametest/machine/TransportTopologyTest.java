package org.goldgom.chemicalelements.gametest.machine;
import java.util.*;
import net.minecraft.core.*;
import org.goldgom.chemicalelements.machine.TransportTopology;
public final class TransportTopologyTest {
 public static void main(String[] args){int checks=0;
  for(int x:new int[]{-17,-16,-1,0,15,16})for(int z:new int[]{-17,-16,-1,0,15,16})for(Direction d:Direction.values()){
   var p=new BlockPos(x,64,z);var r=TransportTopology.directed(p,d);
   if(!r.destination().equals(p.relative(d))||!r.source().equals(p.relative(d.getOpposite())))throw new AssertionError("direction mismatch");checks++;
   if(new HashSet<>(r.participants()).size()!=3)throw new AssertionError("overlap");checks++;
   boolean same=r.participants().stream().allMatch(q->Math.floorDiv(q.getX(),16)==Math.floorDiv(x,16)&&Math.floorDiv(q.getZ(),16)==Math.floorDiv(z,16));
   if(same!=r.sameChunk())throw new AssertionError("negative/boundary chunk mismatch");checks++;
   for(var missing:r.participants()){if(r.allLoaded(q->!q.equals(missing)))throw new AssertionError("unloaded participant accepted");checks++;}
   if(!r.allLoaded(q->true))throw new AssertionError("loaded route rejected");checks++;
  }
  var mutable=new BlockPos.MutableBlockPos(1,2,3);var r=TransportTopology.legacyVertical(mutable);mutable.set(9,9,9);
  if(!r.controller().equals(new BlockPos(1,2,3))||!r.destination().equals(new BlockPos(1,1,3)))throw new AssertionError("mutable/legacy route changed");checks++;
  try{new TransportTopology.Route(BlockPos.ZERO,BlockPos.ZERO,new BlockPos(1,0,0));throw new AssertionError("overlap permitted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println(checks+" transport topology assertions passed (read-only geometry, not durable transfer or game evidence)");
 }
}

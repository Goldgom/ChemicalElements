package org.goldgom.chemicalelements.gametest.machine;
import java.math.*;
import java.util.*;
import net.minecraft.core.*;
import org.goldgom.chemicalelements.network.*;
import org.goldgom.chemicalelements.machine.PartDefinition;
import org.goldgom.chemicalelements.compat.DetectorProfile;
import net.minecraft.resources.ResourceLocation;
public final class DetectorPartTest {
 public static void main(String[] args){int checks=0;
  for(int bits:new int[]{1,128,1025,4096,65536})for(int precision:new int[]{1,3,8,18}){
   var original=new BigDecimal(BigInteger.ONE.shiftLeft(bits).add(BigInteger.ONE),18);var shown=DisplayNumbers.format(original,precision);
   if(shown.length()>128)throw new AssertionError("unbounded display");
   var parsed=new BigDecimal(shown.startsWith("≈")?shown.substring(1):shown);if(parsed.compareTo(original)!=0&&!shown.startsWith("≈"))throw new AssertionError("approximation concealed");checks+=2;
  }
  for(var dimensions:List.of(new int[]{1,1,1},new int[]{1,2,1},new int[]{1,3,1},new int[]{2,2,2},new int[]{3,3,3},new int[]{3,6,3}))for(var direction:Direction.Plane.HORIZONTAL){
   var d=new PartDefinition(dimensions[0],dimensions[1],dimensions[2],"test");var cells=d.cells(BlockPos.ZERO,direction);
   if(cells.size()!=dimensions[0]*dimensions[1]*dimensions[2]||new HashSet<>(cells).size()!=cells.size()||!cells.contains(BlockPos.ZERO))throw new AssertionError("overlap or missing anchor");checks++;
  }
  var carbon=ResourceLocation.parse("chemicalelements:carbon");var uranium=ResourceLocation.parse("chemicalelements:uranium");
  var basic=new DetectorProfile("basic",1,3,8,false,false,false,false,false,Set.of(carbon));if(!basic.permits(carbon)||basic.permits(uranium))throw new AssertionError("range leak");checks++;
  try{new CompositionPages.Page("OK",1,1,0,List.of("x".repeat(1025)));throw new AssertionError("line budget bypass");}catch(IllegalArgumentException expected){checks++;}
  try{new PartDefinition(4,2,1,"bad");throw new AssertionError("part bound bypass");}catch(IllegalArgumentException expected){checks++;}
  var first=new PageSession();long old=first.next();if(!first.accepts(first.id(),old))throw new AssertionError("current response rejected");checks++;
  long fresh=first.next();if(first.accepts(first.id(),old)||!first.accepts(first.id(),fresh)||first.accepts(UUID.randomUUID(),fresh))throw new AssertionError("out of order/foreign session accepted");checks+=3;
  first.close();if(first.accepts(first.id(),fresh))throw new AssertionError("closed page replay");checks++;
  var reopened=new PageSession();long opened=reopened.next();if(reopened.accepts(first.id(),fresh)||!reopened.accepts(reopened.id(),opened))throw new AssertionError("reopen replay");checks+=2;
  var ordered=new PageSession();long request=ordered.next();
  if(!ordered.accepts(ordered.id(),request,100)||!ordered.accepts(ordered.id(),request,102))throw new AssertionError("current authorization/revocation rejected");checks+=2;
  if(ordered.accepts(ordered.id(),request,101)||ordered.accepts(ordered.id(),request,102))throw new AssertionError("late authorized page restored after revocation");checks+=2;
  long changed=ordered.next();if(ordered.accepts(ordered.id(),request,103)||!ordered.accepts(ordered.id(),changed,104))throw new AssertionError("changed target accepted old reply");checks+=2;
  ordered.close();var reconnect=new PageSession();long reconnected=reconnect.next();if(reconnect.accepts(ordered.id(),changed,105)||!reconnect.accepts(reconnect.id(),reconnected,106))throw new AssertionError("reconnect replay");checks+=2;
  System.out.println(checks+" detector presentation/part geometry assertions passed (not game evidence)");
 }
}

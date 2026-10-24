package org.goldgom.chemicalelements.network;

import java.util.*;
import java.math.BigDecimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import org.goldgom.chemicalelements.machine.MachineServices;
import org.goldgom.chemicalelements.compat.*;
import org.goldgom.chemicalelements.api.chem.*;

/** No target player UUID, no mutations, no persistent data cache. */
public final class CompositionPages {
 public static final int PAGE_SIZE=1,MAX_ROWS=16,MAX_TEXT=24000,MAX_LINE=1024;
 private static final Map<UUID,Long> LAST_REQUEST=new HashMap<>();
 public record Request(int containerId,int slot,long revision,int instancePage,int componentPage){}
 public record Page(String status,long revision,int count,int page,List<String> lines){public Page{
  lines=List.copyOf(lines);if(status.length()>64||lines.size()>256||lines.stream().anyMatch(s->s.length()>MAX_LINE)||lines.stream().mapToInt(String::length).sum()>MAX_TEXT)throw new IllegalArgumentException("composition display budget");
 }}
 private CompositionPages(){}
 public static Page query(ServerPlayer player,Request request){return query(player,request,true);}
 static Page recheck(ServerPlayer player,Request request){return query(player,request,false);}
 private static Page query(ServerPlayer player,Request request,boolean throttle){
  var menu=player.containerMenu;long now=player.serverLevel().getGameTime();
  if(throttle){Long last=LAST_REQUEST.put(player.getUUID(),now);if(!QueryPolicy.throttle(last,now))return error("RATE_LIMIT");}
  if(!DetectorAccess.available())return error("CURIOS_UNAVAILABLE");
  var access=DetectorAccess.equipped(player);if(access.isEmpty())return error("DETECTOR_NOT_EQUIPPED");var profile=access.get();
  if(!QueryPolicy.authorized(menu.containerId,request.containerId(),menu.slots.size(),request.slot(),menu.stillValid(player)))return error("FORBIDDEN");
  var slot=menu.getSlot(request.slot());
  // Only the requesting player's actual inventory, never another container/player's slots.
  if(slot.container!=player.getInventory()||slot.getContainerSlot()>profile.maxSlot())return error("OUT_OF_DETECTOR_RANGE");
  if(request.instancePage()<0||request.componentPage()<0||request.componentPage()>65535)return error("INVALID_PAGE");
  var stack=slot.getItem();if(stack.isEmpty())return error("EMPTY_SLOT");
  // Nominal data belongs to the atomically reloaded catalog, not the instance settlement service.
  // Equipment and target authorization above remain mandatory for both branches.
  var nominalCatalog=org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.snapshot();
  var lines=new ArrayList<String>();
  if(!profile.instances()){
   if(request.instancePage()!=0)return error("INVALID_PAGE");
   var nominal=nominalCatalog.itemCompositions().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));if(nominal==null)return error("NO_NOMINAL_MODEL");
   lines.add("NOMINAL ONLY / 名义组成; not measured instance data; per item");
   appendElements(lines,nominal,profile,nominalCatalog,request.componentPage());
   return new Page("NOMINAL",nominalCatalog.epoch(),1,0,lines);
  }
  var services=MachineServices.current();if(services.isEmpty())return error("UNMODELED");
  // Instance measurement must use the real runtime and its matching catalog, never a nominal ledger.
  var catalog=services.get().catalog().get();
  var ledger=services.get().stacks().snapshot(stack);if(ledger.isEmpty())return error("NO_LEDGER");var value=ledger.get();
  if(!QueryPolicy.revisionValid(request.revision(),value.revision()))return new Page("STALE",value.revision(),value.instances().size(),request.instancePage(),List.of("Inventory changed; refresh"));
  if(!QueryPolicy.pageValid(request.instancePage(),PAGE_SIZE,value.instances().size()))return error("INVALID_PAGE");
  int start=request.instancePage()*PAGE_SIZE;
  lines.add("INSTANCE MEASUREMENT / 实例测量; detector="+profile.id());
  for(int i=start;i<Math.min(start+PAGE_SIZE,value.instances().size());i++){
   var instance=value.instances().get(i);lines.add("#"+(i+1)+" "+instance.id());
   if(profile.temperature())lines.add("Temperature "+DisplayNumbers.format(instance.thermal().temperatureK(),profile.precision())+" K");
   appendElements(lines,instance.remaining(),profile,catalog,request.componentPage());
   if(profile.liquid()){
    lines.add("Pore water "+DisplayNumbers.format(instance.poreSolution().waterMol().decimal(),profile.precision())+" mol; volume "+DisplayNumbers.format(instance.poreSolution().volumeLitres(),profile.precision())+" L");
    appendElements(lines,instance.poreSolution().dissolved().add(instance.poreSolution().suspended()),profile,catalog,request.componentPage());
   }
   if(profile.nuclides()){
    var isotopes=instance.nuclides().amounts().entrySet().stream().filter(e->catalog.elements().values().stream().anyMatch(element->element.atomicNumber()==e.getKey().z()&&profile.permits(element.id()))).toList();
    for(int n=request.componentPage()*MAX_ROWS;n<Math.min((request.componentPage()+1)*MAX_ROWS,isotopes.size());n++)lines.add("Nuclide "+isotopes.get(n).getKey()+" = "+DisplayNumbers.format(isotopes.get(n).getValue().decimal(),profile.precision())+" mol");
   }
  }
  return new Page("OK",value.revision(),value.instances().size(),request.instancePage(),lines);
 }
 private static void appendElements(List<String> lines,Composition composition,DetectorProfile profile,org.goldgom.chemicalelements.api.data.RegistrySnapshot catalog,int page){
  var filtered=new ArrayList<Map.Entry<net.minecraft.resources.ResourceLocation,java.math.BigInteger>>();
  var visible=new TreeMap<net.minecraft.resources.ResourceLocation,MolAmount>();
  composition.amounts().forEach((id,amount)->{var phase=catalog.requireSpecies(id).phase();if((phase!=Phase.LIQUID||profile.liquid())&&(phase!=Phase.GAS||profile.gas()))visible.put(id,amount);});
  new Composition(visible).inventory(catalog).atoms().entrySet().stream().filter(e->profile.permits(e.getKey())).forEach(filtered::add);
  int offset=page*MAX_ROWS;lines.add("Detected elements "+filtered.size()+"; page "+page);
  for(int i=offset;i<Math.min(offset+MAX_ROWS,filtered.size());i++){
   var entry=filtered.get(i);lines.add(DisplayNumbers.bounded(entry.getKey()+" = "+DisplayNumbers.format(new BigDecimal(entry.getValue(),MolAmount.SCALE),profile.precision())+" atom-mol"));
  }
  if(offset+MAX_ROWS<filtered.size())lines.add("MORE_COMPONENTS");
 }
 public static void forget(UUID player){LAST_REQUEST.remove(player);}
 private static Page error(String status){return new Page(status,-1,0,0,List.of());}
}

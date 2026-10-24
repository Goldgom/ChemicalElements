package org.goldgom.chemicalelements.world;
import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.fluid.*;
public final class SorptionRuntime {
 public record Contact(String identity,long revision,long lastTick,ResourceLocation material,FiniteVolume.Parcel water,FiniteVolume.Parcel pores){}
 public interface Port {
  Collection<Contact> loadedContacts(ServerLevel level);
  /** Persist water plus the same block's pore composition/heat, versions and receipt atomically. */
  boolean commit(ServerLevel level,UUID receipt,Contact expected,SorptionExchange.Exchange replacement,long tick);
  void diagnostic(ServerLevel level,String identity,String reason);
 }
 private static volatile Port port;
 private SorptionRuntime(){}
 public static synchronized void bind(Port value){if(port!=null&&port!=value)throw new IllegalStateException("sorption owner already bound");port=Objects.requireNonNull(value);}
 public static boolean ready(){return port!=null;}
 public static void tick(LevelTickEvent.Post event){
  var owner=port;if(owner==null||!(event.getLevel() instanceof ServerLevel level))return;
  long tick=level.getGameTime();Set<String> seen=new HashSet<>();
  for(var contact:owner.loadedContacts(level).stream().sorted(Comparator.comparing(Contact::identity)).toList()){
   if(!seen.add(contact.identity)||contact.lastTick>=tick)continue;
   var material=SorptionLibrary.INSTANCE.material(contact.material);
   if(material.isEmpty()){owner.diagnostic(level,contact.identity,"UNMODELED material porosity: preserve water and block input");continue;}
   try{
    var result=SorptionExchange.absorb(contact.water,contact.pores,material.get());
    if(result.water().equals(contact.water)&&result.pores().equals(contact.pores))continue;
    var receipt=UUID.nameUUIDFromBytes((level.dimension().location()+"/sorption/"+contact.identity+"/"+contact.revision+"/"+tick).getBytes(StandardCharsets.UTF_8));
    if(!owner.commit(level,receipt,contact,result,tick))owner.diagnostic(level,contact.identity,"CAS rejected; both inventories unchanged");
   }catch(IllegalArgumentException invalid){owner.diagnostic(level,contact.identity,invalid.getMessage());}
  }
 }
}

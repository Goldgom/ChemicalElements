package org.goldgom.chemicalelements.network;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.goldgom.chemicalelements.compat.*;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;

/** Public validated rules only. No inventory, instance, proposal, or transaction authority is sent. */
@EventBusSubscriber(modid="chemicalelements")
public final class ReactionCatalogSync {
 private ReactionCatalogSync(){}
 private record Transfer(Object bundle,UUID token,String hash,List<ReactionDisplay> entries,int page){}
 private static final Map<UUID,Transfer> TRANSFERS=new HashMap<>();
 public record Page(UUID token,String hash,int index,int pages,List<ReactionDisplay> entries) implements CustomPacketPayload {
  public static final Type<Page> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","reaction_catalog"));
  public Page { entries=List.copyOf(entries);if(index<0||pages<1||pages>512||index>=pages||entries.size()>8||hash.length()>160)throw new IllegalArgumentException("Invalid reaction catalog page"); }
  public static final StreamCodec<RegistryFriendlyByteBuf,Page> CODEC=StreamCodec.of((b,p)->{
   b.writeUUID(p.token);b.writeUtf(p.hash,160);b.writeVarInt(p.index);b.writeVarInt(p.pages);b.writeVarInt(p.entries.size());
   for(var e:p.entries){b.writeUtf(e.id(),256);b.writeVarInt(e.lines().size());for(String line:e.lines())b.writeUtf(line,1024);}
  },b->{UUID token=b.readUUID();String hash=b.readUtf(160);int index=b.readVarInt(),pages=b.readVarInt(),n=b.readVarInt();if(n<0||n>8)throw new IllegalArgumentException("Oversized catalog page");var entries=new ArrayList<ReactionDisplay>();for(int i=0;i<n;i++){String id=b.readUtf(256);int count=b.readVarInt();if(count<0||count>32)throw new IllegalArgumentException("Oversized rule");var lines=new ArrayList<String>();for(int j=0;j<count;j++)lines.add(b.readUtf(1024));entries.add(new ReactionDisplay(id,lines));}return new Page(token,hash,index,pages,entries);});
  @Override public Type<Page> type(){return TYPE;}
 }
 public static List<ReactionDisplay> document(org.goldgom.chemicalelements.data.ChemistryBundle bundle){
  var entries=new ArrayList<ReactionDisplay>();
  for(var p:bundle.reactions().pathways()){
   var r=p.rule();var d=r.domain();
   entries.add(new ReactionDisplay(r.id(),List.of("Type: "+r.type(),terms(r.reactants())+" -> "+terms(r.products()),"Temperature: "+d.minK()+" .. "+d.maxK()+" K","Pressure: "+d.minPa()+" .. "+d.maxPa()+" Pa","Capabilities: "+String.join(", ",new TreeSet<>(d.capabilities())),"Catalysts: "+String.join(", ",new TreeSet<>(p.catalysts())),"Duration: "+r.durationTicks()+" ticks | Energy: "+p.joulesPerMol()+" J/mol","Gibbs: "+(r.deltaG()==null?"unknown":r.deltaG()+" J/mol"),"Basis: "+r.basis(),"Source: "+r.source(),"Documentation only; actual inventory and conditions are checked on server.")));
  }
  if(entries.size()>4096)throw new IllegalArgumentException("Reaction display limit exceeded");
  return List.copyOf(entries);
 }
 private static String terms(Map<String,Integer> terms){var list=new ArrayList<String>();new TreeMap<>(terms).forEach((id,n)->list.add(n+" "+id));return String.join(" + ",list);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
  if(!(event.getEntity() instanceof ServerPlayer player))return;
  var bundle=AtomicChemistryCatalog.INSTANCE.bundle();if(bundle.catalog().epoch()==0)return;
  var t=TRANSFERS.get(player.getUUID());
  if(t==null||t.bundle()!=bundle){t=new Transfer(bundle,UUID.randomUUID(),bundle.catalog().contentHash()+":"+bundle.reactions().hash(),document(bundle),0);TRANSFERS.put(player.getUUID(),t);}
  int pages=Math.max(1,(t.entries().size()+7)/8);if(t.page()>=pages)return;
  int from=t.page()*8,to=Math.min(from+8,t.entries().size());
  PacketDistributor.sendToPlayer(player,new Page(t.token(),t.hash(),t.page(),pages,t.entries().subList(from,to)));
  TRANSFERS.put(player.getUUID(),new Transfer(bundle,t.token(),t.hash(),t.entries(),t.page()+1));
 }
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){TRANSFERS.remove(e.getEntity().getUUID());}
 @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e){TRANSFERS.clear();}
}

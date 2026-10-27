package org.goldgom.chemicalelements.network;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="chemicalelements",bus=EventBusSubscriber.Bus.MOD)
public final class ChemistryNetwork {
 private ChemistryNetwork(){}
 public static Consumer<Reply> pageReceiver=page->{};
 private record Watch(Query query,String item,int count,long expires){}
 private static String targetItem(ServerPlayer player,CompositionPages.Request r){if(r.slot()<0||r.slot()>=player.containerMenu.slots.size())return "";return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.containerMenu.getSlot(r.slot()).getItem().getItem()).toString();}
 private static int targetCount(ServerPlayer player,CompositionPages.Request r){return r.slot()<0||r.slot()>=player.containerMenu.slots.size()?0:player.containerMenu.getSlot(r.slot()).getItem().getCount();}
 private static final Map<UUID,Watch> WATCHES=new HashMap<>();
 public record Query(UUID session,long sequence,CompositionPages.Request request) implements CustomPacketPayload {
  public static final Type<Query> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","composition_query"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.sequence);var r=p.request;b.writeVarInt(r.containerId());b.writeVarInt(r.slot());b.writeLong(r.revision());b.writeVarInt(r.instancePage());b.writeVarInt(r.componentPage());},b->new Query(b.readUUID(),b.readLong(),new CompositionPages.Request(b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt(),b.readVarInt())));
  @Override public Type<Query> type(){return TYPE;}
 }
 public record Close(UUID session) implements CustomPacketPayload {
  public static final Type<Close> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","composition_close"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Close> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.session()),b->new Close(b.readUUID()));
  @Override public Type<Close> type(){return TYPE;}
 }
 private static long emissionSequence;
 public record Reply(UUID session,long sequence,long emission,CompositionPages.Page page) implements CustomPacketPayload {
  public Reply(UUID session,long sequence,CompositionPages.Page page){this(session,sequence,++emissionSequence,page);}
  public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","composition_page"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p)->{
   b.writeUUID(p.session);b.writeLong(p.sequence);b.writeLong(p.emission);var v=p.page;b.writeUtf(v.status(),64);b.writeLong(v.revision());b.writeVarInt(v.count());b.writeVarInt(v.page());b.writeVarInt(v.lines().size());for(var l:v.lines())b.writeUtf(l,CompositionPages.MAX_LINE);
  },b->{UUID session=b.readUUID();long sequence=b.readLong(),emission=b.readLong();String status=b.readUtf(64);long revision=b.readLong();int count=b.readVarInt(),page=b.readVarInt(),n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("oversized composition page");var lines=new ArrayList<String>();int chars=0;for(int i=0;i<n;i++){String s=b.readUtf(CompositionPages.MAX_LINE);chars+=s.length();if(chars>CompositionPages.MAX_TEXT)throw new IllegalArgumentException("oversized composition text");lines.add(s);}return new Reply(session,sequence,emission,new CompositionPages.Page(status,revision,count,page,lines));});
  @Override public Type<Reply> type(){return TYPE;}
 }
 public static void forget(UUID id){WATCHES.remove(id);CompositionPages.forget(id);}
 /** Server main thread. Re-query before emitting; no stored chemistry snapshot used for authorization. */
 public static void tick(ServerPlayer player){
  var watch=WATCHES.get(player.getUUID());if(watch==null)return;
  if(player.serverLevel().getGameTime()>watch.expires()){WATCHES.remove(player.getUUID());return;}
  var fresh=CompositionPages.recheck(player,watch.query.request());
  if(!watch.item.equals(targetItem(player,watch.query.request()))||watch.count!=targetCount(player,watch.query.request()))fresh=new CompositionPages.Page("STALE",-1,0,0,List.of("Inventory target changed; refresh"));
  {
   PacketDistributor.sendToPlayer(player,new Reply(watch.query.session(),watch.query.sequence(),fresh));
   WATCHES.put(player.getUUID(),new Watch(watch.query,watch.item,watch.count,watch.expires));
  }
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
  var registrar=event.registrar("7");
  InspectorNetwork.register(registrar);
  registrar.playToClient(ReactionCatalogSync.Page.TYPE,ReactionCatalogSync.Page.CODEC,(packet,context)->context.enqueueWork(()->org.goldgom.chemicalelements.compat.RecipeViews.accept(packet)));
  registrar.playToClient(MachineStatus.TYPE,MachineStatus.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player().containerMenu instanceof org.goldgom.chemicalelements.machine.MachineMenu menu)menu.acceptStatus(packet);}));
  registrar.playToServer(Close.TYPE,Close.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){var watch=WATCHES.get(player.getUUID());if(watch!=null&&watch.query.session().equals(packet.session()))WATCHES.remove(player.getUUID());}}));
  registrar.playToServer(Query.TYPE,Query.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){
   var checked=CompositionPages.query(player,packet.request());
   // A second check immediately before send closes reload/equipment changes between preparation and delivery.
   if(!checked.status().equals("RATE_LIMIT"))checked=CompositionPages.recheck(player,packet.request());
   context.reply(new Reply(packet.session(),packet.sequence(),checked));
   if(!checked.status().equals("RATE_LIMIT")){
    var r=packet.request();var pinned=new Query(packet.session(),packet.sequence(),new CompositionPages.Request(r.containerId(),r.slot(),checked.revision(),r.instancePage(),r.componentPage()));
    WATCHES.put(player.getUUID(),new Watch(pinned,targetItem(player,r),targetCount(player,r),player.serverLevel().getGameTime()+40));
   }
  }}));
  registrar.playToClient(Reply.TYPE,Reply.CODEC,(packet,context)->context.enqueueWork(()->pageReceiver.accept(packet)));
 }
}

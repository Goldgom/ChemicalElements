package org.goldgom.chemicalelements.network;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.goldgom.chemicalelements.item.ChemicalInspectorItem;

public final class InspectorNetwork {
    public static final int PAGE_SIZE=64;
    public static Consumer<Page> receiver=page->{};
    private static final Map<ServerPlayer,Long> lastQuery=new WeakHashMap<>();
    private InspectorNetwork() {}
    public record Query(BlockPos pos,int page) implements CustomPacketPayload {
        public static final Type<Query> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","inspect_block"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeVarInt(p.page);},b->new Query(b.readBlockPos(),b.readVarInt()));
        @Override public Type<Query> type(){return TYPE;}
    }
    public record Page(BlockPos pos,boolean open,int page,int pages,List<String> lines) implements CustomPacketPayload {
        public Page {pos=pos.immutable();lines=List.copyOf(lines);if(page<0||pages<1||page>=pages||lines.size()>PAGE_SIZE||lines.stream().anyMatch(s->s.length()>1024))throw new IllegalArgumentException("invalid inspection page");}
        public static final Type<Page> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","block_chemistry"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Page> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeBoolean(p.open);b.writeVarInt(p.page);b.writeVarInt(p.pages);b.writeVarInt(p.lines.size());p.lines.forEach(s->b.writeUtf(s,1024));},b->{var pos=b.readBlockPos();boolean open=b.readBoolean();int page=b.readVarInt(),pages=b.readVarInt(),count=b.readVarInt();if(count<0||count>PAGE_SIZE)throw new IllegalArgumentException("oversized inspection page");var lines=new ArrayList<String>();for(int i=0;i<count;i++)lines.add(b.readUtf(1024));return new Page(pos,open,page,pages,lines);});
        @Override public Type<Page> type(){return TYPE;}
    }
    public static boolean canQuery(ServerPlayer player,BlockPos pos){return ChemicalInspectorItem.authorized(player)&&BlockChemistryInspection.target(player).filter(pos::equals).isPresent();}
    public static void send(ServerPlayer player,BlockPos pos,int requested,boolean open){
        if(!canQuery(player,pos)||requested<0||!player.connection.hasChannel(Page.TYPE))return;
        var lines=BlockChemistryInspection.describe(player,pos);int pages=Math.max(1,(lines.size()+PAGE_SIZE-1)/PAGE_SIZE),page=Math.min(requested,pages-1);
        var bounded=lines.subList(page*PAGE_SIZE,Math.min(lines.size(),(page+1)*PAGE_SIZE)).stream().map(s->s.length()>1024?s.substring(0,1021)+"...":s).toList();
        PacketDistributor.sendToPlayer(player,new Page(pos,open,page,pages,bounded));
    }
    public static void register(PayloadRegistrar registrar){
        registrar.playToClient(Page.TYPE,Page.CODEC,(packet,context)->context.enqueueWork(()->receiver.accept(packet)));
        registrar.playToServer(Query.TYPE,Query.CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player){long now=player.serverLevel().getGameTime();var previous=lastQuery.get(player);if(previous!=null&&now-previous<2)return;lastQuery.put(player,now);send(player,packet.pos,packet.page,false);}}));
    }
}

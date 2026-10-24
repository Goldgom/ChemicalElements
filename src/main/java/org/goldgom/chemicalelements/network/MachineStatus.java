package org.goldgom.chemicalelements.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One complete server menu diagnostic; never assembled from independently delivered short fields. */
public record MachineStatus(int containerId,BlockPos position,String text) implements CustomPacketPayload {
 public static final int MAX_TEXT=32767;
 public static final Type<MachineStatus> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("chemicalelements","machine_status"));
 public MachineStatus {if(text.length()>MAX_TEXT)throw new IllegalArgumentException("machine diagnostic too large");}
 public static final StreamCodec<RegistryFriendlyByteBuf,MachineStatus> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.containerId);b.writeBlockPos(p.position);b.writeUtf(p.text,MAX_TEXT);},b->new MachineStatus(b.readVarInt(),b.readBlockPos(),b.readUtf(MAX_TEXT)));
 @Override public Type<MachineStatus> type(){return TYPE;}
}

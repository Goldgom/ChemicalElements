package org.goldgom.chemicalelements.compat.jade;

import snownee.jade.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.machine.MachineBlockEntity;

@WailaPlugin("chemicalelements")
public final class ChemistryJadePlugin implements IWailaPlugin {
 @Override public void register(IWailaCommonRegistration registration){registration.registerBlockDataProvider(MachineData.INSTANCE,MachineBlockEntity.class);}
 @Override public void registerClient(IWailaClientRegistration registration){org.goldgom.chemicalelements.compat.jade.client.JadeClient.register(registration);}
 public enum MachineData implements IServerDataProvider<BlockAccessor>{INSTANCE;
  @Override public ResourceLocation getUid(){return ResourceLocation.fromNamespaceAndPath("chemicalelements","machine_data");}
  @Override public void appendServerData(CompoundTag root,BlockAccessor accessor){
   if(!(accessor.getBlockEntity() instanceof MachineBlockEntity machine))return;
   var player=accessor.getPlayer();var pos=machine.getBlockPos();if(player==null||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64)return;
   CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putLong("revision",machine.revision());tag.putString("machine",machine.kind().id());tag.putBoolean("running",machine.running());tag.putString("status",machine.message().substring(0,Math.min(256,machine.message().length())));tag.putInt("elapsed",machine.data.get(0));tag.putInt("duration",machine.data.get(1));root.put("chemicalelements",tag);
  }
 }
}

package org.goldgom.chemicalelements.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

public final class MachineMenu extends AbstractContainerMenu {
 private String diagnostic="Awaiting server / 等待服务端",lastSent;
 private final Player viewer;
 private final MachineBlockEntity machine;
 private final BlockPos position;
 private final ContainerData data;
 private final int layoutSide,layoutVersion;
 private final java.util.UUID transactionSession;
 private boolean transactionSessionActive;
 /** Server-only locator. No inventory copy and no CAS revision is manufactured here. */
 public record ResolvedSlot(Container container,int containerSlot,int menuSlot,int layoutVersion,boolean machineOwner){}

 public MachineMenu(int id,Inventory inventory,BlockPos pos,int configuredSide){this(id,inventory,inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity be?be:null,pos,configuredSide);}
 public MachineMenu(int id,Inventory inventory,MachineBlockEntity machine){this(id,inventory,machine,machine.getBlockPos(),machine.side());}
 private MachineMenu(int id,Inventory inventory,MachineBlockEntity be,BlockPos pos,int configuredSide){
  super(MachineBootstrap.MENU.get(),id);machine=be;position=pos;viewer=inventory.player;
  data=be==null?new SimpleContainerData(6):be.data;
  Container container=be==null?new SimpleContainer(90):be.inventory();
  // Real configured 1..9 grid; reserve a separate control/output bay.
  if(configuredSide<1||configuredSide>MachineBlockEntity.MAX_SIDE)throw new IllegalArgumentException("invalid server grid");
  int side=configuredSide;
  layoutSide=side;layoutVersion=100+side;
  boolean authoritative=viewer instanceof net.minecraft.server.level.ServerPlayer&&!viewer.level().isClientSide&&be!=null;
  transactionSession=authoritative?java.util.UUID.randomUUID():null;
  transactionSessionActive=authoritative;
  if(authoritative)be.trackMenu(this);
  if(be==null)data.set(3,side);
  for(int y=0;y<side;y++)for(int x=0;x<side;x++){
   final int index=y*side+x;
   addSlot(new Slot(container,index,8+x*18,20+y*18){
    @Override public boolean mayPlace(ItemStack stack){return machine==null?!running():stillValid(viewer)&&machine.mayEdit(index);}
    @Override public boolean mayPickup(Player player){return stillValid(player)&&!running();}
   });
  }
  for(int i=0;i<9;i++)addSlot(new Slot(container,81+i,180+(i%3)*18,20+(i/3)*18){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return stillValid(player)&&!running();}});
  int playerY=playerInventoryY(side);
  for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inventory,x+y*9+9,8+x*18,playerY+y*18));
  for(int x=0;x<9;x++)addSlot(new Slot(inventory,x,8+x*18,playerY+58));
  addDataSlots(data);
 }
 public static int playerInventoryY(int side){return Math.max(142,40+side*18);}
 public int elapsed(){return data.get(0);} public int duration(){return data.get(1);} public boolean running(){return data.get(2)!=0;} public int side(){return data.get(3)==0?5:data.get(3);}
 public int tankFillBasisPoints(){return Math.clamp(data.get(5),0,10000);}
 public String statusMessage(){return diagnostic;}
 public boolean acceptStatus(org.goldgom.chemicalelements.network.MachineStatus packet){if(packet.containerId()!=containerId||!packet.position().equals(position))return false;diagnostic=packet.text();return true;}
 @Override public void broadcastChanges(){super.broadcastChanges();if(machine!=null&&viewer instanceof net.minecraft.server.level.ServerPlayer player&&player.containerMenu==this){String current=machine.message();if(!current.equals(lastSent)){net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new org.goldgom.chemicalelements.network.MachineStatus(containerId,position,current));lastSent=current;}}}
 public BlockPos position(){return position;}
 public java.util.Optional<org.goldgom.chemicalelements.api.tx.MenuAuthority> transactionAuthority(net.minecraft.server.level.ServerPlayer actor){
  return transactionSession(actor).map(session->new org.goldgom.chemicalelements.api.tx.MenuAuthority(actor.getUUID(),session,
   net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","machine"),layoutVersion));
 }
 public java.util.Optional<org.goldgom.chemicalelements.api.tx.AuthoritySlot> resolveTransactionSlot(net.minecraft.server.level.ServerPlayer actor,int menuSlot){
  return resolveAuthoritativeSlot(actor,menuSlot).map(slot->{
   org.goldgom.chemicalelements.api.tx.OwnerKey owner=slot.machineOwner()
    ?new org.goldgom.chemicalelements.api.tx.OwnerKey.MachineInventory(actor.level().dimension().location(),position.asLong(),machine.incarnation(),
      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements",machine.kind().id()))
    :new org.goldgom.chemicalelements.api.tx.OwnerKey.PlayerInventory(actor.getUUID());
   return new org.goldgom.chemicalelements.api.tx.AuthoritySlot(owner,slot.container(),slot.containerSlot(),slot.menuSlot(),slot.layoutVersion());
  });
 }
 public int layoutVersion(){return layoutVersion;}
 public int layoutSide(){return layoutSide;}
 /** Generated only by the server, never the client PageSession. No authorization survives invalidation. */
 public java.util.Optional<java.util.UUID> transactionSession(net.minecraft.server.level.ServerPlayer actor){
  return transactionGuard(actor)?java.util.Optional.of(transactionSession):java.util.Optional.empty();
 }
 private boolean transactionGuard(net.minecraft.server.level.ServerPlayer actor){
  if(!transactionSessionActive)return false;
  if(actor!=viewer||!actor.server.isSameThread()||actor.containerMenu!=this||actor.level().isClientSide||!actor.isAlive()||actor.hasDisconnected()||!stillValid(actor)){
   invalidateTransactionSession();return false;
  }
  return true;
 }
 /** Resolve the actual server Slot and verify its reference and raw index against the fixed layout. */
 public java.util.Optional<ResolvedSlot> resolveAuthoritativeSlot(net.minecraft.server.level.ServerPlayer actor,int menuSlot){
  if(!transactionGuard(actor)||menuSlot<0||menuSlot>=slots.size())return java.util.Optional.empty();
  int n=layoutSide*layoutSide,index;Container expected;boolean machineOwner;
  if(menuSlot<n){expected=machine.inventory();index=menuSlot;machineOwner=true;}
  else if(menuSlot<n+9){expected=machine.inventory();index=81+menuSlot-n;machineOwner=true;}
  else if(menuSlot<n+36){expected=actor.getInventory();index=menuSlot-n;machineOwner=false;}
  else if(menuSlot<n+45){expected=actor.getInventory();index=menuSlot-(n+36);machineOwner=false;}
  else return java.util.Optional.empty();
  Slot actual=getSlot(menuSlot);
  if(actual.container!=expected||actual.getContainerSlot()!=index){invalidateTransactionSession();return java.util.Optional.empty();}
  return java.util.Optional.of(new ResolvedSlot(expected,index,menuSlot,layoutVersion,machineOwner));
 }
 public void invalidateTransactionSession(){transactionSessionActive=false;if(machine!=null)machine.untrackMenu(this);}
 @Override public void removed(Player player){invalidateTransactionSession();super.removed(player);}
 @Override public boolean stillValid(Player player){
  return player==viewer&&player.isAlive()&&machine!=null&&!machine.isRemoved()&&machine.getLevel()==player.level()
   &&player.level().hasChunkAt(position)&&player.level().getBlockEntity(position)==machine&&machine.side()==layoutSide
   &&player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)<=64;
 }
 @Override public boolean clickMenuButton(Player player,int id){if(player.level().isClientSide||!stillValid(player)||player.containerMenu!=this)return false;if(id==0){machine.start();player.displayClientMessage(net.minecraft.network.chat.Component.literal(machine.message()),false);return true;}if(id==1){machine.cancel();player.displayClientMessage(net.minecraft.network.chat.Component.literal(machine.message()),false);return true;}return false;}
 @Override public void clicked(int slot,int button,ClickType type,Player player){
  if(player instanceof net.minecraft.server.level.ServerPlayer actor&&!transactionGuard(actor))return;
  super.clicked(slot,button,type,player);
 }
 @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY; /* explicit: no unsafe vanilla copyWithCount transfer; ordinary clicks use B ledger hooks */}
}

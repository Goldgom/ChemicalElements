package org.goldgom.chemicalelements.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.reaction.DissolutionTransaction;
import org.goldgom.chemicalelements.reaction.PhaseEquilibrium;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import java.util.*;

/** Real item contact dispatch; all material mutation stays in B's single transactional owner. */
public final class DissolutionRuntime {
 public interface Port {
  Optional<DissolutionTransaction.Owner> contact(ServerLevel level,ItemEntity entity,BlockPos water);
  RegistrySnapshot catalog();
  List<PhaseEquilibrium.Salt> salts();
  void diagnostic(ItemEntity entity,String code,String message);
  /** B enumerates only loaded actual water/block contacts; no synthetic inventory copies. */
  default Collection<BlockContact> blocks(ServerLevel level){return List.of();}
  default void diagnostic(ServerLevel level,BlockPos block,String code,String message){}
 }
 public record BlockContact(BlockPos solid,BlockPos water,DissolutionTransaction.Owner owner){
  public BlockContact {solid=solid.immutable();water=water.immutable();Objects.requireNonNull(owner);}
 }
 private static Port port;
 private DissolutionRuntime(){}
 public static void bind(Port value){if(port!=null&&port!=value)throw new IllegalStateException("dissolution port already bound");port=Objects.requireNonNull(value);}
 public static void tick(LevelTickEvent.Post event){
  if(port==null||!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
  // One reference supplies both species and solubility: legacy port getters cannot mix epochs.
  var models=org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle();
  for(var entity:level.getAllEntities()){
   if(!(entity instanceof ItemEntity item)||!item.isAlive()||!item.isInWater())continue;
   BlockPos at=item.blockPosition();if(!level.hasChunkAt(at))continue;
   var owner=port.contact(level,item,at);if(owner.isEmpty())continue;
   try{var transaction=new DissolutionTransaction(owner.get(),models.catalog(),models.solubility(),64);var receipt=transaction.commit();if(!receipt.committed())port.diagnostic(item,receipt.code(),receipt.detail());}
   catch(IllegalArgumentException invalidModel){port.diagnostic(item,"UNMODELED",invalidModel.getMessage());}
  }
  Set<BlockPos> visited=new HashSet<>();
  for(var contact:port.blocks(level).stream().sorted(Comparator.comparingLong(c->c.solid().asLong())).toList()){
   if(!visited.add(contact.solid())||!level.hasChunkAt(contact.solid())||!level.hasChunkAt(contact.water()))continue;
   if(!level.getFluidState(contact.water()).is(net.minecraft.tags.FluidTags.WATER))continue;
   if(contact.solid().distManhattan(contact.water())!=1)continue;
   try{var transaction=new DissolutionTransaction(contact.owner(),models.catalog(),models.solubility(),64);var receipt=transaction.commit();if(!receipt.committed())port.diagnostic(level,contact.solid(),receipt.code(),receipt.detail());}
   catch(IllegalArgumentException invalidModel){port.diagnostic(level,contact.solid(),"UNMODELED",invalidModel.getMessage());}
  }
 }
}

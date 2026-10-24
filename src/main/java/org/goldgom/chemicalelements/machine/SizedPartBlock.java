package org.goldgom.chemicalelements.machine;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
/** All occupied cells share an origin derived from immutable offsets; only the origin owns a drop. */
public final class SizedPartBlock extends Block {
 public static final IntegerProperty X=IntegerProperty.create("part_x",0,2),Y=IntegerProperty.create("part_y",0,5),Z=IntegerProperty.create("part_z",0,2);
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 private static final Set<String> REMOVING=new HashSet<>();
 private final String id;
 public SizedPartBlock(String id,Properties p){super(p);this.id=id;registerDefaultState(stateDefinition.any().setValue(X,0).setValue(Y,0).setValue(Z,0).setValue(FACING,Direction.NORTH));}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(X,Y,Z,FACING);}
 public boolean complete(Level level,BlockPos pos,BlockState state){
  var definition=PartDefinition.load(level,id);if(definition.isEmpty())return false;var anchor=origin(pos,state);
  for(var cell:definition.get().cells(anchor,state.getValue(FACING))){if(!level.hasChunkAt(cell))return false;var other=level.getBlockState(cell);if(!other.is(this)||!origin(cell,other).equals(anchor)||other.getValue(FACING)!=state.getValue(FACING))return false;}
  return true;
 }
 public BlockPos origin(BlockPos pos,BlockState s){var d=PartDefinition.rotate(s.getValue(X),s.getValue(Y),s.getValue(Z),s.getValue(FACING));return pos.offset(-d.getX(),-d.getY(),-d.getZ());}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){
  var level=context.getLevel();if(level.isClientSide)return defaultBlockState().setValue(FACING,context.getHorizontalDirection());
  var definition=PartDefinition.load(level,id);if(definition.isEmpty())return null;
  for(var pos:definition.get().cells(context.getClickedPos(),context.getHorizontalDirection()))if((pos.getX()>>4)!=(context.getClickedPos().getX()>>4)||(pos.getZ()>>4)!=(context.getClickedPos().getZ()>>4)||!level.hasChunkAt(pos)||!level.isInWorldBounds(pos)||!level.getBlockState(pos).canBeReplaced(context)||!level.isUnobstructed(defaultBlockState(),pos,net.minecraft.world.phys.shapes.CollisionContext.empty()))return null;
  return defaultBlockState().setValue(FACING,context.getHorizontalDirection());
 }
 @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){
  super.setPlacedBy(level,pos,state,entity,stack);if(level.isClientSide)return;
  var definition=PartDefinition.load(level,id).orElseThrow();
  for(int x=0;x<definition.width();x++)for(int y=0;y<definition.height();y++)for(int z=0;z<definition.depth();z++){
   if(x==0&&y==0&&z==0)continue;
   level.setBlock(pos.offset(PartDefinition.rotate(x,y,z,state.getValue(FACING))),state.setValue(X,x).setValue(Y,y).setValue(Z,z),3);
  }
 }
 @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return List.of();}
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
  if(!level.isClientSide&&!state.is(next.getBlock())){
   var anchor=origin(pos,state);String key=level.dimension().location()+":"+anchor.asLong();
   boolean ownsDrop=pos.equals(anchor)||(level.hasChunkAt(anchor)&&level.getBlockState(anchor).is(this));
   if(REMOVING.add(key))try{
    // Fixed bounded scan, independent of changed/reloaded definition. Never load chunks.
    for(int x=0;x<3;x++)for(int y=0;y<6;y++)for(int z=0;z<3;z++){
     var cell=anchor.offset(PartDefinition.rotate(x,y,z,state.getValue(FACING)));if(!level.hasChunkAt(cell))continue;
     var other=level.getBlockState(cell);if(other.is(this)&&origin(cell,other).equals(anchor))level.removeBlock(cell,false);
    }
    if(ownsDrop)Block.popResource(level,anchor,new ItemStack(this));
   }finally{REMOVING.remove(key);}
  }
  super.onRemove(state,level,pos,next,moving);
 }
 @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos from,boolean moving){
  if(!level.isClientSide){var anchor=origin(pos,state);if(level.hasChunkAt(anchor)&&!level.getBlockState(anchor).is(this))level.destroyBlock(pos,false);}
  super.neighborChanged(state,level,pos,neighbor,from,moving);
 }
}

package org.goldgom.chemicalelements.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MachineBlock extends BaseEntityBlock {
 public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
 public static final net.minecraft.world.level.block.state.properties.IntegerProperty VISUAL=net.minecraft.world.level.block.state.properties.IntegerProperty.create("visual",0,3);
 private final MachineKind kind;
 public MachineBlock(MachineKind kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(VISUAL,0));}
 @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,VISUAL);}
 @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection());}
 public static int quarterTurns(BlockState state){return switch(state.getValue(FACING)){case EAST->1;case SOUTH->2;case WEST->3;default->0;};}
 public MachineKind kind(){return kind;}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new MachineBlock(kind,p));}
 @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MachineBlockEntity(pos,state);}
 @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&level.getServer()!=null&&level.getBlockEntity(pos) instanceof MachineBlockEntity machine){machine.configureGrid(MachineGridSettings.side(level.getServer(),kind));PackedMachine.unpack(stack,machine);}}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
  if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof MachineBlockEntity machine)server.openMenu(machine,buffer->{buffer.writeBlockPos(pos);buffer.writeVarInt(machine.side());});
  return InteractionResult.sidedSuccess(level.isClientSide);
 }
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,MachineBootstrap.CONTROLLER.get(),MachineBlockEntity::serverTick);}
 @Override protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){
  var entity=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
  if(entity instanceof MachineBlockEntity machine)return java.util.List.of(PackedMachine.pack(machine));
  return super.getDrops(state,builder);
 }
 @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
  if(!state.is(next.getBlock())&&!level.isClientSide&&level.getBlockEntity(pos) instanceof MachineBlockEntity machine){machine.cancel();StructureClaims.release(level,pos);machine.dropContentsOnce();}
  super.onRemove(state,level,pos,next,moving);
 }
}

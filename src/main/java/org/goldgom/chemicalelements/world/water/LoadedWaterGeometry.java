package org.goldgom.chemicalelements.world.water;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.*;
import org.goldgom.chemicalelements.fluid.waterbody.WaterGeometry;

/** Real read-only adapter: getChunkNow only, including collision-shape neighbor lookups.
 * Short-lived query object, not an index. Partial collision geometry is explicitly UNMODELED. */
public final class LoadedWaterGeometry implements WaterGeometry.LoadedGeometry {
    private final ServerLevel level;
    private final Map<WaterGeometry.Pos,String> receivers;
    private final Map<Long,net.minecraft.world.level.chunk.LevelChunk> observed=new HashMap<>();
    private final Map<BlockPos,BlockState> states=new HashMap<>();
    private boolean unavailable;
    public LoadedWaterGeometry(ServerLevel level,Map<WaterGeometry.Pos,String> receivingOwners) {
        this.level=Objects.requireNonNull(level);this.receivers=Map.copyOf(receivingOwners);
    }
    private net.minecraft.world.level.chunk.LevelChunk chunk(BlockPos pos) {
        var c=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(c==null){unavailable=true;return null;}
        var old=observed.putIfAbsent(c.getPos().toLong(),c);if(old!=null&&old!=c){unavailable=true;return null;}
        return c;
    }
    private final BlockGetter view=new BlockGetter() {
        @Override public BlockState getBlockState(BlockPos pos) {
            if(pos.getY()<getMinBuildHeight()||pos.getY()>=getMinBuildHeight()+getHeight()){unavailable=true;return Blocks.AIR.defaultBlockState();}
            var c=chunk(pos);if(c==null)return Blocks.AIR.defaultBlockState();
            if(states.size()>=16384&&!states.containsKey(pos)){unavailable=true;return Blocks.AIR.defaultBlockState();}
            var state=c.getBlockState(pos);var old=states.putIfAbsent(pos.immutable(),state);
            if(old!=null&&!old.equals(state))unavailable=true;
            return state;
        }
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public BlockEntity getBlockEntity(BlockPos pos){var c=chunk(pos);return c==null?null:c.getBlockEntity(pos);}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
    };
    @Override public WaterGeometry.Cell read(WaterGeometry.Pos p) {
        if(unavailable)return WaterGeometry.Cell.of(WaterGeometry.Kind.UNLOADED);
        var pos=new BlockPos(p.x(),p.y(),p.z());var state=view.getBlockState(pos);
        if(unavailable)return WaterGeometry.Cell.of(WaterGeometry.Kind.UNLOADED);
        var shape=state.getCollisionShape(view,pos,CollisionContext.empty());
        if(unavailable)return WaterGeometry.Cell.of(WaterGeometry.Kind.UNLOADED);
        if(!shape.isEmpty())return WaterGeometry.Cell.of(Shapes.joinIsNotEmpty(shape,Shapes.block(),BooleanOp.NOT_SAME)?WaterGeometry.Kind.UNMODELED:WaterGeometry.Kind.SOLID);
        // Only empty air/water cavities modeled; plants and arbitrary fluid-containing blocks require explicit models.
        if(!state.isAir()&&!state.is(Blocks.WATER))return WaterGeometry.Cell.of(WaterGeometry.Kind.UNMODELED);
        if(receivers.containsKey(p))return new WaterGeometry.Cell(WaterGeometry.Kind.RECEIVER,receivers.get(p));
        return WaterGeometry.Cell.of(WaterGeometry.Kind.SPACE);
    }
    /** Call immediately before Owner commit; B must still include all revisions/state guards in atomic CAS. */
    public boolean stillCurrent() {
        if(unavailable)return false;
        for(var c:observed.values())if(level.getChunkSource().getChunkNow(c.getPos().x,c.getPos().z)!=c)return false;
        for(var e:states.entrySet()){var c=level.getChunkSource().getChunkNow(e.getKey().getX()>>4,e.getKey().getZ()>>4);if(c==null||!c.getBlockState(e.getKey()).equals(e.getValue()))return false;}
        return true;
    }
}

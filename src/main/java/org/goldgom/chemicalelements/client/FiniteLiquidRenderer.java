package org.goldgom.chemicalelements.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import org.goldgom.chemicalelements.fluid.*;

/** Continuous shared surface vertices from ten volume bands; no internal wet-edge walls. */
public final class FiniteLiquidRenderer {
    private FiniteLiquidRenderer(){}
    public static void render(BlockAndTintGetter world,BlockPos pos,VertexConsumer output,BlockState state,FluidState fluid){
        var sprites=net.neoforged.neoforge.client.textures.FluidSpriteCache.getFluidSprites(world,pos,fluid);
        int tint=net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor(fluid,world,pos);
        float alpha=(tint>>>24&255)/255F,r=(tint>>>16&255)/255F,g=(tint>>>8&255)/255F,b=(tint&255)/255F;
        int[] grid=new int[9];for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)grid[(dz+1)*3+dx+1]=neighbor(world,pos.offset(dx,0,dz),fluid);
        var mesh=LiquidSurfaceGeometry.smoothMesh(grid,neighbor(world,pos.above(),fluid),neighbor(world,pos.below(),fluid));
        int light=light(world,pos);float x=pos.getX()&15,y=pos.getY()&15,z=pos.getZ()&15;
        for(var quad:mesh){
            var direction=Direction.valueOf(quad.face().name());var otherPos=pos.relative(direction);var other=world.getBlockState(otherPos);
            // Solid faces only occlude where they actually touch this liquid column.
            boolean touching=direction!=Direction.UP||quad.top()==1;
            if(touching&&other.canOcclude()&&Shapes.blockOccudes(Shapes.box(0,0,0,1,Math.max(0.001F,quad.top()),1),other.getOcclusionShape(world,otherPos),direction))continue;
            boolean horizontal=direction.getAxis().isHorizontal();var sprite=sprites[horizontal?1:0];float shade=world.getShade(direction,true);
            // Both windings make the exposed surface visible from inside liquid as well.
            for(int winding=0;winding<2;winding++)for(int index=0;index<4;index++){
                var point=quad.points().get(winding==0?index:3-index);
                float u=horizontal?(direction.getAxis()==Direction.Axis.Z?point.x():point.z())*0.5F:point.x();
                float v=horizontal?(1-point.y())*0.5F:point.z();
                float sign=winding==0?1:-1;
                output.addVertex(x+point.x(),y+point.y(),z+point.z()).setColor(r*shade,g*shade,b*shade,alpha)
                        .setUv(sprite.getU(u),sprite.getV(v)).setLight(light)
                        .setNormal(direction.getStepX()*sign,direction.getStepY()*sign,direction.getStepZ()*sign);
            }
        }
    }
    private static int neighbor(BlockAndTintGetter world,BlockPos pos,FluidState fluid){
        var state=world.getBlockState(pos);var other=state.getFluidState();if(!fluid.getType().isSame(other.getType()))return 0;
        if(FiniteLiquidShape.managed(state))return FiniteLiquidShape.step(state);
        return other.isSource()?10:Math.max(1,Math.min(10,(other.getAmount()*10+7)/8));
    }
    private static int light(BlockAndTintGetter world,BlockPos pos){
        int a=LevelRenderer.getLightColor(world,pos),b=LevelRenderer.getLightColor(world,pos.above());
        return Math.max(a&0xffff,b&0xffff)|(Math.max(a>>>16&0xffff,b>>>16&0xffff)<<16);
    }
}

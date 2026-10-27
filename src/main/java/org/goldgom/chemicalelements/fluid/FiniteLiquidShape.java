package org.goldgom.chemicalelements.fluid;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Synchronized in vanilla block-state packets; zero means an old/unprojected vanilla cell. */
public final class FiniteLiquidShape {
    public static final IntegerProperty VOLUME_STEP=IntegerProperty.create("finite_volume",0,10);
    private FiniteLiquidShape(){}
    public static boolean managed(BlockState state){return state.hasProperty(VOLUME_STEP);}
    public static int step(BlockState state){
        int stored=state.getValue(VOLUME_STEP);if(stored>0)return stored;
        var fluid=state.getFluidState();return fluid.isSource()?10:Math.max(1,Math.min(10,(fluid.getAmount()*10+7)/8));
    }
    public static float height(BlockState state){return step(state)/10.0F;}
}

package org.goldgom.chemicalelements.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;

/** Registry identity shared by source and flowing variants, including registered mod fluids. */
public final class FiniteFluids {
    public static final String WATER="minecraft:water";
    private FiniteFluids() {}
    public static boolean exposed(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                && state.getFluidState().getType() instanceof FlowingFluid;
    }
    public static String id(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid instanceof FlowingFluid flowing?flowing.getSource():fluid).toString();
    }
    public static FlowingFluid resolve(String id) {
        var fluid=BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
        if(!(fluid instanceof FlowingFluid flowing))throw new IllegalArgumentException("Unsupported finite flowing fluid: "+id);
        return flowing;
    }
}

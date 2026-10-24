package org.goldgom.chemicalelements.world.water;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.goldgom.chemicalelements.fluid.waterbody.*;
import org.goldgom.chemicalelements.world.*;

/** Read/plan only. No Owner binding, inventory, journal, projection or debit. */
public final class NaturalWaterAdmission {
    private NaturalWaterAdmission() {}
    public enum Status { MEASURED, UNLOADED, STALE, NOT_WATER, UNMODELED_WATERLOGGED }
    public record Observation(Status status,Optional<WaterVolume> volume) {}
    /** Vanilla exposed water uses amount/8 of 1m3 as an explicit game convention, not source=1m3
     * for every water-bearing block. Waterlogged/partial-solid blocks need a separate pore model. */
    public static Observation measure(ServerLevel level,LevelChunk expected,BlockPos position) {
        if(position.getX()>>4!=expected.getPos().x||position.getZ()>>4!=expected.getPos().z)return new Observation(Status.STALE,Optional.empty());
        var current=level.getChunkSource().getChunkNow(expected.getPos().x,expected.getPos().z);
        if(current==null)return new Observation(Status.UNLOADED,Optional.empty());
        if(current!=expected)return new Observation(Status.STALE,Optional.empty());
        var state=current.getBlockState(position);var fluid=state.getFluidState();
        if(!fluid.is(net.minecraft.tags.FluidTags.WATER))return new Observation(Status.NOT_WATER,Optional.empty());
        if(!state.is(Blocks.WATER))return new Observation(Status.UNMODELED_WATERLOGGED,Optional.empty());
        int amount=fluid.getAmount();if(amount<1||amount>8)throw new IllegalArgumentException("invalid exposed-water amount");
        return new Observation(Status.MEASURED,Optional.of(WaterVolume.ofSubquanta(WaterVolume.SUBQUANTA_PER_M3.multiply(BigInteger.valueOf(amount)).divide(BigInteger.valueOf(8)))));
    }
    public record Plan(WaterBody explicitInitialAdmission,WaterWetting.Plan wetting,NaturalWaterInitialization.Context provenance) {}
    /** provenNatural is supplied ONLY by B provenance; no inhabitedTime/source heuristics. Repeated admission
     * refuses before profile sampling. Owner must commit initial admission + shared body + pores + target/body
     * tombstones + cursor/seal + all cross-chunk versions in ONE existing journal transaction. */
    public static Plan plan(boolean provenNatural,boolean alreadyAdmitted,WaterVolume measured,BigInteger energy,
                            NaturalWaterInitialization.Context context,Collection<WaterWetting.Target> contacts) {
        if(!provenNatural)throw new IllegalArgumentException("natural provenance unavailable");
        if(alreadyAdmitted)throw new IllegalArgumentException("already admitted; preserve exhausted tombstone");
        var species=new NaturalWaterProfile().sample(context,NaturalWaterInitialization.seed(context),measured.legacyLitreQuanta());
        var initial=new WaterBody(measured,species,energy);
        return new Plan(initial,WaterWetting.initialize(initial,false,contacts),context);
    }
}

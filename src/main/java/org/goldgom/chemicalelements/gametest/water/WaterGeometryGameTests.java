package org.goldgom.chemicalelements.gametest.water;

import java.math.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.fluid.*;
import org.goldgom.chemicalelements.fluid.waterbody.*;
import org.goldgom.chemicalelements.world.water.*;

/** Real ServerLevel entry tests; no fake Owner, journal, or production enable. Not executed in r24. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class WaterGeometryGameTests {
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void loadedPitAndAdmission(GameTestHelper helper) {
        var level=helper.getLevel();var p=helper.absolutePos(new BlockPos(2,2,2));
        for(var d:net.minecraft.core.Direction.values())level.setBlock(p.relative(d),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);
        level.setBlock(p,Blocks.WATER.defaultBlockState(),2);
        var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
        var measured=NaturalWaterAdmission.measure(level,chunk,p);
        helper.assertTrue(measured.status()==NaturalWaterAdmission.Status.MEASURED&&measured.volume().orElseThrow().equals(WaterVolume.BUCKET),"exposed source 1 m3 game convention");
        var adapter=new LoadedWaterGeometry(level,Map.of());
        var water=new WaterBody(WaterVolume.BUCKET,Map.of("salt",BigInteger.TEN),BigInteger.ONE);
        var plan=WaterGeometry.plan(water,new WaterGeometry.Pos(p.getX(),p.getY(),p.getZ()),p.getY()+1,32,adapter);
        helper.assertTrue(plan.status()==WaterGeometry.Status.RETAINED&&adapter.stillCurrent(),"real full collision shell retains water");
        level.setBlock(p.below(),Blocks.AIR.defaultBlockState(),2);
        helper.assertTrue(!adapter.stillCurrent(),"geometry changes invalidate transaction read set");
        var receiver=new WaterGeometry.Pos(p.getX(),p.getY()-1,p.getZ());
        var open=WaterGeometry.plan(water,new WaterGeometry.Pos(p.getX(),p.getY(),p.getZ()),p.getY()+1,32,new LoadedWaterGeometry(level,Map.of(receiver,"known-lower-owner")));
        helper.assertTrue(open.status()==WaterGeometry.Status.OUTFLOW&&open.outgoing().equals(water),"real opening requires explicit receiver and conserves outgoing snapshot");
        helper.succeed();
    }
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void actualSorptionDefaultAndLaterContact(GameTestHelper helper) {
        var m=SorptionLibrary.INSTANCE.material(net.minecraft.resources.ResourceLocation.parse("minecraft:dirt")).orElseThrow();
        var t=new WaterWetting.Target("soil-position",1,true,true,false,WaterBody.empty(),m);
        var water=new WaterBody(WaterVolume.BUCKET,Map.of("salt",BigInteger.TEN.pow(18)),BigInteger.TEN);
        var initial=WaterWetting.initialize(water,false,List.of(t,t));
        helper.assertTrue(initial.status()==WaterWetting.Status.READY&&initial.pores().size()==1,"real sorption data deduplicates target");
        helper.assertTrue(initial.pores().get(t.identity()).volume().legacyLitreQuanta().equals(m.poreCapacity()),"load default saturated");
        helper.assertTrue(initial.remaining().plus(initial.pores().get(t.identity())).equals(water),"initial allocation conserved");
        helper.assertTrue(WaterWetting.progressive(water,List.of(t)).pores().get(t.identity()).volume().compareTo(initial.pores().get(t.identity()).volume())<0,"later contact gradual");
        helper.succeed();
    }
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void unloadedReadsDoNotGenerate(GameTestHelper helper) {
        var level=helper.getLevel();var base=helper.absolutePos(BlockPos.ZERO);
        int x=(base.getX()>>4)+4096,z=(base.getZ()>>4)+4096;
        for(int i=0;i<32;i++,x+=17,z+=19) {
            if(level.getChunkSource().getChunkNow(x,z)!=null)continue;
            var adapter=new LoadedWaterGeometry(level,Map.of());
            var c=adapter.read(new WaterGeometry.Pos(x*16,level.getSeaLevel(),z*16));
            helper.assertTrue(c.kind()==WaterGeometry.Kind.UNLOADED&&level.getChunkSource().getChunkNow(x,z)==null,"no strong neighbor load");
            helper.succeed();return;
        }
        helper.fail("no unloaded candidate found");
    }
}

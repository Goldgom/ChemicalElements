package org.goldgom.chemicalelements.gametest.water;

import java.math.BigInteger;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.instance.WaterStorePort;
import org.goldgom.chemicalelements.persistence.*;
import org.goldgom.chemicalelements.fluid.*;
import org.goldgom.chemicalelements.world.FiniteWaterRuntime;
import java.util.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class FiniteWaterGameTests {
    @GameTest(template="finite_water_lab",timeoutTicks=400)
    public static void boundarySupplyWakesSleepingRegionAndSpreadsAtItsOriginalBase(GameTestHelper helper){
        var level=helper.getLevel();int height=org.goldgom.chemicalelements.Config.fluidUpdateRadius+1;
        var base=helper.absolutePos(new BlockPos(2,110,2));var outlet=base.east();
        var open=new HashSet<BlockPos>();for(int y=0;y<=height;y++)open.add(base.above(y));open.add(outlet);
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(var p:open)for(var d:Direction.values())if(!open.contains(p.relative(d)))seal(helper,p.relative(d));
            for(var p:open){clearFixtureWater(helper,p);level.setBlock(p,p.equals(base.above(height))?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);}
        }finally{WaterStorePort.RENDERING.set(previous);}
        FiniteWaterRuntime.markUrgent(level,base);
        helper.runAfterDelay((height+12)*org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks,()->{
            helper.assertTrue(volume(helper,base).equals(WaterStorePort.BLOCK_CAPACITY.divide(BigInteger.TWO)),"original sleeping region resumes after external supply arrives");
            helper.assertTrue(volume(helper,outlet).equals(WaterStorePort.BLOCK_CAPACITY.divide(BigInteger.TWO)),"replenished water can continue sideways inside the original radius");
            for(int y=1;y<=height;y++)helper.assertTrue(volume(helper,base.above(y)).signum()==0,"no stranded water in the supply shaft");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=150)
    public static void diggingWaterSupportProgressesDespiteDiscoveryBacklog(GameTestHelper helper){
        var level=helper.getLevel();var source=helper.absolutePos(new BlockPos(2,95,2));
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(int y=0;y<=3;y++){
                var p=source.below(y);for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))seal(helper,p.relative(d));
                clearFixtureWater(helper,p);level.setBlock(p,y==0?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            }
            seal(helper,source.below(4));seal(helper,source.above());seal(helper,source.below());
        }finally{WaterStorePort.RENDERING.set(previous);}
        int before=FiniteWaterRuntime.queuedPositions(level);
        for(int x=-24;x<=24;x+=8)for(int z=-24;z<=24;z+=8)for(int y=180;y<=270;y+=30)FiniteWaterRuntime.mark(level,source.offset(x,y-95,z));
        helper.assertTrue(FiniteWaterRuntime.queuedPositions(level)-before>=40,"fixture has a real background discovery backlog");
        // Remove a real support block through the level event, not an explicit runtime mark.
        clearFixtureWater(helper,source.below());level.setBlock(source.below(),Blocks.AIR.defaultBlockState(),3);
        helper.runAfterDelay(4*org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks+8,()->{
            helper.assertTrue(volume(helper,source.below(3)).equals(WaterStorePort.BLOCK_CAPACITY),"all falling stages retain priority over discovery");
            for(int y=0;y<3;y++)helper.assertTrue(volume(helper,source.below(y)).signum()==0,"dug-out shaft has no suspended water");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=200)
    public static void finiteCoastalPoolReplenishesRepeatedUnderwaterBucketHoles(GameTestHelper helper){
        var level=helper.getLevel();var base=helper.absolutePos(new BlockPos(2,55,2));var open=new HashSet<BlockPos>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++)open.add(base.offset(x,y,z));
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(var p:open)for(var d:Direction.values())if(!open.contains(p.relative(d)))seal(helper,p.relative(d));
            for(var p:open){clearFixtureWater(helper,p);level.setBlock(p,Blocks.WATER.defaultBlockState(),2);}
        }finally{WaterStorePort.RENDERING.set(previous);}
        helper.assertTrue(WaterStorePort.INSTANCE.prepare(level,List.of(base)),"natural coastal pool admitted once");
        helper.assertTrue(!WaterStorePort.needsUpdate(level,base.above()),"full stable ocean interior does not create discovery work");
        coastalBucketCycle(helper,base,open,0);
    }
    private static void coastalBucketCycle(GameTestHelper helper,BlockPos target,Set<BlockPos> pool,int collected){
        var bucket=WaterStorePort.pickup(helper.getLevel(),target);
        helper.assertTrue(bucket.is(Items.WATER_BUCKET),"underwater cell is full before collection "+collected);
        helper.runAfterDelay(3*org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks+4,()->{
            var total=pool.stream().map(p->volume(helper,p)).reduce(BigInteger.ZERO,BigInteger::add);
            helper.assertTrue(total.equals(WaterStorePort.BLOCK_CAPACITY.multiply(BigInteger.valueOf(pool.size()-collected-1))),"refill debits the finite pool exactly, without regenerating bucket water");
            helper.assertTrue(volume(helper,target).equals(WaterStorePort.BLOCK_CAPACITY),"surrounding sea refills the underwater hole");
            if(collected<2)coastalBucketCycle(helper,target,pool,collected+1);else helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=240)
    public static void clippedColumnRefillsFromNaturalWaterAboveTheRegion(GameTestHelper helper){
        clippedColumnRefills(helper,false);
    }
    @GameTest(template="finite_water_lab",timeoutTicks=240)
    public static void emptyBoundaryCellAcceptsNaturalLavaFromAbove(GameTestHelper helper){
        clippedColumnRefills(helper,true);
    }
    private static void clippedColumnRefills(GameTestHelper helper,boolean lava){
        var level=helper.getLevel();int radius=org.goldgom.chemicalelements.Config.fluidUpdateRadius;
        int depth=radius+3;var base=helper.absolutePos(new BlockPos(2,65,2));
        int gap=lava?radius:0;var fluidBlock=lava?Blocks.LAVA:Blocks.WATER;
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(int y=0;y<=depth;y++){
                var p=base.above(y);
                for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))seal(helper,p.relative(d));
                clearFixtureWater(helper,p);level.setBlock(p,y==gap?Blocks.AIR.defaultBlockState():fluidBlock.defaultBlockState(),2);
            }
            seal(helper,base.below());seal(helper,base.above(depth+1));
        }finally{WaterStorePort.RENDERING.set(previous);}
        helper.assertTrue(WaterStorePort.INSTANCE.prepare(level,List.of(base)),"admit the bounded part of the natural column");
        var snapshot=WaterStorePort.INSTANCE.neighborhood(level,base);
        var cap=base.above(radius);var key=new WaterTransport.Position(cap.getX(),cap.getY(),cap.getZ());
        if(!lava)helper.assertTrue(snapshot.get(key).continuesAbove(),"snapshot cap is not the physical free surface");
        helper.assertTrue(!snapshot.containsKey(new WaterTransport.Position(cap.getX(),cap.getY()+1,cap.getZ())),"upper supply stays outside the original region");
        FiniteWaterRuntime.markUrgent(level,base);
        helper.runAfterDelay(15*org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks,()->{
            BigInteger total=BigInteger.ZERO;
            for(int y=0;y<=depth;y++){
                var amount=volume(helper,base.above(y));total=total.add(amount);
                helper.assertTrue(amount.equals(y<depth?WaterStorePort.BLOCK_CAPACITY:BigInteger.ZERO),"vacancy reaches the real top, with no suspended gap at height "+y+": "+amount);
                if(y<depth)helper.assertTrue(level.getBlockState(base.above(y)).is(fluidBlock),"boundary replenishment preserves fluid identity");
            }
            helper.assertTrue(total.equals(WaterStorePort.BLOCK_CAPACITY.multiply(BigInteger.valueOf(depth))),"natural upper supply is admitted once and remains finite");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=240)
    public static void ledgeDrainsIntoLowerBasinAcrossVisibleStages(GameTestHelper helper){
        var level=helper.getLevel();var source=helper.absolutePos(new BlockPos(2,85,2));
        var lip=source.east();var basin=lip.below();var open=Set.of(source,lip,basin);
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(var p:open)for(var d:Direction.values())if(!open.contains(p.relative(d)))seal(helper,p.relative(d));
            for(var p:open){clearFixtureWater(helper,p);level.setBlock(p,p.equals(source)?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);}
        }finally{WaterStorePort.RENDERING.set(previous);}
        helper.assertTrue(WaterStorePort.INSTANCE.prepare(level,List.of(source)),"admit ledge fixture");
        var initial=new FiniteVolume.Parcel(WaterStorePort.BLOCK_CAPACITY,Map.of("test:salt",BigInteger.valueOf(137)),BigInteger.valueOf(591));
        replace(helper,source,initial);FiniteWaterRuntime.markUrgent(level,source);
        helper.runAfterDelay(16*org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks,()->{
            var total=FiniteVolume.Parcel.empty();
            for(var p:open){var cell=WorldChemistryStore.water(level,p).orElseThrow();total=total.plus(new FiniteVolume.Parcel(cell.volume(),cell.species(),cell.energy()));}
            helper.assertTrue(total.equals(initial),"all visible stages preserve water, solute and heat");
            helper.assertTrue(volume(helper,lip).signum()==0&&level.getBlockState(lip).isAir(),"unsupported lip drains completely and its mesh is removed");
            helper.assertTrue(volume(helper,source).compareTo(BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)))<0,"ledge drains until the confirmed lateral deadband stops it");
            helper.assertTrue(volume(helper,basin).compareTo(BigInteger.valueOf(950).multiply(BigInteger.TEN.pow(18)))>0,"lower basin receives the drained water");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=350)
    public static void gravityCrossesRegionBoundaryAndReplaceableGrassDoesNotSupportWater(GameTestHelper helper){
        var level=helper.getLevel();int depth=org.goldgom.chemicalelements.Config.fluidUpdateRadius+3;
        var source=helper.absolutePos(new BlockPos(2,100,2));
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(int i=0;i<=depth;i++){
                var p=source.below(i);for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))seal(helper,p.relative(d));
                clearFixtureWater(helper,p);level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            }
            seal(helper,source.below(depth+1));seal(helper,source.above());
            level.setBlock(source.below(),Blocks.SHORT_GRASS.defaultBlockState(),2);
        }finally{WaterStorePort.RENDERING.set(previous);}
        helper.assertTrue(WaterStorePort.placeBucket(level,source,new ItemStack(Items.WATER_BUCKET)),"finite bucket above a grass-filled fall path");
        helper.runAfterDelay((depth+5)*org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks,()->{
            helper.assertTrue(volume(helper,source.below(depth)).equals(WaterStorePort.BLOCK_CAPACITY),"gravity must continue beyond the lateral calculation boundary to real support");
            for(int i=0;i<depth;i++)helper.assertTrue(volume(helper,source.below(i)).signum()==0,"no suspended residual at height "+i);
            helper.assertTrue(!level.getBlockState(source.below()).is(Blocks.SHORT_GRASS),"replaceable non-colliding grass is displaced, not treated as support");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=160)
    public static void oneTriggerCannotPropagatePastConfiguredRadius(GameTestHelper helper){
        var level=helper.getLevel();int radius=org.goldgom.chemicalelements.Config.fluidUpdateRadius;
        var pos=helper.absolutePos(new BlockPos(2,40,2));
        boolean previous=WaterStorePort.RENDERING.get();WaterStorePort.RENDERING.set(true);
        try{
            for(int x=0;x<=radius+3;x++){
                var p=pos.offset(x,0,0);for(var d:Direction.values())if(d!=Direction.EAST&&d!=Direction.WEST)seal(helper,p.relative(d));
                clearFixtureWater(helper,p);level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            }
            seal(helper,pos.west());seal(helper,pos.offset(radius+4,0,0));
        }finally{WaterStorePort.RENDERING.set(previous);}
        helper.assertTrue(WaterStorePort.placeBucket(level,pos,new ItemStack(Items.WATER_BUCKET)),"one explicit propagation trigger");
        helper.runAfterDelay(100,()->{
            helper.assertTrue(volume(helper,pos.offset(radius+1,0,0)).signum()==0,"changed cells and vanilla scheduled ticks cannot reset the original radius");
            BigInteger total=BigInteger.ZERO;for(int x=0;x<=radius+3;x++)total=total.add(volume(helper,pos.offset(x,0,0)));
            helper.assertTrue(total.equals(WaterStorePort.BLOCK_CAPACITY),"bounded region preserves the bucket volume");helper.succeed();
        });
    }
    /** Pre-wet test walls so each fixture measures only its explicitly supplied free water. */
    private static void seal(GameTestHelper helper,BlockPos pos){
        var level=helper.getLevel();level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        var material=SorptionLibrary.INSTANCE.materialForBlock(net.minecraft.resources.ResourceLocation.parse("minecraft:stone"));
        var current=WorldChemistryStore.water(level,pos);long revision=current.map(ChunkChemistry.WaterCell::revision).orElse(-1L);
        var cell=new ChunkChemistry.WaterCell(material.poreCapacity(),new TreeMap<>(),BigInteger.ZERO,material.poreCapacity(),"saturated_test_wall",revision+1);
        helper.assertTrue(WorldChemistryStore.commitWater(level,UUID.randomUUID(),List.of(new WorldChemistryStore.WaterChange(pos,revision,cell))),"saturated wall fixture");
    }
    private static void clearFixtureWater(GameTestHelper helper,BlockPos pos){
        var current=WorldChemistryStore.water(helper.getLevel(),pos);if(current.isEmpty())return;
        var cell=new ChunkChemistry.WaterCell(BigInteger.ZERO,new TreeMap<>(),BigInteger.ZERO,WaterStorePort.BLOCK_CAPACITY,"dry_test_fixture",current.get().revision()+1);
        helper.assertTrue(WorldChemistryStore.commitWater(helper.getLevel(),UUID.randomUUID(),List.of(new WorldChemistryStore.WaterChange(pos,current.get().revision(),cell))),"dry receiving fixture");
    }
    private static BlockPos enclosedSource(GameTestHelper helper) {
        var pos=helper.absolutePos(new BlockPos(2,2,2));var level=helper.getLevel();
        for(var direction:Direction.values())seal(helper,pos.relative(direction));
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        helper.assertTrue(WaterStorePort.placeBucket(level,pos,new ItemStack(Items.WATER_BUCKET)),"explicit bucket enters finite store");
        return pos;
    }
    private static BigInteger volume(GameTestHelper helper,BlockPos pos) {
        return WorldChemistryStore.water(helper.getLevel(),pos).map(ChunkChemistry.WaterCell::volume).orElse(BigInteger.ZERO);
    }
    private static void replace(GameTestHelper helper,BlockPos pos,FiniteVolume.Parcel parcel){
        var cell=WorldChemistryStore.water(helper.getLevel(),pos).orElseThrow();var key=new WaterTransport.Position(pos.getX(),pos.getY(),pos.getZ());
        helper.assertTrue(WaterStorePort.INSTANCE.commit(helper.getLevel(),UUID.randomUUID(),new WaterTransport.Plan(Map.of(key,cell.revision()),Map.of(key,parcel))),"exact finite fixture");
        WaterStorePort.INSTANCE.render(helper.getLevel(),key,WaterLevelProjection.height(parcel.volume(),WaterStorePort.BLOCK_CAPACITY));
    }
    @GameTest(template="finite_water_lab")
    public static void tenBandsControlWorldGeometryRaycastAndSourceIdentity(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();
        for(int litres:new int[]{1,100,101,200,250,350,400,500,600,700,800,900,901,999,1000}){
            replace(helper,pos,new FiniteVolume.Parcel(BigInteger.valueOf(litres).multiply(BigInteger.TEN.pow(18)),Map.of(),BigInteger.ZERO));
            var state=level.getBlockState(pos);int band=(litres+99)/100;
            helper.assertTrue(state.getValue(FiniteLiquidShape.VOLUME_STEP)==band,"block-state packet carries the actual ten-band volume");
            helper.assertTrue(Math.abs(state.getFluidState().getHeight(level,pos)-band/10F)<0.00001,"fluid height agrees with ten-band geometry");
            helper.assertTrue(Math.abs(state.getFluidState().getShape(level,pos).max(Direction.Axis.Y)-band/10.0)<0.00001,"bucket ray shape agrees with the surface and cannot reuse an eight-level cache entry");
            helper.assertTrue(state.getFluidState().isSource()==(litres==1000),"a visually full tenth band cannot mint a source from 901-999 L");
            var saved=net.minecraft.nbt.NbtUtils.writeBlockState(state);
            var restored=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved);
            helper.assertTrue(restored.equals(state),"ten-band state survives block serialization");
        }
        replace(helper,pos,new FiniteVolume.Parcel(BigInteger.valueOf(250).multiply(BigInteger.TEN.pow(18)),Map.of(),BigInteger.ZERO));
        level.setBlock(pos.above(),Blocks.WATER.defaultBlockState(),2);
        helper.assertTrue(Math.abs(level.getFluidState(pos).getHeight(level,pos)-0.3F)<0.00001,"water above cannot inflate a partial cell into a full column");helper.succeed();
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void emptyBucketUseCollectsFlowingWaterAndPartialBucketCanPour(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();
        var parcel=new FiniteVolume.Parcel(WaterStorePort.BLOCK_CAPACITY.divide(BigInteger.valueOf(4)),Map.of("test:solute",BigInteger.valueOf(137)),BigInteger.valueOf(591));
        replace(helper,pos,parcel);
        clearFixtureWater(helper,pos.north());level.setBlock(pos.north(),Blocks.AIR.defaultBlockState(),2);
        var player=helper.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
        player.setPos(pos.getX()+0.5,pos.getY()-1.5,pos.getZ()-1.2);player.setYRot(0);player.setXRot(0);
        var result=Items.BUCKET.use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(result.getItem() instanceof org.goldgom.chemicalelements.item.PartialFluidBucketItem,"actual empty-bucket raycast hits a flowing layer");
        helper.assertTrue(volume(helper,pos).signum()==0&&FiniteBucketContents.read(result).orElseThrow().parcel().equals(parcel),"empty bucket removes the entire cell with its solutes and heat");
        var restored=ItemStack.parse(level.registryAccess(),result.save(level.registryAccess())).orElseThrow();
        helper.assertTrue(FiniteBucketContents.read(restored).equals(FiniteBucketContents.read(result)),"partial bucket inventory survives item serialization");
        clearFixtureWater(helper,pos.above());level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,restored);
        player.setPos(pos.getX()+0.5,pos.getY()+1,pos.getZ()-0.6);player.setYRot(0);
        var eye=player.getEyePosition();player.setXRot((float)Math.toDegrees(Math.atan2(eye.y-pos.getY(),pos.getZ()+0.5-eye.z)));
        var emptied=restored.getItem().use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND).getObject();
        helper.assertTrue(emptied.is(Items.BUCKET),"partial bucket use pours its contents and returns an empty bucket");
        helper.assertTrue(volume(helper,pos).equals(parcel.volume()),"pouring 250 L cannot introduce a full 1000 L");helper.succeed();
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void partialBucketAccumulatesToVanillaBucketAndLeavesExcess(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();var capacity=WaterStorePort.BLOCK_CAPACITY;
        replace(helper,pos,new FiniteVolume.Parcel(capacity.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(5)),Map.of("test:salt",BigInteger.valueOf(137)),BigInteger.valueOf(591)));
        var first=WaterStorePort.pickup(level,pos);var held=FiniteBucketContents.read(first).orElseThrow();
        helper.assertTrue(first.getItem() instanceof org.goldgom.chemicalelements.item.PartialFluidBucketItem,"600 L is not a vanilla usable water bucket");
        helper.assertTrue(WaterStorePort.placeBucket(level,pos,new ItemStack(Items.WATER_BUCKET)),"refill collection target");
        var full=WaterStorePort.collect(level,pos,held);var contents=FiniteBucketContents.read(full).orElseThrow();
        helper.assertTrue(full.is(Items.WATER_BUCKET)&&contents.parcel().volume().equals(capacity),"exactly 1000 L promotes the container to a vanilla water bucket");
        helper.assertTrue(volume(helper,pos).equals(capacity.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(5))),"600 L excess stays in the target cell");
        helper.assertTrue(contents.parcel().species().equals(held.parcel().species())&&contents.parcel().energy().equals(held.parcel().energy()),"topping up preserves previously collected solutes and energy");
        helper.assertTrue(WaterStorePort.placeBucket(level,pos,full)==false,"enclosed partial cell rejects a bucket that cannot entirely fit");helper.succeed();
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void postEventIsOneGameTickAndVisibleStagesAdvanceOneFrontier(GameTestHelper helper){
        var level=helper.getLevel();int interval=FiniteWaterRuntime.FLOW_INTERVAL_TICKS;
        int delay=(int)((1-level.getGameTime()%interval+interval)%interval);if(delay==0)delay=interval;
        helper.runAfterDelay(delay,()->{
            var pos=enclosedSource(helper);var channel=new ArrayList<BlockPos>();channel.add(pos);
            for(int x=1;x<=3;x++){
                var p=pos.offset(x,0,0);for(var d:Direction.values())if(d!=Direction.EAST&&d!=Direction.WEST)seal(helper,p.relative(d));
                clearFixtureWater(helper,p);level.setBlock(p,Blocks.AIR.defaultBlockState(),2);channel.add(p);
            }
            seal(helper,pos.offset(4,0,0));
            long gameTime=level.getGameTime(),events=FiniteWaterRuntime.timing(level).events();
            helper.runAfterDelay(interval,()->{
                helper.assertTrue(volume(helper,pos.offset(1,0,0)).signum()>0&&volume(helper,pos.offset(3,0,0)).signum()==0,"one visible stage advances one frontier without skipping intermediate cells");
                helper.assertTrue(channel.stream().map(p->volume(helper,p)).reduce(BigInteger.ZERO,BigInteger::add).equals(WaterStorePort.BLOCK_CAPACITY),"accelerated propagation conserves all water");
                helper.assertTrue(FiniteWaterRuntime.timing(level).events()-events==level.getGameTime()-gameTime,"Post events count game ticks, not seconds or random fluid ticks");
                helper.runAfterDelay(interval*2+3,()->{helper.assertTrue(volume(helper,pos.offset(3,0,0)).signum()>0,"successive visible stages continue until the frontier can no longer advance");helper.succeed();});
            });
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void fallingWaterDebitsItsSource(GameTestHelper helper) {
        var pos=enclosedSource(helper);var level=helper.getLevel();var lower=pos.below();
        for(var direction:Direction.values())if(direction!=Direction.UP)seal(helper,lower.relative(direction));
        clearFixtureWater(helper,lower);
        level.setBlock(lower,Blocks.AIR.defaultBlockState(),2);
        helper.runAfterDelay(20,()->{
            helper.assertTrue(volume(helper,pos).add(volume(helper,lower)).equals(WaterStorePort.BLOCK_CAPACITY),"falling water cannot multiply the source volume");
            helper.assertTrue(volume(helper,pos).signum()==0&&volume(helper,lower).equals(WaterStorePort.BLOCK_CAPACITY),"source drains into the cell below: source="+volume(helper,pos)+", lower="+volume(helper,lower));
            helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=100)
    public static void soilAbsorbsAndBreakingItReleasesTheSameWater(GameTestHelper helper) {
        var pos=enclosedSource(helper);var soil=pos.east();var level=helper.getLevel();
        clearFixtureWater(helper,soil);
        level.setBlock(soil,Blocks.DIRT.defaultBlockState(),2);
        helper.runAfterDelay(35,()->{
            var absorbed=volume(helper,soil);
            helper.assertTrue(absorbed.signum()>0,"adjacent soil really absorbs in world ticks");
            helper.assertTrue(volume(helper,pos).add(absorbed).equals(WaterStorePort.BLOCK_CAPACITY),"free water plus pore water is conserved");
            helper.assertTrue(level.getBlockState(soil).is(Blocks.DIRT),"pore projection must preserve the solid block");
            var chunk=WorldChemistryStore.loadedChunk(level,soil).orElseThrow();
            var saved=ChunkChemistry.read(chunk.getData(ChemistryComponents.CHUNK.get()).write());
            helper.assertTrue(saved.water(soil.asLong()).orElseThrow().equals(WorldChemistryStore.water(level,soil).orElseThrow()),"saved pore inventory round trips exactly");
            var partial=WaterStorePort.pickup(level,pos);
            helper.assertTrue(partial.getItem() instanceof org.goldgom.chemicalelements.item.PartialFluidBucketItem,"non-full cell supplies a partial bucket, not a vanilla water bucket");
            helper.assertTrue(WaterStorePort.placeBucket(level,pos,partial),"partial bucket returns exactly its real volume");
            for(var d:Direction.values())if(!soil.relative(d).equals(pos))seal(helper,soil.relative(d));
            level.setBlock(soil,Blocks.AIR.defaultBlockState(),2);
            helper.runAfterDelay(10,()->{
                helper.assertTrue(volume(helper,pos).add(volume(helper,soil)).equals(WaterStorePort.BLOCK_CAPACITY),"breaking wet soil preserves released water");
                helper.assertTrue(level.getBlockState(soil).is(Blocks.WATER),"released pore water becomes visible");
                helper.succeed();
            });
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=60)
    public static void bucketCanRefillAnExhaustedCellWithoutDuplication(GameTestHelper helper) {
        var pos=enclosedSource(helper);var level=helper.getLevel();
        var cell=WorldChemistryStore.water(level,pos).orElseThrow();
        var position=new WaterTransport.Position(pos.getX(),pos.getY(),pos.getZ());
        var solution=new FiniteVolume.Parcel(WaterStorePort.BLOCK_CAPACITY,Map.of("test:solute",BigInteger.valueOf(137)),BigInteger.valueOf(591));
        helper.assertTrue(WaterStorePort.INSTANCE.commit(level,UUID.randomUUID(),new WaterTransport.Plan(Map.of(position,cell.revision()),Map.of(position,solution))),"admit saline test fixture");
        var bucket=((net.minecraft.world.level.block.LiquidBlock)Blocks.WATER).pickupBlock(null,level,pos,level.getBlockState(pos));
        helper.assertTrue(!bucket.isEmpty()&&volume(helper,pos).signum()==0,"pickup debits inventory");
        helper.assertTrue(WaterStorePort.pickup(level,pos).isEmpty(),"empty inventory cannot be picked up again");
        helper.assertTrue(((BucketItem)Items.WATER_BUCKET).emptyContents(null,level,pos,null,bucket),"explicit finite refill is allowed despite exhaustion marker");
        helper.assertTrue(volume(helper,pos).equals(WaterStorePort.BLOCK_CAPACITY),"refill restores exactly the bucket volume");
        var restored=WorldChemistryStore.water(level,pos).orElseThrow();
        helper.assertTrue(restored.species().equals(solution.species())&&restored.energy().equals(solution.energy()),"real bucket entry points preserve solutes and energy");
        helper.assertTrue(!WaterStorePort.placeBucket(level,pos,bucket),"full cell cannot accept and discard a second bucket");
        helper.succeed();
    }
    @GameTest(template="finite_water_lab",timeoutTicks=100)
    public static void horizontalSpreadUpdatesEveryTickAndReachesFourSides(GameTestHelper helper){
        int interval=FiniteWaterRuntime.FLOW_INTERVAL_TICKS;
        long now=helper.getLevel().getGameTime();int delay=(int)((1-now%interval+interval)%interval);if(delay==0)delay=interval;
        helper.runAfterDelay(delay,()->{
            var pos=enclosedSource(helper);var level=helper.getLevel();var sides=List.of(pos.north(),pos.south(),pos.east(),pos.west());
            for(var side:sides){for(var d:Direction.values())if(!side.relative(d).equals(pos))seal(helper,side.relative(d));clearFixtureWater(helper,side);level.setBlock(side,Blocks.AIR.defaultBlockState(),2);}
            helper.assertTrue(volume(helper,pos).equals(WaterStorePort.BLOCK_CAPACITY)&&sides.stream().allMatch(p->volume(helper,p).signum()==0),"no horizontal settlement immediately on placement");
            helper.runAfterDelay(FiniteWaterRuntime.FLOW_INTERVAL_TICKS,()->{
                helper.assertTrue(sides.stream().allMatch(p->volume(helper,p).signum()>0),"one scheduled step spreads to all four sides");
                helper.assertTrue(sides.stream().allMatch(p->volume(helper,p).equals(volume(helper,pos))),"symmetric dry floor shares water evenly with the source, without an inverted center");
                var total=sides.stream().map(p->volume(helper,p)).reduce(volume(helper,pos),BigInteger::add);
                helper.assertTrue(total.equals(WaterStorePort.BLOCK_CAPACITY),"one spread step conserves the entire bucket");helper.succeed();
            });
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void fullBucketOverflowsWithoutDeletingEitherBucket(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();var upper=pos.above();
        for(var d:Direction.values())if(d!=Direction.DOWN)seal(helper,upper.relative(d));
        clearFixtureWater(helper,upper);level.setBlock(upper,Blocks.AIR.defaultBlockState(),2);
        helper.assertTrue(((BucketItem)Items.WATER_BUCKET).emptyContents(null,level,pos,null,new ItemStack(Items.WATER_BUCKET)),"second bucket overflows through the real bucket entry point");
        helper.assertTrue(volume(helper,pos).equals(WaterStorePort.BLOCK_CAPACITY)&&volume(helper,upper).equals(WaterStorePort.BLOCK_CAPACITY),"two full buckets occupy two cells");
        helper.assertTrue(!WaterStorePort.placeBucket(level,pos,new ItemStack(Items.WATER_BUCKET)),"full enclosed vessel rejects an additional bucket atomically");
        helper.runAfterDelay(8,()->{
            helper.assertTrue(volume(helper,pos).add(volume(helper,upper)).equals(WaterStorePort.BLOCK_CAPACITY.multiply(BigInteger.TWO)),"overflow remains conserved after settlement");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void partialCellOverflowPreservesSolutesAndHeat(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();var upper=pos.above();
        for(var d:Direction.values())if(d!=Direction.DOWN)seal(helper,upper.relative(d));
        clearFixtureWater(helper,upper);level.setBlock(upper,Blocks.AIR.defaultBlockState(),2);
        var current=WorldChemistryStore.water(level,pos).orElseThrow();var key=new WaterTransport.Position(pos.getX(),pos.getY(),pos.getZ());
        var initial=new FiniteVolume.Parcel(WaterStorePort.BLOCK_CAPACITY.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(5)),Map.of("test:salt",BigInteger.valueOf(137)),BigInteger.valueOf(591));
        helper.assertTrue(WaterStorePort.INSTANCE.commit(level,UUID.randomUUID(),new WaterTransport.Plan(Map.of(key,current.revision()),Map.of(key,initial))),"partial fixture");
        helper.assertTrue(WaterStorePort.placeBucket(level,pos,new ItemStack(Items.WATER_BUCKET)),"partial cell accepts a bucket and spills its excess");
        var a=WorldChemistryStore.water(level,pos).orElseThrow();var b=WorldChemistryStore.water(level,upper).orElseThrow();
        helper.assertTrue(a.volume().add(b.volume()).equals(initial.volume().add(WaterStorePort.BLOCK_CAPACITY)),"all 1600 L retained");
        helper.assertTrue(a.species().getOrDefault("test:salt",BigInteger.ZERO).add(b.species().getOrDefault("test:salt",BigInteger.ZERO)).equals(BigInteger.valueOf(137))&&a.energy().add(b.energy()).equals(BigInteger.valueOf(591)),"overflow preserves solutes and heat");helper.succeed();
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void lavaFallsAndRoundTripsThroughLavaBucket(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();
        helper.assertTrue(!WaterStorePort.pickup(level,pos).isEmpty(),"remove water fixture");
        helper.assertTrue(WaterStorePort.placeBucket(level,pos,new ItemStack(Items.LAVA_BUCKET)),"lava bucket enters finite store");
        var lower=pos.below();for(var d:Direction.values())if(d!=Direction.UP)seal(helper,lower.relative(d));
        clearFixtureWater(helper,lower);level.setBlock(lower,Blocks.AIR.defaultBlockState(),2);
        helper.runAfterDelay(8,()->{
            var cell=WorldChemistryStore.water(level,lower).orElseThrow();
            helper.assertTrue(volume(helper,pos).signum()==0&&cell.volume().equals(WaterStorePort.BLOCK_CAPACITY),"lava falls without multiplying");
            helper.assertTrue(cell.fluidId().equals("minecraft:lava")&&level.getBlockState(lower).is(Blocks.LAVA),"lava identity and texture survive flow");
            var saved=ChunkChemistry.read(WorldChemistryStore.data(level,lower).orElseThrow().write()).water(lower.asLong()).orElseThrow();
            helper.assertTrue(saved.equals(cell),"fluid identity round trips through storage");
            var bucket=((net.minecraft.world.level.block.LiquidBlock)Blocks.LAVA).pickupBlock(null,level,lower,level.getBlockState(lower));
            helper.assertTrue(bucket.is(Items.LAVA_BUCKET)&&volume(helper,lower).signum()==0,"lava pickup debits inventory and returns the correct bucket");
            helper.assertTrue(((BucketItem)Items.LAVA_BUCKET).emptyContents(null,level,lower,null,bucket),"lava bucket refills exhausted cell");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void waterAndLavaContactCannotDeleteOrConvertInventories(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();var lava=pos.east();
        for(var d:Direction.values())if(d!=Direction.WEST)seal(helper,lava.relative(d));
        clearFixtureWater(helper,lava);level.setBlock(lava,Blocks.AIR.defaultBlockState(),2);
        helper.assertTrue(WaterStorePort.placeBucket(level,lava,new ItemStack(Items.LAVA_BUCKET)),"adjacent lava placement");
        helper.runAfterDelay(8,()->{
            helper.assertTrue(level.getBlockState(pos).is(Blocks.WATER)&&level.getBlockState(lava).is(Blocks.LAVA),"contact states: "+level.getBlockState(pos)+" / "+level.getBlockState(lava)+"; volumes "+volume(helper,pos)+" / "+volume(helper,lava));
            helper.assertTrue(volume(helper,pos).equals(WaterStorePort.BLOCK_CAPACITY)&&volume(helper,lava).equals(WaterStorePort.BLOCK_CAPACITY),"both fluid inventories preserved separately");helper.succeed();
        });
    }
    @GameTest(template="finite_water_lab",timeoutTicks=80)
    public static void traceEvaporationIsRecordedOnceAndSurvivesSerialization(GameTestHelper helper){
        var pos=enclosedSource(helper);var level=helper.getLevel();var cell=WorldChemistryStore.water(level,pos).orElseThrow();var position=new WaterTransport.Position(pos.getX(),pos.getY(),pos.getZ());
        var trace=new FiniteVolume.Parcel(new java.math.BigDecimal("0.005").movePointRight(18).toBigIntegerExact(),Map.of("test:trace",BigInteger.valueOf(3)),BigInteger.valueOf(7));
        helper.assertTrue(WaterStorePort.INSTANCE.commit(level,UUID.randomUUID(),new WaterTransport.Plan(Map.of(position,cell.revision()),Map.of(position,trace))),"trace fixture");
        org.goldgom.chemicalelements.world.FiniteWaterRuntime.mark(level,pos);
        helper.runAfterDelay(16,()->{
            var after=WorldChemistryStore.water(level,pos).orElseThrow();helper.assertTrue(after.volume().signum()==0&&after.evaporated().equals(trace),"trace volume, solute and heat explicitly leave free inventory once");
            helper.assertTrue(level.getBlockState(pos).isAir(),"sub-threshold water disappears visually");
            var saved=ChunkChemistry.read(WorldChemistryStore.data(level,pos).orElseThrow().write()).water(pos.asLong()).orElseThrow();helper.assertTrue(saved.equals(after),"evaporation audit survives serialization");
            org.goldgom.chemicalelements.world.FiniteWaterRuntime.mark(level,pos);
            helper.runAfterDelay(FiniteWaterRuntime.FLOW_INTERVAL_TICKS,()->{helper.assertTrue(WorldChemistryStore.water(level,pos).orElseThrow().evaporated().equals(trace),"repeated ticks do not count the same loss twice");helper.succeed();});
        });
    }
}

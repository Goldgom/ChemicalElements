package org.goldgom.chemicalelements.instance;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.goldgom.chemicalelements.fluid.FiniteVolume;
import org.goldgom.chemicalelements.fluid.FiniteFluids;
import org.goldgom.chemicalelements.fluid.WaterTransport;
import org.goldgom.chemicalelements.fluid.SorptionLibrary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import org.goldgom.chemicalelements.persistence.ChunkChemistry;
import org.goldgom.chemicalelements.persistence.WorldChemistryStore;
import org.goldgom.chemicalelements.world.FiniteWaterRuntime;

/**
 * B's implementation of C's {@link FiniteWaterRuntime.StorePort}. Cells live only in the chunk store.
 * Unowned vanilla water is admitted once as legacy_unknown, without invented solutes.
 * Empty cells retain their revision and serve as durable exhaustion markers in the same journal.
 */
public final class WaterStorePort implements FiniteWaterRuntime.StorePort {
    /** 1 block = 1000 L at 1e-18 quanta. */
    public static final BigInteger BLOCK_CAPACITY = new BigInteger("1000").multiply(BigInteger.TEN.pow(18));
    public static final WaterStorePort INSTANCE = new WaterStorePort();
    private static final int[][] NEIGHBORS = {{0, 0, 0}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}};
    private static int cachedRadius=-1;
    private static List<int[]> cachedOffsets=List.of();
    private static List<int[]> snapshotOffsets(){
        int radius=org.goldgom.chemicalelements.Config.fluidUpdateRadius;
        if(radius==cachedRadius)return cachedOffsets;
        var offsets=new ArrayList<int[]>();
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++)if(Math.abs(x)+Math.abs(y)+Math.abs(z)<=radius)offsets.add(new int[]{x,y,z});
        cachedRadius=radius;cachedOffsets=List.copyOf(offsets);return cachedOffsets;
    }
    private static final Map<ServerLevel,LinkedHashMap<Long,Integer>> wakeScans = new WeakHashMap<>();

    private WaterStorePort() {}

    /** Non-colliding replaceable vegetation cannot support a suspended liquid sheet. */
    public static boolean freeSpace(ServerLevel level,BlockPos pos){
        var state=level.getBlockState(pos);
        return state.isAir()||FiniteFluids.exposed(state)||state.canBeReplaced()&&state.getFluidState().isEmpty()&&state.getCollisionShape(level,pos).isEmpty();
    }
    @Override public Map<WaterTransport.Position,WaterTransport.Cell> gravityNeighborhood(ServerLevel level,BlockPos source){
        var result=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();
        for(var p:List.of(source,source.below())){
            if(level.isOutsideBuildHeight(p)||WorldChemistryStore.loadedChunk(level,p).isEmpty())continue;
            var c=cellAt(level,p);if(c!=null)result.put(new WaterTransport.Position(p.getX(),p.getY(),p.getZ()),c);
        }
        flagClippedWaterColumns(level,result);return result;
    }
    @Override public boolean hasFallSpace(ServerLevel level,WaterTransport.Position position){
        var pos=new BlockPos(position.x(),position.y(),position.z());var below=pos.below();
        if(level.isOutsideBuildHeight(pos)||WorldChemistryStore.loadedChunk(level,pos).isEmpty())return false;
        if(level.isOutsideBuildHeight(below)||WorldChemistryStore.loadedChunk(level,below).isEmpty()||!freeSpace(level,below))return false;
        var source=WorldChemistryStore.water(level,pos);var sourceState=level.getBlockState(pos);
        if(!freeSpace(level,pos)||source.isEmpty()&&WorldChemistryStore.tombstoned(level,pos))return false;
        if(source.isPresent()?source.get().volume().signum()==0:!FiniteFluids.exposed(sourceState))return false;
        String sourceFluid=source.map(ChunkChemistry.WaterCell::fluidId).orElseGet(()->FiniteFluids.id(sourceState.getFluidState().getType()));
        var other=WorldChemistryStore.water(level,below);
        if(other.isPresent())return other.get().volume().compareTo(BLOCK_CAPACITY)<0&&(other.get().volume().signum()==0||sourceFluid.equals(other.get().fluidId()));
        var state=level.getBlockState(below);
        return !FiniteFluids.exposed(state)||!state.getFluidState().isSource()&&sourceFluid.equals(FiniteFluids.id(state.getFluidState().getType()));
    }

    public static synchronized void bindIfAbsent() {
        if (!FiniteWaterRuntime.ready()) FiniteWaterRuntime.bind(INSTANCE);
    }

    public static void changed(ServerLevel level,BlockPos center) {
        for(var delta:NEIGHBORS){
            var pos=center.offset(delta[0],delta[1],delta[2]);
            if(level.isOutsideBuildHeight(pos)||WorldChemistryStore.loadedChunk(level,pos).isEmpty())continue;
            if(FiniteFluids.exposed(level.getBlockState(pos))||WorldChemistryStore.water(level,pos).map(c->c.volume().signum()>0).orElse(false))
                {FiniteWaterRuntime.markUrgent(level,center);return;}
        }
    }

    @Override
    public boolean prepare(ServerLevel level,List<BlockPos> centers){
        var changes=new TreeMap<Long,WorldChemistryStore.WaterChange>();
        for(var center:centers)for(var delta:snapshotOffsets()){
            var pos=center.offset(delta[0],delta[1],delta[2]);
            if(level.isOutsideBuildHeight(pos)||WorldChemistryStore.loadedChunk(level,pos).isEmpty()||changes.containsKey(pos.asLong())||WorldChemistryStore.water(level,pos).isPresent()||WorldChemistryStore.tombstoned(level,pos))continue;
            var state=level.getBlockState(pos);if(!FiniteFluids.exposed(state))continue;
            var volume=BLOCK_CAPACITY.multiply(BigInteger.valueOf(state.getFluidState().getAmount())).divide(BigInteger.valueOf(8));
            var cell=new ChunkChemistry.WaterCell(volume,new TreeMap<>(),BigInteger.ZERO,BLOCK_CAPACITY,"legacy_unknown",0,FiniteVolume.Parcel.empty(),FiniteFluids.id(state.getFluidState().getType()));
            changes.put(pos.asLong(),new WorldChemistryStore.WaterChange(pos,-1,cell));
        }
        return changes.isEmpty()||WorldChemistryStore.commitWater(level,UUID.randomUUID(),List.copyOf(changes.values()));
    }

    @Override
    public Map<WaterTransport.Position, WaterTransport.Cell> neighborhood(ServerLevel level, BlockPos center) {
        var out = new TreeMap<WaterTransport.Position, WaterTransport.Cell>();
        for (var d : snapshotOffsets()) {
            var p = center.offset(d[0], d[1], d[2]);
            if (level.isOutsideBuildHeight(p) || WorldChemistryStore.loadedChunk(level, p).isEmpty()) continue;
            var cell = cellAt(level, p);
            if (cell == null) continue;
            out.put(new WaterTransport.Position(p.getX(), p.getY(), p.getZ()), cell);
        }
        flagClippedWaterColumns(level,out);return out;
    }

    private static void flagClippedWaterColumns(ServerLevel level,Map<WaterTransport.Position,WaterTransport.Cell> snapshot){
        for(var p:new ArrayList<>(snapshot.keySet())){
            var c=snapshot.get(p);if(c.absorptionRate()!=null)continue;
            var up=new WaterTransport.Position(p.x(),p.y()+1,p.z());if(snapshot.containsKey(up))continue;
            var pos=new BlockPos(up.x(),up.y(),up.z());
            if(level.isOutsideBuildHeight(pos)||WorldChemistryStore.loadedChunk(level,pos).isEmpty())continue;
            if(!FiniteFluids.exposed(level.getBlockState(pos)))continue;
            var above=level.getFluidState(pos);
            if(!above.isEmpty()&&c.fluidId().equals(FiniteFluids.id(above.getType())))snapshot.put(p,new WaterTransport.Cell(c.parcel(),c.capacity(),c.loaded(),c.permeable(),c.revision(),null,c.fluidId(),true));
        }
    }

    /** Current cell (initializing once if a vanilla water block has none), dry permeable cell, or null for solids. */
    WaterTransport.Cell cellAt(ServerLevel level, BlockPos p) {
        var stored = WorldChemistryStore.water(level, p);
        var state = level.getBlockState(p);
        boolean water = FiniteFluids.exposed(state);
        boolean open = freeSpace(level,p);
        var material = SorptionLibrary.INSTANCE.materialForBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        BigInteger poreCapacity = material.poreCapacity();
        BigInteger absorption = new java.math.BigDecimal(material.rate()).multiply(material.wettability()).multiply(java.math.BigDecimal.valueOf(org.goldgom.chemicalelements.Config.fluidFlowIntervalTicks)).toBigInteger();
        if (stored.isEmpty() && water && !WorldChemistryStore.tombstoned(level, p)) stored = initialize(level, p);
        if (stored.isPresent()) {
            var c = stored.get();
            var parcel = new FiniteVolume.Parcel(c.volume(), c.species(), c.energy());
            // Pore water uses the same durable cell and journal as open water, counted exactly once.
            // Breaking a wet block exposes this inventory; replacing it by a smaller/unknown material
            // preserves trapped contents and blocks further intake until there is room again.
            if (open) return new WaterTransport.Cell(parcel, BLOCK_CAPACITY.max(c.volume()), true, true, c.revision(),null,c.fluidId());
            return new WaterTransport.Cell(parcel, poreCapacity.max(c.volume()), true, poreCapacity.signum()>0, c.revision(), absorption,c.fluidId());
        }
        if (!open) return poreCapacity.signum()>0 ? new WaterTransport.Cell(FiniteVolume.Parcel.empty(), poreCapacity, true, true, -1, absorption) : null;
        return new WaterTransport.Cell(FiniteVolume.Parcel.empty(), BLOCK_CAPACITY, true, true, -1);
    }

    Optional<ChunkChemistry.WaterCell> initialize(ServerLevel level, BlockPos p) {
        var chunk = WorldChemistryStore.loadedChunk(level, p);
        if (chunk.isEmpty()) return Optional.empty();
        var fluid = level.getFluidState(p);
        BigInteger volume = fluid.isSource() ? BLOCK_CAPACITY : BLOCK_CAPACITY.multiply(BigInteger.valueOf(fluid.getAmount())).divide(BigInteger.valueOf(8));
        var parcel = new FiniteVolume.Parcel(volume, Map.of(), BigInteger.ZERO);
        String origin = "legacy_unknown";
        var cell = new ChunkChemistry.WaterCell(parcel.volume(), new TreeMap<>(parcel.species()), parcel.energy(), BLOCK_CAPACITY, origin, 0,FiniteVolume.Parcel.empty(),FiniteFluids.id(fluid.getType()));
        var tx = UUID.nameUUIDFromBytes(("water_init|" + level.dimension().location() + "|" + p.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!WorldChemistryStore.commitWater(level, tx, List.of(new WorldChemistryStore.WaterChange(p, -1, cell)))) return WorldChemistryStore.water(level, p);
        return Optional.of(cell);
    }

    @Override
    public boolean commit(ServerLevel level, UUID transaction, WaterTransport.Plan plan) {
        var changes = new ArrayList<WorldChemistryStore.WaterChange>();
        for (var e : plan.replacements().entrySet()) {
            var pos = new BlockPos(e.getKey().x(), e.getKey().y(), e.getKey().z());
            long expected = plan.expectedRevisions().getOrDefault(e.getKey(), -1L);
            var cur = WorldChemistryStore.water(level, pos);
            var parcel = e.getValue();
            var snapshot = cellAt(level, pos);
            if (snapshot == null || snapshot.revision() != expected) return false;
            var origin = snapshot.absorptionRate() != null ? "pores:" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) : cur.map(ChunkChemistry.WaterCell::origin).orElse("flow");
            var capacity = snapshot.capacity();
            changes.add(new WorldChemistryStore.WaterChange(pos, expected,
                    new ChunkChemistry.WaterCell(parcel.volume(), new TreeMap<>(parcel.species()), parcel.energy(), capacity, origin, expected + 1,
                            cur.map(ChunkChemistry.WaterCell::evaporated).orElse(FiniteVolume.Parcel.empty()).plus(plan.evaporated().getOrDefault(e.getKey(),FiniteVolume.Parcel.empty())),plan.fluidTypes().getOrDefault(e.getKey(),snapshot.fluidId()))));
        }
        return WorldChemistryStore.commitWater(level, transaction, changes);
    }

    @Override
    public void render(ServerLevel level, WaterTransport.Position position, int height) {
        boolean previous = RENDERING.get();
        RENDERING.set(true);
        try { FiniteWaterRuntime.project(level, position, height); }
        finally { RENDERING.set(previous); }
    }

    /** Re-enqueue persisted inventories and both sides of chunk boundaries without loading neighbors. */
    public static void load(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
        wakeScans.computeIfAbsent(level,k->new LinkedHashMap<>()).put(chunk.getPos().toLong(),-1);
    }

    public static void unload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level){var queue=wakeScans.get(level);if(queue!=null)queue.remove(event.getChunk().getPos().toLong());}
    }
    public static void unloadLevel(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)wakeScans.remove(level);
    }

    /** Bounded read-only discovery, separate from provenance admission. Rescanning never refills a cell. */
    public static void wake(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var queue=wakeScans.get(level);if(queue==null||queue.isEmpty())return;
        int budget=2048,visits=Math.min(queue.size(),8);
        while(budget>0&&visits-->0&&!queue.isEmpty()){
            var iterator=queue.entrySet().iterator();var entry=iterator.next();long key=entry.getKey();int cursor=entry.getValue();iterator.remove();
            var chunk=level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(key),net.minecraft.world.level.ChunkPos.getZ(key));
            if(chunk==null)continue;
            if(cursor<0){
                WorldChemistryStore.recoverChunk(level,chunk.getPos());
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    if(Math.abs(dx)+Math.abs(dz)>1)continue;
                    var neighbor=level.getChunkSource().getChunkNow(chunk.getPos().x+dx,chunk.getPos().z+dz);if(neighbor==null)continue;
                    for(long stored:neighbor.getData(org.goldgom.chemicalelements.persistence.ChemistryComponents.CHUNK.get()).waterCells().keySet()){
                        var pos=BlockPos.of(stored);
                        if((dx==0&&dz==0||Math.abs(pos.getX()-chunk.getPos().getMinBlockX())<=1||Math.abs(pos.getX()-chunk.getPos().getMaxBlockX())<=1||Math.abs(pos.getZ()-chunk.getPos().getMinBlockZ())<=1||Math.abs(pos.getZ()-chunk.getPos().getMaxBlockZ())<=1)&&needsUpdate(level,pos))FiniteWaterRuntime.mark(level,pos);
                    }
                }
                cursor=0;
            }
            int total=level.getHeight()*256,end=Math.min(total,cursor+Math.min(budget,256));budget-=end-cursor;
            for(;cursor<end;cursor++){
                // Skip entire palettes without exposed fluid. Scanning bedrock, solid
                // mountains and empty sky first can delay discovery of unsupported sea water.
                if(!chunk.getSections()[cursor/4096].maybeHas(FiniteFluids::exposed)){
                    cursor=Math.min(total,((cursor/4096)+1)*4096)-1;continue;
                }
                int local=cursor%256;var pos=new BlockPos(chunk.getPos().getMinBlockX()+(local&15),level.getMinBuildHeight()+cursor/256,chunk.getPos().getMinBlockZ()+(local>>4));
                var state=chunk.getBlockState(pos);if(!FiniteFluids.exposed(state))continue;
                // Newly projected water may appear while this incremental discovery scan is
                // still running. Its region already owns propagation; do not invent a new origin.
                if(state.hasProperty(org.goldgom.chemicalelements.fluid.FiniteLiquidShape.VOLUME_STEP)
                        &&state.getValue(org.goldgom.chemicalelements.fluid.FiniteLiquidShape.VOLUME_STEP)>0)continue;
                if(needsUpdate(level,pos))FiniteWaterRuntime.mark(level,pos);
            }
            if(cursor<total)queue.put(key,cursor);
        }
        if(queue.isEmpty())wakeScans.remove(level);
    }

    /** Read-only discovery: adjacent full ocean cells are not themselves a reason to run physics. */
    public static boolean needsUpdate(ServerLevel level,BlockPos pos){
        if(WorldChemistryStore.loadedChunk(level,pos).isEmpty()||!freeSpace(level,pos))return false;
        var state=level.getBlockState(pos);var stored=WorldChemistryStore.water(level,pos);
        BigInteger amount=observedVolume(level,pos);
        if(amount.signum()==0)return FiniteFluids.exposed(state)&&stored.isPresent();
        if(!FiniteFluids.exposed(state))return true; // Released pore water needs projection.
        String fluid=stored.map(ChunkChemistry.WaterCell::fluidId).orElseGet(()->FiniteFluids.id(state.getFluidState().getType()));
        if(INSTANCE.hasFallSpace(level,new WaterTransport.Position(pos.getX(),pos.getY(),pos.getZ())))return true;
        var threshold=java.math.BigDecimal.valueOf(org.goldgom.chemicalelements.Config.minimumWaterLevelDifferenceLitres).movePointRight(18).toBigInteger();
        for(var direction:net.minecraft.core.Direction.values()){
            var adjacent=pos.relative(direction);if(level.isOutsideBuildHeight(adjacent)||WorldChemistryStore.loadedChunk(level,adjacent).isEmpty())continue;
            var other=level.getBlockState(adjacent);var inventory=WorldChemistryStore.water(level,adjacent);
            BigInteger otherAmount=observedVolume(level,adjacent);
            String otherFluid=inventory.map(ChunkChemistry.WaterCell::fluidId).orElseGet(()->FiniteFluids.exposed(other)?FiniteFluids.id(other.getFluidState().getType()):fluid);
            if(otherAmount.signum()>0&&!fluid.equals(otherFluid))continue;
            if(freeSpace(level,adjacent)){
                var difference=amount.subtract(otherAmount);
                if(direction.getAxis().isHorizontal()&&difference.signum()>0&&difference.compareTo(threshold)>=0)return true;
            }else{
                var material=SorptionLibrary.INSTANCE.materialForBlock(BuiltInRegistries.BLOCK.getKey(other.getBlock()));
                if(material.rate().signum()>0&&material.wettability().signum()>0&&material.poreCapacity().compareTo(otherAmount)>0)return true;
            }
        }
        return false;
    }
    private static BigInteger observedVolume(ServerLevel level,BlockPos pos){
        var stored=WorldChemistryStore.water(level,pos);if(stored.isPresent())return stored.get().volume();
        var state=level.getBlockState(pos);
        return !FiniteFluids.exposed(state)||WorldChemistryStore.tombstoned(level,pos)?BigInteger.ZERO:
                BLOCK_CAPACITY.multiply(BigInteger.valueOf(state.getFluidState().getAmount())).divide(BigInteger.valueOf(8));
    }

    /** True while B projects stored volume to vanilla blocks (C/D may use it to ignore self-induced updates). */
    public static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    /** An empty bucket removes the entire free cell, including partial visual layers. */
    public static net.minecraft.world.item.ItemStack pickup(ServerLevel level, BlockPos pos) {
        return collect(level,pos,null);
    }
    /** Top up a partial bucket; excess stays in the source, never deletes another cell's contents. */
    public static net.minecraft.world.item.ItemStack collect(ServerLevel level,BlockPos pos,org.goldgom.chemicalelements.fluid.FiniteBucketContents held){
        if(WorldChemistryStore.loadedChunk(level,pos).isEmpty()||!FiniteFluids.exposed(level.getBlockState(pos)))return net.minecraft.world.item.ItemStack.EMPTY;
        var cell=INSTANCE.cellAt(level,pos);
        if(cell==null||cell.absorptionRate()!=null||cell.parcel().volume().signum()==0
                ||held!=null&&!held.fluidId().equals(cell.fluidId()))return net.minecraft.world.item.ItemStack.EMPTY;
        if(FiniteFluids.resolve(cell.fluidId()).getBucket()==net.minecraft.world.item.Items.AIR)return net.minecraft.world.item.ItemStack.EMPTY;
        var before=held==null?FiniteVolume.Parcel.empty():held.parcel();
        var room=BLOCK_CAPACITY.subtract(before.volume());if(room.signum()<=0)return net.minecraft.world.item.ItemStack.EMPTY;
        var split=FiniteVolume.take(cell.parcel(),room.min(cell.parcel().volume()));
        var bucket=new org.goldgom.chemicalelements.fluid.FiniteBucketContents(cell.fluidId(),before.plus(split.moved())).stack();
        var position=new WaterTransport.Position(pos.getX(),pos.getY(),pos.getZ());
        if(!INSTANCE.commit(level,UUID.randomUUID(),new WaterTransport.Plan(Map.of(position,cell.revision()),Map.of(position,split.remainder()))))return net.minecraft.world.item.ItemStack.EMPTY;
        INSTANCE.render(level,position,split.remainder().volume().signum()==0?0:org.goldgom.chemicalelements.fluid.WaterLevelProjection.height(split.remainder().volume(),BLOCK_CAPACITY));
        FiniteWaterRuntime.markUrgent(level,pos);return bucket;
    }

    /** Buckets introduce one exact parcel; overflow fills connected loaded cells in one transaction. */
    public static boolean placeBucket(ServerLevel level, BlockPos pos, net.minecraft.world.item.ItemStack bucket) {
        if(bucket!=null){var stored=org.goldgom.chemicalelements.fluid.FiniteBucketContents.read(bucket);if(stored.isPresent())return placeBucket(level,pos,bucket,FiniteFluids.resolve(stored.get().fluidId()));}
        var fluid=net.minecraft.core.registries.BuiltInRegistries.FLUID.stream()
                .filter(f->f instanceof net.minecraft.world.level.material.FlowingFluid flowing&&flowing.getSource()==f
                        &&bucket!=null&&f.getBucket()==bucket.getItem()).findFirst().orElse(net.minecraft.world.level.material.Fluids.WATER);
        return placeBucket(level,pos,bucket,(net.minecraft.world.level.material.FlowingFluid)fluid);
    }
    public static boolean placeBucket(ServerLevel level, BlockPos pos, net.minecraft.world.item.ItemStack bucket,
                                     net.minecraft.world.level.material.FlowingFluid fluid) {
        String fluidId=FiniteFluids.id(fluid);
        FiniteVolume.Parcel parcel=new FiniteVolume.Parcel(BLOCK_CAPACITY,Map.of(),BigInteger.ZERO);
        if(bucket!=null){
            var custom=bucket.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if(custom!=null){var root=custom.copyTag();if(root.contains("chemicalelements:finite_water")){
                var tag=root.getCompound("chemicalelements:finite_water");
                if(tag.contains("fluid")&&!tag.getString("fluid").equals(fluidId))return false;
                var species=new TreeMap<String,BigInteger>();var amounts=tag.getCompound("species");
                for(String key:amounts.getAllKeys())species.put(key,new BigInteger(amounts.getString(key)));
                parcel=new FiniteVolume.Parcel(new BigInteger(tag.getString("volume")),species,new BigInteger(tag.getString("energy")));
                if(parcel.volume().signum()<=0||parcel.volume().compareTo(BLOCK_CAPACITY)>0)return false;
            }}
        }
        var pending=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();pending.add(pos.immutable());
        var replacements=new TreeMap<WaterTransport.Position,FiniteVolume.Parcel>();var versions=new TreeMap<WaterTransport.Position,Long>();
        var types=new TreeMap<WaterTransport.Position,String>();
        // Bounded, loaded-only overflow. An enclosed full vessel rejects the whole bucket.
        while(!pending.isEmpty()&&visited.size()<512&&parcel.volume().signum()>0){
            var target=pending.removeFirst();if(!visited.add(target)||target.distManhattan(pos)>Math.min(8,org.goldgom.chemicalelements.Config.fluidUpdateRadius)
                    ||level.isOutsideBuildHeight(target)||WorldChemistryStore.loadedChunk(level,target).isEmpty())continue;
            var state=level.getBlockState(target);if(!freeSpace(level,target))continue;
            var cell=INSTANCE.cellAt(level,target);if(cell==null||cell.absorptionRate()!=null
                    ||cell.parcel().volume().signum()>0&&!cell.fluidId().equals(fluidId))continue;
            var room=BLOCK_CAPACITY.subtract(cell.parcel().volume()).max(BigInteger.ZERO);
            if(room.signum()>0){
                var split=FiniteVolume.take(parcel,room.min(parcel.volume()));parcel=split.remainder();
                var key=new WaterTransport.Position(target.getX(),target.getY(),target.getZ());
                replacements.put(key,cell.parcel().plus(split.moved()));versions.put(key,cell.revision());types.put(key,fluidId);
            }
            // Downward room first, then sides; the next layer above receives overflow when needed.
            for(var direction:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.NORTH,
                    net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.UP})
                pending.addLast(target.relative(direction));
        }
        if(parcel.volume().signum()>0||replacements.isEmpty())return false;
        if(!INSTANCE.commit(level,UUID.randomUUID(),new WaterTransport.Plan(versions,replacements,Map.of(),types)))return false;
        replacements.forEach((key,value)->{
            INSTANCE.render(level,key,org.goldgom.chemicalelements.fluid.WaterLevelProjection.height(value.volume(),BLOCK_CAPACITY));
        });
        FiniteWaterRuntime.markUrgent(level,pos);
        return true;
    }
}

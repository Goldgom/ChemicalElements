package org.goldgom.chemicalelements.network;

import java.math.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.content.material.MaterialBlockEntity;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.fluid.*;
import org.goldgom.chemicalelements.persistence.*;

public final class BlockChemistryInspection {
    public static final double RANGE=8;
    private BlockChemistryInspection() {}

    /** Ray tracing treats unavailable chunks as walls and cannot force a chunk load. */
    public static Optional<BlockPos> target(ServerPlayer player){
        var level=player.serverLevel();
        BlockGetter view=new BlockGetter(){
            @Override public BlockState getBlockState(BlockPos p){var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);return chunk==null?Blocks.BARRIER.defaultBlockState():chunk.getBlockState(p);}
            @Override public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
            @Override public BlockEntity getBlockEntity(BlockPos p){return null;}
            @Override public int getHeight(){return level.getHeight();}
            @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
        };
        var eye=player.getEyePosition();var hit=view.clip(new ClipContext(eye,eye.add(player.getViewVector(1).scale(RANGE)),ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,player));
        if(hit.getType()!=HitResult.Type.BLOCK||WorldChemistryStore.loadedChunk(level,hit.getBlockPos()).isEmpty())return Optional.empty();
        return Optional.of(hit.getBlockPos());
    }
    public static List<String> describe(ServerPlayer player,BlockPos pos){
        var level=player.serverLevel();if(player.distanceToSqr(pos.getCenter())>100||WorldChemistryStore.loadedChunk(level,pos).isEmpty())return List.of("目标不可用 / Target unavailable");
        var state=level.getBlockState(pos);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());var lines=new ArrayList<String>();
        var catalog=AtomicChemistryCatalog.INSTANCE.snapshot();
        lines.add(state.getBlock().getName().getString()+" · "+id);lines.add("位置 / Position: "+pos.getX()+", "+pos.getY()+", "+pos.getZ());
        var material=SorptionLibrary.INSTANCE.materialForBlock(id);
        lines.add("吸水容量 / Water capacity: "+units(material.poreCapacity())+" L");
        lines.add("吸水速率 / Uptake: "+units(material.rate())+" L/t · wettability "+material.wettability().toPlainString());
        var water=WorldChemistryStore.water(level,pos);
        if(water.isPresent()){
            var cell=water.get();boolean free=state.isAir()||FiniteFluids.exposed(state);
            lines.add("流体类型 / Fluid: "+cell.fluidId());
            lines.add((free?"自由流体 / Free fluid: ":"孔隙/表面流体 / Retained fluid: ")+units(cell.volume())+" L");
            lines.add("流体库存容量 / Stored capacity: "+units(cell.capacity())+" L · revision "+cell.revision());
            lines.add("流体能量 / Fluid energy: "+units(cell.energy())+" J");
            lines.add("微量蒸发移出 / Evaporated: "+units(cell.evaporated().volume())+" L");
            lines.add("流体来源 / Fluid origin: "+cell.origin());
            lines.add("流体组分 / Fluid species:");cell.species().forEach((key,n)->lines.add(key+" = "+units(n)+" mol"));
            lines.add("蒸发携出能量 / Removed energy: "+units(cell.evaporated().energy())+" J");
            cell.evaporated().species().forEach((key,n)->lines.add("Evaporated "+key+" = "+units(n)+" mol"));
        }else if(FiniteFluids.exposed(state)){
            var volume=org.goldgom.chemicalelements.instance.WaterStorePort.BLOCK_CAPACITY.multiply(BigInteger.valueOf(state.getFluidState().getAmount())).divide(BigInteger.valueOf(8));
            lines.add("尚未登记的流体 / Unadmitted fluid: "+FiniteFluids.id(state.getFluidState().getType())+" · "+units(volume)+" L (显示估计 / visual estimate)");
        }else lines.add("孔隙/表面水 / Retained water: 0 L");
        if(state.is(Blocks.WATER)||water.filter(c->c.volume().signum()>0&&c.fluidId().equals(FiniteFluids.WATER)).isPresent()){
            var solvent=catalog.species().get(net.minecraft.resources.ResourceLocation.parse("chemicalelements:water"));
            if(solvent!=null){lines.add("溶剂模型 / Solvent: chemicalelements:water = H₂O · "+solvent.phase()+" · "+solvent.molarMass()+" g/mol");lines.add("溶剂化学式 / Solvent formula: "+solvent.atoms());}
        }
        lines.add("目录版本 / Catalog epoch: "+catalog.epoch());
        var placed=WorldChemistryStore.block(level,pos);var be=level.getBlockEntity(pos);
        if(be instanceof MaterialBlockEntity materialBlock){
            try{var ledger=materialBlock.ledger();lines.add("实际材质库存 / Material inventory · revision "+ledger.revision());for(var instance:ledger.instances())appendInstance(lines,instance);if(ledger.instances().isEmpty())lines.add("库存为空 / Empty inventory");}
            catch(IllegalStateException isolated){lines.add("材质库存暂不可读 / Material inventory unavailable");}
        }else if(placed.isPresent()){lines.add("实际方块库存 / Actual block inventory");appendInstance(lines,placed.get().record());}
        else if(FiniteFluids.exposed(state)){
            lines.add("流体以体积记录；组分列表仅显示已登记物质 / Fluid uses volume; only recorded species amounts are listed");
            lines.add("温度、同位素：无逐块测量 / Temperature and isotopes: no block measurement");
        }else{
            lines.add("材质名义模型 / Nominal material model (非逐块实测 / not measured)");
            var composition=catalog.itemCompositions().get(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()));
            if(composition==null||composition.amounts().isEmpty())lines.add("固体组成未建模 / Solid composition unmodeled");else appendComposition(lines,"固体组分 / Solid species",composition);
            lines.add("温度、同位素：无逐块测量 / Temperature and isotopes: no block measurement");
        }
        lines.add("吸水模型 / Sorption model: "+material.basis());lines.add(material.source());
        lines.add("方块状态 / Block state: "+state);
        return List.copyOf(lines);
    }
    private static void appendInstance(List<String> lines,InstanceRecord instance){
        lines.add("实例 / Instance: "+instance.id()+" · revision "+instance.revision());
        lines.add("来源 / Origin: "+instance.origin());
        lines.add("温度 / Temperature: "+instance.thermal().temperatureK().toPlainString()+" K");
        lines.add("能量 / Energy: "+instance.thermal().energyJ().toPlainString()+" J");
        lines.add("相态 / Phases: "+instance.thermal().phaseFractions());
        appendComposition(lines,"剩余组成 / Remaining composition",instance.remaining());
        appendComposition(lines,"吸附组分 / Adsorbed species",instance.adsorbed());
        var pores=instance.poreSolution();lines.add("实例孔隙水 / Instance pore water: "+pores.volumeLitres().toPlainString()+" L; "+pores.waterMol()+" mol; "+pores.temperatureK()+" K");
        appendComposition(lines,"溶解组分 / Dissolved species",pores.dissolved());appendComposition(lines,"悬浮组分 / Suspended species",pores.suspended());
        lines.add("核素 / Nuclides:");instance.nuclides().amounts().forEach((key,n)->lines.add(key+" = "+n+" mol"));
    }
    private static void appendComposition(List<String> lines,String title,Composition composition){
        lines.add(title+":");var catalog=AtomicChemistryCatalog.INSTANCE.snapshot();
        composition.amounts().forEach((key,n)->{var definition=catalog.species().get(key);lines.add(key+" = "+n+" mol"+(definition==null?" (未建模 / unmodeled)":" · "+definition.phase()+" · charge "+definition.charge()+" · "+definition.molarMass()+" g/mol"));});
        var known=new TreeMap<net.minecraft.resources.ResourceLocation,MolAmount>();composition.amounts().forEach((key,n)->{if(catalog.species().containsKey(key))known.put(key,n);});
        if(!known.isEmpty()){
            var value=new Composition(known);lines.add("已建模组分质量 / Modeled mass: "+value.mass(catalog).stripTrailingZeros().toPlainString()+" g");
            value.inventory(catalog).atoms().forEach((key,n)->lines.add("元素 / Element "+key+" = "+units(n)+" atom-mol"));
        }
    }
    public static String units(BigInteger n){return new BigDecimal(n,18).stripTrailingZeros().toPlainString();}
}

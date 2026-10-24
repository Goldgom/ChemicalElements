package org.goldgom.chemicalelements.world.water;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.goldgom.chemicalelements.fluid.SorptionLibrary;
import org.goldgom.chemicalelements.fluid.waterbody.*;

/** B provides existing pore snapshots only. This adapter never defaults a missing Owner balance to zero. */
public final class LoadedWettingContacts {
    private LoadedWettingContacts() {}
    public record PoreSnapshot(long revision,boolean initializationTombstone,WaterBody pores) {}
    public record ContactRead(LevelChunk incarnation,BlockPos position,BlockState state,WaterWetting.Target target) {
        public ContactRead { position=position.immutable(); }
    }
    public record Read(List<ContactRead> contacts,String pendingReason) {
        public Read { contacts=List.copyOf(contacts); }
        public boolean ready(){return pendingReason.isEmpty();}
        public List<WaterWetting.Target> targets(){if(!ready())throw new IllegalStateException(pendingReason);return contacts.stream().map(ContactRead::target).toList();}
    }
    /** At most 256 known wet positions / 1536 neighbor reads, no generation or cross-boundary loads.
     * Pore keys include the level dimension and packed position. Caller must pass actual contacting wet
     * positions, not every empty cell of a cavity above its liquid surface. */
    public static Read read(ServerLevel level,Collection<BlockPos> wetPositions,Map<String,PoreSnapshot> ownerSnapshots) {
        if(wetPositions.size()>256)return new Read(List.of(),"contact slice budget exceeded");
        var result=new TreeMap<String,ContactRead>();
        for(var water:wetPositions) {
            if(level.getChunkSource().getChunkNow(water.getX()>>4,water.getZ()>>4)==null)return new Read(List.of(),"water chunk unloaded");
            for(var direction:Direction.values()) {
                var pos=water.relative(direction);var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                if(chunk==null)return new Read(List.of(),"contact neighbor unloaded");
                var state=chunk.getBlockState(pos);
                if(state.isAir()||state.is(net.minecraft.world.level.block.Blocks.WATER))continue;
                String id=level.dimension().location()+"/"+pos.asLong();
                var prior=result.get(id);if(prior!=null){if(prior.incarnation()!=chunk||!prior.state().equals(state))return new Read(List.of(),"contact changed while reading");continue;}
                // Geometry adapter classifies actual full collision solids without strong shape neighbor loads.
                var geometry=new LoadedWaterGeometry(level,Map.of());
                var kind=geometry.read(new WaterGeometry.Pos(pos.getX(),pos.getY(),pos.getZ())).kind();
                if(kind==WaterGeometry.Kind.UNLOADED)return new Read(List.of(),"shape neighbor unloaded");
                if(kind!=WaterGeometry.Kind.SOLID)return new Read(List.of(),"partial/non-solid contact needs geometric pore model");
                var material=SorptionLibrary.INSTANCE.material(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                if(material.isEmpty())return new Read(List.of(),"unmodeled sorption material "+BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                var snapshot=ownerSnapshots.get(id);if(snapshot==null)return new Read(List.of(),"missing B pore snapshot "+id);
                result.put(id,new ContactRead(chunk,pos,state,new WaterWetting.Target(id,snapshot.revision(),true,true,snapshot.initializationTombstone(),snapshot.pores(),material.get())));
            }
        }
        return new Read(new ArrayList<>(result.values()),"");
    }
    public static boolean stillCurrent(ServerLevel level,Read read) {
        if(!read.ready())return false;
        for(var c:read.contacts()) {var p=c.position();if(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=c.incarnation()||!c.incarnation().getBlockState(p).equals(c.state()))return false;}
        return true;
    }
}

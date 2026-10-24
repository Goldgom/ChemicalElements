package org.goldgom.chemicalelements.machine;

import java.io.InputStreamReader;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class MultiblockValidator {
 public enum State{VALID,INVALID,UNLOADED,NOT_REQUIRED,DEFINITION_ERROR}
 public record Result(State state,List<String> diagnostics){public Result{diagnostics=List.copyOf(diagnostics);}public String summary(){return state+": "+String.join("; ",diagnostics);}}
 private MultiblockValidator(){}
 public static State validate(Level level,BlockPos origin,MachineKind kind,int quarterTurns){return inspect(level,origin,kind,quarterTurns).state();}
 public static Result inspect(Level level,BlockPos origin,MachineKind kind,int quarterTurns){
  var path=ResourceLocation.fromNamespaceAndPath("chemicalelements","chemicalelements/multiblocks/"+kind.id()+".json");
  var resource=level.getServer()==null?Optional.<net.minecraft.server.packs.resources.Resource>empty():level.getServer().getResourceManager().getResource(path);
  if(resource.isEmpty())return new Result(State.DEFINITION_ERROR,List.of("Missing structure definition "+path+"; machine cannot run"));
  try(var reader=new InputStreamReader(resource.get().open(),java.nio.charset.StandardCharsets.UTF_8)){
   var json=JsonParser.parseReader(reader).getAsJsonObject();if(json.get("schema_version").getAsInt()!=1)return new Result(State.DEFINITION_ERROR,List.of("Unsupported schema"));
   var expected=new HashSet<BlockPos>();var missing=new ArrayList<String>();boolean unloaded=false;
   var parts=json.getAsJsonArray("parts");if(parts==null||parts.isEmpty()||parts.size()>4096)return new Result(State.DEFINITION_ERROR,List.of("Structure must contain 1..4096 cells"));
   for(var entry:parts){
    var part=entry.getAsJsonObject();var offset=part.getAsJsonArray("offset");if(offset.size()!=3)throw new IllegalArgumentException("offset length");int x=offset.get(0).getAsInt(),y=offset.get(1).getAsInt(),z=offset.get(2).getAsInt();
    if(Math.abs((long)x)>32||Math.abs((long)y)>64||Math.abs((long)z)>32)throw new IllegalArgumentException("structure bounds");
    for(int i=0;i<Math.floorMod(quarterTurns,4);i++){int t=x;x=-z;z=t;}
    var pos=origin.offset(x,y,z);if(!expected.add(pos))throw new IllegalArgumentException("overlapping structure cell "+pos);
    var id=ResourceLocation.parse(part.get("block").getAsString());if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("unknown block "+id);
    if(!level.hasChunkAt(pos)){unloaded=true;if(missing.size()<8)missing.add("Unloaded "+pos.toShortString());continue;}
    var actual=level.getBlockState(pos);if(!actual.is(BuiltInRegistries.BLOCK.get(id))&&missing.size()<8)missing.add("Missing "+id+" at "+pos.toShortString());
    else if(actual.getBlock() instanceof SizedPartBlock sized){
     if(!sized.complete(level,pos,actual)&&missing.size()<8)missing.add("Incomplete sized part "+id+" at "+pos.toShortString());
     if(part.has("facing")){var wanted=net.minecraft.core.Direction.byName(part.get("facing").getAsString());if(wanted==null||wanted.getAxis().isVertical())throw new IllegalArgumentException("invalid part facing");for(int turn=0;turn<Math.floorMod(quarterTurns,4);turn++)wanted=wanted.getClockWise();if(actual.getValue(SizedPartBlock.FACING)!=wanted&&missing.size()<8)missing.add("Wrong direction at "+pos.toShortString()+" expected "+wanted);}
    }
   }
   if(!unloaded&&missing.isEmpty()){
    var claims=new HashSet<BlockPos>(expected);
    for(var cell:expected){var state=level.getBlockState(cell);if(state.getBlock() instanceof SizedPartBlock sized){var anchor=sized.origin(cell,state);for(int dx=0;dx<3;dx++)for(int dy=0;dy<6;dy++)for(int dz=0;dz<3;dz++){var candidate=anchor.offset(PartDefinition.rotate(dx,dy,dz,state.getValue(SizedPartBlock.FACING)));if(level.hasChunkAt(candidate)){var other=level.getBlockState(candidate);if(other.is(sized)&&sized.origin(candidate,other).equals(anchor))claims.add(candidate);}}}}
    var conflict=StructureClaims.claim(level,origin,claims);if(conflict.isPresent())return new Result(State.INVALID,List.of("Structure overlaps controller "+conflict.get().toShortString()));
   }
   return new Result(unloaded?State.UNLOADED:missing.isEmpty()?State.VALID:State.INVALID,missing);
  }catch(Exception failure){return new Result(State.DEFINITION_ERROR,List.of("Invalid structure: "+failure.getMessage()));}
 }
}

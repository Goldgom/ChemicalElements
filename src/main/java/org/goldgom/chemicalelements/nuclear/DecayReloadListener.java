package org.goldgom.chemicalelements.nuclear;
import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
/** Nuclear definitions have independent nuclide identities and do not publish chemical species. */
public final class DecayReloadListener extends SimpleJsonResourceReloadListener {
 public static final DecayReloadListener INSTANCE=new DecayReloadListener();
 private volatile List<BranchingDecay.Rule> rules=List.of();
 private DecayReloadListener(){super(new Gson(),"chemicalelements/decay");}
 public List<BranchingDecay.Rule> snapshot(){return rules;}
 @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
  var prepared=DecayLibrary.parse(files);rules=prepared;
 }
}

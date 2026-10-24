package org.goldgom.chemicalelements.gametest.material;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.content.material.*;
import org.goldgom.chemicalelements.data.*;
import org.goldgom.chemicalelements.instance.*;
import org.goldgom.chemicalelements.registry.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialAdmissionGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void unknownRealInstancesPreserved(GameTestHelper h) {
        var bundle=AtomicChemistryCatalog.INSTANCE.bundle();
        for(String name:ElementItems.NAMES) {
            // Real sampler creates local test data only. This is not a fabricated Owner or production inventory.
            var stack=new ItemStack(ElementItems.ELEMENTS.get(name).get(),2);
            var ledger=InstanceSampler.mint(stack,2,new CreationContext(
                    new Provenance("gametest","material-content-r23",23L,bundle.epoch(),Map.of()),Optional.empty(),false));
            StackLedgers.set(stack,ledger);
            var before=stack.copy();
            var result=MaterialAdmission.inspect(h.getLevel(),stack,ElementMaterials.id("elemental_"+name));
            h.assertTrue(result.status()==MaterialAdmission.Status.UNKNOWN && !result.mayPlace(),"UNKNOWN not ambient solid: "+name);
            h.assertTrue(result.expected()==ledger || result.expected().equals(ledger),"Complete ledger retained");
            h.assertTrue(result.matches(stack,bundle) && result.observations().size()==2,"Same bundle and all instances");
            h.assertTrue(result.observations().getFirst().instance().equals(ledger.instances().getFirst()),"UUID/thermal/nuclides/pore/adsorbed unchanged");
            h.assertTrue(result.observations().getFirst().behavior().tags().contains(ElementMaterials.id("material/pure")),"Dynamic pure tag consumed");
            h.assertTrue(stack.getCount()==2 && ItemStack.isSameItemSameComponents(stack,before),"No preflight mutation");
        }
        h.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void realBlockItemPlaceRejectsWithoutMutation(GameTestHelper h) {
        var item=ElementMaterials.BLOCK_ITEMS.get("elemental_iron").get();
        var stack=new ItemStack(item,2); var before=stack.copy();
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        var state=h.getLevel().getBlockState(pos);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var context=new BlockPlaceContext(h.getLevel(),null,InteractionHand.MAIN_HAND,stack,hit);
        h.assertTrue(item.place(context)==InteractionResult.FAIL,"Never use vanilla placement without ledger/Owner");
        h.assertTrue(stack.getCount()==2 && ItemStack.isSameItemSameComponents(stack,before),"Input untouched");
        h.assertTrue(h.getLevel().getBlockState(pos).equals(state),"World untouched");
        h.assertTrue(MaterialAdmission.inspect(h.getLevel(),stack,item.target()).status()==MaterialAdmission.Status.MISSING_LEDGER,"No implicit mint");
        h.succeed();
    }
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void mixtureUsesOneCarrierAndUnmodeledQuery(GameTestHelper h) {
        var bundle=AtomicChemistryCatalog.INSTANCE.bundle();
        var a=bundle.catalog().itemCompositions().get(ElementMaterials.id("element_iron"));
        var b=bundle.catalog().itemCompositions().get(ElementMaterials.id("element_copper"));
        var query=bundle.materials().query(a.add(b));
        h.assertTrue(query.modelStatus()==MaterialBehaviorLibrary.ModelStatus.UNMODELED,"No pure model extrapolation");
        h.assertTrue(query.tags().contains(ElementMaterials.id("material/mixture")),"Shared dynamic mixture rule");
        h.assertTrue(ElementMaterials.CARRIER_FORMS.size()==4,"No ratio-specific registration");
        h.succeed();
    }
}

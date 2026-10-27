package org.goldgom.chemicalelements.gametest.water;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.item.ChemicalInspectorItem;
import org.goldgom.chemicalelements.network.*;
import org.goldgom.chemicalelements.persistence.*;
import org.goldgom.chemicalelements.registry.ElementItems;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class ChemicalInspectorGameTests {
    @GameTest(template="core_empty")
    public static void inspectorRequiresCreativeAndHeldToolAndDoesNotInitializeWater(GameTestHelper helper){
        var player=helper.makeMockServerPlayerInLevel();var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(2,2,2));
        level.setBlock(pos,Blocks.WATER.defaultBlockState(),2);
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(!ChemicalInspectorItem.authorized(player),"creative alone does not grant inspection access");
        player.getInventory().setItem(player.getInventory().selected,new ItemStack(ElementItems.CHEMICAL_INSPECTOR.get()));
        helper.assertTrue(ChemicalInspectorItem.authorized(player),"held creative tool grants inspection access");
        player.setPos(pos.getX()+0.5,pos.getY()-1,pos.getZ()-2);player.setYRot(0);player.setXRot(0);
        helper.assertTrue(InspectorNetwork.canQuery(player,pos),"server ray trace authorizes the actually viewed water block");
        var chunk=WorldChemistryStore.data(level,pos).orElseThrow();long revision=chunk.revision();
        var lines=BlockChemistryInspection.describe(player,pos);
        helper.assertTrue(lines.stream().anyMatch(s->s.contains("minecraft:water")),"unowned natural water still has inspectable information");
        helper.assertTrue(chunk.revision()==revision&&WorldChemistryStore.water(level,pos).isEmpty(),"read-only inspection must not initialize water or mint inventory");
        helper.assertTrue(!InspectorNetwork.canQuery(player,pos.offset(500,0,0)),"arbitrary distant blocks cannot be queried");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(!ChemicalInspectorItem.authorized(player),"tool held in survival cannot expose creative inspection data");helper.succeed();
    }
    @GameTest(template="core_empty")
    public static void ordinaryBlocksExposeCatalogAndFiniteWettingInformation(GameTestHelper helper){
        var player=helper.makeMockServerPlayerInLevel();var pos=helper.absolutePos(new BlockPos(2,2,2));helper.getLevel().setBlock(pos,Blocks.IRON_BLOCK.defaultBlockState(),2);player.setPos(pos.getX(),pos.getY(),pos.getZ());
        var lines=BlockChemistryInspection.describe(player,pos);
        helper.assertTrue(lines.stream().anyMatch(s->s.contains("minecraft:iron_block")),"ordinary block identified");
        helper.assertTrue(lines.stream().anyMatch(s->s.contains("chemicalelements:iron")),"catalog iron composition is available without a placed instance");
        helper.assertTrue(lines.stream().anyMatch(s->s.contains("Nominal material model")),"nominal data is identified honestly");
        helper.assertTrue(lines.stream().anyMatch(s->s.contains("0.001 L")),"dense block surface wetting capacity displayed");helper.succeed();
    }
    @GameTest(template="core_empty")
    public static void heldInspectorInterceptsInteractiveBlockMenus(GameTestHelper helper){
        var player=helper.makeMockServerPlayerInLevel();var pos=helper.absolutePos(new BlockPos(2,2,2));var level=helper.getLevel();level.setBlock(pos,Blocks.CHEST.defaultBlockState(),2);
        player.setGameMode(GameType.CREATIVE);player.getInventory().setItem(player.getInventory().selected,new ItemStack(ElementItems.CHEMICAL_INSPECTOR.get()));
        player.setPos(pos.getX()+0.5,pos.getY()-1,pos.getZ()-2);player.setYRot(0);player.setXRot(0);
        var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.NORTH,pos,false);
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
        org.goldgom.chemicalelements.item.InspectorInteractions.rightClick(event);
        helper.assertTrue(event.isCanceled()&&event.getCancellationResult().consumesAction(),"creative inspector takes priority over a chest menu");
        helper.assertTrue(player.containerMenu==player.inventoryMenu,"inspection does not open the ordinary chest inventory");
        helper.assertTrue(!ElementItems.CHEMICAL_INSPECTOR.get().canAttackBlock(level.getBlockState(pos),level,pos,player),"creative inspection cannot accidentally break the inspected block");helper.succeed();
    }
}

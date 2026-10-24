package org.goldgom.chemicalelements.gametest.core;

import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.tx.*;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MenuAuthorityGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void authorityLocationValues(GameTestHelper h) {
        UUID player=new UUID(17,1), session=new UUID(17,2), incarnation=new UUID(17,3);
        var type=ResourceLocation.parse("chemicalelements:machine");
        var authority=new MenuAuthority(player,session,type,105);
        h.assertTrue(!authority.equals(new MenuAuthority(player,new UUID(17,4),type,105)),"Session changes authorization");
        var owner=new OwnerKey.PlayerInventory(player);
        h.assertTrue(owner.equals(new OwnerKey.PlayerInventory(player)),"Persistent player identity excludes session");
        var machine=new OwnerKey.MachineInventory(ResourceLocation.parse("minecraft:overworld"),0,incarnation,type);
        h.assertTrue(!machine.equals(new OwnerKey.MachineInventory(ResourceLocation.parse("minecraft:overworld"),0,new UUID(17,5),type)),"Replacement machine has different identity");
        var container=new SimpleContainer(9);
        container.setItem(0,new ItemStack(Items.STONE,3));
        var slot=new AuthoritySlot(owner,container,0,34,105);
        h.assertTrue(slot.container()==container && slot.containerSlot()==0 && slot.menuSlot()==34,"Location references exact original container and raw slot");
        h.assertTrue(container.getItem(0).getCount()==3,"Construction does not move inventory");
        int rejected=0;
        try { new MenuAuthority(player,session,type,-1); } catch(IllegalArgumentException e){rejected++;}
        try { new MenuAuthority(player,null,type,105); } catch(NullPointerException e){rejected++;}
        try { new AuthoritySlot(owner,container,9,0,105); } catch(IllegalArgumentException e){rejected++;}
        try { new AuthoritySlot(owner,container,-1,0,105); } catch(IllegalArgumentException e){rejected++;}
        try { new AuthoritySlot(owner,container,0,-1,105); } catch(IllegalArgumentException e){rejected++;}
        try { new AuthoritySlot(owner,container,0,0,-1); } catch(IllegalArgumentException e){rejected++;}
        h.assertTrue(rejected==6,"Malformed authorization/location values rejected");
        h.succeed();
    }
}

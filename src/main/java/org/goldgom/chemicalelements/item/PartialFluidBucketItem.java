package org.goldgom.chemicalelements.item;

import java.math.BigDecimal;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.HitResult;
import org.goldgom.chemicalelements.fluid.*;
import org.goldgom.chemicalelements.instance.WaterStorePort;

public final class PartialFluidBucketItem extends Item {
    public PartialFluidBucketItem(Properties properties){super(properties);}
    @Override public Component getName(ItemStack stack){
        return Component.translatable("item.chemicalelements.partial_fluid_bucket",FiniteBucketContents.read(stack)
                .map(c->new BigDecimal(c.parcel().volume(),18).stripTrailingZeros().toPlainString()).orElse("0"));
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        FiniteBucketContents.read(stack).ifPresent(c->lines.add(Component.literal(c.fluidId())));
        lines.add(Component.translatable("item.chemicalelements.partial_fluid_bucket.help"));
    }
    @Override public boolean isBarVisible(ItemStack stack){return true;}
    @Override public int getBarColor(ItemStack stack){return 0x3D98DB;}
    @Override public int getBarWidth(ItemStack stack){return FiniteBucketContents.read(stack).map(c->c.parcel().volume().multiply(java.math.BigInteger.valueOf(13)).divide(WaterStorePort.BLOCK_CAPACITY).intValue()).orElse(0);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var held=player.getItemInHand(hand);var contents=FiniteBucketContents.read(held);
        if(contents.isEmpty())return InteractionResultHolder.fail(held);
        var hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.ANY);
        if(hit.getType()!=HitResult.Type.BLOCK)return InteractionResultHolder.pass(held);
        var pos=hit.getBlockPos();if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,hit.getDirection(),held))return InteractionResultHolder.fail(held);
        if(!(level instanceof ServerLevel server))return InteractionResultHolder.sidedSuccess(held,true);
        if(!player.isShiftKeyDown()&&FiniteFluids.exposed(level.getBlockState(pos))){
            var result=WaterStorePort.collect(server,pos,contents.get());
            if(result.isEmpty())return InteractionResultHolder.fail(held);
            return InteractionResultHolder.sidedSuccess(result,false);
        }
        var target=FiniteFluids.exposed(level.getBlockState(pos))?pos:pos.relative(hit.getDirection());
        if(!player.mayUseItemAt(target,hit.getDirection(),held)||!WaterStorePort.placeBucket(server,target,held,FiniteFluids.resolve(contents.get().fluidId())))return InteractionResultHolder.fail(held);
        return InteractionResultHolder.sidedSuccess(new ItemStack(Items.BUCKET),false);
    }
}

package org.goldgom.chemicalelements.content.material;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Candidate block item. Vanilla placement would discard the stack ledger, so it is never called. */
public final class MaterialBlockItem extends BlockItem {
    private final ResourceLocation target;
    public MaterialBlockItem(Block block, Properties properties, ResourceLocation target) {
        super(block, properties); this.target = target;
    }
    public ResourceLocation target() { return target; }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            var inspection = MaterialAdmission.inspect(level, context.getItemInHand(), target);
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(
                    Component.translatable("tooltip.chemicalelements.material_content.blocked",
                            Component.translatable("tooltip.chemicalelements.material_content.status." + inspection.status().name().toLowerCase(java.util.Locale.ROOT))), true);
        }
        return InteractionResult.FAIL;
    }
}

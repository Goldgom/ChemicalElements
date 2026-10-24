package org.goldgom.chemicalelements.instance;

import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;

/** Narrow proven-immutable payload domain. Unknown compound/third-party values are rejected even
 * when their codec round-trips and their digest matches: neither proves absence of internal ABA.
 * Broader support requires a reviewed immutable type or a real mutation clock, not namespace trust. */
public final class MutablePayloadPolicy {
    private MutablePayloadPolicy() {}
    public static void requireSupported(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (var component : stack.getComponents()) {
            if (component.type() == ChemistryComponents.LEDGER.get()) continue;
            Object value = component.value();
            // Audited MC 1.21.1 defaults only, by identity, NOT equals/isEmpty. Arbitrary
            // ItemEnchantments retains Mutable's map; arbitrary Lore/Attributes retain lists.
            // Their EMPTY singletons alone have no externally retained mutable constructor input.
            if (value == net.minecraft.world.item.component.ItemLore.EMPTY
                    || value == net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY
                    || value == net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY
                    || value == net.minecraft.util.Unit.INSTANCE
                    || value == net.minecraft.world.item.Rarity.COMMON
                    || value == net.minecraft.world.item.Rarity.UNCOMMON
                    || value == net.minecraft.world.item.Rarity.RARE
                    || value == net.minecraft.world.item.Rarity.EPIC) continue;
            if (value instanceof String || value instanceof Boolean || value instanceof Integer
                    || value instanceof Long || value instanceof Byte || value instanceof Short
                    || value instanceof Float || value instanceof Double
                    || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal
                    || value instanceof net.minecraft.resources.ResourceLocation) continue;
            throw new IllegalStateException("UNTRACKED_MUTABLE_COMPONENT_PAYLOAD");
        }
    }
}

package org.goldgom.chemicalelements.api.instance;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import org.goldgom.chemicalelements.api.tx.ChemistryTransaction;
public interface StackChemistry {
 StackLedger resolve(ItemStack stack,CreationContext context);
 Optional<StackLedger> snapshot(ItemStack stack);
 ItemStack split(ItemStack source,int count);
 ChemistryTransaction transfer(ItemStack source,ItemStack target,int count,TransferContext context);
 ChemistryTransaction consume(ItemStack source,Selection selection,ConsumptionReason reason);
 boolean canMerge(ItemStack a,ItemStack b);
}
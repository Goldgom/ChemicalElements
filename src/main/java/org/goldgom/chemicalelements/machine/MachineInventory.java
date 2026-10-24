package org.goldgom.chemicalelements.machine;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The actual machine inventory, not a shadow inventory. Projection publishes one prepared root. */
final class MachineInventory implements Container {
 private NonNullList<ItemStack> items=NonNullList.withSize(90,ItemStack.EMPTY);
 private final Runnable dirty;
 MachineInventory(Runnable dirty){this.dirty=dirty;}
 NonNullList<ItemStack> items(){return items;}
 void publish(NonNullList<ItemStack> prepared){items=prepared;}
 public int getContainerSize(){return items.size();}
 public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
 public ItemStack getItem(int i){return items.get(i);}
 public ItemStack removeItem(int i,int count){var result=ContainerHelper.removeItem(items,i,count);if(!result.isEmpty())setChanged();return result;}
 public ItemStack removeItemNoUpdate(int i){var result=ContainerHelper.takeItem(items,i);setChanged();return result;}
 public void setItem(int i,ItemStack stack){items.set(i,stack);setChanged();}
 public void setChanged(){dirty.run();}
 public boolean stillValid(Player player){return true;}
 public void clearContent(){items.clear();setChanged();}
}

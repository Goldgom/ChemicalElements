package org.goldgom.chemicalelements.machine;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
/** Only sealed process storage is packed, never a copied full inventory. Player inventory items drop separately. */
public final class PackedMachine {
 private PackedMachine(){}
 public static ItemStack pack(MachineBlockEntity machine){
  var item=machine.getBlockState().getBlock().asItem().getDefaultInstance();var root=new CompoundTag();root.putInt("schema",1);root.put("tank",machine.tank.save());machine.power.save(root);var custom=new CompoundTag();custom.put("chemicalelements_machine",root);item.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));return item;
 }
 public static void unpack(ItemStack stack,MachineBlockEntity machine){
  var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return;var root=data.copyTag();if(!root.contains("chemicalelements_machine"))return;var packed=root.getCompound("chemicalelements_machine");if(packed.getInt("schema")!=1)throw new IllegalStateException("Unsupported packed machine schema");machine.tank.load(packed.getCompound("tank"));machine.power.load(packed);machine.setChanged();
 }
}

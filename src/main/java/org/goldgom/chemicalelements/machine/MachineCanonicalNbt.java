package org.goldgom.chemicalelements.machine;

import java.util.TreeSet;
import net.minecraft.nbt.*;

/** Canonical SNBT: sorted compound keys, preserved list order and numeric/array tag types. */
public final class MachineCanonicalNbt {
 private MachineCanonicalNbt() {}
 public static String encode(Tag tag){
  if(tag instanceof CompoundTag compound){var out=new StringBuilder("{");boolean first=true;for(String key:new TreeSet<>(compound.getAllKeys())){if(!first)out.append(',');first=false;out.append(StringTag.quoteAndEscape(key)).append(':').append(encode(compound.get(key)));}return out.append('}').toString();}
  if(tag instanceof ListTag list){var out=new StringBuilder("[");for(int i=0;i<list.size();i++){if(i>0)out.append(',');out.append(encode(list.get(i)));}return out.append(']').toString();}
  return tag.toString();
 }
 public static CompoundTag decode(String canonical){
  try{var tag=TagParser.parseTag(canonical);if(!encode(tag).equals(canonical))throw new IllegalArgumentException("NONCANONICAL_TANK_NBT");return tag;}
  catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new IllegalArgumentException("INVALID_TANK_NBT",invalid);}
 }
}

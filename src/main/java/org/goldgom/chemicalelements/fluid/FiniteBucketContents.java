package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import org.goldgom.chemicalelements.instance.WaterStorePort;
import org.goldgom.chemicalelements.registry.ElementItems;

/** Exact liquid inventory; partial buckets cannot satisfy vanilla water-bucket recipes or use paths. */
public record FiniteBucketContents(String fluidId,FiniteVolume.Parcel parcel) {
    public static final String KEY="chemicalelements:finite_water";
    public FiniteBucketContents {
        Objects.requireNonNull(fluidId);Objects.requireNonNull(parcel);
        if(parcel.volume().signum()<0||parcel.volume().compareTo(WaterStorePort.BLOCK_CAPACITY)>0)throw new IllegalArgumentException("bucket capacity");
    }
    public static Optional<FiniteBucketContents> read(ItemStack stack){
        var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return Optional.empty();
        var root=data.copyTag();if(!root.contains(KEY))return Optional.empty();
        var tag=root.getCompound(KEY);var amounts=tag.getCompound("species");var species=new TreeMap<String,BigInteger>();
        for(String key:amounts.getAllKeys())species.put(key,new BigInteger(amounts.getString(key)));
        return Optional.of(new FiniteBucketContents(tag.contains("fluid")?tag.getString("fluid"):FiniteFluids.WATER,
                new FiniteVolume.Parcel(new BigInteger(tag.getString("volume")),species,new BigInteger(tag.getString("energy")))));
    }
    public ItemStack stack(){
        if(parcel.volume().signum()==0)return new ItemStack(Items.BUCKET);
        var full=FiniteFluids.resolve(fluidId).getBucket();
        var stack=new ItemStack(parcel.volume().equals(WaterStorePort.BLOCK_CAPACITY)?full:ElementItems.PARTIAL_FLUID_BUCKET.get());
        if(stack.isEmpty())throw new IllegalArgumentException("fluid has no bucket");
        var tag=new CompoundTag();tag.putString("fluid",fluidId);tag.putString("volume",parcel.volume().toString());tag.putString("energy",parcel.energy().toString());
        var species=new CompoundTag();parcel.species().forEach((id,n)->species.putString(id,n.toString()));tag.put("species",species);
        var root=new CompoundTag();root.put(KEY,tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));return stack;
    }
}

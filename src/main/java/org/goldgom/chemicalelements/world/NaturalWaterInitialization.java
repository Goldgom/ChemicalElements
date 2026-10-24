package org.goldgom.chemicalelements.world;

import org.goldgom.chemicalelements.fluid.FiniteVolume;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Pure initialization; caller must atomically persist marker and parcel in B's chunk store. */
public final class NaturalWaterInitialization {
    public record Context(long seed,String dimension,String biome,int y,String geology,long position,String profileHash) {}
    public record Marker(Context context,boolean initialized,FiniteVolume.Parcel remaining) {}
    public interface Profile { Map<String,BigInteger> sample(Context context,long stableSeed,BigInteger volume); }
    public static Marker initialize(Marker existing,Context generated,boolean provenNatural,BigInteger volume,BigInteger energy,Profile profile){
        if(existing!=null&&existing.initialized)return existing; // Includes exhausted tombstones; never refill.
        if(!provenNatural)throw new IllegalArgumentException("legacy/placed water must inherit inventory, not sample natural salts");
        return new Marker(generated,true,new FiniteVolume.Parcel(volume,profile.sample(generated,seed(generated),volume),energy));
    }
    public static long seed(Context context){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest((context.seed+"|"+context.dimension+"|"+context.biome+"|"+context.y+"|"+context.geology+"|"+context.position+"|"+context.profileHash).getBytes(StandardCharsets.UTF_8));return java.nio.ByteBuffer.wrap(hash).getLong();}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
}

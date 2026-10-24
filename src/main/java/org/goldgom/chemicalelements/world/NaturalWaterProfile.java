package org.goldgom.chemicalelements.world;
import java.math.*;
import java.util.*;
/** Default gameplay-calibrated profiles, NOT measured Minecraft geology. Called once only by
 * NaturalWaterInitialization with proven worldgen origin; existing/tombstoned cells never sample again.
 * Volume and species both use 1e-18 units (litres vs mol); neutral formula units are dissociated exactly. */
public final class NaturalWaterProfile implements NaturalWaterInitialization.Profile {
 public static final String VERSION="natural-water-game-calibration-v1";
 @Override public Map<String,BigInteger> sample(NaturalWaterInitialization.Context c,long seed,BigInteger volume){
  if(volume.signum()<0)throw new IllegalArgumentException("negative water volume");
  if(!c.profileHash().equals(VERSION))throw new IllegalArgumentException("Unknown natural profile version; preserve source for migration");
  boolean ocean=c.biome().contains("ocean");
  // Unknown dimensions default to fresh water, never inferred seawater from position alone.
  BigDecimal salt=new BigDecimal(c.dimension().equals("minecraft:overworld")&&ocean?"0.47":"0.0001");
  int variation=900+Math.floorMod(seed,201);
  salt=salt.multiply(BigDecimal.valueOf(variation)).divide(new BigDecimal("1000"));
  if(c.y()<0)salt=salt.multiply(new BigDecimal("1.25"));
  BigInteger chloride=new BigDecimal(volume).multiply(salt).toBigInteger();
  TreeMap<String,BigInteger> out=new TreeMap<>();
  out.put("chemicalelements:sodium_ion",chloride);out.put("chemicalelements:chloride_ion",chloride);
  if(c.geology().contains("calcite")||c.geology().contains("limestone")){
   BigInteger carbonate=new BigDecimal(volume).multiply(new BigDecimal("0.00001")).toBigInteger();
   out.put("chemicalelements:calcium_ion",carbonate);out.put("chemicalelements:carbonate_ion",carbonate);
  }
  out.values().removeIf(n->n.signum()==0);return Collections.unmodifiableMap(out);
 }
}

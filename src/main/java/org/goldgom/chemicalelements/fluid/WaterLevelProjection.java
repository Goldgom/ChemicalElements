package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;

/** Ten visible volume bands. Vanilla levels are only the compatibility fluid identity. */
public final class WaterLevelProjection {
    private WaterLevelProjection() {}
    public static int height(BigInteger volume,BigInteger capacity){
        if(volume.signum()<0||capacity.signum()<=0||volume.compareTo(capacity)>0)throw new IllegalArgumentException("invalid water projection");
        if(volume.signum()==0)return 0;
        return volume.multiply(BigInteger.TEN).add(capacity).subtract(BigInteger.ONE).divide(capacity).intValueExact();
    }
    public static int vanillaLevel(int height){
        return vanillaLevel(height,height==10);
    }
    public static int vanillaLevel(int height,boolean full){
        if(height<1||height>10)throw new IllegalArgumentException("invalid water height");
        if(full)return 0;
        return 8-(height*7+9)/10;
    }
}

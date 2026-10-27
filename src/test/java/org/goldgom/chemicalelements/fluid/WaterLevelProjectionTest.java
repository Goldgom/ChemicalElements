package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterLevelProjectionTest {
    @Test void volumeMapsToTenBandsAndOnlyExactFullVolumeIsASource(){
        int previous=0;var capacity=BigInteger.valueOf(1000);
        for(int volume=0;volume<=1000;volume++){
            int height=WaterLevelProjection.height(BigInteger.valueOf(volume),capacity);assertTrue(height>=previous);previous=height;
            if(volume==0){assertEquals(0,height);continue;}
            assertEquals((volume+99)/100,height);
            int level=WaterLevelProjection.vanillaLevel(height,volume==1000);
            if(volume==1000)assertEquals(0,level);else assertTrue(level>=1&&level<=7,"partial volume must remain flowing water");
        }
        for(int height=1;height<=10;height++)assertTrue(WaterLevelProjection.vanillaLevel(height,false)>=1);
        assertEquals(10,WaterLevelProjection.height(BigInteger.valueOf(901),capacity));
        assertEquals(1,WaterLevelProjection.vanillaLevel(10,false));
    }
    @Test void invalidProjectionCannotCreateWater(){
        assertThrows(IllegalArgumentException.class,()->WaterLevelProjection.height(BigInteger.ONE,BigInteger.ZERO));
        assertThrows(IllegalArgumentException.class,()->WaterLevelProjection.height(BigInteger.TWO,BigInteger.ONE));
        assertThrows(IllegalArgumentException.class,()->WaterLevelProjection.vanillaLevel(0));
    }
}

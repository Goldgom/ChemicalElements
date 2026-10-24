package org.goldgom.chemicalelements.api.chem;
import net.minecraft.resources.ResourceLocation;
import java.math.BigInteger;
import java.util.*;
/** Atom-mol quanta and signed elementary-charge-mol quanta. */
public record ElementInventory(Map<ResourceLocation,BigInteger> atoms,BigInteger charge) {
 public ElementInventory { var m=new TreeMap<ResourceLocation,BigInteger>(); atoms.forEach((k,v)->{if(v.signum()<0)throw new IllegalArgumentException("negative atoms"); if(v.signum()!=0)m.put(k,v);}); atoms=Collections.unmodifiableMap(m); Objects.requireNonNull(charge); }
}
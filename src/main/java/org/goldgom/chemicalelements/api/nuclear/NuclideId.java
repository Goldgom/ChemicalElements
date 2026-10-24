package org.goldgom.chemicalelements.api.nuclear;
public record NuclideId(int z,int a,int isomer) implements Comparable<NuclideId> {
 public NuclideId {if(z<0||z>118||a<1||a<z||isomer<0)throw new IllegalArgumentException("invalid nuclide");}
 public int compareTo(NuclideId o){int c=Integer.compare(z,o.z);if(c==0)c=Integer.compare(a,o.a);if(c==0)c=Integer.compare(isomer,o.isomer);return c;}
}
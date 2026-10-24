package org.goldgom.chemicalelements.api.chem;
import java.math.*;
import java.util.Objects;
/** Nonnegative fixed point amount. Scale is a persistent protocol invariant. */
public record MolAmount(BigInteger quanta) implements Comparable<MolAmount> {
 public static final int SCALE=18;
 public static final MolAmount ZERO=new MolAmount(BigInteger.ZERO);
 public static final MolAmount ONE=parseDecimal("1");
 public MolAmount { Objects.requireNonNull(quanta); if(quanta.signum()<0) throw new IllegalArgumentException("negative mol"); }
 public static MolAmount parseDecimal(String s) { return new MolAmount(new BigDecimal(s).movePointRight(SCALE).toBigIntegerExact()); }
 public static MolAmount readBounded(String s) { if(s.length()>80) throw new IllegalArgumentException("amount encoding too long"); var decimal=new BigDecimal(s); if(decimal.signum()<0||decimal.scale()>SCALE||(long)decimal.precision()-decimal.scale()>21)throw new IllegalArgumentException("amount exceeds input precision/magnitude"); var a=new MolAmount(decimal.movePointRight(SCALE).toBigIntegerExact()); if(a.quanta.bitLength()>128) throw new IllegalArgumentException("amount exceeds input limit"); return a; }
 public MolAmount add(MolAmount b){return new MolAmount(quanta.add(b.quanta));}
 public MolAmount subtract(MolAmount b){return new MolAmount(quanta.subtract(b.quanta));}
 public MolAmount multiply(BigInteger n){return new MolAmount(quanta.multiply(n));}
 public boolean isZero(){return quanta.signum()==0;}
 public BigDecimal decimal(){return new BigDecimal(quanta,SCALE);}
 public int compareTo(MolAmount b){return quanta.compareTo(b.quanta);}
 public String toString(){return decimal().toPlainString();}
}
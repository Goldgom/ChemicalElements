package org.goldgom.chemicalelements.fluid.waterbody;

import java.math.*;
import java.util.Objects;

/** SI volume, never a balance. Canonical scale-18 m3 plus 0..999 thousandths of that quantum.
 * The residual preserves every old scale-18 litre quantum (1e-21 m3). */
public record WaterVolume(BigInteger cubicMetreQuanta, int residual) implements Comparable<WaterVolume> {
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);
    public static final BigInteger SUBQUANTA_PER_M3 = BigInteger.TEN.pow(21);
    public static final WaterVolume ZERO = ofSubquanta(BigInteger.ZERO);
    public static final WaterVolume BUCKET = ofCubicMetres(BigDecimal.ONE);
    public WaterVolume {
        Objects.requireNonNull(cubicMetreQuanta);
        if (cubicMetreQuanta.signum() < 0 || residual < 0 || residual >= 1000)
            throw new IllegalArgumentException("noncanonical or negative volume");
    }
    public static WaterVolume ofSubquanta(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("negative volume");
        var qr = value.divideAndRemainder(THOUSAND);
        return new WaterVolume(qr[0], qr[1].intValueExact());
    }
    public static WaterVolume ofLegacyLitreQuanta(BigInteger value) { return ofSubquanta(value); }
    public static WaterVolume ofCubicMetres(BigDecimal value) { return ofSubquanta(value.movePointRight(21).toBigIntegerExact()); }
    public static WaterVolume ofLitres(BigDecimal value) { return ofCubicMetres(value.movePointLeft(3)); }
    public BigInteger subquanta() { return cubicMetreQuanta.multiply(THOUSAND).add(BigInteger.valueOf(residual)); }
    public BigInteger legacyLitreQuanta() { return subquanta(); }
    public BigDecimal cubicMetres() { return new BigDecimal(subquanta(), 21); }
    public BigDecimal litres() { return cubicMetres().movePointRight(3); }
    public WaterVolume plus(WaterVolume other) { return ofSubquanta(subquanta().add(other.subquanta())); }
    public WaterVolume minus(WaterVolume other) { return ofSubquanta(subquanta().subtract(other.subquanta())); }
    public WaterVolume min(WaterVolume other) { return compareTo(other) <= 0 ? this : other; }
    @Override public int compareTo(WaterVolume other) { return subquanta().compareTo(other.subquanta()); }
}

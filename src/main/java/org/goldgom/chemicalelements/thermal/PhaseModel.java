package org.goldgom.chemicalelements.thermal;

import java.math.BigDecimal;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Immutable, validated by PhaseLibrary. No registry, inventory or world ownership. */
public record PhaseModel(ResourceLocation id, ResourceLocation subject, String allotrope,
        Kind kind, String reason, Domain domain, String energyReference,
        ResourceLocation lowSpecies, ResourceLocation highSpecies, BigDecimal transitionK,
        BigDecimal cpLow, BigDecimal cpHigh, BigDecimal latentJMol,
        String legacyOperation, List<Source> sources) {
    public enum Kind { UNKNOWN, UNMODELED, LEGACY_WATER_LATENT, CONSTANT_CP_TWO_PHASE }
    public record Domain(BigDecimal minK, BigDecimal maxK, BigDecimal minPa, BigDecimal maxPa) {
        public Domain {
            if (minK.signum() <= 0 || minK.compareTo(maxK) > 0 || minPa.signum() <= 0
                    || minPa.compareTo(maxPa) > 0) throw new IllegalArgumentException("Invalid T/P domain");
        }
        public boolean contains(BigDecimal k, BigDecimal pa) {
            return k != null && pa != null && k.compareTo(minK) >= 0 && k.compareTo(maxK) <= 0
                    && pa.compareTo(minPa) >= 0 && pa.compareTo(maxPa) <= 0;
        }
    }
    public record Source(String id, String uri, String citation, String locator, String accessed,
            String basis, String method, String pressureEvidence, String uncertainty,
            String licenseStatus, String units, String limitations) {}
    public PhaseModel { sources = List.copyOf(sources); }
    public boolean hasEnthalpy() { return kind == Kind.CONSTANT_CP_TWO_PHASE; }
    /** Zero is low phase at domain.minK, per mole of this chemical identity. Not legacy AMBIENT. */
    public BigDecimal molarEnthalpy(boolean high, BigDecimal k) {
        if (!hasEnthalpy()) throw new IllegalStateException("No complete enthalpy model");
        return high ? cpLow.multiply(transitionK.subtract(domain.minK())).add(latentJMol)
                .add(cpHigh.multiply(k.subtract(transitionK))) : cpLow.multiply(k.subtract(domain.minK()));
    }
}

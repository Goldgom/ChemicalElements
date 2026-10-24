package org.goldgom.chemicalelements.world.heat;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.data.*;
import org.goldgom.chemicalelements.instance.TransactionEncoding;

/** Detached root evidence, never another heat inventory. Stored under finite_heat in canonicalRoot. */
public record FiniteHeatEvidence(ResourceLocation model, String modelHash, String reference,
        long epoch, String catalogHash, BigDecimal pressurePa, BigDecimal joulesPerKelvin,
        BigDecimal maxEnergyJ, BigDecimal maxMol, BigDecimal occupiedVolumeM3,
        BigDecimal maxVolumeM3, List<InstanceRecord> instances) {
    public FiniteHeatEvidence {
        Objects.requireNonNull(model); Objects.requireNonNull(modelHash); Objects.requireNonNull(reference);
        Objects.requireNonNull(catalogHash); Objects.requireNonNull(pressurePa);
        Objects.requireNonNull(joulesPerKelvin); Objects.requireNonNull(maxEnergyJ);
        Objects.requireNonNull(maxMol); Objects.requireNonNull(occupiedVolumeM3); Objects.requireNonNull(maxVolumeM3);
        instances = List.copyOf(instances);
        if (epoch < 0 || reference.isBlank() || !modelHash.matches("[0-9a-f]{64}")
                || !catalogHash.matches("[0-9a-f]{64}") || pressurePa.signum() <= 0
                || joulesPerKelvin.signum() <= 0 || maxEnergyJ.signum() < 0 || maxMol.signum() < 0
                || occupiedVolumeM3.signum() < 0 || maxVolumeM3.signum() < 0 || instances.isEmpty())
            throw new IllegalArgumentException("invalid finite heat evidence");
    }
    public String canonical() {
        var j = new JsonObject(); j.addProperty("model", model.toString()); j.addProperty("model_hash", modelHash);
        j.addProperty("reference", reference); j.addProperty("epoch", epoch); j.addProperty("catalog_hash", catalogHash);
        j.addProperty("pressure_pa", pressurePa.toPlainString()); j.addProperty("capacity_j_k", joulesPerKelvin.toPlainString());
        j.addProperty("max_energy_j", maxEnergyJ.toPlainString()); j.addProperty("max_mol", maxMol.toPlainString());
        j.addProperty("occupied_volume_m3", occupiedVolumeM3.toPlainString()); j.addProperty("max_volume_m3", maxVolumeM3.toPlainString());
        j.add("instances", LedgerJson.encode(new StackLedger(StackLedger.CURRENT_SCHEMA, 0, instances)));
        return TransactionEncoding.canonical(j);
    }
    public static FiniteHeatEvidence fromRoot(String canonicalRoot) {
        var j = JsonParser.parseString(canonicalRoot).getAsJsonObject().getAsJsonObject("finite_heat");
        if (j == null) throw new IllegalArgumentException("missing finite_heat evidence");
        var result = new FiniteHeatEvidence(ResourceLocation.parse(j.get("model").getAsString()),
                j.get("model_hash").getAsString(), j.get("reference").getAsString(), j.get("epoch").getAsLong(),
                j.get("catalog_hash").getAsString(), decimal(j,"pressure_pa"), decimal(j,"capacity_j_k"),
                decimal(j,"max_energy_j"), decimal(j,"max_mol"), decimal(j,"occupied_volume_m3"), decimal(j,"max_volume_m3"),
                LedgerJson.decode(j.getAsJsonObject("instances")).instances());
        if (!result.canonical().equals(TransactionEncoding.canonical(j))) throw new IllegalArgumentException("noncanonical heat evidence");
        return result;
    }
    private static BigDecimal decimal(JsonObject j, String key) { return new BigDecimal(j.get(key).getAsString()); }
    public static String modelHash(org.goldgom.chemicalelements.thermal.PhaseModel model) {
        // Include provenance, domain, reference and coefficients, not merely model ID.
        return TransactionEncoding.sha256(TransactionEncoding.canonical(new Gson().toJsonTree(model)));
    }
}

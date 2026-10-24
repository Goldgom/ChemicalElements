package org.goldgom.chemicalelements.world.heat;

import java.math.BigDecimal;
import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.instance.TransactionEncoding;

/** Main-style boundary tests; test fixture values are NOT production thermodynamic data. */
public final class FiniteHeatEvidenceTest {
    public static void main(String[] args) {
        var instance = new InstanceRecord(UUID.randomUUID(), Composition.EMPTY,
                new Provenance("test", "heat-evidence-only", 0L, 1L, Map.of()), 0);
        var evidence = new FiniteHeatEvidence(ResourceLocation.parse("chemicalelements:test_only"),
                "0".repeat(64), "test-reference", 1L, "1".repeat(64), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, List.of(instance));
        var root = new JsonObject(); root.add("finite_heat", JsonParser.parseString(evidence.canonical()));
        var roundtrip = FiniteHeatEvidence.fromRoot(TransactionEncoding.canonical(root));
        require(roundtrip.equals(evidence), "Full instance FIFO roundtrip");
        require(roundtrip.instances().getFirst().equals(instance), "All thermal/nuclide/pore/adsorbed fields survive");
        root.getAsJsonObject("finite_heat").addProperty("unrecognized", true);
        rejects(() -> FiniteHeatEvidence.fromRoot(TransactionEncoding.canonical(root)));
        rejects(() -> FiniteHeatEvidence.fromRoot("{}"));
        rejects(() -> new FiniteHeatEvidence(evidence.model(), evidence.modelHash(), evidence.reference(), 1,
                evidence.catalogHash(), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ONE, List.of(instance)));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable run) {
        try { run.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid evidence accepted");
    }
}

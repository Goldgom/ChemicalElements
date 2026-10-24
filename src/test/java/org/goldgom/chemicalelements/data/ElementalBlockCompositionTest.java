package org.goldgom.chemicalelements.data;

import com.google.gson.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;

/** Standalone assertion main, matching CoreVerification; no JUnit dependency required.
 * Integration owner runs this main with the test runtime classpath from project root.
 * This tests real on-disk data and CatalogParser; live ItemStack sampling is in GameTest.
 */
public final class ElementalBlockCompositionTest {
    private static int checks;
    private static ResourceLocation id(String path) { return ResourceLocation.parse("chemicalelements:" + path); }
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        checks++;
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/resources/data/chemicalelements/chemicalelements");
        var files = new TreeMap<ResourceLocation, JsonElement>();
        for (String section : List.of("elements", "species", "item_compositions")) {
            try (var paths = Files.list(root.resolve(section))) {
                for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String name = path.getFileName().toString();
                    files.put(id(section + "/" + name.substring(0, name.length() - 5)), JsonParser.parseString(Files.readString(path)));
                }
            }
        }
        var snapshot = CatalogParser.parse(23, files);
        check(snapshot.elements().size() == 118, "118 elements");
        var mapped = files.keySet().stream().filter(k -> k.getPath().startsWith("item_compositions/elemental_")).toList();
        check(mapped.size() == 118, "118 distinct mapping resources");
        for (var element : snapshot.elements().keySet()) {
            String name = element.getPath();
            var key = id("item_compositions/elemental_" + name);
            var doc = files.get(key).getAsJsonObject();
            check(doc.get("schema_version").getAsInt() == 1 && doc.get("basis").getAsString().equals("game_convention"), "schema and basis " + name);
            check(doc.getAsJsonObject("match").getAsJsonArray("items").size() == 1, "single item match");
            check(doc.getAsJsonObject("match").getAsJsonArray("items").get(0).getAsString().equals(id("elemental_" + name).toString()), "exact item ID");
            boolean diatomic = name.equals("hydrogen") || name.equals("oxygen");
            var species = id(diatomic ? name + "_gas" : name);
            var amount = MolAmount.readBounded(diatomic ? "0.5" : "1");
            var expected = new Composition(Map.of(species, amount));
            var actual = snapshot.itemCompositions().get(id("elemental_" + name));
            check(expected.equals(actual) && !actual.equals(Composition.EMPTY), "actual parsed composition " + name);
            check(snapshot.requireSpecies(species).atoms().equals(Map.of(element, diatomic ? 2 : 1)), "unchanged stoichiometry " + name);
            check(actual.inventory(snapshot).atoms().equals(Map.of(element, MolAmount.ONE.quanta())), "one atom-mol " + name);
            check(actual.inventory(snapshot).charge().equals(BigInteger.ZERO), "neutral " + name);
            check(actual.multiply(BigInteger.valueOf(64)).inventory(snapshot).atoms()
                    .equals(Map.of(element, MolAmount.ONE.quanta().multiply(BigInteger.valueOf(64)))), "64 stack total " + name);
            check(actual.mass(snapshot).signum() > 0, "positive mass from existing species " + name);
        }
        for (String form : List.of("solid", "liquid", "gas", "unresolved"))
            check(!snapshot.itemCompositions().containsKey(id("mixture_carrier_" + form)), "generic carrier stays unmapped: " + form);
        for (String invalid : List.of("unknown_species", "bad_schema", "precision", "negative", "conflict")) {
            var broken = new TreeMap<>(files);
            var key = id("item_compositions/elemental_hydrogen");
            var doc = broken.get(key).deepCopy().getAsJsonObject();
            switch (invalid) {
                case "unknown_species" -> { var per = new JsonObject(); per.addProperty("chemicalelements:missing_r23", "1"); doc.add("per_instance", per); }
                case "bad_schema" -> doc.addProperty("schema_version", 2);
                case "precision" -> doc.getAsJsonObject("per_instance").addProperty("chemicalelements:hydrogen_gas", "0.0000000000000000001");
                case "negative" -> doc.getAsJsonObject("per_instance").addProperty("chemicalelements:hydrogen_gas", "-0.5");
                case "conflict" -> broken.put(id("item_compositions/duplicate_r23"), doc.deepCopy());
                default -> throw new AssertionError(invalid);
            }
            broken.put(key, doc);
            rejects(() -> CatalogParser.parse(24, broken), invalid);
        }
        System.out.println("ElementalBlockCompositionTest PASS checks=" + checks + "; elements=118; live sampling is separate GameTest");
    }
}

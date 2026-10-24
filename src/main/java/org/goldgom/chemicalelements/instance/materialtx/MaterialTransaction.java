package org.goldgom.chemicalelements.instance.materialtx;

import com.google.gson.*;
import java.util.*;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.instance.TransactionEncoding;
import net.minecraft.resources.ResourceLocation;

/** New journal-kind payload; deliberately NOT a reinterpretation of MultiOwnerRequest schema3.
 * Structural encoding only: authenticated planning/conservation is required before PREPARE. */
public record MaterialTransaction(UUID operation, Action action, long bundleEpoch, String bundleHash,
                                  String thermalReference, List<Change> changes) {
    public enum Action { PLACE, PICKUP, PHASE }
    public record Change(MaterialOwnerSnapshot before, MaterialOwnerSnapshot after) {
        public Change {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (!before.owner().equals(after.owner()) || !before.liveGeneration().equals(after.liveGeneration())
                    || before.revision() == Long.MAX_VALUE || after.revision() != before.revision() + 1)
                throw new IllegalArgumentException("material owner lineage");
        }
    }
    public MaterialTransaction {
        Objects.requireNonNull(operation); Objects.requireNonNull(action); Objects.requireNonNull(thermalReference);
        if (bundleEpoch < 0 || bundleHash == null || !bundleHash.matches("[0-9a-f]{64}")
                || thermalReference.isBlank() || thermalReference.length() > 4096)
            throw new IllegalArgumentException("material bundle reference");
        var ordered = new TreeMap<String, Change>();
        for (var change : changes) {
            if (ordered.putIfAbsent(TransactionEncoding.canonical(ownerTree(change.before.owner())), change) != null)
                throw new IllegalArgumentException("duplicate material owner");
        }
        int minimum = action == Action.PHASE ? 1 : 2;
        if (ordered.size() < minimum || ordered.size() > 4) throw new IllegalArgumentException("material owner budget");
        changes = List.copyOf(ordered.values());
    }
    public String encode() {
        var root = new JsonObject(); root.addProperty("version", 1); root.addProperty("operation", operation.toString());
        root.addProperty("action", action.name()); root.addProperty("bundle_epoch", bundleEpoch);
        root.addProperty("bundle_hash", bundleHash); root.addProperty("thermal_reference", thermalReference);
        var owners = new JsonArray();
        for (var change : changes) {
            var entry = new JsonObject(); entry.add("owner", ownerTree(change.before.owner()));
            entry.addProperty("live_generation", change.before.liveGeneration().toString());
            entry.add("before", image(change.before)); entry.add("after", image(change.after)); owners.add(entry);
        }
        root.add("owners", owners); return TransactionEncoding.canonical(root);
    }
    public String hash() { return TransactionEncoding.sha256(encode()); }
    private static JsonObject image(MaterialOwnerSnapshot snapshot) {
        var out = new JsonObject(); out.addProperty("revision", snapshot.revision());
        out.addProperty("root", snapshot.canonicalRoot()); out.addProperty("root_hash", snapshot.rootHash()); return out;
    }
    public static MaterialTransaction decode(String encoded) {
        if (encoded.length() > 32 * 1024 * 1024) throw new IllegalArgumentException("material payload budget");
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        if (number(root, "version") != 1) throw new IllegalArgumentException("material payload version");
        var changes = new ArrayList<Change>();
        var entries = root.getAsJsonArray("owners");
        if (entries.isEmpty() || entries.size() > 4) throw new IllegalArgumentException("material owner budget");
        for (var value : entries) {
            var entry = value.getAsJsonObject(); var owner = readOwner(entry.getAsJsonObject("owner"));
            UUID generation = UUID.fromString(text(entry, "live_generation"));
            changes.add(new Change(readImage(owner, generation, entry.getAsJsonObject("before")),
                    readImage(owner, generation, entry.getAsJsonObject("after"))));
        }
        var out = new MaterialTransaction(UUID.fromString(text(root, "operation")), Action.valueOf(text(root, "action")),
                number(root, "bundle_epoch"), text(root, "bundle_hash"), text(root, "thermal_reference"), changes);
        if (!out.encode().equals(encoded)) throw new IllegalArgumentException("noncanonical material payload");
        return out;
    }
    private static MaterialOwnerSnapshot readImage(OwnerKey owner, UUID generation, JsonObject image) {
        return new MaterialOwnerSnapshot(owner, generation, number(image, "revision"), text(image, "root"), text(image, "root_hash"));
    }
    static JsonObject ownerTree(OwnerKey owner) {
        var out = new JsonObject();
        if (owner instanceof OwnerKey.PlayerInventory player) {
            out.addProperty("kind", "player"); out.addProperty("player", player.player().toString());
        } else if (owner instanceof OwnerKey.WorldBlock block) {
            out.addProperty("kind", "world_block"); out.addProperty("dimension", block.dimension().toString());
            out.addProperty("position", block.blockPos()); out.addProperty("incarnation", block.incarnation().toString());
        } else if (owner instanceof OwnerKey.FiniteHeatSource heat) {
            out.addProperty("kind", "finite_heat"); out.addProperty("dimension", heat.dimension().toString());
            out.addProperty("position", heat.blockPos()); out.addProperty("incarnation", heat.incarnation().toString());
            out.addProperty("port", heat.port().toString());
        } else if (owner instanceof OwnerKey.MachineInventory machine) {
            out.addProperty("kind", "machine"); out.addProperty("dimension", machine.dimension().toString());
            out.addProperty("position", machine.controllerPos()); out.addProperty("incarnation", machine.incarnation().toString());
            out.addProperty("type", machine.machineType().toString());
        } else throw new IllegalArgumentException("unsupported owner");
        return out;
    }
    private static OwnerKey readOwner(JsonObject owner) {
        return switch (text(owner, "kind")) {
            case "player" -> new OwnerKey.PlayerInventory(UUID.fromString(text(owner, "player")));
            case "world_block" -> new OwnerKey.WorldBlock(ResourceLocation.parse(text(owner, "dimension")),
                    position(owner), UUID.fromString(text(owner, "incarnation")));
            case "finite_heat" -> new OwnerKey.FiniteHeatSource(ResourceLocation.parse(text(owner, "dimension")),
                    position(owner), UUID.fromString(text(owner, "incarnation")), ResourceLocation.parse(text(owner, "port")));
            case "machine" -> new OwnerKey.MachineInventory(ResourceLocation.parse(text(owner, "dimension")),
                    position(owner), UUID.fromString(text(owner, "incarnation")), ResourceLocation.parse(text(owner, "type")));
            default -> throw new IllegalArgumentException("unsupported owner kind");
        };
    }
    private static long position(JsonObject obj) {
        var value = obj.get("position").getAsJsonPrimitive();
        if (!value.isNumber() || !value.getAsString().matches("0|-?[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("signed block position required");
        return new java.math.BigInteger(value.getAsString()).longValueExact();
    }
    private static String text(JsonObject obj, String key) {
        var value = obj.get(key).getAsJsonPrimitive();
        if (!value.isString()) throw new IllegalArgumentException("string required: " + key);
        return value.getAsString();
    }
    private static long number(JsonObject obj, String key) {
        var value = obj.get(key).getAsJsonPrimitive();
        if (!value.isNumber() || !value.getAsString().matches("0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("nonnegative integer required: " + key);
        return new java.math.BigInteger(value.getAsString()).longValueExact();
    }
}

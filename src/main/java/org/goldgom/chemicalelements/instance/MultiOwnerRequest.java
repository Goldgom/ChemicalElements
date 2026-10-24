package org.goldgom.chemicalelements.instance;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import org.goldgom.chemicalelements.api.tx.*;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.machine.MachineTransactions.*;

/** Schema 3 intent value. Not authority, a reservation, or a second inventory.
 * Live adapters must independently resolve every owner and validate its generation and mutation
 * revision before this payload may enter the single SinkLedger operation UUID namespace.
 * Existing schema 1/2 encodings are deliberately not changed or converted in place. */
public record MultiOwnerRequest(UUID operation, MenuAuthority authority, CatalogVersion catalog,
        ReactionProposal proposal, List<Change> owners) {
    public record Change(OwnerKey owner, UUID generation, Mirror before, Mirror after) {
        public Change {
            Objects.requireNonNull(owner); Objects.requireNonNull(generation);
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (before.revision() == Long.MAX_VALUE || after.revision() != before.revision() + 1)
                throw new IllegalArgumentException("owner revision must advance exactly once");
            var a = new TreeSet<Integer>(); var b = new TreeSet<Integer>();
            before.slots().forEach(s -> a.add(s.index())); after.slots().forEach(s -> b.add(s.index()));
            if (!a.equals(b)) throw new IllegalArgumentException("owner slot layout changed");
            var x = new TreeMap<String, UUID>(); var y = new TreeMap<String, UUID>();
            before.tanks().forEach(t -> x.put(t.port(), t.identity()));
            after.tanks().forEach(t -> y.put(t.port(), t.identity()));
            if (!x.equals(y)) throw new IllegalArgumentException("owner port identity changed");
            if (owner instanceof OwnerKey.PlayerInventory && (!before.tanks().isEmpty() || !after.tanks().isEmpty()))
                throw new IllegalArgumentException("player cannot own machine tanks");
        }
    }
    public MultiOwnerRequest {
        Objects.requireNonNull(operation); Objects.requireNonNull(authority);
        Objects.requireNonNull(catalog); Objects.requireNonNull(proposal);
        if (catalog.epoch() != proposal.registryEpoch()) throw new IllegalArgumentException("catalog mismatch");
        if (owners.isEmpty() || owners.size() > 64) throw new IllegalArgumentException("owner count budget");
        var sorted = new TreeMap<String, Change>();
        var beforeIds = new HashSet<UUID>(); var afterIds = new HashSet<UUID>();
        for (var change : List.copyOf(owners)) {
            if (change.owner() instanceof OwnerKey.PlayerInventory p && !p.player().equals(authority.player()))
                throw new IllegalArgumentException("foreign player owner");
            String key = TransactionEncoding.canonical(ownerTree(change.owner()));
            if (sorted.putIfAbsent(key, change) != null) throw new IllegalArgumentException("duplicate owner");
            unique(change.before(), beforeIds); unique(change.after(), afterIds);
        }
        owners = List.copyOf(sorted.values());
    }
    private static void unique(Mirror image, Set<UUID> ids) {
        image.slots().forEach(s -> s.instances().forEach(i -> add(ids, i.id())));
        image.tanks().forEach(t -> t.parcels().forEach(i -> add(ids, i.id())));
    }
    private static void add(Set<UUID> ids, UUID id) {
        if (!ids.add(id)) throw new IllegalArgumentException("matter identity shared by owners");
    }
    public String encode(HolderLookup.Provider registries) {
        var root = new JsonObject(); root.addProperty("schema", 3);
        root.addProperty("operation", operation.toString());
        var auth = new JsonObject(); auth.addProperty("player", authority.player().toString());
        auth.addProperty("session", authority.session().toString());
        auth.addProperty("menu_type", authority.menuType().toString());
        auth.addProperty("layout_version", authority.layoutVersion()); root.add("authority", auth);
        root.addProperty("catalog_epoch", catalog.epoch()); root.addProperty("catalog_sha256", catalog.sha256());
        root.add("proposal", JsonParser.parseString(TransactionEncoding.proposal(proposal)));
        var entries = new JsonArray();
        for (var change : owners) {
            var entry = new JsonObject(); entry.add("owner", ownerTree(change.owner()));
            entry.addProperty("generation", change.generation().toString());
            String before = TransactionEncoding.mirror(change.before(), registries);
            String after = TransactionEncoding.mirror(change.after(), registries);
            entry.add("before", JsonParser.parseString(before)); entry.add("after", JsonParser.parseString(after));
            entry.addProperty("before_sha256", TransactionEncoding.sha256(before));
            entry.addProperty("after_sha256", TransactionEncoding.sha256(after)); entries.add(entry);
        }
        root.add("owners", entries); return TransactionEncoding.canonical(root);
    }
    public String payloadSha256(HolderLookup.Provider registries) { return TransactionEncoding.sha256(encode(registries)); }
    /** Recovery decoder, not authorization. Exact round-trip preserves the original hash contract. */
    public static MultiOwnerRequest decode(String encoded, HolderLookup.Provider registries) {
        Objects.requireNonNull(encoded); Objects.requireNonNull(registries);
        if (encoded.length() > 16 * 1024 * 1024) throw new IllegalArgumentException("request encoding budget");
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        if (integer(root, "schema", false) != 3) throw new IllegalArgumentException("not schema3");
        var a = root.getAsJsonObject("authority");
        var auth = new MenuAuthority(uuid(a, "player"), uuid(a, "session"),
                location(a, "menu_type"), Math.toIntExact(integer(a, "layout_version", false)));
        var catalog = new CatalogVersion(integer(root, "catalog_epoch", false), text(root, "catalog_sha256"));
        var p = root.getAsJsonObject("proposal");
        var diagnostics = new ArrayList<String>();
        for (var value : p.getAsJsonArray("diagnostics")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("invalid diagnostic");
            diagnostics.add(value.getAsString());
        }
        var proposal = new ReactionProposal(location(p, "rule"), composition(p.getAsJsonObject("consumed")),
                composition(p.getAsJsonObject("products")), composition(p.getAsJsonObject("byproducts")),
                amount(text(p, "extent")), decimal(text(p, "energy_j")),
                decimal(text(p, "duration_seconds")), integer(p, "epoch", false),
                text(p, "basis"), diagnostics);
        var entries = root.getAsJsonArray("owners");
        if (entries.isEmpty() || entries.size() > 64) throw new IllegalArgumentException("owner count budget");
        var changes = new ArrayList<Change>();
        for (var value : entries) {
            var entry = value.getAsJsonObject(); var owner = entry.getAsJsonObject("owner");
            OwnerKey key = switch (text(owner, "kind")) {
                case "player" -> new OwnerKey.PlayerInventory(uuid(owner, "player"));
                case "machine" -> new OwnerKey.MachineInventory(location(owner, "dimension"),
                        integer(owner, "position", true), uuid(owner, "incarnation"), location(owner, "type"));
                default -> throw new IllegalArgumentException("unsupported owner kind");
            };
            String before = TransactionEncoding.canonical(entry.get("before"));
            String after = TransactionEncoding.canonical(entry.get("after"));
            if (!TransactionEncoding.sha256(before).equals(text(entry, "before_sha256"))
                    || !TransactionEncoding.sha256(after).equals(text(entry, "after_sha256")))
                throw new IllegalArgumentException("owner mirror digest mismatch");
            changes.add(new Change(key, uuid(entry, "generation"),
                    TransactionEncoding.decodeMirror(before, registries), TransactionEncoding.decodeMirror(after, registries)));
        }
        var result = new MultiOwnerRequest(uuid(root, "operation"), auth, catalog, proposal, changes);
        // Compare the original text, not a reparsed tree: rejects duplicate keys, unknown fields,
        // whitespace, alternate UUID/number spellings, defaults, ordering and lossy ItemStack codecs.
        if (!result.encode(registries).equals(encoded)) throw new IllegalArgumentException("noncanonical schema3 request");
        return result;
    }
    private static String text(JsonObject object, String key) {
        var p = object.get(key).getAsJsonPrimitive();
        if (!p.isString()) throw new IllegalArgumentException("expected string: " + key);
        return p.getAsString();
    }
    private static UUID uuid(JsonObject object, String key) { return UUID.fromString(text(object, key)); }
    private static net.minecraft.resources.ResourceLocation location(JsonObject object, String key) {
        return net.minecraft.resources.ResourceLocation.parse(text(object, key));
    }
    private static long integer(JsonObject object, String key, boolean signed) {
        var p = object.get(key).getAsJsonPrimitive();
        if (!p.isNumber() || !p.getAsString().matches(signed ? "0|-?[1-9][0-9]{0,18}" : "0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("invalid integer: " + key);
        return new java.math.BigInteger(p.getAsString()).longValueExact();
    }
    private static java.math.BigDecimal decimal(String value) {
        if (value.length() > org.goldgom.chemicalelements.data.LedgerJson.MAX_STORED_AMOUNT_CHARACTERS
                || !value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))
            throw new IllegalArgumentException("invalid stored decimal");
        return new java.math.BigDecimal(value);
    }
    private static org.goldgom.chemicalelements.api.chem.MolAmount amount(String value) {
        if (value.length() > org.goldgom.chemicalelements.data.LedgerJson.MAX_STORED_AMOUNT_CHARACTERS
                || !value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("invalid quanta");
        return new org.goldgom.chemicalelements.api.chem.MolAmount(new java.math.BigInteger(value));
    }
    private static org.goldgom.chemicalelements.api.chem.Composition composition(JsonObject object) {
        var values = new TreeMap<net.minecraft.resources.ResourceLocation, org.goldgom.chemicalelements.api.chem.MolAmount>();
        for (var key : object.keySet()) values.put(net.minecraft.resources.ResourceLocation.parse(key), amount(text(object, key)));
        return new org.goldgom.chemicalelements.api.chem.Composition(values);
    }
    private static JsonObject ownerTree(OwnerKey key) {
        var out = new JsonObject();
        switch (key) {
            case OwnerKey.PlayerInventory p -> { out.addProperty("kind", "player"); out.addProperty("player", p.player().toString()); }
            case OwnerKey.MachineInventory m -> {
                out.addProperty("kind", "machine"); out.addProperty("dimension", m.dimension().toString());
                out.addProperty("position", m.controllerPos()); out.addProperty("incarnation", m.incarnation().toString());
                out.addProperty("type", m.machineType().toString());
            }
            case OwnerKey.WorldBlock ignored -> throw new IllegalArgumentException("world owner requires material journal kind");
            case OwnerKey.FiniteHeatSource ignored -> throw new IllegalArgumentException("heat owner requires material journal kind");
        }
        return out;
    }
}

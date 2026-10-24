package org.goldgom.chemicalelements.instance.materialtx;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.HolderLookup;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.content.material.MaterialProjection;
import org.goldgom.chemicalelements.instance.TransactionEncoding;

/** Full-record relocation gate for real player and content root schemas. No projection or minting.
 * Exact InstanceRecord equality preserves UUID/revision/provenance, thermal, nuclides, pores and
 * adsorbates, unlike an element-only sum. Item-shell roundtrip requires additional planner evidence. */
final class MaterialTransferValidation {
    private MaterialTransferValidation() {}
    static void validate(MaterialTransaction tx, HolderLookup.Provider registries) {
        if (tx.action() == MaterialTransaction.Action.PHASE) throw new IllegalArgumentException("PHASE_REQUIRES_ENTHALPY_PLAN");
        var before = new HashMap<UUID, InstanceRecord>(); var after = new HashMap<UUID, InstanceRecord>();
        for (var change : tx.changes()) {
            collect(change.before(), before, registries); collect(change.after(), after, registries);
        }
        if (!before.equals(after)) throw new IllegalStateException("MATERIAL_FULL_RECORD_CONSERVATION");
    }
    private static void collect(MaterialOwnerSnapshot snapshot, Map<UUID, InstanceRecord> result,
                                HolderLookup.Provider registries) {
        if (snapshot.owner() instanceof OwnerKey.PlayerInventory) {
            var root = JsonParser.parseString(snapshot.canonicalRoot()).getAsJsonObject();
            if (!root.keySet().equals(Set.of("version", "persistent_incarnation", "revision", "inventory"))
                    || root.get("version").getAsInt() != 1 || root.get("revision").getAsLong() != snapshot.revision())
                throw new IllegalArgumentException("PLAYER_MATERIAL_ROOT_SHAPE");
            UUID.fromString(root.get("persistent_incarnation").getAsString());
            var image = TransactionEncoding.decodeMirror(TransactionEncoding.canonical(root.get("inventory")), registries);
            if (image.revision() != snapshot.revision() || !image.tanks().isEmpty())
                throw new IllegalArgumentException("PLAYER_MATERIAL_ROOT_REVISION_OR_TANKS");
            for (var slot : image.slots()) for (var record : slot.instances()) add(result, record);
        } else if (snapshot.owner() instanceof OwnerKey.WorldBlock) {
            var image = MaterialProjection.decode(snapshot.canonicalRoot());
            if (image.revision() != snapshot.revision()) throw new IllegalArgumentException("WORLD_ROOT_REVISION");
            for (var record : image.ledger().instances()) add(result, record);
            // finite_heat is validated evidence of these SAME records; do not count it twice.
        } else throw new IllegalStateException("RELOCATION_ADAPTER_SCHEMA_NOT_SUPPORTED");
    }
    private static void add(Map<UUID, InstanceRecord> target, InstanceRecord record) {
        if (target.putIfAbsent(record.id(), record) != null) throw new IllegalStateException("DUPLICATE_PHYSICAL_MATERIAL");
    }
}

package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Metadata only, persisted with the actual player inventory, never an alternate inventory.
 * A marker cannot authorize delivery; callers must reconcile it with the unique SinkLedger.
 * The live mutation generation is deliberately separate from this persistent projection lineage.
 */
public final class PlayerProjectionState {
    public static final String KEY = "chemicalelements_owner_projection";
    private UUID generation = UUID.randomUUID();
    private long revision;
    private UUID operation;
    private String digest = "";
    private Tag quarantined;
    private static final String MATERIAL_KEY = "chemicalelements_material_projection";
    private org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker materialPending;
    private org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker materialLast;
    private Tag materialQuarantined;
    public Optional<org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker> materialPending() {
        if (materialQuarantined != null) throw new IllegalStateException("PLAYER_MATERIAL_QUARANTINED");
        return Optional.ofNullable(materialPending);
    }
    public Optional<org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker> materialLast() {
        if (materialQuarantined != null) throw new IllegalStateException("PLAYER_MATERIAL_QUARANTINED");
        return Optional.ofNullable(materialLast);
    }
    public boolean materialIsolated() { return materialQuarantined != null || materialPending != null; }
    public record Marker(UUID generation, long revision, Optional<UUID> operation, String digest) {}
    public Marker marker() {
        if (quarantined != null) throw new IllegalStateException("PLAYER_PROJECTION_QUARANTINED");
        return new Marker(generation, revision, Optional.ofNullable(operation), digest);
    }
    public boolean readOnly() { return quarantined != null; }
    public void read(CompoundTag player) {
        readMaterial(player);
        if (!player.contains(KEY)) {
            // Legacy inventory starts a new lineage; never reuse an earlier loaded player's identity.
            generation = UUID.randomUUID(); revision = 0; operation = null; digest = ""; quarantined = null;
            return;
        }
        if (!player.contains(KEY, Tag.TAG_COMPOUND)) {
            quarantined = Objects.requireNonNull(player.get(KEY)).copy();
            return;
        }
        var tag = player.getCompound(KEY);
        try {
            var allowed = tag.getLong("revision") == 0
                    ? Set.of("schema", "generation", "revision", "digest")
                    : Set.of("schema", "generation", "revision", "digest", "operation");
            if (!tag.getAllKeys().equals(allowed)) throw new IllegalArgumentException("unknown projection fields");
            if (tag.getInt("schema") != 1 || !tag.contains("schema", Tag.TAG_INT)
                    || !tag.hasUUID("generation") || !tag.contains("revision", Tag.TAG_LONG)
                    || tag.getLong("revision") < 0 || !tag.contains("digest", Tag.TAG_STRING))
                throw new IllegalArgumentException("player projection shape");
            long loadedRevision = tag.getLong("revision");
            String loadedDigest = tag.getString("digest");
            if (loadedRevision == 0) {
                if (tag.contains("operation") || !loadedDigest.isEmpty()) throw new IllegalArgumentException("initial marker");
            } else if (!tag.hasUUID("operation") || !loadedDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("committed marker");
            }
            generation = tag.getUUID("generation"); revision = loadedRevision;
            operation = loadedRevision == 0 ? null : tag.getUUID("operation"); digest = loadedDigest; quarantined = null;
        } catch (RuntimeException invalid) {
            quarantined = tag.copy(); // preserve unknown/corrupt data, do not silently reset lineage
        }
    }
    public void write(CompoundTag player) {
        writeMaterial(player);
        if (quarantined != null) { player.put(KEY, quarantined.copy()); return; }
        var tag = new CompoundTag(); tag.putInt("schema", 1); tag.putUUID("generation", generation);
        tag.putLong("revision", revision); tag.putString("digest", digest);
        if (operation != null) tag.putUUID("operation", operation);
        player.put(KEY, tag);
    }
    private void readMaterial(CompoundTag player) {
        materialPending = null; materialLast = null; materialQuarantined = null;
        if (!player.contains(MATERIAL_KEY)) return;
        try {
            if (!player.contains(MATERIAL_KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("material metadata type");
            var tag = player.getCompound(MATERIAL_KEY);
            var allowed = new HashSet<>(Set.of("schema"));
            if (tag.contains("pending")) allowed.add("pending");
            if (tag.contains("last")) allowed.add("last");
            if (!tag.getAllKeys().equals(allowed) || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1)
                throw new IllegalArgumentException("material metadata shape");
            if (tag.contains("pending")) {
                if (!tag.contains("pending", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("pending type");
                materialPending = org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker.decode(tag.getCompound("pending"));
            }
            if (tag.contains("last")) {
                if (!tag.contains("last", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("last type");
                materialLast = org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker.decode(tag.getCompound("last"));
                if (materialLast.phase() != org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker.Phase.PROJECTED)
                    throw new IllegalArgumentException("last projection phase");
            }
        } catch (RuntimeException invalid) {
            materialPending = null; materialLast = null;
            materialQuarantined = Objects.requireNonNull(player.get(MATERIAL_KEY)).copy();
        }
    }
    private void writeMaterial(CompoundTag player) {
        if (materialQuarantined != null) { player.put(MATERIAL_KEY, materialQuarantined.copy()); return; }
        if (materialPending == null && materialLast == null) return;
        var tag = new CompoundTag(); tag.putInt("schema", 1);
        if (materialPending != null) tag.put("pending", materialPending.encode());
        if (materialLast != null) tag.put("last", materialLast.encode());
        player.put(MATERIAL_KEY, tag);
    }
    public void isolateMaterial(org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease,
            org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker marker) {
        requireMaterialLease(lease, marker);
        if (marker.phase() != org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker.Phase.RESERVED
                || revision != marker.beforeRevision()) throw new IllegalStateException("PLAYER_RESERVATION_REVISION");
        if (materialPending != null && !materialPending.equals(marker)) throw new IllegalStateException("PLAYER_PENDING_CONFLICT");
        materialPending = marker;
    }
    public void projectMaterial(org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease,
            org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker marker) {
        validateMaterialProjection(lease, marker);
        revision = marker.afterRevision(); operation = marker.operation(); digest = marker.afterHash();
        materialPending = marker; materialLast = marker;
    }
    public void validateMaterialProjection(org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease,
            org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker marker) {
        requireMaterialLease(lease, marker);
        if (materialPending == null || !materialPending.projected().equals(marker))
            throw new IllegalStateException("PLAYER_PROJECTION_WITHOUT_RESERVATION");
    }
    public void finishMaterial(org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease,
                               boolean cancel) {
        lease.requireRoot(this);
        if (materialPending == null) return;
        requireMaterialLease(lease, materialPending);
        boolean projected = materialPending.phase() == org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker.Phase.PROJECTED;
        if (cancel == projected) throw new IllegalStateException("PLAYER_FINISH_PHASE_MISMATCH");
        materialPending = null;
    }
    private void requireMaterialLease(org.goldgom.chemicalelements.instance.materialtx.MaterialObservationBarrier.Lease lease,
            org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker marker) {
        lease.requireRoot(this);
        if (readOnly() || materialQuarantined != null || !marker.matches(lease.operation(), lease.globalHash())
                || !generation.equals(marker.incarnation())) throw new IllegalStateException("PLAYER_METADATA_BINDING");
    }
}

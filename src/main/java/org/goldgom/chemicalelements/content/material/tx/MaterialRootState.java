package org.goldgom.chemicalelements.content.material.tx;

import java.util.*;
import net.minecraft.nbt.*;
import org.goldgom.chemicalelements.content.material.MaterialProjection;
import org.goldgom.chemicalelements.instance.TransactionEncoding;
import org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker;

/** One immutable authoritative unit; no shadow inventory. NBT persists business and metadata together. */
public record MaterialRootState(UUID incarnation, MaterialProjection image,
        Optional<MaterialPendingMarker> pending, Optional<MaterialPendingMarker> last) {
    public MaterialRootState {
        Objects.requireNonNull(incarnation); Objects.requireNonNull(image);
        Objects.requireNonNull(pending); Objects.requireNonNull(last);
        pending.ifPresent(m -> validate(m, incarnation, image));
        last.ifPresent(m -> {
            if (m.phase() != MaterialPendingMarker.Phase.PROJECTED) throw new IllegalArgumentException("invalid last marker");
            if (!m.incarnation().equals(incarnation) || m.afterRevision() > image.revision())
                throw new IllegalArgumentException("last lineage mismatch");
            if (m.afterRevision() == image.revision()) validate(m, incarnation, image);
        });
    }
    private static void validate(MaterialPendingMarker m, UUID id, MaterialProjection image) {
        boolean projected = m.phase() == MaterialPendingMarker.Phase.PROJECTED;
        if (!m.incarnation().equals(id) || image.revision() != (projected ? m.afterRevision() : m.beforeRevision())
                || !TransactionEncoding.sha256(image.canonical()).equals(projected ? m.afterHash() : m.beforeHash()))
            throw new IllegalArgumentException("material marker/root mismatch; quarantine original NBT");
    }
    public MaterialRootState isolate(MaterialPendingMarker m) {
        if (m.phase() != MaterialPendingMarker.Phase.RESERVED) throw new IllegalArgumentException("reserve first");
        if (pending.isPresent()) {
            if (!pending.get().equals(m)) throw new IllegalStateException("conflicting pending transaction");
            return this;
        }
        return new MaterialRootState(incarnation, image, Optional.of(m), last);
    }
    public MaterialRootState project(MaterialProjection after, MaterialPendingMarker m) {
        if (pending.isEmpty() || !pending.get().projected().equals(m))
            throw new IllegalStateException("projection without matching reservation");
        return new MaterialRootState(incarnation, after, Optional.of(m), Optional.of(m));
    }
    public MaterialRootState complete(UUID op, String hash) {
        if (pending.isEmpty()) {
            if (last.filter(m -> m.matches(op, hash)).isPresent()) return this;
            throw new IllegalStateException("unknown completion");
        }
        var m = pending.get();
        if (!m.matches(op, hash) || m.phase() != MaterialPendingMarker.Phase.PROJECTED)
            throw new IllegalStateException("not projected by this operation");
        return new MaterialRootState(incarnation, image, Optional.empty(), Optional.of(m));
    }
    public MaterialRootState cancel(UUID op, String hash) {
        if (pending.isEmpty()) {
            // A completed AFTER root must never masquerade as an idempotent cancellation.
            // For a marker-free BEFORE root the coordinator must prove ALL-BEFORE against
            // the original journal while holding the original-root lease. No cancellation
            // marker is invented here and `last` remains exclusively committed/projected lineage.
            if (last.filter(m -> m.matches(op, hash)).isPresent())
                throw new IllegalStateException("cannot cancel completed operation");
            return this;
        }
        var m = pending.get();
        if (!m.matches(op, hash) || m.phase() != MaterialPendingMarker.Phase.RESERVED)
            throw new IllegalStateException("cannot cancel projected/conflicting operation");
        return new MaterialRootState(incarnation, image, Optional.empty(), last);
    }
    /** Validate leaf publication against the same immutable root state machine used by B.
     * A world capability cannot erase AFTER lineage, skip reservation, or replace a balance
     * under a marker for a different operation. Admission still belongs to the durable coordinator. */
    public MaterialRootState worldReplacement(UUID op, String hash, MaterialRootState replacement) {
        Objects.requireNonNull(replacement);
        if (!incarnation.equals(replacement.incarnation)) throw new IllegalArgumentException("foreign incarnation");
        if (equals(replacement)) return this;
        MaterialRootState expected;
        if (pending.isEmpty()) {
            var marker = replacement.pending.orElseThrow(() -> new IllegalStateException("reserve before world replacement"));
            if (!marker.matches(op, hash)) throw new IllegalStateException("foreign world operation");
            expected = isolate(marker);
        } else {
            var marker = pending.get();
            if (!marker.matches(op, hash)) throw new IllegalStateException("foreign world operation");
            if (marker.phase() == MaterialPendingMarker.Phase.PROJECTED) expected = complete(op, hash);
            else if (replacement.pending.isEmpty()) expected = cancel(op, hash);
            else expected = project(replacement.image, marker.projected());
        }
        if (!expected.equals(replacement)) throw new IllegalStateException("INVALID_WORLD_ROOT_TRANSITION");
        return replacement;
    }
    public CompoundTag encode() {
        var n = new CompoundTag(); n.putInt("schema", 1); n.putUUID("incarnation", incarnation);
        n.putString("business", image.canonical()); pending.ifPresent(m -> n.put("pending", m.encode()));
        last.ifPresent(m -> n.put("last", m.encode())); return n;
    }
    public static MaterialRootState decode(CompoundTag n) {
        if (!n.contains("schema", Tag.TAG_INT) || n.getInt("schema") != 1 || !n.hasUUID("incarnation")
                || !n.contains("business", Tag.TAG_STRING)) throw new IllegalArgumentException("invalid material root NBT");
        var r = new MaterialRootState(n.getUUID("incarnation"), MaterialProjection.decode(n.getString("business")),
                n.contains("pending") ? Optional.of(MaterialPendingMarker.decode(n.getCompound("pending"))) : Optional.empty(),
                n.contains("last") ? Optional.of(MaterialPendingMarker.decode(n.getCompound("last"))) : Optional.empty());
        if (!r.encode().equals(n)) throw new IllegalArgumentException("unknown or noncanonical root NBT");
        return r;
    }
}

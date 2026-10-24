package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable metadata embedded in the SAME durable owner root as its material state.
 * No material balance is stored here. An AFTER mark alone never authorizes release: all owners
 * and the authoritative SinkLedger decision must be reconciled first. Loading an invalid mark
 * must quarantine the original owner NBT; callers must not catch this and clear the marker.
 */
public record MaterialPendingMarker(UUID operation, String globalHash, UUID incarnation,
                                    long beforeRevision, long afterRevision,
                                    String beforeHash, String afterHash, Phase phase) {
    public enum Phase { RESERVED, PROJECTED }
    private static final Set<String> KEYS = Set.of("version", "operation", "global_hash", "incarnation",
            "before_revision", "after_revision", "before_hash", "after_hash", "phase");
    public MaterialPendingMarker {
        Objects.requireNonNull(operation); Objects.requireNonNull(incarnation); Objects.requireNonNull(phase);
        digest(globalHash); digest(beforeHash); digest(afterHash);
        if (beforeRevision < 0 || beforeRevision == Long.MAX_VALUE || afterRevision != beforeRevision + 1)
            throw new IllegalArgumentException("material owner revision progression");
    }
    public MaterialPendingMarker projected() {
        return new MaterialPendingMarker(operation, globalHash, incarnation, beforeRevision, afterRevision,
                beforeHash, afterHash, Phase.PROJECTED);
    }
    public boolean matches(UUID operation, String globalHash) {
        return this.operation.equals(operation) && this.globalHash.equals(globalHash);
    }
    public CompoundTag encode() {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putUUID("operation", operation);
        tag.putString("global_hash", globalHash); tag.putUUID("incarnation", incarnation);
        tag.putLong("before_revision", beforeRevision); tag.putLong("after_revision", afterRevision);
        tag.putString("before_hash", beforeHash); tag.putString("after_hash", afterHash);
        tag.putString("phase", phase.name()); return tag;
    }
    public static MaterialPendingMarker decode(CompoundTag tag) {
        if (!tag.getAllKeys().equals(KEYS) || !tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != 1 || !tag.hasUUID("operation") || !tag.hasUUID("incarnation")
                || !tag.contains("global_hash", Tag.TAG_STRING) || !tag.contains("before_hash", Tag.TAG_STRING)
                || !tag.contains("after_hash", Tag.TAG_STRING) || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("before_revision", Tag.TAG_LONG) || !tag.contains("after_revision", Tag.TAG_LONG))
            throw new IllegalArgumentException("invalid material isolation marker; preserve original NBT");
        var result = new MaterialPendingMarker(tag.getUUID("operation"), tag.getString("global_hash"),
                tag.getUUID("incarnation"), tag.getLong("before_revision"), tag.getLong("after_revision"),
                tag.getString("before_hash"), tag.getString("after_hash"), Phase.valueOf(tag.getString("phase")));
        if (!result.encode().equals(tag)) throw new IllegalArgumentException("noncanonical material marker");
        return result;
    }
    private static void digest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 required");
    }
}

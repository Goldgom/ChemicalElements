package org.goldgom.chemicalelements.world.material;

import java.util.*;
import net.minecraft.nbt.*;
import org.goldgom.chemicalelements.instance.materialtx.MaterialPendingMarker;

/** Position-lifetime identity. Occupied positions refer to the original BE root, never its ledger.
 * Tombstones are intentionally never collected. A corrupt record must stop loading, not become air. */
public record WorldPositionMetadata(long position, UUID incarnation, long revision, Occupancy occupancy,
        String rootHash, Optional<MaterialPendingMarker> pending, Optional<MaterialPendingMarker> last) {
    public enum Occupancy { AIR, MATERIAL }
    public static final String CHUNK_TAG = "chemicalelements_world_positions";
    public WorldPositionMetadata {
        Objects.requireNonNull(incarnation); Objects.requireNonNull(occupancy);
        Objects.requireNonNull(pending); Objects.requireNonNull(last);
        if (revision < 0 || rootHash == null || !rootHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid position identity");
        // With a material BE, transaction metadata remains exclusively in its original root.
        if (occupancy == Occupancy.MATERIAL && (pending.isPresent() || last.isPresent()))
            throw new IllegalArgumentException("occupied metadata cannot duplicate BE lineage");
        pending.ifPresent(m -> {
            long expected = m.phase() == MaterialPendingMarker.Phase.RESERVED ? m.beforeRevision() : m.afterRevision();
            String hash = m.phase() == MaterialPendingMarker.Phase.RESERVED ? m.beforeHash() : m.afterHash();
            if (!m.incarnation().equals(incarnation) || expected != revision || !hash.equals(rootHash))
                throw new IllegalArgumentException("position marker/image mismatch");
        });
        last.ifPresent(m -> {
            if (!m.incarnation().equals(incarnation) || m.afterRevision() != revision || !m.afterHash().equals(rootHash))
                throw new IllegalArgumentException("position last/image mismatch");
        });
    }
    public CompoundTag encode() {
        var n = new CompoundTag(); n.putInt("schema", 1); n.putLong("position", position);
        n.putUUID("incarnation", incarnation); n.putLong("revision", revision);
        n.putString("occupancy", occupancy.name()); n.putString("root_hash", rootHash);
        pending.ifPresent(m -> n.put("pending", m.encode())); last.ifPresent(m -> n.put("last", m.encode()));
        return n;
    }
    public static WorldPositionMetadata decode(CompoundTag n) {
        if (!n.contains("schema", Tag.TAG_INT) || n.getInt("schema") != 1
                || !n.contains("position", Tag.TAG_LONG) || !n.hasUUID("incarnation")
                || !n.contains("revision", Tag.TAG_LONG) || !n.contains("occupancy", Tag.TAG_STRING)
                || !n.contains("root_hash", Tag.TAG_STRING)
                || n.contains("pending") && !n.contains("pending", Tag.TAG_COMPOUND)
                || n.contains("last") && !n.contains("last", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("invalid position metadata types");
        var result = new WorldPositionMetadata(n.getLong("position"), n.getUUID("incarnation"), n.getLong("revision"),
                Occupancy.valueOf(n.getString("occupancy")), n.getString("root_hash"),
                n.contains("pending") ? Optional.of(MaterialPendingMarker.decode(n.getCompound("pending"))) : Optional.empty(),
                n.contains("last") ? Optional.of(MaterialPendingMarker.decode(n.getCompound("last"))) : Optional.empty());
        if (!result.encode().equals(n)) throw new IllegalArgumentException("noncanonical position metadata");
        return result;
    }
    public static NavigableMap<Long, WorldPositionMetadata> decodeChunk(CompoundTag n, int chunkX, int chunkZ) {
        var result = new TreeMap<Long, WorldPositionMetadata>();
        if (!n.contains(CHUNK_TAG)) return Collections.unmodifiableNavigableMap(result);
        if (!n.contains(CHUNK_TAG, Tag.TAG_LIST)) throw new IllegalArgumentException("position metadata is not a list");
        Tag raw = n.get(CHUNK_TAG);
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("invalid position list element type");
        for (Tag entry : list) {
            var m = decode((CompoundTag) entry);
            var pos = net.minecraft.core.BlockPos.of(m.position());
            if ((pos.getX() >> 4) != chunkX || (pos.getZ() >> 4) != chunkZ || result.putIfAbsent(m.position(), m) != null)
                throw new IllegalArgumentException("foreign or duplicate position metadata");
        }
        return Collections.unmodifiableNavigableMap(result);
    }
    public static ListTag encodeChunk(Map<Long, WorldPositionMetadata> positions) {
        var list = new ListTag();
        new TreeMap<>(positions).forEach((key, value) -> {
            if (key.longValue() != value.position()) throw new IllegalArgumentException("position key mismatch");
            list.add(value.encode());
        });
        return list;
    }
}

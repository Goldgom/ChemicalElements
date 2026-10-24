package org.goldgom.chemicalelements.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.data.LedgerJson;

/**
 * Sparse per-chunk chemistry store owned by package B. Keys are packed {@code BlockPos.asLong()}.
 * <ul>
 *   <li>{@code blocks}: the persistent instance of a placed block (one InstanceRecord) plus the block id
 *       it belongs to; a block broken with remaining matter drops exactly that instance.</li>
 *   <li>{@code tombstones}: positions whose natural inventory was initialized (or exhausted) and must
 *       never be re-sampled (water, ores, air).</li>
 *   <li>{@code water}: finite water cells (volume quanta, species quanta, energy, revision) for C.</li>
 *   <li>{@code extra}: opaque named sub-stores (atmosphere/sorption/thermal) kept for other owners.</li>
 * </ul>
 * Every mutator increments the revision and the caller must mark the chunk unsaved (done by
 * {@link WorldChemistryStore}). Unknown schema versions are preserved verbatim and the chunk becomes
 * read-only for chemistry until migrated.
 */
public final class ChunkChemistry {
    public static final int SCHEMA = 1;

    public record BlockInstance(String blockId, String itemId, InstanceRecord record, long revision) {
        public BlockInstance { Objects.requireNonNull(blockId); Objects.requireNonNull(itemId); Objects.requireNonNull(record); }
    }

    /** Finite water cell. All amounts in 1e-18 units (litres for volume, mol for species). */
    public record WaterCell(BigInteger volume, SortedMap<String, BigInteger> species, BigInteger energy, BigInteger capacity, String origin, long revision) {
        public WaterCell {
            if (volume.signum() < 0 || energy.signum() < 0 || capacity.signum() < 0) throw new IllegalArgumentException("negative water cell");
            var m = new TreeMap<String, BigInteger>();
            species.forEach((k, v) -> { if (v.signum() < 0) throw new IllegalArgumentException("negative species"); if (v.signum() > 0) m.put(k, v); });
            species = Collections.unmodifiableSortedMap(m);
            Objects.requireNonNull(origin);
        }
    }

    public record Tombstone(String kind, String context, long revision) {}

    final TreeMap<Long, BlockInstance> blocks = new TreeMap<>();
    final TreeMap<Long, Tombstone> tombstones = new TreeMap<>();
    final TreeMap<Long, WaterCell> water = new TreeMap<>();
    final TreeMap<String, CompoundTag> extra = new TreeMap<>();
    long revision;
    CompoundTag quarantinedRaw;
    String quarantineReason;

    public ChunkChemistry() {}
    public ChunkChemistry(IAttachmentHolder holder) {}

    public long revision() { return revision; }
    public boolean readOnly() { return quarantinedRaw != null; }
    public Optional<String> quarantineReason() { return Optional.ofNullable(quarantineReason); }

    public Optional<BlockInstance> block(long pos) { return Optional.ofNullable(blocks.get(pos)); }
    public Optional<WaterCell> water(long pos) { return Optional.ofNullable(water.get(pos)); }
    public Optional<Tombstone> tombstone(long pos) { return Optional.ofNullable(tombstones.get(pos)); }
    public Map<Long, BlockInstance> blocks() { return Collections.unmodifiableMap(blocks); }
    public Map<Long, WaterCell> waterCells() { return Collections.unmodifiableMap(water); }
    public Optional<CompoundTag> extra(String key) { return Optional.ofNullable(extra.get(key)).map(CompoundTag::copy); }

    void guard() { if (readOnly()) throw new IllegalStateException("chunk chemistry quarantined: " + quarantineReason); }

    void putBlock(long pos, BlockInstance b) { guard(); blocks.put(pos, b); revision++; }
    Optional<BlockInstance> removeBlock(long pos) { guard(); var r = Optional.ofNullable(blocks.remove(pos)); if (r.isPresent()) revision++; return r; }
    // Empty cells retain their revision and origin: absence is reserved for never-initialized cells.
    void putWater(long pos, WaterCell c) { guard(); water.put(pos, c); revision++; }
    void tombstone(long pos, Tombstone t) { guard(); tombstones.putIfAbsent(pos, t); revision++; }
    void putExtra(String key, CompoundTag tag) { guard(); extra.put(key, tag.copy()); revision++; }

    // ------------------------------------------------------------------ serialization

    public static final class Serializer implements IAttachmentSerializer<CompoundTag, ChunkChemistry> {
        @Override
        public ChunkChemistry read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) { return ChunkChemistry.read(tag); }

        @Override
        public CompoundTag write(ChunkChemistry c, HolderLookup.Provider provider) {
            if (c.quarantinedRaw == null && c.blocks.isEmpty() && c.tombstones.isEmpty() && c.water.isEmpty() && c.extra.isEmpty()) return null;
            return c.write();
        }
    }

    public CompoundTag write() {
        if (quarantinedRaw != null) return quarantinedRaw.copy();
        var tag = new CompoundTag();
        tag.putInt("schema_version", SCHEMA);
        tag.putLong("revision", revision);
        var b = new ListTag();
        blocks.forEach((pos, bi) -> {
            var e = new CompoundTag();
            e.putLong("pos", pos);
            e.putString("block", bi.blockId());
            e.putString("item", bi.itemId());
            e.putLong("rev", bi.revision());
            e.putString("record", LedgerJson.encode(new StackLedger(StackLedger.CURRENT_SCHEMA, 0, List.of(bi.record()))).toString());
            b.add(e);
        });
        tag.put("blocks", b);
        var t = new ListTag();
        tombstones.forEach((pos, ts) -> { var e = new CompoundTag(); e.putLong("pos", pos); e.putString("kind", ts.kind()); e.putString("ctx", ts.context()); e.putLong("rev", ts.revision()); t.add(e); });
        tag.put("tombstones", t);
        var w = new ListTag();
        water.forEach((pos, c) -> {
            var e = new CompoundTag();
            e.putLong("pos", pos);
            e.putString("volume", c.volume().toString());
            e.putString("energy", c.energy().toString());
            e.putString("capacity", c.capacity().toString());
            e.putString("origin", c.origin());
            e.putLong("rev", c.revision());
            var s = new CompoundTag();
            c.species().forEach((k, v) -> s.putString(k, v.toString()));
            e.put("species", s);
            w.add(e);
        });
        tag.put("water", w);
        var x = new CompoundTag();
        extra.forEach(x::put);
        tag.put("extra", x);
        return tag;
    }

    public static ChunkChemistry read(CompoundTag tag) {
        var c = new ChunkChemistry();
        int v = tag.getInt("schema_version");
        if (v != SCHEMA) { c.quarantinedRaw = tag.copy(); c.quarantineReason = "unsupported chunk chemistry schema " + v; return c; }
        try {
            c.revision = tag.getLong("revision");
            for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
                var e = (CompoundTag) t;
                var ledger = LedgerJson.decode(JsonParser.parseString(e.getString("record")).getAsJsonObject());
                if (ledger.instances().size() != 1) throw new IllegalArgumentException("block instance must hold exactly one record");
                c.blocks.put(e.getLong("pos"), new BlockInstance(e.getString("block"), e.getString("item"), ledger.instances().getFirst(), e.getLong("rev")));
            }
            for (Tag t : tag.getList("tombstones", Tag.TAG_COMPOUND)) {
                var e = (CompoundTag) t;
                c.tombstones.put(e.getLong("pos"), new Tombstone(e.getString("kind"), e.getString("ctx"), e.getLong("rev")));
            }
            for (Tag t : tag.getList("water", Tag.TAG_COMPOUND)) {
                var e = (CompoundTag) t;
                var s = new TreeMap<String, BigInteger>();
                var st = e.getCompound("species");
                for (var k : st.getAllKeys()) s.put(k, bounded(st.getString(k)));
                c.water.put(e.getLong("pos"), new WaterCell(bounded(e.getString("volume")), s, bounded(e.getString("energy")), bounded(e.getString("capacity")), e.getString("origin"), e.getLong("rev")));
            }
            var x = tag.getCompound("extra");
            for (var k : x.getAllKeys()) c.extra.put(k, x.getCompound(k));
        } catch (RuntimeException ex) {
            var q = new ChunkChemistry();
            q.quarantinedRaw = tag.copy();
            q.quarantineReason = "corrupt chunk chemistry: " + ex.getMessage();
            return q;
        }
        return c;
    }

    static BigInteger bounded(String s) {
        if (s.isEmpty() || s.length() > 16384) throw new IllegalArgumentException("stored integer encoding budget exceeded");
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9')
            throw new IllegalArgumentException("stored integer must be unsigned decimal digits");
        var v = new BigInteger(s);
        if (v.signum() < 0) throw new IllegalArgumentException("negative");
        return v;
    }
}

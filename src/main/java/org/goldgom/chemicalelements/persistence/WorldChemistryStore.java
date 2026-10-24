package org.goldgom.chemicalelements.persistence;

import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The single durable owner of block-bound chemistry (B). C/D access block instances and water
 * cells only through here; no second copy of the facts exists.
 * <p>
 * Multi-chunk commits are journaled in {@link SinkLedger}: prepare (expected revisions + replacement
 * payload) → apply to every loaded chunk → mark committed. Unloaded participants reject the commit
 * before anything is written. If the server stops between apply and mark, recovery re-applies only
 * cells whose revision still equals the expected one (idempotent: already-applied cells carry the
 * post-revision and are skipped), so no cell is written twice.
 */
public final class WorldChemistryStore {
    private WorldChemistryStore() {}

    public static Optional<LevelChunk> loadedChunk(ServerLevel level, BlockPos pos) {
        return Optional.ofNullable(level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
    }

    public static Optional<ChunkChemistry> data(ServerLevel level, BlockPos pos) {
        return loadedChunk(level, pos).map(c -> c.getData(ChemistryComponents.CHUNK.get()));
    }

    static void touched(LevelChunk chunk) { chunk.setUnsaved(true); }

    // ------------------------------------------------------------ blocks

    public static Optional<ChunkChemistry.BlockInstance> block(ServerLevel level, BlockPos pos) {
        return data(level, pos).flatMap(d -> d.block(pos.asLong()));
    }

    public static boolean putBlock(ServerLevel level, BlockPos pos, ChunkChemistry.BlockInstance b) {
        var chunk = loadedChunk(level, pos);
        if (chunk.isEmpty()) return false;
        var d = chunk.get().getData(ChemistryComponents.CHUNK.get());
        if (d.readOnly()) return false;
        d.putBlock(pos.asLong(), b);
        touched(chunk.get());
        return true;
    }

    public static Optional<ChunkChemistry.BlockInstance> takeBlock(ServerLevel level, BlockPos pos) {
        var chunk = loadedChunk(level, pos);
        if (chunk.isEmpty()) return Optional.empty();
        var d = chunk.get().getData(ChemistryComponents.CHUNK.get());
        if (d.readOnly()) return Optional.empty();
        var r = d.removeBlock(pos.asLong());
        if (r.isPresent()) touched(chunk.get());
        return r;
    }

    /** Compare-and-swap on the block instance revision. */
    public static boolean casBlock(ServerLevel level, BlockPos pos, long expectedRevision, UnaryOperator<ChunkChemistry.BlockInstance> update) {
        var chunk = loadedChunk(level, pos);
        if (chunk.isEmpty()) return false;
        var d = chunk.get().getData(ChemistryComponents.CHUNK.get());
        if (d.readOnly()) return false;
        var cur = d.block(pos.asLong());
        if (cur.isEmpty() || cur.get().revision() != expectedRevision) return false;
        var next = update.apply(cur.get());
        if (next == null) d.removeBlock(pos.asLong());
        else d.putBlock(pos.asLong(), new ChunkChemistry.BlockInstance(next.blockId(), next.itemId(), next.record(), expectedRevision + 1));
        touched(chunk.get());
        return true;
    }

    public static boolean tombstoned(ServerLevel level, BlockPos pos) {
        return data(level, pos).flatMap(d -> d.tombstone(pos.asLong())).isPresent();
    }

    public static boolean tombstone(ServerLevel level, BlockPos pos, String kind, String context) {
        var chunk = loadedChunk(level, pos);
        if (chunk.isEmpty()) return false;
        var d = chunk.get().getData(ChemistryComponents.CHUNK.get());
        if (d.readOnly()) return false;
        d.tombstone(pos.asLong(), new ChunkChemistry.Tombstone(kind, context, level.getGameTime()));
        touched(chunk.get());
        return true;
    }

    // ------------------------------------------------------------ water cells

    public static Optional<ChunkChemistry.WaterCell> water(ServerLevel level, BlockPos pos) {
        return data(level, pos).flatMap(d -> d.water(pos.asLong()));
    }

    public static Optional<CompoundTag> extra(ServerLevel level, BlockPos chunkAnchor, String key) {
        return data(level, chunkAnchor).flatMap(d -> d.extra(key));
    }

    public static boolean putExtra(ServerLevel level, BlockPos chunkAnchor, String key, CompoundTag value) {
        var chunk = loadedChunk(level, chunkAnchor);
        if (chunk.isEmpty()) return false;
        var d = chunk.get().getData(ChemistryComponents.CHUNK.get());
        if (d.readOnly()) return false;
        d.putExtra(key, value);
        touched(chunk.get());
        return true;
    }

    /** Expected revision per cell; -1 means "cell must be absent". */
    public record WaterChange(BlockPos pos, long expectedRevision, ChunkChemistry.WaterCell replacement) {}

    /**
     * Atomic multi-cell (possibly multi-chunk) water commit. Returns false and writes nothing on any
     * unloaded/read-only chunk or stale revision. Duplicate receipt ids are rejected (idempotent).
     */
    public static synchronized boolean commitWater(ServerLevel level, UUID receipt, List<WaterChange> changes) {
        var journal = SinkLedger.get();
        if (journal.readOnly()) return false;
        changes = canonical(changes);
        var payload = encode(level, changes);
        boolean known = journal.journalEntry(receipt).isPresent();
        if (known && (!journal.matches(receipt, payload) || journal.aborted(receipt))) return false;
        boolean decided = journal.committed(receipt);
        var chunks = new LinkedHashMap<Long, LevelChunk>();
        for (var c : changes) {
            var chunk = loadedChunk(level, c.pos());
            if (chunk.isEmpty()) return false;
            var d = chunk.get().getData(ChemistryComponents.CHUNK.get());
            if (d.readOnly()) return false;
            long cur = d.water(c.pos().asLong()).map(ChunkChemistry.WaterCell::revision).orElse(-1L);
            if (cur != c.expectedRevision() && !(known && isPost(d, c))
                    && !(decided && cur > post(c).revision())) return false;
            chunks.put(ChunkPos.asLong(c.pos()), chunk.get());
        }
        journal.prepareDurably(receipt, payload);
        apply(level, changes, chunks);
        journal.commitDurably(receipt);
        return true;
    }

    private static void apply(ServerLevel level, List<WaterChange> changes, Map<Long, LevelChunk> chunks) {
        for (var c : changes) {
            var chunk = chunks.get(ChunkPos.asLong(c.pos()));
            var d = chunk.getData(ChemistryComponents.CHUNK.get());
            long cur = d.water(c.pos().asLong()).map(ChunkChemistry.WaterCell::revision).orElse(-1L);
            if (cur != c.expectedRevision()) continue; // already applied during an earlier attempt
            var r = c.replacement();
            d.putWater(c.pos().asLong(), post(c));
            touched(chunk);
        }
    }

    /**
     * Startup/chunk-load recovery includes COMMITTED decisions: a journal flush does not flush chunk
     * attachments. Replay in journal order, only with all participants loaded. For committed entries,
     * a strictly newer revision is left alone; an equal-revision payload mismatch blocks the entire
     * operation. Pending conflicts stay pending, never abort a possibly partial projection.
     */
    public static synchronized int recover(ServerLevel level) {
        var journal = SinkLedger.get();
        int done = 0;
        for (var tx : journal.recoverable()) {
            var entry = journal.journalEntry(tx).orElse(null);
            if (entry == null || !entry.getString("dim").equals(level.dimension().location().toString())
                    || !entry.contains("changes", Tag.TAG_LIST)) continue;
            var changes = decode(entry);
            if (changes.isEmpty()) continue;
            boolean decided = journal.committed(tx);
            var chunks = new LinkedHashMap<Long, LevelChunk>();
            boolean loaded = true, conflict = false, needsProjection = false;
            for (var c : changes) {
                var chunk = loadedChunk(level, c.pos());
                if (chunk.isEmpty()) { loaded = false; break; }
                long cur = chunk.get().getData(ChemistryComponents.CHUNK.get()).water(c.pos().asLong()).map(ChunkChemistry.WaterCell::revision).orElse(-1L);
                var data = chunk.get().getData(ChemistryComponents.CHUNK.get());
                if (cur == c.expectedRevision()) needsProjection = true;
                if (data.readOnly() || (cur != c.expectedRevision() && !isPost(data, c)
                        && !(decided && cur > post(c).revision()))) conflict = true;
                chunks.put(ChunkPos.asLong(c.pos()), chunk.get());
            }
            if (!loaded) continue;
            // Never discard a possibly partially applied transaction on conflict.
            if (conflict || (decided && !needsProjection)) continue;
            // A recovered intent is already durable; explicitly reflush before projection
            // so retrying an in-memory failed write cannot bypass the T1 barrier.
            journal.flushDurably();
            apply(level, changes, chunks);
            if (!decided) journal.commitDurably(tx);
            done++;
        }
        return done;
    }

    private static List<WaterChange> canonical(List<WaterChange> input) {
        var sorted = new TreeMap<Long, WaterChange>();
        for (var change : List.copyOf(input)) {
            Objects.requireNonNull(change.replacement());
            if (change.expectedRevision() < -1 || change.expectedRevision() == Long.MAX_VALUE)
                throw new IllegalArgumentException("invalid expected water revision");
            var copy = new WaterChange(change.pos().immutable(), change.expectedRevision(), change.replacement());
            if (sorted.putIfAbsent(copy.pos().asLong(), copy) != null)
                throw new IllegalArgumentException("duplicate water destination");
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("empty water transaction");
        return List.copyOf(sorted.values());
    }

    private static ChunkChemistry.WaterCell post(WaterChange c) {
        var r = c.replacement();
        return new ChunkChemistry.WaterCell(r.volume(), r.species(), r.energy(), r.capacity(), r.origin(), Math.max(c.expectedRevision() + 1, r.revision()));
    }

    private static boolean isPost(ChunkChemistry d, WaterChange c) {
        return d.water(c.pos().asLong()).map(post(c)::equals).orElse(false);
    }

    static CompoundTag encode(ServerLevel level, List<WaterChange> changes) {
        var t = new CompoundTag();
        t.putString("dim", level.dimension().location().toString());
        var l = new ListTag();
        for (var c : changes) {
            var e = new CompoundTag();
            e.putLong("pos", c.pos().asLong());
            e.putLong("expected", c.expectedRevision());
            var cell = new ChunkChemistry();
            cell.water.put(0L, c.replacement());
            e.put("cell", cell.write().getList("water", Tag.TAG_COMPOUND).getCompound(0));
            l.add(e);
        }
        t.put("changes", l);
        return t;
    }

    static List<WaterChange> decode(CompoundTag t) {
        var out = new ArrayList<WaterChange>();
        for (Tag x : t.getList("changes", Tag.TAG_COMPOUND)) {
            var e = (CompoundTag) x;
            var wrap = new CompoundTag();
            wrap.putInt("schema_version", ChunkChemistry.SCHEMA);
            var list = new ListTag();
            list.add(e.getCompound("cell"));
            wrap.put("water", list);
            var cell = ChunkChemistry.read(wrap).water(0L).orElseThrow();
            out.add(new WaterChange(BlockPos.of(e.getLong("pos")), e.getLong("expected"), cell));
        }
        return out;
    }
}

package org.goldgom.chemicalelements.persistence;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.storage.LevelResource;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.data.LedgerJson;
import org.goldgom.chemicalelements.instance.LedgerOps;

/**
 * World-level persistent state of package B (overworld data storage):
 * <ul>
 *   <li>identity serial (instances never reuse an id across restarts);</li>
 *   <li>sink accounts: matter that left item/block inventories through an explicit route
 *       (consumption, audit excess, unconverted crafting residue), per reason, exact mol;</li>
 *   <li>cross-chunk transaction journal: prepared/committed receipts for idempotent replay;</li>
 *   <li>quarantine registry: payloads this version could not decode, kept verbatim.</li>
 * </ul>
 * Nothing in here is ever "refilled" into the world; it is an audit trail and recovery source.
 */
public final class SinkLedger extends SavedData {
    public static final String NAME = "chemicalelements_instances";
    public static final int SCHEMA = 1;
    static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath("chemicalelements", "water");

    private long serial = 1;
    private final TreeMap<String, Composition> sinks = new TreeMap<>();
    private final TreeMap<String, Long> sinkCounts = new TreeMap<>();
    private final LinkedHashMap<UUID, CompoundTag> journal = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> quarantine = new LinkedHashMap<>();
    private CompoundTag unknownFuture;
    // Terminal receipts are retained: eviction would allow an old operation UUID to mint again.

    public static final Factory<SinkLedger> FACTORY = new Factory<>(SinkLedger::new, SinkLedger::load, null);

    /** Fallback accumulator for operations before a server is attached (tests/early boot). */
    private static final SinkLedger DETACHED = new SinkLedger();

    public static SinkLedger get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) return DETACHED;
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static SinkLedger detached() { return DETACHED; }

    public static void depositGlobal(String reason, List<InstanceRecord> records) { get().deposit(reason, records); }

    public synchronized boolean readOnly() { return unknownFuture != null; }
    private void guard() { if (readOnly()) throw new IllegalStateException("sink ledger quarantined; original NBT retained"); }
    public synchronized long nextSerial() { guard(); long v = serial++; setDirty(); return v; }

    public synchronized void deposit(String reason, List<InstanceRecord> records) {
        guard();
        if (records.isEmpty()) return;
        Composition c = sinks.getOrDefault(reason, Composition.EMPTY);
        for (var r : records) c = c.add(LedgerOps.fullComposition(r, WATER));
        sinks.put(reason, c);
        sinkCounts.merge(reason, (long) records.size(), Long::sum);
        setDirty();
    }

    public synchronized void depositMatter(String reason, Composition matter) {
        guard();
        if (matter.amounts().isEmpty()) return;
        sinks.merge(reason, matter, Composition::add);
        setDirty();
    }

    public synchronized Map<String, Composition> sinks() { return Map.copyOf(sinks); }
    public synchronized long sinkCount(String reason) { return sinkCounts.getOrDefault(reason, 0L); }

    public synchronized Composition totalSunk() {
        return sinks.values().stream().reduce(Composition.EMPTY, Composition::add);
    }

    // ------------------------------------------------------------ journal

    public synchronized boolean committed(UUID tx) {
        var t = journal.get(tx);
        return t != null && t.getBoolean("committed");
    }

    /** Derived read-only lookup from this authoritative journal, including terminal decisions
     * whose local cleanup may not have been persisted. No caller-supplied pending flag. */
    public synchronized List<UUID> materialOperationsForPlayer(UUID player) {
        guard();
        var result = new ArrayList<UUID>();
        for (var entry : journal.entrySet()) {
            if (!org.goldgom.chemicalelements.instance.materialtx.MaterialJournal.KIND.equals(entry.getValue().getString("kind"))) continue;
            org.goldgom.chemicalelements.instance.materialtx.MaterialJournal.validateStored(entry.getKey(), entry.getValue());
            var tx = org.goldgom.chemicalelements.instance.materialtx.MaterialTransaction.decode(entry.getValue().getString("payload"));
            if (tx.changes().stream().anyMatch(c -> c.before().owner() instanceof org.goldgom.chemicalelements.api.tx.OwnerKey.PlayerInventory key
                    && key.player().equals(player))) result.add(entry.getKey());
        }
        return List.copyOf(result);
    }

    public synchronized Optional<CompoundTag> journalEntry(UUID tx) { return Optional.ofNullable(journal.get(tx)).map(CompoundTag::copy); }

    public synchronized void prepare(UUID tx, CompoundTag payload) {
        guard();
        Objects.requireNonNull(tx);
        Objects.requireNonNull(payload);
        if (payload.contains("committed") || payload.contains("aborted") || payload.contains("__tx"))
            throw new IllegalArgumentException("reserved journal field");
        var existing = journal.get(tx);
        if (existing != null) {
            if (!matches(tx, payload)) throw new IllegalStateException("transaction payload conflict: " + tx);
            if (existing.getBoolean("aborted")) throw new IllegalStateException("transaction cancelled: " + tx);
            return;
        }
        var t = payload.copy();
        t.putBoolean("committed", false);
        journal.put(tx, t);
        trim();
        setDirty();
    }

    public synchronized void markCommitted(UUID tx) {
        guard();
        var t = journal.get(tx);
        if (t == null || t.getBoolean("aborted")) throw new IllegalStateException("transaction not prepared: " + tx);
        t.putBoolean("committed", true);
        trim();
        setDirty();
    }

    public synchronized List<UUID> pending() {
        var out = new ArrayList<UUID>();
        journal.forEach((k, v) -> { if (!v.getBoolean("committed") && !v.getBoolean("aborted")) out.add(k); });
        return out;
    }

    /** Ordered recovery source, including durable decisions with potentially stale projections. */
    /** Bounded insertion-order cursor for incremental recovery. A concurrent new PREPARE invalidates
     * the iterator; the next call restarts safely because recovery is idempotent. No full list copy. */
    public static final class RecoveryCursor {
        private Iterator<Map.Entry<UUID, CompoundTag>> iterator;
    }
    public synchronized List<UUID> recoveryPage(RecoveryCursor cursor, int visits, String kind) {
        Objects.requireNonNull(cursor); Objects.requireNonNull(kind);
        if (visits < 1 || visits > 8) throw new IllegalArgumentException("recovery visit budget");
        var out = new ArrayList<UUID>();
        if (cursor.iterator == null) cursor.iterator = journal.entrySet().iterator();
        try {
            for (int i = 0; i < visits; i++) {
                if (!cursor.iterator.hasNext()) { cursor.iterator = null; break; }
                var entry = cursor.iterator.next();
                if (kind.equals(entry.getValue().getString("kind"))) out.add(entry.getKey());
            }
        } catch (ConcurrentModificationException changed) { cursor.iterator = null; }
        return List.copyOf(out);
    }

    public synchronized List<UUID> recoverable() {
        var out = new ArrayList<UUID>();
        journal.forEach((id, entry) -> { if (!entry.getBoolean("aborted")) out.add(id); });
        return List.copyOf(out);
    }

    public synchronized boolean matches(UUID tx, CompoundTag payload) {
        var stored = journal.get(tx);
        if (stored == null) return false;
        var body = stored.copy();
        body.remove("committed");
        body.remove("aborted");
        return body.equals(payload);
    }

    public synchronized boolean aborted(UUID tx) {
        var stored = journal.get(tx);
        return stored != null && stored.getBoolean("aborted");
    }

    public synchronized void abort(UUID tx) {
        guard();
        var stored = journal.get(tx);
        if (stored == null || stored.getBoolean("committed")) return;
        stored.putBoolean("aborted", true);
        setDirty();
    }

    /** Persist intent before the first owner projection. Same UUID/payload retries also flush. */
    public synchronized void prepareDurably(UUID tx, CompoundTag payload) {
        requireAttachedServer();
        prepare(tx, payload);
        flushDurably();
    }

    /** Persist the decision before returning a successful commit receipt. */
    public synchronized void commitDurably(UUID tx) {
        requireAttachedServer();
        markCommitted(tx);
        flushDurably();
    }

    /** Call only before any projection; committed operations cannot be refunded. */
    public synchronized void abortDurably(UUID tx) {
        requireAttachedServer();
        if (committed(tx)) throw new IllegalStateException("committed transaction cannot be cancelled");
        if (journalEntry(tx).isEmpty()) throw new IllegalStateException("unknown transaction");
        abort(tx);
        flushDurably();
    }

    /** Zero-projection rejection: publish UUID binding and aborted state in one durable image.
     * Never use for an existing prepared operation: it may already have partial projections. */
    public synchronized void rejectDurably(UUID tx, CompoundTag payload) {
        requireAttachedServer();
        guard();
        var rejected = payload.copy(); rejected.putBoolean("committed", false); rejected.putBoolean("aborted", true);
        org.goldgom.chemicalelements.instance.MultiOwnerJournal.validateStored(tx, rejected,
                ServerLifecycleHooks.getCurrentServer().registryAccess());
        if (journal.containsKey(tx)) {
            if (!matches(tx, payload) || !aborted(tx) || committed(tx))
                throw new IllegalStateException("cannot reject an existing non-rejected operation");
        } else {
            prepare(tx, payload);
            abort(tx);
        }
        flushDurably();
    }

    private void requireAttachedServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null || !server.isSameThread() || get() != this)
            throw new IllegalStateException("durable journal requires its authoritative server thread");
    }

    private void trim() { /* Do not forget terminal UUID/payload bindings. */ }

    // ------------------------------------------------------------ quarantine

    public synchronized void quarantine(String where, String payload) {
        guard();
        quarantine.putIfAbsent(where + "#" + Integer.toHexString(payload.hashCode()), payload);
        setDirty();
    }

    public synchronized Map<String, String> quarantined() { return Map.copyOf(quarantine); }

    // ------------------------------------------------------------ persistence

    /**
     * T1 write barrier for this SAME SavedData/journal, never an auxiliary transaction log.
     * Must complete before a caller projects any prepared operation into inventories/chunks.
     * Detached tests are deliberately not a successful persistence target.
     */
    public synchronized void flushDurably() {
        guard();
        requireAttachedServer();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat");
        // Force a write even for a retry of a known UUID. An earlier failed write must never
        // turn into success just because the in-memory prepared entry already exists.
        setDirty();
        save(path.toFile(), server.registryAccess());
    }

    /**
     * NeoForge SavedData.save queues an asynchronous task and logs/swallow IOExceptions.
     * Override only this ledger so ordinary autosaves cannot race a newer explicit barrier.
     * Same monitor as every journal mutation; dirty is cleared ONLY after atomic publication.
     */
    @Override
    public synchronized void save(File file, HolderLookup.Provider provider) {
        if (!isDirty()) return;
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), provider));
        NbtUtils.addCurrentDataVersion(root);
        try {
            writeDurableImage(file.toPath(), root);
            setDirty(false);
        } catch (IOException ex) {
            // No rollback to a guessed state: a failed publication can have uncertain effects.
            // Keep dirty and propagate. The transaction caller must not apply or acknowledge.
            throw new UncheckedIOException("chemistry journal durability barrier failed", ex);
        }
    }

    /** Package-visible for disk fault/reload tests. Atomic move is required, not a silent fallback. */
    static void writeDurableImage(Path target, CompoundTag root) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, NAME + "-", ".pending");
        // On failure retain the uncommitted image for diagnosis; it is never loaded as journal.
        NbtIo.writeCompressed(root, temporary);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        Files.move(temporary, target.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        // Force the published file too. Directory fsync is not portable on Windows; this
        // guarantees process-crash ordering, not a claim about every disk's power-loss cache.
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (unknownFuture != null) return unknownFuture.copy(); // never downgrade a newer world file
        tag.putInt("schema_version", SCHEMA);
        tag.putLong("serial", serial);
        var s = new CompoundTag();
        sinks.forEach((k, v) -> {
            var e = new CompoundTag();
            v.amounts().forEach((sp, n) -> e.putString(sp.toString(), n.toString()));
            e.putLong("__count", sinkCounts.getOrDefault(k, 0L));
            s.put(k, e);
        });
        tag.put("sinks", s);
        var j = new ListTag();
        journal.forEach((k, v) -> { var e = v.copy(); e.putUUID("__tx", k); j.add(e); });
        tag.put("journal", j);
        var q = new CompoundTag();
        quarantine.forEach((k, v) -> q.put(k, StringTag.valueOf(v)));
        tag.put("quarantine", q);
        return tag;
    }

    public static SinkLedger load(CompoundTag tag, HolderLookup.Provider provider) {
        var d = new SinkLedger();
        int v = tag.getInt("schema_version");
        if (v != SCHEMA) { d.unknownFuture = tag.copy(); d.serial = Math.max(1, tag.getLong("serial")) + 1_000_000; return d; }
        try {
        d.serial = Math.max(1, tag.getLong("serial"));
        var s = tag.getCompound("sinks");
        for (var k : s.getAllKeys()) {
            var e = s.getCompound(k);
            var m = new TreeMap<ResourceLocation, MolAmount>();
            for (var sp : e.getAllKeys()) if (!sp.equals("__count")) m.put(ResourceLocation.parse(sp), storedAmount(e.getString(sp)));
            d.sinks.put(k, new Composition(m));
            d.sinkCounts.put(k, e.getLong("__count"));
        }
        for (Tag t : tag.getList("journal", Tag.TAG_COMPOUND)) {
            var e = ((CompoundTag) t).copy();
            if (!e.hasUUID("__tx")) throw new IllegalArgumentException("journal entry lacks UUID");
            var id = e.getUUID("__tx");
            e.remove("__tx");
            if (org.goldgom.chemicalelements.instance.MultiOwnerJournal.KIND.equals(e.getString("kind")))
                org.goldgom.chemicalelements.instance.MultiOwnerJournal.validateStored(id, e, provider);
            if (org.goldgom.chemicalelements.instance.MachineJournal.KIND.equals(e.getString("kind")))
                org.goldgom.chemicalelements.instance.MachineJournal.validateStored(id, e, provider);
            if (org.goldgom.chemicalelements.instance.materialtx.MaterialJournal.KIND.equals(e.getString("kind")))
                org.goldgom.chemicalelements.instance.materialtx.MaterialJournal.validateStored(id, e);
            if (d.journal.putIfAbsent(id, e) != null) throw new IllegalArgumentException("duplicate journal UUID");
        }
        var q = tag.getCompound("quarantine");
        for (var k : q.getAllKeys()) d.quarantine.put(k, q.getString(k));
        return d;
        } catch (RuntimeException ex) {
            // Do not throw into DimensionDataStorage's new-empty fallback. Preserve the entire
            // original, including valid siblings and pending journal, and disable mutations.
            d.unknownFuture = tag.copy();
            return d;
        }
    }

    private static MolAmount storedAmount(String text) {
        if (text.isEmpty() || text.length() > 16384) throw new IllegalArgumentException("stored mol encoding budget exceeded");
        int dot = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '.' && dot < 0) dot = i;
            else if (ch < '0' || ch > '9') throw new IllegalArgumentException("invalid stored mol");
        }
        if (dot == 0 || dot == text.length() - 1 || (dot >= 0 && text.length() - dot - 1 > MolAmount.SCALE))
            throw new IllegalArgumentException("invalid stored mol scale");
        return MolAmount.parseDecimal(text);
    }

    /** Test helper: decode a record list stored as LedgerJson text. */
    static List<InstanceRecord> records(String json) { return LedgerJson.decode(JsonParser.parseString(json).getAsJsonObject()).instances(); }
}

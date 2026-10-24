package org.goldgom.chemicalelements.persistence;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real disk tests, not a substitute for the subsequent owner/crash-boundary matrix. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class JournalDurabilityGameTests {
    @GameTest(template = "core_empty")
    public static void journalDiskBarrier(GameTestHelper helper) throws Exception {
        // Isolated newly-created test directory; never open or replace a user's world journal.
        var dir = Files.createTempDirectory("chemicalelements-journal-test-");
        var file = dir.resolve(SinkLedger.NAME + ".dat");
        var ledger = new SinkLedger();
        var operation = UUID.randomUUID();
        var payload = new CompoundTag();
        payload.putString("kind", "disk-barrier-test");
        payload.putString("exact", "123456789012345678901234567890.000000000000000001");
        ledger.prepare(operation, payload);
        ledger.save(file.toFile(), helper.getLevel().registryAccess());
        helper.assertTrue(!ledger.isDirty(), "successful synchronous publication clears dirty");
        var stored = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        var restored = SinkLedger.load(stored.getCompound("data"), helper.getLevel().registryAccess());
        helper.assertTrue(restored.matches(operation, payload) && !restored.committed(operation), "prepared UUID/payload survives disk reload");
        restored.markCommitted(operation);
        restored.save(file.toFile(), helper.getLevel().registryAccess());
        var committed = SinkLedger.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess());
        helper.assertTrue(committed.committed(operation) && committed.matches(operation, payload), "committed receipt survives same-file replacement");
        var conflict = payload.copy(); conflict.putString("exact", "0");
        boolean rejected = false;
        try { committed.prepare(operation, conflict); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected && committed.matches(operation, payload), "UUID payload conflict preserves original after reload");
        helper.succeed();
    }

    @GameTest(template = "core_empty")
    public static void committedWaterProjectionRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        var data = WorldChemistryStore.data(level, pos).orElseThrow();
        helper.assertTrue(data.water(pos.asLong()).isEmpty(), "fresh test position");
        var before = data.write();
        var cell = new ChunkChemistry.WaterCell(java.math.BigInteger.TEN,
                new java.util.TreeMap<>(), java.math.BigInteger.ZERO,
                java.math.BigInteger.ONE, "journal-recovery-test", 0);
        var changes = java.util.List.of(new WorldChemistryStore.WaterChange(pos, -1, cell));
        var operation = UUID.randomUUID();
        helper.assertTrue(WorldChemistryStore.commitWater(level, operation, changes), "real durable commit");
        helper.assertTrue(SinkLedger.get().committed(operation), "journal decision exists");
        // Simulate the precise crash window: durable journal, pre-commit chunk attachment image.
        var chunk = WorldChemistryStore.loadedChunk(level, pos).orElseThrow();
        chunk.setData(ChemistryComponents.CHUNK.get(), ChunkChemistry.read(before));
        helper.assertTrue(WorldChemistryStore.recover(level) >= 1, "committed stale attachment recovered");
        helper.assertTrue(WorldChemistryStore.water(level, pos).orElseThrow().equals(cell), "exact post image restored");
        long revision = WorldChemistryStore.data(level, pos).orElseThrow().revision();
        WorldChemistryStore.recover(level);
        helper.assertTrue(WorldChemistryStore.data(level, pos).orElseThrow().revision() == revision, "recovery is idempotent");
        var conflict = new ChunkChemistry.WaterCell(java.math.BigInteger.ONE,
                new java.util.TreeMap<>(), java.math.BigInteger.ZERO,
                java.math.BigInteger.ONE, "conflict", 0);
        WorldChemistryStore.data(level, pos).orElseThrow().putWater(pos.asLong(), conflict);
        helper.assertTrue(!WorldChemistryStore.commitWater(level, operation, changes), "equal revision different payload is not acknowledged");
        WorldChemistryStore.recover(level);
        helper.assertTrue(WorldChemistryStore.water(level, pos).orElseThrow().equals(conflict), "conflict not overwritten");
        // Restore the recorded post image, not the input, at the end of this isolated test.
        WorldChemistryStore.data(level, pos).orElseThrow().putWater(pos.asLong(), cell);
        helper.succeed();
    }

    @GameTest(template = "core_empty")
    public static void journalWriteFailureKeepsDirty(GameTestHelper helper) throws Exception {
        var dir = Files.createTempDirectory("chemicalelements-journal-fault-");
        var blocker = Files.createFile(dir.resolve("not-a-directory"));
        var ledger = new SinkLedger();
        var operation = UUID.randomUUID();
        var payload = new CompoundTag(); payload.putString("kind", "fault-test");
        ledger.prepare(operation, payload);
        boolean failed = false;
        try { ledger.save(blocker.resolve("journal.dat").toFile(), helper.getLevel().registryAccess()); }
        catch (UncheckedIOException expected) { failed = true; }
        helper.assertTrue(failed && ledger.isDirty() && !ledger.committed(operation), "IO failure propagates; dirty prepared state not acknowledged");
        var file = dir.resolve("retry.dat");
        ledger.save(file.toFile(), helper.getLevel().registryAccess());
        var recovered = SinkLedger.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"), helper.getLevel().registryAccess());
        helper.assertTrue(recovered.matches(operation, payload), "retry publishes original UUID/payload");
        ledger.markCommitted(operation);
        failed = false;
        try { ledger.save(blocker.resolve("journal.dat").toFile(), helper.getLevel().registryAccess()); }
        catch (UncheckedIOException expected) { failed = true; }
        helper.assertTrue(failed && ledger.isDirty() && ledger.committed(operation), "failed decision flush remains dirty and cannot imply durable success");
        helper.assertTrue(!SinkLedger.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"),
                helper.getLevel().registryAccess()).committed(operation), "disk still prepared before retry");
        ledger.save(file.toFile(), helper.getLevel().registryAccess());
        helper.assertTrue(SinkLedger.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"),
                helper.getLevel().registryAccess()).committed(operation), "retry publishes committed decision");
        helper.succeed();
    }
}

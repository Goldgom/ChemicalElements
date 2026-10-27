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
    @GameTest(template="core_empty",timeoutTicks=200)
    public static void legacyWaterHistoryLoadsPackedAndRetainsExactReplay(GameTestHelper helper)throws Exception{
        var root=new CompoundTag();root.putInt("schema_version",1);var entries=new net.minecraft.nbt.ListTag();
        var tx=UUID.randomUUID();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        var cell=new ChunkChemistry.WaterCell(java.math.BigInteger.TEN,new java.util.TreeMap<>(),java.math.BigInteger.ZERO,java.math.BigInteger.TEN,"legacy-memory-test",0);
        var payload=WorldChemistryStore.encode(helper.getLevel(),java.util.List.of(new WorldChemistryStore.WaterChange(pos,-1,cell)));
        var entry=payload.copy();entry.putUUID("__tx",tx);entry.putBoolean("committed",true);entries.add(entry);root.put("journal",entries);
        var cold=SinkLedger.load(root,helper.getLevel().registryAccess());
        helper.assertTrue(!cold.readOnly()&&cold.committed(tx)&&cold.matches(tx,payload),"legacy exact UUID/payload survives compression without changing source input");
        helper.assertTrue(root.getList("journal",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).hasUUID("__tx"),"migration leaves its input untouched");
        var packed=cold.save(new CompoundTag(),helper.getLevel().registryAccess());
        helper.assertTrue(packed.getInt("schema_version")==2&&packed.getList("journal",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).contains("__packed_water"),"checkpoint retains compressed history rather than expanded change trees");
        var again=SinkLedger.load(packed,helper.getLevel().registryAccess());
        helper.assertTrue(!again.readOnly()&&again.matches(tx,payload)&&again.journalEntry(tx).orElseThrow().getList("changes",net.minecraft.nbt.Tag.TAG_COMPOUND).equals(payload.getList("changes",net.minecraft.nbt.Tag.TAG_COMPOUND)),"packed reload restores exact post-images");
        String snapshot=System.getenv("CE_RECOVERY_SNAPSHOT");
        if(snapshot!=null&&!snapshot.isBlank())verifyLargeLegacySnapshot(helper,java.nio.file.Path.of(snapshot));
        helper.succeed();
    }
    private static void verifyLargeLegacySnapshot(GameTestHelper helper,java.nio.file.Path snapshot)throws Exception{
        var tag=NbtIo.readCompressed(snapshot,NbtAccounter.unlimitedHeap()).getCompound("data");
        int expected=tag.getList("journal",net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        var cold=SinkLedger.load(tag,helper.getLevel().registryAccess());tag=null;
        helper.assertTrue(!cold.readOnly()&&cold.recoverable().size()==expected,"actual legacy snapshot loads without losing receipts");
        cold.attachWaterJournal(snapshot.resolveSibling(SinkLedger.NAME+"-water.wal"));
        int merged=cold.recoverable().size();
        var file=Files.createTempDirectory("chemicalelements-large-history-").resolve("compact.dat");
        cold.save(file.toFile(),helper.getLevel().registryAccess());cold=null;
        var reloaded=SinkLedger.load(NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()).getCompound("data"),helper.getLevel().registryAccess());
        helper.assertTrue(!reloaded.readOnly()&&reloaded.recoverable().size()==merged,"actual legacy snapshot plus WAL survives compact checkpoint/reload");
        System.out.println("Large legacy history recovery passed: snapshot receipts="+expected+", merged receipts="+merged+", compact bytes="+Files.size(file));
    }
    @GameTest(template="core_empty")
    public static void waterAppendSurvivesStaleCheckpointAndMergesIdempotently(GameTestHelper helper)throws Exception{
        var dir=Files.createTempDirectory("chemicalelements-water-wal-");var snapshot=dir.resolve("checkpoint.dat");
        var level=helper.getLevel();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        var ledger=SinkLedger.get();ledger.flushDurably();
        var data=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
        var authoritative=data.resolve(SinkLedger.NAME+".dat");var wal=data.resolve(SinkLedger.NAME+"-water.wal");
        Files.copy(authoritative,snapshot);
        byte[] before=Files.readAllBytes(snapshot);
        var cell=new ChunkChemistry.WaterCell(java.math.BigInteger.TEN,new java.util.TreeMap<>(),java.math.BigInteger.ZERO,java.math.BigInteger.TEN,"append-test",0);
        var payload=WorldChemistryStore.encode(level,java.util.List.of(new WorldChemistryStore.WaterChange(pos,-1,cell)));var tx=UUID.randomUUID();
        helper.assertTrue(WorldChemistryStore.commitWater(level,tx,WorldChemistryStore.decode(payload)),"production append commit");
        helper.assertTrue(java.util.Arrays.equals(before,Files.readAllBytes(authoritative)),"flow commit must not rewrite the historical checkpoint");
        var cold=SinkLedger.load(NbtIo.readCompressed(snapshot,NbtAccounter.unlimitedHeap()).getCompound("data"),level.registryAccess());
        helper.assertTrue(!cold.committed(tx),"checkpoint deliberately predates this flow");cold.attachWaterJournal(wal);
        helper.assertTrue(cold.committed(tx)&&cold.decisionDurable(tx)&&cold.matches(tx,payload),"a fresh ledger restores the exact durable decision from only its append frame");
        helper.assertTrue(cold.waterOperationsForChunk(level.dimension().location().toString(),net.minecraft.world.level.ChunkPos.asLong(pos)).contains(tx),"replay rebuilds the per-chunk recovery index");
        cold.save(snapshot.toFile(),level.registryAccess());
        var checkpointed=SinkLedger.load(NbtIo.readCompressed(snapshot,NbtAccounter.unlimitedHeap()).getCompound("data"),level.registryAccess());
        int count=checkpointed.recoverable().size();checkpointed.attachWaterJournal(wal);
        helper.assertTrue(checkpointed.recoverable().size()==count&&checkpointed.matches(tx,payload),"snapshot plus already-checkpointed WAL merges idempotently");helper.succeed();
    }
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

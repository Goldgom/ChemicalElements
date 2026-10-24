package org.goldgom.chemicalelements.instance.materialtx;

import java.io.IOException;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.*;

/** Actual player serializer + real temporary files; no fake owner or material journal.
 * Fault injection tests process-visible publication only. Directory force is explicitly injected
 * here for portability; these tests cannot certify native power-loss durability on Windows.
 */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class MaterialPlayerFileGameTests {
    private MaterialPlayerFileGameTests() {}
    @GameTest(template="core_empty")
    public static void fullPlayerNbtSurvivesPublication(GameTestHelper h) throws IOException {
        var player = h.makeMockServerPlayerInLevel();
        var tag = player.saveWithoutId(new CompoundTag());
        tag.putString("test_non_inventory_field", "must survive complete publication");
        Path directory = testDirectory("full-");
        MaterialPlayerFilePublication.publish(directory, player.getUUID(), tag, io(null, false));
        h.assertTrue(NbtIo.readCompressed(directory.resolve(player.getStringUUID()+".dat"), NbtAccounter.unlimitedHeap()).equals(tag),
                "all player fields must be preserved, not only inventory");
        h.succeed();
    }
    @GameTest(template="core_empty")
    public static void replaceFailureKeepsOldAuthoritativeImage(GameTestHelper h) throws IOException {
        var player = h.makeMockServerPlayerInLevel(); var before = player.saveWithoutId(new CompoundTag());
        var after = before.copy(); after.putString("test_non_inventory_field", "new");
        Path directory = testDirectory("replace-failure-");
        Path target = directory.resolve(player.getStringUUID()+".dat"); NbtIo.writeCompressed(before, target);
        boolean failed = false;
        try { MaterialPlayerFilePublication.publish(directory, player.getUUID(), after, io(null, true)); }
        catch (IOException expected) { failed = true; }
        h.assertTrue(failed, "replace failure must propagate");
        h.assertTrue(NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()).equals(before), "old image remains authoritative");
        h.succeed();
    }
    @GameTest(template="core_empty")
    public static void failureAfterReplaceIsNotRollbackOrSuccess(GameTestHelper h) throws IOException {
        var player = h.makeMockServerPlayerInLevel(); var before = player.saveWithoutId(new CompoundTag());
        var after = before.copy(); after.putString("test_non_inventory_field", "new");
        Path directory = testDirectory("force-failure-");
        Path target = directory.resolve(player.getStringUUID()+".dat"); NbtIo.writeCompressed(before, target);
        boolean failed = false;
        try { MaterialPlayerFilePublication.publish(directory, player.getUUID(), after,
                io(MaterialPlayerFilePublication.Step.REPLACED, false)); }
        catch (IOException expected) { failed = true; }
        h.assertTrue(failed, "post-replace failure must propagate, even with new file visible");
        h.assertTrue(NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()).equals(after), "do not blindly restore BEFORE");
        h.succeed();
    }
    private static Path testDirectory(String prefix) throws IOException {
        Path root = Path.of("E:/Projects/ChemicalElements/run/material-player-io-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix);
    }
    private static MaterialPlayerFilePublication.Io io(MaterialPlayerFilePublication.Step failure, boolean failReplace) {
        return new MaterialPlayerFilePublication.Io() {
            public void forceFile(Path path) throws IOException { MaterialPlayerFilePublication.SYSTEM.forceFile(path); }
            public void forceDirectory(Path path) { /* test fixture only, not durability evidence */ }
            public void replace(Path source, Path target) throws IOException {
                if (failReplace) throw new IOException("injected replace failure");
                MaterialPlayerFilePublication.SYSTEM.replace(source, target);
            }
            public void reached(MaterialPlayerFilePublication.Step step) throws IOException {
                if (step == failure) throw new IOException("injected failure at " + step);
            }
        };
    }
}

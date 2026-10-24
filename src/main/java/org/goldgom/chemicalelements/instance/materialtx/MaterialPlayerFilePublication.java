package org.goldgom.chemicalelements.instance.materialtx;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.*;

/** Strict ORIGINAL player.dat publication primitive; no second journal or inventory.
 * The full tag must come from the controlled complete-player serializer, not an inventory tag.
 * This class is not a serializer and is not by itself a usable player transaction adapter.
 * Failure can occur AFTER atomic replacement: retain isolation and reconcile original journal;
 * never refund or restore old material blindly. Temporary/backup files are never recovery roots.
 */
final class MaterialPlayerFilePublication {
    private MaterialPlayerFilePublication() {}
    enum Step { ENCODED, TEMP_FORCED, BACKUP_FORCED, REPLACED, TARGET_FORCED, DIRECTORY_FORCED, VERIFIED }
    interface Io {
        void forceFile(Path path) throws IOException;
        void forceDirectory(Path path) throws IOException;
        void replace(Path source, Path target) throws IOException;
        void reached(Step step) throws IOException;
    }
    static final Io SYSTEM = new Io() {
        public void forceFile(Path path) throws IOException {
            try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
        }
        public void forceDirectory(Path path) throws IOException {
            // No silent fallback. Windows/provider refusal is a failed durability barrier,
            // not proof of successful persistence or a reason to release an owner's lease.
            try (var channel = FileChannel.open(path, StandardOpenOption.READ)) { channel.force(true); }
        }
        public void replace(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        public void reached(Step step) {}
    };

    static void publish(Path directory, UUID player, CompoundTag completePlayer, Io io) throws IOException {
        Objects.requireNonNull(directory); Objects.requireNonNull(player); Objects.requireNonNull(io);
        var expected = Objects.requireNonNull(completePlayer).copy();
        if (!expected.hasUUID("UUID") || !expected.getUUID("UUID").equals(player)
                || !expected.contains("Inventory", Tag.TAG_LIST) || !expected.contains("Pos", Tag.TAG_LIST))
            throw new IllegalArgumentException("COMPLETE_PLAYER_NBT_REQUIRED");
        Path dir = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dir))
            throw new IOException("PLAYER_DIRECTORY_NOT_REGULAR");
        Path target = dir.resolve(player + ".dat");
        if (Files.isSymbolicLink(target)) throw new IOException("PLAYER_TARGET_SYMLINK");
        // Probe the required directory barrier before changing the authoritative target.
        io.forceDirectory(dir);
        Path temporary = Files.createTempFile(dir, player + "-material-", ".dat");
        NbtIo.writeCompressed(expected, temporary);
        if (!NbtIo.readCompressed(temporary, NbtAccounter.unlimitedHeap()).equals(expected))
            throw new IOException("PLAYER_TEMP_NBT_MISMATCH");
        io.reached(Step.ENCODED);
        io.forceFile(temporary); io.reached(Step.TEMP_FORCED);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("PLAYER_TARGET_NOT_REGULAR");
            // Unique diagnostic backup; never delete or overwrite the user's existing .dat_old.
            Path backup = dir.resolve(player + "-material-before-" + UUID.randomUUID() + ".dat");
            Files.copy(target, backup);
            io.forceFile(backup); io.forceDirectory(dir);
        }
        io.reached(Step.BACKUP_FORCED);
        io.replace(temporary, target); io.reached(Step.REPLACED);
        io.forceFile(target); io.reached(Step.TARGET_FORCED);
        io.forceDirectory(dir); io.reached(Step.DIRECTORY_FORCED);
        if (!NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()).equals(expected))
            throw new IOException("PLAYER_PUBLISHED_NBT_MISMATCH");
        io.reached(Step.VERIFIED);
    }
}

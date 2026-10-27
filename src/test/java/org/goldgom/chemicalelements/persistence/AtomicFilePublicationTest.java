package org.goldgom.chemicalelements.persistence;

import com.sun.nio.file.ExtendedOpenOption;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AtomicFilePublicationTest {
    @TempDir Path directory;

    @Test void repeatedReplacementPublishesCompleteImages() throws Exception {
        var target=directory.resolve("journal.dat");
        Files.writeString(target,"previous durable image");
        for(int revision=0;revision<100;revision++) {
            var image=Files.createTempFile(directory,"journal-",".pending");
            String contents="revision="+revision+"\n"+"payload".repeat(1000);
            Files.writeString(image,contents);
            AtomicFilePublication.replace(image,target);
            assertEquals(contents,Files.readString(target));
            assertFalse(Files.exists(image));
        }
    }

    @Test void windowsTemporaryDeleteLockRetriesTheSameImage() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var target=directory.resolve("journal.dat");var image=directory.resolve("prepared.pending");
        Files.writeString(target,"old receipt");Files.writeString(image,"new receipt");
        try(var lock=FileChannel.open(target,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE);
            var release=Executors.newSingleThreadScheduledExecutor()) {
            assertThrows(AccessDeniedException.class,()->Files.move(image,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING));
            assertEquals("old receipt",Files.readString(target));
            var unlocked=release.schedule(()->{lock.close();return null;},150,TimeUnit.MILLISECONDS);
            AtomicFilePublication.replace(image,target);
            unlocked.get();
            assertEquals("new receipt",Files.readString(target));
            assertFalse(Files.exists(image));
        }
    }

    @Test void windowsPersistentDeleteLockPreservesBothImagesAndReportsFailure() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var target=directory.resolve("journal.dat");var image=directory.resolve("prepared.pending");
        Files.writeString(target,"old receipt");Files.writeString(image,"new receipt");
        try(var lock=FileChannel.open(target,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE)) {
            assertThrows(AccessDeniedException.class,()->AtomicFilePublication.replace(image,target));
            assertEquals("old receipt",Files.readString(target));
            assertEquals("new receipt",Files.readString(image));
        }
    }

    @Test void interruptedRetryPreservesInterruptAndOriginalJournal() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var target=directory.resolve("journal.dat");var image=directory.resolve("prepared.pending");
        Files.writeString(target,"old receipt");Files.writeString(image,"new receipt");
        try(var lock=FileChannel.open(target,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE)) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(InterruptedIOException.class,()->AtomicFilePublication.replace(image,target));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertEquals("old receipt",Files.readString(target));
            assertEquals("new receipt",Files.readString(image));
        }
    }
}

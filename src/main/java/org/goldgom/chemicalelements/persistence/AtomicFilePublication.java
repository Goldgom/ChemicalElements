package org.goldgom.chemicalelements.persistence;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publishes the already flushed image without deleting the last durable journal. */
final class AtomicFilePublication {
    private static final int MAX_ATTEMPTS = 12;

    private AtomicFilePublication() {}

    static void replace(Path image, Path target) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(image, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AccessDeniedException denied) {
                // Windows readers (including file scanners) can briefly withhold delete sharing.
                // Retry the same atomic publication; never delete the old file or acknowledge it early.
                if (attempt == MAX_ATTEMPTS - 1) throw denied;
                try {
                    Thread.sleep(Math.min(10L << attempt, 200L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    var failure = new InterruptedIOException("Interrupted while publishing chemistry journal");
                    failure.initCause(interrupted);
                    failure.addSuppressed(denied);
                    throw failure;
                }
            }
        }
    }
}

package org.goldgom.chemicalelements.world.material;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Storage fixture tests, not actual world tests, subprocess death or power-failure evidence. */
class AtomicRegionPublisherTest {
    @TempDir Path directory;
    private byte[] payload(int length, int seed) {
        byte[] bytes = new byte[length]; new Random(seed).nextBytes(bytes); bytes[0] = 3; return bytes;
    }
    @Test void inlineExternalExternalInlineRetainsExactAuthority() throws Exception {
        var file = directory.resolve("r.0.0.mca");
        var publisher = new AtomicRegionPublisher(file, 0, 0);
        for (var bytes : List.of(payload(40, 1), payload(1_100_000, 2), payload(1_100_000, 3), payload(80, 4))) {
            var result = publisher.publish(0, 0, bytes, p -> {});
            assertFalse(result.directoryDurabilityConfirmed());
            assertArrayEquals(bytes, new AtomicRegionPublisher(file, 0, 0).read(0, 0));
        }
    }
    @Test void everyInjectedPublicationPointReopensOnlyCompleteOldOrNewImage() throws Exception {
        for (var point : AtomicRegionPublisher.Point.values()) {
            Path caseDir = Files.createDirectory(directory.resolve(point.name()));
            Path file = caseDir.resolve("r.0.0.mca");
            var publisher = new AtomicRegionPublisher(file, 0, 0);
            byte[] old = payload(1_100_000, 1), next = payload(1_100_000, 2);
            publisher.publish(0, 0, old, p -> {});
            assertThrows(IOException.class, () -> publisher.publish(0, 0, next, p -> {
                if (p == point) throw new IOException("injected:" + point);
            }));
            byte[] reopened = new AtomicRegionPublisher(file, 0, 0).read(0, 0);
            boolean after = point == AtomicRegionPublisher.Point.AFTER_RENAME
                    || point == AtomicRegionPublisher.Point.PUBLISHED_FILE_FORCED;
            assertArrayEquals(after ? next : old, reopened);
        }
    }
    @Test void deletingOneChunkPreservesItsNeighbour() throws Exception {
        var publisher = new AtomicRegionPublisher(directory.resolve("r.0.0.mca"), 0, 0);
        byte[] a = payload(40, 1), b = payload(50, 2);
        publisher.publish(0, 0, a, p -> {}); publisher.publish(1, 0, b, p -> {});
        publisher.publish(0, 0, null, p -> {});
        assertNull(publisher.read(0, 0)); assertArrayEquals(b, publisher.read(1, 0));
    }
    @Test void corruptExternalPayloadDoesNotBecomeEmptyChunk() throws Exception {
        var publisher = new AtomicRegionPublisher(directory.resolve("r.0.0.mca"), 0, 0);
        publisher.publish(0, 0, payload(1_100_000, 1), p -> {});
        Path external;
        try (var files = Files.list(directory)) { external = files.filter(p -> p.toString().endsWith(".mcc")).findFirst().orElseThrow(); }
        Files.write(external, new byte[] { 1, 2, 3 });
        assertThrows(IOException.class, () -> publisher.read(0, 0));
    }
}

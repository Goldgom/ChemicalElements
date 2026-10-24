package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Metadata/codec fixtures ONLY. These do not prove real player/world placement or phase success. */
@GameTestHolder("chemicalelements")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class MaterialIsolationGameTests {
    private MaterialIsolationGameTests() {}
    @GameTest(template = "core_empty")
    public static void reservationRejectsSameThreadObservation(GameTestHelper helper) {
        Object root = new Object();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "a".repeat(64), List.of(root));
        boolean rejected = false;
        try { MaterialObservationBarrier.requireObservable(root); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "same-thread observation must be rejected");
        lease.releaseUnprojected(); MaterialObservationBarrier.requireObservable(root); helper.succeed();
    }
    @GameTest(template = "core_empty")
    public static void partialLeaseCannotAutomaticallyRefund(GameTestHelper helper) {
        Object root = new Object();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "b".repeat(64), List.of(root));
        lease.beforeProjection(); boolean rejected = false;
        try { lease.releaseUnprojected(); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "partial lease cannot release as unprojected");
        // Fixture cleanup only. Production release is coordinated with durable all-owner evidence.
        lease.releaseCommitted(); helper.succeed();
    }
    @GameTest(template = "core_empty")
    public static void replacementChildrenRemainIsolatedWithOriginalAliases(GameTestHelper helper) {
        Object original = new Object(), replacement = new Object(), foreign = new Object();
        var lease = MaterialObservationBarrier.reserve(UUID.randomUUID(), "a".repeat(64), List.of(original));
        var other = MaterialObservationBarrier.reserve(UUID.randomUUID(), "b".repeat(64), List.of(foreign));
        lease.reserveReplacementChildren(List.of(replacement));
        for (Object root : List.of(original, replacement)) {
            boolean blocked = false;
            try { MaterialObservationBarrier.requireObservable(root); } catch (IllegalStateException expected) { blocked = true; }
            helper.assertTrue(blocked, "both old and newly published aliases must remain isolated");
        }
        boolean conflict = false;
        try { lease.reserveReplacementChildren(List.of(foreign)); } catch (IllegalStateException expected) { conflict = true; }
        helper.assertTrue(conflict, "foreign lease cannot be adopted");
        lease.releaseUnprojected(); other.releaseUnprojected(); helper.succeed();
    }
    @GameTest(template = "core_empty")
    public static void markerRejectsUnknownFieldsAndRetainsGlobalBinding(GameTestHelper helper) {
        var marker = new MaterialPendingMarker(UUID.randomUUID(), "c".repeat(64), UUID.randomUUID(),
                8, 9, "d".repeat(64), "e".repeat(64), MaterialPendingMarker.Phase.RESERVED);
        helper.assertTrue(MaterialPendingMarker.decode(marker.encode()).equals(marker), "marker roundtrip");
        helper.assertTrue(marker.projected().matches(marker.operation(), marker.globalHash()), "projection preserves global binding");
        var bad = marker.encode(); bad.putString("unexpected", "retained by owner quarantine");
        boolean rejected = false;
        try { MaterialPendingMarker.decode(bad); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "unknown metadata must not silently disappear"); helper.succeed();
    }
}

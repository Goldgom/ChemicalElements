package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Internal engine, not an untrusted public entry point. Production planners must authenticate,
 * validate bundle/reference and exact conservation before calling. No fake adapter is installed.
 * Any exception after PREPARE leaves a recoverable decision and retained isolation; never refund.
 */
final class MaterialCommitCoordinator {
    private MaterialCommitCoordinator() {}
    // In-process recovery metadata only, never an alternate material store. Retained on failure.
    private static final Map<SinkLedger, Map<UUID, Held>> ACTIVE = new IdentityHashMap<>();
    record Held(MaterialTransaction transaction, List<MaterialOwnerAccess> owners,
                List<MaterialLocationGuard> guards, MaterialObservationBarrier.Lease lease) {}

    static Held prepare(SinkLedger ledger, MaterialTransaction tx, List<MaterialOwnerAccess> supplied) {
        return prepare(ledger, tx, supplied, List.of());
    }
    static Held prepare(SinkLedger ledger, MaterialTransaction tx, List<MaterialOwnerAccess> supplied,
                        List<MaterialLocationGuard> guards) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || SinkLedger.get() != ledger)
            throw new IllegalStateException("AUTHORITATIVE_SERVER_REQUIRED");
        var bundle = org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle();
        if (tx.bundleEpoch() != bundle.epoch() || !tx.bundleHash().equals(bundle.catalog().contentHash()))
            throw new IllegalStateException("STALE_MATERIAL_BUNDLE");
        if (tx.action() == MaterialTransaction.Action.PHASE) {
            MaterialHeatValidation.validate(tx, bundle);
            throw new IllegalStateException("JOINT_PHASE_PLANNER_NOT_YET_INSTALLED");
        }
        MaterialTransferValidation.validate(tx, server.registryAccess());
        var owners = ordered(tx, supplied);
        var boundGuards = bindGuards(owners, guards);
        var previous = MaterialJournal.query(ledger, tx);
        if (previous.state() != MaterialJournal.State.UNKNOWN)
            throw new IllegalStateException("EXISTING_OPERATION_REQUIRES_RECONCILIATION:" + previous.state());
        var roots = new ArrayList<Object>();
        var physicalRoots = new IdentityHashMap<Object, MaterialOwnerAccess>();
        for (int i = 0; i < owners.size(); i++) marker(tx, i); // reject invalid lineage before PREPARE
        for (var owner : owners) {
            if (!owner.isLoaded()) throw new IllegalStateException("OWNER_UNAVAILABLE");
            owner.isolationGap().ifPresent(gap -> { throw new IllegalStateException("ISOLATION_GAP:" + gap); });
            for (Object root : owner.observationRoots()) {
                var prior = physicalRoots.putIfAbsent(root, owner);
                if (prior != null && prior != owner) throw new IllegalArgumentException("SHARED_ROOT_REQUIRES_PHYSICAL_NORMALIZATION");
                roots.add(root);
            }
        }
        var lease = MaterialObservationBarrier.reserve(tx.operation(), tx.hash(), roots);
        var held = new Held(tx, owners, boundGuards, lease);
        try {
            checkAll(held, false, MaterialLocationGuard.Boundary.PREPARE);
            requireAll(held, MaterialOwnerAccess.Position.BEFORE, false);
            // From the first durable write attempt onward its result may be uncertain.
            // Retain original roots even if no owner's marker has been installed yet.
            ACTIVE.computeIfAbsent(ledger, ignored -> new LinkedHashMap<>()).put(tx.operation(), held);
            lease.beforeProjection();
            ledger.prepareDurably(tx.operation(), MaterialJournal.payload(tx));
            // The durable I/O barrier may run callbacks. Every owner and the bundle are revalidated afterwards.
            if (org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.bundle() != bundle)
                throw new IllegalStateException("BUNDLE_CHANGED_AFTER_PREPARE");
            requireAll(held, MaterialOwnerAccess.Position.BEFORE, false);
        } catch (RuntimeException failure) {
            // No owner mutation yet. Journal may still contain PREPARE after a failed flush:
            // never rebind/replay a new UUID; recovery inspects that exact entry.
            if (retained(ledger, tx.operation()).isEmpty()) lease.releaseUnprojected();
            throw failure;
        }
        // Mark the lease non-cancellable BEFORE the first owner isolation write. Even a failure
        // in metadata persistence must leave roots blocked until explicit all-BEFORE recovery.
        ACTIVE.computeIfAbsent(ledger, ignored -> new LinkedHashMap<>()).put(tx.operation(), held);
        lease.beforeProjection();
        for (int i = 0; i < owners.size(); i++) {
            isolate(held, i, false);
            persist(held, i, false, MaterialOwnerAccess.Position.BEFORE);
        }
        return held;
    }

    static void forward(SinkLedger ledger, Held held, boolean recovery) {
        var tx = held.transaction();
        var receipt = MaterialJournal.query(ledger, tx);
        if (receipt.state() != MaterialJournal.State.PREPARED && receipt.state() != MaterialJournal.State.COMMITTED)
            throw new IllegalStateException("NO_RECOVERABLE_MATERIAL_PREPARE");
        // Classify all first. Never write one owner then discover another has a conflict.
        var positions = positions(held, recovery);
        for (var p : positions) if (p != MaterialOwnerAccess.Position.BEFORE && p != MaterialOwnerAccess.Position.AFTER)
            throw new IllegalStateException("MATERIAL_RECOVERY_BLOCKED:" + p);
        // A crash can occur after durable PREPARE but before any owner's RESERVED marker.
        // Establish/persist ALL remaining BEFORE reservations before the first material mutation.
        for (int i = 0; i < held.owners().size(); i++) {
            if (positions.get(i) == MaterialOwnerAccess.Position.BEFORE) {
                isolate(held, i, recovery);
                persist(held, i, recovery, MaterialOwnerAccess.Position.BEFORE);
            }
        }
        for (int i = 0; i < held.owners().size(); i++) {
            var owner = held.owners().get(i);
            if (positions.get(i) == MaterialOwnerAccess.Position.BEFORE) {
                check(held, i, recovery, MaterialOwnerAccess.Position.BEFORE, MaterialLocationGuard.Boundary.PROJECT_BEFORE);
                owner.project(tx.changes().get(i).after(), marker(tx, i).projected());
                check(held, i, recovery, MaterialOwnerAccess.Position.AFTER, MaterialLocationGuard.Boundary.PROJECT_AFTER);
            }
            persist(held, i, recovery, MaterialOwnerAccess.Position.AFTER); // retry AFTER must flush too
        }
        requireAll(held, MaterialOwnerAccess.Position.AFTER, recovery);
        ledger.commitDurably(tx.operation());
        // Keep all observation roots blocked until each owner's completed metadata is durable.
        for (int i = 0; i < held.owners().size(); i++) {
            check(held, i, recovery, MaterialOwnerAccess.Position.AFTER, MaterialLocationGuard.Boundary.COMPLETE_BEFORE);
            held.owners().get(i).complete(tx.operation(), tx.hash());
            check(held, i, recovery, MaterialOwnerAccess.Position.AFTER, MaterialLocationGuard.Boundary.COMPLETE_AFTER);
            persist(held, i, recovery, MaterialOwnerAccess.Position.AFTER);
        }
        held.lease().releaseCommitted();
        forget(ledger, tx.operation());
        for (var owner : held.owners()) owner.notifyCommitted(tx.operation(), tx.hash());
    }

    static void cancelAllBefore(SinkLedger ledger, Held held, boolean recovery) {
        var tx = held.transaction();
        var receipt = MaterialJournal.query(ledger, tx);
        if (receipt.state() != MaterialJournal.State.PREPARED && receipt.state() != MaterialJournal.State.CANCELLED)
            throw new IllegalStateException("CANNOT_CANCEL_MATERIAL_OPERATION");
        requireAll(held, MaterialOwnerAccess.Position.BEFORE, recovery);
        if (receipt.state() == MaterialJournal.State.PREPARED) ledger.abortDurably(tx.operation());
        else ledger.flushDurably();
        for (int i = 0; i < held.owners().size(); i++) {
            check(held, i, recovery, MaterialOwnerAccess.Position.BEFORE, MaterialLocationGuard.Boundary.CANCEL_BEFORE);
            held.owners().get(i).cancelIsolation(tx.operation(), tx.hash());
            check(held, i, recovery, MaterialOwnerAccess.Position.BEFORE, MaterialLocationGuard.Boundary.CANCEL_AFTER);
            persist(held, i, recovery, MaterialOwnerAccess.Position.BEFORE);
        }
        // No mutation is reversed; complete validation above established every full BEFORE root.
        held.lease().releaseCommitted();
        forget(ledger, tx.operation());
    }

    static Optional<Held> retained(SinkLedger ledger, UUID operation) {
        return Optional.ofNullable(ACTIVE.getOrDefault(ledger, Map.of()).get(operation));
    }
    private static void forget(SinkLedger ledger, UUID operation) {
        var entries = ACTIVE.get(ledger);
        if (entries != null) { entries.remove(operation); if (entries.isEmpty()) ACTIVE.remove(ledger); }
    }

    /** Attach loaded real roots to durable evidence after a restart, never mint or force-load. */
    static Held attachRecovery(SinkLedger ledger, MaterialTransaction tx, List<MaterialOwnerAccess> supplied) {
        return attachRecovery(ledger, tx, supplied, List.of());
    }
    static Held attachRecovery(SinkLedger ledger, MaterialTransaction tx, List<MaterialOwnerAccess> supplied,
                               List<MaterialLocationGuard> guards) {
        var existing = retained(ledger, tx.operation());
        if (existing.isPresent()) {
            if (!existing.get().transaction().hash().equals(tx.hash())) throw new IllegalStateException("UUID_PAYLOAD_CONFLICT");
            var ordered = ordered(tx, supplied);
            if (!sameInstances(ordered, existing.get().owners()) || !sameInstances(guards, existing.get().guards()))
                throw new IllegalStateException("RETAINED_RESOLUTION_MISMATCH");
            checkAll(existing.get(), true, MaterialLocationGuard.Boundary.ATTACH_RECOVERY);
            return existing.get();
        }
        var receipt = MaterialJournal.query(ledger, tx);
        if (receipt.state() != MaterialJournal.State.PREPARED && receipt.state() != MaterialJournal.State.COMMITTED
                && receipt.state() != MaterialJournal.State.CANCELLED) throw new IllegalStateException("NO_RECOVERY_EVIDENCE");
        var owners = ordered(tx, supplied); var roots = new ArrayList<Object>();
        var boundGuards = bindGuards(owners, guards);
        var physicalRoots = new IdentityHashMap<Object, MaterialOwnerAccess>();
        for (var owner : owners) {
            if (!owner.isLoaded()) throw new IllegalStateException("OWNER_UNAVAILABLE");
            owner.isolationGap().ifPresent(gap -> { throw new IllegalStateException("ISOLATION_GAP:" + gap); });
            for (Object root : owner.observationRoots()) {
                var prior = physicalRoots.putIfAbsent(root, owner);
                if (prior != null && prior != owner) throw new IllegalArgumentException("SHARED_ROOT_REQUIRES_PHYSICAL_NORMALIZATION");
                roots.add(root);
            }
        }
        var lease = MaterialObservationBarrier.reserve(tx.operation(), tx.hash(), roots);
        var held = new Held(tx, owners, boundGuards, lease);
        ACTIVE.computeIfAbsent(ledger, ignored -> new LinkedHashMap<>()).put(tx.operation(), held);
        lease.beforeProjection();
        checkAll(held, true, MaterialLocationGuard.Boundary.ATTACH_RECOVERY);
        // Conflicts remain isolated, not automatically released or overwritten.
        for (var p : positions(held, true)) if (p != MaterialOwnerAccess.Position.BEFORE && p != MaterialOwnerAccess.Position.AFTER)
            throw new IllegalStateException("RECOVERY_CONFLICT:" + p);
        return held;
    }

    private static MaterialPendingMarker marker(MaterialTransaction tx, int index) {
        var change = tx.changes().get(index);
        UUID incarnation = switch (change.before().owner()) {
            case org.goldgom.chemicalelements.api.tx.OwnerKey.PlayerInventory ignored -> UUID.fromString(
                    com.google.gson.JsonParser.parseString(change.before().canonicalRoot()).getAsJsonObject()
                            .get("persistent_incarnation").getAsString());
            case org.goldgom.chemicalelements.api.tx.OwnerKey.WorldBlock block -> block.incarnation();
            case org.goldgom.chemicalelements.api.tx.OwnerKey.FiniteHeatSource heat -> heat.incarnation();
            case org.goldgom.chemicalelements.api.tx.OwnerKey.MachineInventory machine -> machine.incarnation();
        };
        return new MaterialPendingMarker(tx.operation(), tx.hash(), incarnation,
                change.before().revision(), change.after().revision(), change.before().rootHash(), change.after().rootHash(),
                MaterialPendingMarker.Phase.RESERVED);
    }
    private static List<MaterialOwnerAccess> ordered(MaterialTransaction tx, List<MaterialOwnerAccess> supplied) {
        var unique = new HashMap<org.goldgom.chemicalelements.api.tx.OwnerKey, MaterialOwnerAccess>();
        for (var access : supplied) {
            if (access instanceof MaterialLocationGuard guard && guard.physicalAccess() != access)
                throw new IllegalArgumentException("LOCATION_HANDLE_IS_NOT_PHYSICAL_OWNER");
            var prior = unique.putIfAbsent(access.owner(), access);
            if (prior != null && prior != access) throw new IllegalArgumentException("AMBIGUOUS_PHYSICAL_OWNER_ACCESS");
        }
        if (unique.size() != tx.changes().size()) throw new IllegalArgumentException("owner set mismatch");
        var out = new ArrayList<MaterialOwnerAccess>();
        for (var change : tx.changes()) {
            var access = unique.get(change.before().owner());
            if (access == null) throw new IllegalArgumentException("owner resolution mismatch");
            out.add(access);
        }
        return List.copyOf(out);
    }
    private static List<MaterialOwnerAccess.Position> positions(Held held, boolean recovery) {
        var out = new ArrayList<MaterialOwnerAccess.Position>(); var tx = held.transaction();
        for (int i = 0; i < held.owners().size(); i++) {
            var owner = held.owners().get(i); var change = tx.changes().get(i);
            check(held, i, recovery, null, MaterialLocationGuard.Boundary.CLASSIFY_BEFORE);
            var position = !owner.isLoaded() ? MaterialOwnerAccess.Position.UNAVAILABLE
                    : owner.classify(tx.operation(), tx.hash(), change.before(), change.after(), recovery);
            if (position == MaterialOwnerAccess.Position.BEFORE || position == MaterialOwnerAccess.Position.AFTER)
                check(held, i, recovery, position, MaterialLocationGuard.Boundary.CLASSIFY_AFTER);
            out.add(position);
        }
        return List.copyOf(out);
    }
    private static boolean sameInstances(List<?> a, List<?> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }
    private static List<MaterialLocationGuard> bindGuards(List<MaterialOwnerAccess> owners, List<MaterialLocationGuard> guards) {
        for (var guard : guards) {
            Objects.requireNonNull(guard);
            if (owners.stream().noneMatch(owner -> owner == guard.physicalAccess()))
                throw new IllegalArgumentException("GUARD_PHYSICAL_INSTANCE_MISMATCH");
        }
        return List.copyOf(guards);
    }
    private static void check(Held held, int index, boolean recovery, MaterialOwnerAccess.Position expected,
                              MaterialLocationGuard.Boundary boundary) {
        var access = held.owners().get(index); var tx = held.transaction(); var change = tx.changes().get(index);
        for (var guard : held.guards()) if (guard.physicalAccess() == access)
            guard.validate(new MaterialLocationGuard.Check(access, tx.operation(), tx.hash(),
                    change.before(), change.after(), recovery, expected, boundary));
    }
    private static void checkAll(Held held, boolean recovery, MaterialLocationGuard.Boundary boundary) {
        for (int i = 0; i < held.owners().size(); i++) check(held, i, recovery, null, boundary);
    }
    private static void isolate(Held held, int i, boolean recovery) {
        check(held, i, recovery, MaterialOwnerAccess.Position.BEFORE, MaterialLocationGuard.Boundary.ISOLATE_BEFORE);
        held.owners().get(i).isolate(marker(held.transaction(), i));
        check(held, i, recovery, MaterialOwnerAccess.Position.BEFORE, MaterialLocationGuard.Boundary.ISOLATE_AFTER);
    }
    private static void persist(Held held, int i, boolean recovery, MaterialOwnerAccess.Position position) {
        check(held, i, recovery, position, MaterialLocationGuard.Boundary.PERSIST_BEFORE);
        held.owners().get(i).persist();
        check(held, i, recovery, position, MaterialLocationGuard.Boundary.PERSIST_AFTER);
    }
    private static void requireAll(Held held, MaterialOwnerAccess.Position expected, boolean recovery) {
        for (var actual : positions(held, recovery)) if (actual != expected)
            throw new IllegalStateException("MATERIAL_OWNER_NOT_" + expected + ":" + actual);
    }
}

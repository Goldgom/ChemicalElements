package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Internal bounded recovery driver. Runtime event registration remains with the service.
 * Resolver must use already-loaded authoritative roots only; no chunk loads or replacement stores.
 * The caller supplies a bounded page of UUIDs, avoiding SinkLedger.pending/recoverable full copies.
 */
final class MaterialRecoveryBatch {
    private MaterialRecoveryBatch() {}
    interface LoadedResolver {
        Optional<MaterialOwnerAccess> resolveLoaded(OwnerKey key);
        /** Resolve fresh trusted location evidence for the exact historical transaction.
         * Must include every thermal alias; do not return cached handles from an old load. */
        default List<MaterialLocationGuard> guards(MaterialTransaction tx, List<MaterialOwnerAccess> owners) {
            if (tx.action() == MaterialTransaction.Action.PHASE)
                throw new IllegalStateException("PHASE_RECOVERY_LOCATION_GUARDS_REQUIRED");
            return List.of();
        }
    }
    record Outcome(UUID operation, String status) {}
    static List<Outcome> run(SinkLedger ledger, List<UUID> page, LoadedResolver resolver) {
        if (page.size() > 8) throw new IllegalArgumentException("recovery page exceeds 8 operations");
        var results = new ArrayList<Outcome>();
        for (UUID id : page) {
            try {
                var entry = ledger.journalEntry(id);
                if (entry.isEmpty() || !MaterialJournal.KIND.equals(entry.get().getString("kind"))) {
                    results.add(new Outcome(id, "NOT_MATERIAL")); continue;
                }
                MaterialJournal.validateStored(id, entry.get());
                var tx = MaterialTransaction.decode(entry.get().getString("payload"));
                var held = MaterialCommitCoordinator.retained(ledger, id);
                if (held.isEmpty()) {
                    var accesses = new ArrayList<MaterialOwnerAccess>();
                    for (var change : tx.changes()) {
                        var resolved = resolver.resolveLoaded(change.before().owner());
                        if (resolved.isEmpty()) break;
                        accesses.add(resolved.get());
                    }
                    if (accesses.size() != tx.changes().size()) {
                        results.add(new Outcome(id, "WAIT_LOADED_OWNER")); continue;
                    }
                    held = Optional.of(MaterialCommitCoordinator.attachRecovery(ledger, tx, accesses,
                            resolver.guards(tx, List.copyOf(accesses))));
                }
                if (ledger.aborted(id)) MaterialCommitCoordinator.cancelAllBefore(ledger, held.get(), true);
                else MaterialCommitCoordinator.forward(ledger, held.get(), true);
                results.add(new Outcome(id, ledger.aborted(id) ? "CANCELLED" : "COMMITTED"));
            } catch (RuntimeException blocked) {
                results.add(new Outcome(id, "ISOLATED:" + blocked.getClass().getSimpleName() + ":" + blocked.getMessage()));
            }
        }
        return List.copyOf(results);
    }
}

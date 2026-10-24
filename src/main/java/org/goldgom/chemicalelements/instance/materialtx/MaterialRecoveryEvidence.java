package org.goldgom.chemicalelements.instance.materialtx;

import java.util.UUID;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Read-only proof from the ONE authoritative journal plus an active original-root lease.
 * This never issues a permit, changes a generation, relaxes image equality, or clears isolation.
 * Used when PREPARE preceded the first marker, or cancellation cleared RESERVED metadata.
 */
public final class MaterialRecoveryEvidence {
    private MaterialRecoveryEvidence() {}
    public static boolean matches(UUID operation, String hash, MaterialOwnerSnapshot before,
                                  MaterialOwnerSnapshot after, Object originalRoot) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || server.overworld() == null) return false;
        if (!MaterialObservationBarrier.isReservedFor(originalRoot, operation, hash)) return false;
        var ledger = SinkLedger.get();
        if (ledger.readOnly()) return false;
        var entry = ledger.journalEntry(operation);
        if (entry.isEmpty() || !MaterialJournal.KIND.equals(entry.get().getString("kind"))) return false;
        try {
            MaterialJournal.validateStored(operation, entry.get());
            var tx = MaterialTransaction.decode(entry.get().getString("payload"));
            return tx.operation().equals(operation) && tx.hash().equals(hash)
                    && tx.changes().stream().anyMatch(c -> c.before().equals(before) && c.after().equals(after));
        } catch (RuntimeException invalid) { return false; }
    }
}

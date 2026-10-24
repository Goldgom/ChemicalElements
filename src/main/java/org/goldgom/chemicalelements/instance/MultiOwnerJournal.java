package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** First schema3 vertical slice: durable zero-projection rejection in the SAME journal.
 * There is deliberately no success/reservation writer until real owner CAS/recovery is implemented.
 * A terminal rejection binds the global UUID and payload; it is not a refundable reservation.
 */
public final class MultiOwnerJournal {
    public static final String KIND = "multi_owner_rejected_v3";
    private MultiOwnerJournal() {}
    public enum State { UNKNOWN, REJECTED, CONFLICT, CORRUPT }
    public record Receipt(UUID operation, String payloadSha256, State state, String reason) {}

    public static Receipt query(SinkLedger ledger, MultiOwnerRequest request, HolderLookup.Provider registries) {
        String encoded = request.encode(registries), hash = TransactionEncoding.sha256(encoded);
        var stored = ledger.journalEntry(request.operation());
        if (ledger.readOnly()) return new Receipt(request.operation(), hash, State.CORRUPT, "JOURNAL_READ_ONLY");
        if (stored.isEmpty()) return new Receipt(request.operation(), hash, State.UNKNOWN, "UNKNOWN_OPERATION");
        var entry = stored.get();
        // Includes old machine/water/schema2 entries: never reinterpret them or create another UUID space.
        if (!KIND.equals(entry.getString("kind")))
            return new Receipt(request.operation(), hash, State.CONFLICT, "UUID_BOUND_TO_OTHER_PAYLOAD");
        try { validateStored(request.operation(), entry, registries); }
        catch (RuntimeException corrupt) { return new Receipt(request.operation(), hash, State.CORRUPT, "INVALID_SCHEMA3_RECEIPT"); }
        if (!encoded.equals(entry.getString("payload")) || !hash.equals(entry.getString("payload_sha256")))
            return new Receipt(request.operation(), hash, State.CONFLICT, "UUID_PAYLOAD_CONFLICT");
        return new Receipt(request.operation(), hash, State.REJECTED, entry.getString("reason"));
    }

    /** Used only after authenticating the server caller. Does not alter a single inventory slot. */
    public static Receipt rejectDurably(SinkLedger ledger, MultiOwnerRequest request,
            HolderLookup.Provider registries, String reason) {
        var prior = query(ledger, request, registries);
        if (prior.state() == State.CONFLICT || prior.state() == State.CORRUPT) return prior;
        if (prior.state() == State.REJECTED) {
            // An earlier failed publication cannot be acknowledged just because it exists in memory.
            ledger.flushDurably(); return prior;
        }
        if (!reason.matches("[A-Z][A-Z0-9_]{0,95}")) throw new IllegalArgumentException("invalid rejection reason");
        String encoded = request.encode(registries);
        MultiOwnerRequest.decode(encoded, registries); // admit only recoverably lossless requests
        var body = new CompoundTag(); body.putString("kind", KIND);
        body.putString("payload", encoded); body.putString("payload_sha256", TransactionEncoding.sha256(encoded));
        body.putString("reason", reason);
        ledger.rejectDurably(request.operation(), body);
        return query(ledger, request, registries);
    }

    /** Called during SavedData load; corrupt data must quarantine the whole original, not be dropped. */
    public static void validateStored(UUID id, CompoundTag entry, HolderLookup.Provider registries) {
        if (!entry.getAllKeys().equals(Set.of("kind", "payload", "payload_sha256", "reason", "committed", "aborted"))
                || !KIND.equals(entry.getString("kind")) || !entry.contains("payload", Tag.TAG_STRING)
                || !entry.contains("payload_sha256", Tag.TAG_STRING) || !entry.contains("reason", Tag.TAG_STRING)
                || !entry.contains("committed", Tag.TAG_BYTE) || !entry.contains("aborted", Tag.TAG_BYTE)
                || entry.getByte("committed") != 0 || entry.getByte("aborted") != 1
                || !entry.getString("reason").matches("[A-Z][A-Z0-9_]{0,95}"))
            throw new IllegalArgumentException("invalid schema3 rejected envelope");
        String encoded = entry.getString("payload");
        var request = MultiOwnerRequest.decode(encoded, registries);
        if (!id.equals(request.operation()) || !TransactionEncoding.sha256(encoded).equals(entry.getString("payload_sha256")))
            throw new IllegalArgumentException("schema3 UUID/hash mismatch");
    }
}

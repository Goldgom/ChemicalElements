package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.goldgom.chemicalelements.persistence.SinkLedger;
import org.goldgom.chemicalelements.instance.TransactionEncoding;

/** Single SinkLedger namespace. This class contains no inventory and no public success writer.
 * Coordinator integration must validate all real owners before using durable PREPARE. */
public final class MaterialJournal {
    public static final String KIND = "material_owners_v1";
    private MaterialJournal() {}
    public enum State { UNKNOWN, PREPARED, COMMITTED, CANCELLED, CONFLICT, CORRUPT }
    public record Receipt(UUID operation, String globalHash, State state) {}
    public static Receipt query(SinkLedger ledger, MaterialTransaction transaction) {
        String hash = transaction.hash();
        if (ledger.readOnly()) return new Receipt(transaction.operation(), hash, State.CORRUPT);
        var stored = ledger.journalEntry(transaction.operation());
        if (stored.isEmpty()) return new Receipt(transaction.operation(), hash, State.UNKNOWN);
        var tag = stored.get();
        // Includes multi_owner_rejected_v3: a terminal UUID can never become a material success.
        if (!KIND.equals(tag.getString("kind"))) return new Receipt(transaction.operation(), hash, State.CONFLICT);
        try { validateStored(transaction.operation(), tag); }
        catch (RuntimeException invalid) { return new Receipt(transaction.operation(), hash, State.CORRUPT); }
        if (!transaction.encode().equals(tag.getString("payload")))
            return new Receipt(transaction.operation(), hash, State.CONFLICT);
        return new Receipt(transaction.operation(), hash, tag.getBoolean("aborted") ? State.CANCELLED
                : tag.getBoolean("committed") ? State.COMMITTED : State.PREPARED);
    }
    static CompoundTag payload(MaterialTransaction transaction) {
        String encoded = transaction.encode(); MaterialTransaction.decode(encoded);
        var tag = new CompoundTag(); tag.putString("kind", KIND); tag.putString("payload", encoded);
        tag.putString("payload_sha256", TransactionEncoding.sha256(encoded)); return tag;
    }
    public static void validateStored(UUID operation, CompoundTag tag) {
        var fields = new HashSet<>(Set.of("kind", "payload", "payload_sha256", "committed"));
        if (tag.contains("aborted")) fields.add("aborted");
        if (!tag.getAllKeys().equals(fields) || !KIND.equals(tag.getString("kind"))
                || !tag.contains("kind", Tag.TAG_STRING) || !tag.contains("payload", Tag.TAG_STRING)
                || !tag.contains("payload_sha256", Tag.TAG_STRING) || !tag.contains("committed", Tag.TAG_BYTE)
                || (tag.getByte("committed") != 0 && tag.getByte("committed") != 1)
                || (tag.contains("aborted") && (!tag.contains("aborted", Tag.TAG_BYTE) || tag.getByte("aborted") != 1))
                || (tag.getBoolean("aborted") && tag.getBoolean("committed")))
            throw new IllegalArgumentException("invalid material journal envelope");
        String encoded = tag.getString("payload");
        var decoded = MaterialTransaction.decode(encoded);
        if (!operation.equals(decoded.operation()) || !TransactionEncoding.sha256(encoded).equals(tag.getString("payload_sha256")))
            throw new IllegalArgumentException("material journal UUID/hash mismatch");
    }
}

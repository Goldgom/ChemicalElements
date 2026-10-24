package org.goldgom.chemicalelements.instance.materialtx;

import java.util.*;
import com.google.gson.JsonParser;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.instance.TransactionEncoding;

/** Detached evidence from a real authoritative root, never authority to write.
 * canonicalRoot includes ALL state, not a reduced chemistry projection: player slot layout,
 * complete stacks/FIFO instances; world blockstate + full BE; thermal capacity/reference and
 * stored energy. Transaction metadata (pending and last operation/global hash) is EXCLUDED:
 * markers bind rootHash and cannot be hashed into that same root. Markers are independently
 * verified and persisted atomically WITH the business root. incarnation lives in OwnerKey, not liveGeneration.
 * The live generation changes each load. revision is persistent and monotonically increasing.
 */
public record MaterialOwnerSnapshot(OwnerKey owner, UUID liveGeneration, long revision,
                                    String canonicalRoot, String rootHash) {
    public MaterialOwnerSnapshot {
        Objects.requireNonNull(owner); Objects.requireNonNull(liveGeneration);
        Objects.requireNonNull(canonicalRoot);
        if (revision < 0 || canonicalRoot.length() > 16 * 1024 * 1024)
            throw new IllegalArgumentException("material snapshot budget/revision");
        if (!TransactionEncoding.canonical(JsonParser.parseString(canonicalRoot)).equals(canonicalRoot))
            throw new IllegalArgumentException("noncanonical owner root");
        if (!TransactionEncoding.sha256(canonicalRoot).equals(rootHash))
            throw new IllegalArgumentException("owner root digest mismatch");
    }
    public static MaterialOwnerSnapshot of(OwnerKey owner, UUID liveGeneration, long revision, String canonicalRoot) {
        return new MaterialOwnerSnapshot(owner, liveGeneration, revision, canonicalRoot,
                TransactionEncoding.sha256(canonicalRoot));
    }
}

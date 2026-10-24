package org.goldgom.chemicalelements.persistence;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.data.LedgerJson;

/**
 * Item component value. The encoded text is the single source of truth and is never rewritten
 * by a failed decode: an unknown/newer schema stays byte-identical (quarantined) instead of being
 * replaced by an empty or template ledger. Equality is textual so vanilla component equality,
 * hashing and network sync keep their full semantics.
 * Encoding: {@code z1:} + base64(deflate(LedgerJson)) or plain LedgerJson text (accepted on read).
 */
public final class LedgerPayload {
    public static final int MAX_CHARS = 1_048_576;
    private static final int MAX_INFLATED = 16 * 1_048_576;
    public static final Codec<LedgerPayload> CODEC = Codec.STRING.xmap(LedgerPayload::new, LedgerPayload::encoded);
    public static final StreamCodec<ByteBuf, LedgerPayload> STREAM_CODEC = ByteBufCodecs.stringUtf8(MAX_CHARS).map(LedgerPayload::new, LedgerPayload::encoded);

    private final String encoded;
    private volatile Optional<StackLedger> decoded;
    private volatile String error;

    public LedgerPayload(String encoded) {
        this.encoded = Objects.requireNonNull(encoded);
        if (encoded.length() > MAX_CHARS) throw new IllegalArgumentException("ledger payload too large");
    }

    public static LedgerPayload of(StackLedger ledger) {
        var payload = new LedgerPayload(compress(LedgerJson.encode(ledger).toString()));
        payload.decoded = Optional.of(ledger);
        return payload;
    }

    public String encoded() { return encoded; }

    /** Empty when this version cannot decode the text; see {@link #error()}. Never throws. */
    public Optional<StackLedger> ledger() {
        var value = decoded;
        if (value == null) {
            try {
                value = Optional.of(LedgerJson.decode(JsonParser.parseString(text(encoded)).getAsJsonObject()));
            } catch (RuntimeException ex) {
                error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                value = Optional.empty();
            }
            decoded = value;
        }
        return value;
    }

    public boolean quarantined() { return ledger().isEmpty(); }
    public Optional<String> error() { ledger(); return Optional.ofNullable(error); }

    static String compress(String json) {
        var deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
            deflater.finish();
            var out = new ByteArrayOutputStream();
            var buffer = new byte[8192];
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
            return "z1:" + Base64.getEncoder().encodeToString(out.toByteArray());
        } finally { deflater.end(); }
    }

    static String text(String encoded) {
        if (!encoded.startsWith("z1:")) return encoded;
        var inflater = new Inflater(true);
        try {
            inflater.setInput(Base64.getDecoder().decode(encoded.substring(3)));
            var out = new ByteArrayOutputStream();
            var buffer = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buffer, 0, n);
                if (out.size() > MAX_INFLATED) throw new IllegalArgumentException("inflated ledger too large");
            }
            if (!inflater.finished()) throw new IllegalArgumentException("truncated ledger payload");
            return out.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException ex) {
            throw new IllegalArgumentException("corrupt ledger payload", ex);
        } finally { inflater.end(); }
    }

    @Override public boolean equals(Object o) { return o instanceof LedgerPayload p && p.encoded.equals(encoded); }
    @Override public int hashCode() { return encoded.hashCode(); }
    @Override public String toString() { return quarantined() ? "LedgerPayload[quarantined]" : "LedgerPayload[" + ledger().get().instances().size() + "]"; }
}

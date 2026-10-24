package org.goldgom.chemicalelements.fluid.waterbody;

import java.math.BigInteger;
import java.util.*;

/** Proposed standalone SI field codec; not a ChunkChemistry schema migration or a save writer. */
public final class WaterVolumeCodec {
    public static final String FORMAT = "si-m3-scale18-residual1000-v1";
    private WaterVolumeCodec() {}
    public record Decoded(Optional<WaterVolume> volume, Map<String,String> raw, String reason) {
        public Decoded { raw = Map.copyOf(raw); }
        public boolean quarantined() { return volume.isEmpty(); }
    }
    public static Map<String,String> encode(WaterVolume v) {
        return Map.of("format", FORMAT, "m3_q18", v.cubicMetreQuanta().toString(), "m3_q18_remainder_1000", Integer.toString(v.residual()));
    }
    public static Decoded decode(Map<String,String> raw) {
        try {
            if (!FORMAT.equals(raw.get("format"))) return quarantine(raw,"unknown volume format");
            if (!raw.keySet().equals(Set.of("format","m3_q18","m3_q18_remainder_1000"))) return quarantine(raw,"unknown fields preserved");
            return new Decoded(Optional.of(new WaterVolume(unsigned(raw.get("m3_q18")),unsigned(raw.get("m3_q18_remainder_1000")).intValueExact())),raw,"");
        } catch (RuntimeException bad) { return quarantine(raw,"invalid SI volume: " + bad.getMessage()); }
    }
    /** Only for a verified ChunkChemistry schema=1 volume/capacity field. Species and heat are NOT converted. */
    public static Decoded planLegacy(int chunkSchema, String producer, String original) {
        var raw = Map.of("schema_version",Integer.toString(chunkSchema),"producer",producer,"volume",original);
        if (chunkSchema != 1 || !producer.equals("ChunkChemistry.WaterCell:litres-scale18")) return quarantine(raw,"unverified legacy schema/producer");
        try { return new Decoded(Optional.of(WaterVolume.ofLegacyLitreQuanta(unsigned(original))),raw,""); }
        catch (RuntimeException bad) { return quarantine(raw,"invalid legacy integer"); }
    }
    private static Decoded quarantine(Map<String,String> raw,String reason) { return new Decoded(Optional.empty(),raw,reason); }
    private static BigInteger unsigned(String value) {
        if (value == null || value.isEmpty() || value.length()>16384) throw new IllegalArgumentException("integer encoding budget");
        for (int i=0;i<value.length();i++) if(value.charAt(i)<'0'||value.charAt(i)>'9') throw new IllegalArgumentException("unsigned decimal required");
        return new BigInteger(value);
    }
}

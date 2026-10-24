package org.goldgom.chemicalelements.instance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.nuclear.NuclideId;
import org.goldgom.chemicalelements.api.nuclear.NuclideInventory;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.api.world.EnvironmentSample;
import org.goldgom.chemicalelements.persistence.ChunkChemistry;
import org.goldgom.chemicalelements.persistence.LedgerPayload;

/** Package B executable assertion suite (no Minecraft bootstrap needed). Run via main; exits non-zero on failure. */
public final class InstanceVerification {
    private static int checks;
    static void check(boolean c, String m) { checks++; if (!c) throw new AssertionError(m); }
    static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath("chemicalelements", p); }
    static final ResourceLocation WATER = id("water");
    static MolAmount mol(String s) { return MolAmount.parseDecimal(s); }

    static InstanceRecord rec(long n, String fe, String c) {
        var comp = new Composition(Map.of(id("iron"), mol(fe), id("carbon"), mol(c)));
        return new InstanceRecord(new UUID(7, n), comp, Provenance.of("test", "t" + n), 0,
                new ThermalState(new BigDecimal(n), new BigDecimal("298.15"), Map.of(), 0),
                new NuclideInventory(Map.of(new NuclideId(26, 56, 0), mol(fe))), SolutionState.empty(), Composition.EMPTY);
    }

    static List<InstanceRecord> recs(int from, int n) {
        var l = new ArrayList<InstanceRecord>();
        for (int i = 0; i < n; i++) l.add(rec(from + i, "1." + (from + i), "0.00" + (i + 1)));
        return l;
    }

    public static void main(String[] args) {
        serialization();
        quarantine();
        settleBasics();
        settleRandomized();
        settleDuplicateIds();
        derivation();
        perturbation();
        chunkStore();
        System.out.println("InstanceVerification OK: " + checks + " checks");
    }

    static void serialization() {
        var ledger = LedgerOps.of(5, recs(1, 64));
        var p = LedgerPayload.of(ledger);
        check(p.encoded().startsWith("z1:"), "compressed encoding");
        var back = new LedgerPayload(p.encoded());
        check(back.ledger().orElseThrow().equals(ledger), "round trip 64 distinct instances");
        check(back.equals(p) && back.hashCode() == p.hashCode(), "textual equality/hash");
        var plain = new LedgerPayload(org.goldgom.chemicalelements.data.LedgerJson.encode(ledger).toString());
        check(plain.ledger().orElseThrow().equals(ledger), "plain json accepted");
        check(!plain.equals(p), "different encodings are different component values");
        // different compositions -> different payloads (no global equality collapse)
        var a = LedgerPayload.of(LedgerOps.of(0, List.of(rec(1, "1", "0.1"))));
        var b = LedgerPayload.of(LedgerOps.of(0, List.of(rec(1, "1", "0.2"))));
        check(!a.equals(b), "distinct compositions keep distinct component values");
    }

    static void quarantine() {
        var future = "{\"schema_version\":99,\"revision\":0,\"instances\":[]}";
        var q = new LedgerPayload(future);
        check(q.quarantined(), "unknown schema quarantined");
        check(q.encoded().equals(future), "quarantined text preserved verbatim");
        check(q.error().isPresent(), "quarantine reason");
        var corrupt = new LedgerPayload("z1:!!!!");
        check(corrupt.quarantined(), "corrupt payload quarantined, not thrown");
        // chunk store unknown schema preserved and read-only
        var tag = new CompoundTag();
        tag.putInt("schema_version", 42);
        tag.putString("future_field", "keep");
        var c = ChunkChemistry.read(tag);
        check(c.readOnly(), "unknown chunk schema read-only");
        check(c.write().getString("future_field").equals("keep"), "unknown chunk data preserved");
    }

    static SettleMath.Pre pre(Object o, List<InstanceRecord> l, String key) { return new SettleMath.Pre(o, l.size(), l, key, key.split("\\|")[0]); }
    static SettleMath.Post post(Object o, int n, String key) { return new SettleMath.Post(o, n, key, key.split("\\|")[0]); }

    static void settleBasics() {
        // split: source 10 -> keeps tail 6, new object gets head 4 (FIFO)
        Object src = new Object(), out = new Object();
        var l = recs(1, 10);
        var r = SettleMath.settle(List.of(new SettleMath.Position(pre(src, l, "iron|0"), post(src, 6, "iron|0")),
                new SettleMath.Position(null, post(out, 4, "iron|0"))));
        check(r.assigned().get(0).equals(l.subList(4, 10)), "source keeps tail");
        check(r.assigned().get(1).equals(l.subList(0, 4)), "split takes head");
        check(r.leftoverCount() == 0 && r.neededCount() == 0, "split balanced");
        // merge different compositions into target: target keeps own then appends source head
        Object tgt = new Object();
        var t = recs(100, 3);
        var r2 = SettleMath.settle(List.of(new SettleMath.Position(pre(tgt, t, "iron|0"), post(tgt, 8, "iron|0")),
                new SettleMath.Position(pre(src, l, "iron|0"), post(src, 5, "iron|0"))));
        var expect = new ArrayList<>(t); expect.addAll(l.subList(0, 5));
        check(r2.assigned().get(0).equals(expect), "merge appends source head after target");
        check(r2.assigned().get(1).equals(l.subList(5, 10)), "source keeps tail after merge");
        // swap: objects exchange positions -> each keeps its own instances
        Object x = new Object(), y = new Object();
        var lx = recs(200, 2); var ly = recs(300, 5);
        var r3 = SettleMath.settle(List.of(new SettleMath.Position(pre(x, lx, "a|0"), post(y, 5, "a|0")),
                new SettleMath.Position(pre(y, ly, "a|0"), post(x, 2, "a|0"))));
        check(r3.assigned().get(0).equals(ly) && r3.assigned().get(1).equals(lx), "swap keeps identities");
        // vanilla copyWithCount replace pattern: slot object replaced by a copy with more count from carried
        Object slotOld = new Object(), slotNew = new Object(), carried = new Object();
        var ls = recs(400, 3); var lc = recs(500, 4);
        var r4 = SettleMath.settle(List.of(new SettleMath.Position(pre(slotOld, ls, "a|0"), post(slotNew, 7, "a|0")),
                new SettleMath.Position(pre(carried, lc, "a|0"), null)));
        var e4 = new ArrayList<>(ls); e4.addAll(lc);
        check(r4.assigned().get(0).equals(e4), "replaced object reclaims position then draws");
        // disappearance -> leftover; creation without source -> need
        var r5 = SettleMath.settle(List.of(new SettleMath.Position(pre(x, lx, "a|0"), null), new SettleMath.Position(null, post(y, 3, "b|0"))));
        check(r5.leftoverCount() == 2 && r5.neededCount() == 3, "different item never drains");
        // other components differ (key) but same item: still drained after exact key
        var r6 = SettleMath.settle(List.of(new SettleMath.Position(pre(x, lx, "a|1"), null), new SettleMath.Position(null, post(y, 2, "a|2"))));
        check(r6.neededCount() == 0, "same item cross-key drain");
        // no-drain mode (conversion) keeps pool intact
        var r7 = SettleMath.settle(List.of(new SettleMath.Position(pre(x, lx, "a|0"), null), new SettleMath.Position(null, post(y, 2, "a|0"))), Set.of(), false);
        check(r7.leftoverCount() == 2 && r7.neededCount() == 2, "no-drain conversion mode");
    }

    static void settleRandomized() {
        var rnd = new Random(77);
        for (int t = 0; t < 2000; t++) {
            int k = 2 + rnd.nextInt(6);
            var objs = new ArrayList<Object>();
            var positions = new ArrayList<SettleMath.Position>();
            int total = 0, base = t * 1000;
            var all = new HashSet<UUID>();
            var preObjs = new ArrayList<Object>();
            for (int i = 0; i < k; i++) {
                Object o = new Object(); preObjs.add(o);
                int c = rnd.nextInt(5);
                var l = recs(base + i * 10, c);
                l.forEach(r -> all.add(r.id()));
                total += c;
                positions.add(new SettleMath.Position(c == 0 ? null : pre(o, l, "a|0"), null));
            }
            // random redistribution of counts into a mix of retained and new objects, same total
            int left = total;
            var outs = new ArrayList<SettleMath.Position>();
            for (int i = 0; i < k; i++) {
                int c = i == k - 1 ? left : rnd.nextInt(left + 1);
                left -= c;
                Object target = rnd.nextBoolean() ? preObjs.get(i) : new Object();
                outs.add(new SettleMath.Position(positions.get(i).pre(), c == 0 ? null : post(target, c, "a|0")));
            }
            var r = SettleMath.settle(outs);
            var seen = new HashSet<UUID>();
            int assignedCount = 0;
            for (var e : r.assigned().entrySet()) {
                for (var rec : e.getValue()) check(seen.add(rec.id()), "no duplicate instance");
                assignedCount += e.getValue().size();
                check(e.getValue().size() <= outs.get(e.getKey()).post().count(), "never exceeds count");
            }
            for (var l : r.leftover().values()) for (var rec : l) check(seen.add(rec.id()), "leftover unique");
            check(seen.equals(all), "permutation of inputs (nothing lost)");
            check(r.neededCount() == 0 && r.leftoverCount() == 0, "balanced redistribution");
            check(assignedCount == total, "count conservation");
        }
    }

    static void settleDuplicateIds() {
        // vanilla copy() of a full stack leaves two objects holding the same ledger: matter must not double
        Object a = new Object(), b = new Object();
        var l = recs(1, 4);
        var r = SettleMath.settle(List.of(new SettleMath.Position(pre(a, l, "x|0"), post(a, 4, "x|0")),
                new SettleMath.Position(pre(b, l, "x|0"), post(b, 4, "x|0"))));
        var ids = new HashSet<UUID>();
        for (var v : r.assigned().values()) for (var rec : v) check(ids.add(rec.id()), "duplicate ids never assigned twice");
        check(r.neededCount() == 4, "duplicated count surfaces as explicit need (unaccounted), not copied matter");
    }

    static void derivation() {
        var consumed = recs(1, 3);
        var nominal = new Composition(Map.of(id("iron"), mol("1")));
        var specs = List.of(new LedgerOps.OutputSpec("minecraft:iron_nugget", nominal, false),
                new LedgerOps.OutputSpec("minecraft:iron_nugget", nominal, false),
                new LedgerOps.OutputSpec("minecraft:bucket", new Composition(Map.of(id("iron"), mol("0.5"))), true));
        var d = LedgerOps.derive("t", consumed, specs, Provenance.of("conversion", "t"), WATER);
        check(LedgerOps.conserved(consumed, d.outputs(), d.unallocated(), WATER), "derivation conserves matter/nuclides/energy");
        check(d.outputs().get(2).remaining().amounts().get(id("iron")).equals(mol("0.5")), "container keeps nominal part");
        var again = LedgerOps.derive("t", consumed, specs, Provenance.of("conversion", "t"), WATER);
        check(again.outputs().equals(d.outputs()), "deterministic derivation and ids");
        var only = LedgerOps.derive("t", consumed, List.of(new LedgerOps.OutputSpec("minecraft:bucket", new Composition(Map.of(id("iron"), mol("0.5"))), true)), Provenance.of("c", "t"), WATER);
        check(LedgerOps.conserved(consumed, only.outputs(), only.unallocated(), WATER) && !only.unallocated().amounts().isEmpty(), "container-only output routes residue to sink");
        var rnd = new Random(3);
        for (int t = 0; t < 300; t++) {
            var in = recs(t * 10, 1 + rnd.nextInt(6));
            var out = new ArrayList<LedgerOps.OutputSpec>();
            int n = 1 + rnd.nextInt(5);
            for (int i = 0; i < n; i++) out.add(new LedgerOps.OutputSpec("x", new Composition(Map.of(id(rnd.nextBoolean() ? "iron" : "carbon"), mol("0." + (1 + rnd.nextInt(9))))), rnd.nextInt(4) == 0));
            var dd = LedgerOps.derive("r" + t, in, out, Provenance.of("c", "r"), WATER);
            check(LedgerOps.conserved(in, dd.outputs(), dd.unallocated(), WATER), "randomized derivation conserved");
            check(dd.outputs().size() == n, "one instance per requested output");
        }
    }

    static void perturbation() {
        var nominal = new Composition(Map.of(id("iron"), mol("1"), id("carbon"), mol("0.002"), id("sulfur"), mol("0.0001")));
        var envA = new EnvironmentSample(id("overworld"), ResourceLocation.parse("minecraft:plains"), 40, "stone", 11, Map.of());
        var envB = new EnvironmentSample(id("overworld"), ResourceLocation.parse("minecraft:desert"), -40, "deepslate", 11, Map.of("temperature_k", "320"));
        var a = InstanceSampler.perturb(nominal, Optional.of(envA), 5);
        var b = InstanceSampler.perturb(nominal, Optional.of(envB), 5);
        check(a.amounts().get(id("iron")).equals(mol("1")) && b.amounts().get(id("iron")).equals(mol("1")), "principal species exact");
        check(!a.equals(b), "environment changes trace composition");
        check(InstanceSampler.perturb(nominal, Optional.of(envA), 5).equals(a), "deterministic sampling");
        check(InstanceSampler.perturb(nominal, Optional.empty(), 5).equals(nominal), "no environment = nominal");
        check(InstanceSampler.thermal(Optional.of(envB)).temperatureK().compareTo(new BigDecimal("320")) == 0, "temperature sampled from environment");
    }

    static void chunkStore() {
        var c = new ChunkChemistry();
        var tag0 = c.write();
        var back0 = ChunkChemistry.read(tag0);
        check(!back0.readOnly(), "empty chunk store round trip");
        // water cell + block instance round trip through NBT
        var wTag = new CompoundTag();
        wTag.putInt("schema_version", ChunkChemistry.SCHEMA);
        wTag.putLong("revision", 3);
        var blocks = new net.minecraft.nbt.ListTag();
        var be = new CompoundTag();
        be.putLong("pos", 12345L);
        be.putString("block", "minecraft:iron_ore");
        be.putString("item", "minecraft:iron_ore");
        be.putLong("rev", 2);
        be.putString("record", org.goldgom.chemicalelements.data.LedgerJson.encode(LedgerOps.of(0, List.of(rec(9, "2", "0.1")))).toString());
        blocks.add(be);
        wTag.put("blocks", blocks);
        var water = new net.minecraft.nbt.ListTag();
        var we = new CompoundTag();
        we.putLong("pos", 777L);
        we.putString("volume", "1000000000000000000000");
        we.putString("energy", "0");
        we.putString("capacity", "1000000000000000000000");
        we.putString("origin", "natural:v1");
        we.putLong("rev", 4);
        var sp = new CompoundTag(); sp.putString("chemicalelements:sodium_ion", "470000000000000000"); we.put("species", sp);
        water.add(we);
        wTag.put("water", water);
        var cc = ChunkChemistry.read(wTag);
        check(!cc.readOnly(), "valid chunk parsed");
        check(cc.block(12345L).orElseThrow().record().equals(rec(9, "2", "0.1")), "block instance round trip");
        check(cc.water(777L).orElseThrow().species().get("chemicalelements:sodium_ion").equals(new BigInteger("470000000000000000")), "water solute exact");
        var again = ChunkChemistry.read(cc.write());
        check(again.water(777L).equals(cc.water(777L)) && again.block(12345L).equals(cc.block(12345L)), "chunk store stable re-serialization");
        // corrupt entry -> whole chunk quarantined, raw kept
        we.putString("volume", "-5");
        var bad = ChunkChemistry.read(wTag);
        check(bad.readOnly() && bad.write().equals(wTag), "corrupt chunk quarantined verbatim");
    }
}

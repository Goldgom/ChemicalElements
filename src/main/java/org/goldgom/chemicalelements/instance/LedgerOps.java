package org.goldgom.chemicalelements.instance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.instance.Provenance;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.api.nuclear.NuclideId;
import org.goldgom.chemicalelements.api.nuclear.NuclideInventory;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.core.ExactAllocator;

/**
 * Pure, Minecraft-free ledger algebra. Every operation is a permutation/partition of existing
 * instances (FIFO: index 0 is the head) or an exact partition of their matter; nothing here mints
 * identities from randomness or regenerates template material.
 */
public final class LedgerOps {
    private LedgerOps() {}

    public static StackLedger empty() { return new StackLedger(StackLedger.CURRENT_SCHEMA, 0, List.of()); }

    public static StackLedger of(long revision, List<InstanceRecord> instances) {
        return new StackLedger(StackLedger.CURRENT_SCHEMA, revision, instances);
    }

    public static StackLedger head(StackLedger l, int n) { return of(next(l), l.instances().subList(0, clamp(n, l))); }

    public static StackLedger tail(StackLedger l, int n) { return of(next(l), l.instances().subList(clamp(n, l), l.instances().size())); }

    /** Target keeps its instances first; moved instances are appended (contract: tail append). */
    public static StackLedger append(StackLedger target, StackLedger moved) {
        var all = new ArrayList<InstanceRecord>(target.instances().size() + moved.instances().size());
        all.addAll(target.instances());
        all.addAll(moved.instances());
        return of(Math.max(next(target), next(moved)), all);
    }

    /** Restore a failed move: the instances go back to the head, preserving their original order. */
    public static StackLedger prepend(StackLedger target, StackLedger restored) { return append(restored, target); }

    public static long next(StackLedger l) { return Math.addExact(l.revision(), 1); }

    private static int clamp(int n, StackLedger l) { return Math.max(0, Math.min(n, l.instances().size())); }

    /** Combined matter of one instance: solid remainder + adsorbed + pore solutes + pore water. */
    public static Composition fullComposition(InstanceRecord r, ResourceLocation water) {
        var c = r.remaining().add(r.adsorbed()).add(r.poreSolution().dissolved()).add(r.poreSolution().suspended());
        if (!r.poreSolution().waterMol().isZero()) c = c.add(new Composition(Map.of(water, r.poreSolution().waterMol())));
        return c;
    }

    // ---------------------------------------------------------------- deterministic identities

    public static UUID derivedId(String domain, List<UUID> parents, int index) {
        var sorted = new ArrayList<>(parents);
        sorted.sort(Comparator.naturalOrder());
        var sb = new StringBuilder(domain).append('|');
        for (var id : sorted) sb.append(id).append(',');
        sb.append('|').append(index);
        return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- conservative derivation

    /** One requested output instance: catalog nominal composition is only a weight/template, never minted. */
    public record OutputSpec(String itemId, Composition nominal, boolean container) {}

    public record Derivation(List<InstanceRecord> outputs, Composition unallocated, List<String> diagnostics) {
        public Derivation { outputs = List.copyOf(outputs); diagnostics = List.copyOf(diagnostics); }
    }

    /**
     * Partition all matter of {@code consumed} into {@code specs}. Containers (e.g. returned buckets)
     * first take their nominal composition component-wise from the pool, limited by availability.
     * Every remaining species is split across the non-container outputs with the largest-remainder
     * method, weighted by nominal total mol (equal weights when nominal is empty). If no non-container
     * output exists, leftovers are returned as {@code unallocated} for an explicit sink.
     */
    public static Derivation derive(String domain, List<InstanceRecord> consumed, List<OutputSpec> specs,
                                    Provenance origin, ResourceLocation water) {
        var diagnostics = new ArrayList<String>();
        var parents = consumed.stream().map(InstanceRecord::id).toList();
        Composition pool = Composition.EMPTY;
        Composition adsorbed = Composition.EMPTY;
        var nuclides = new TreeMap<NuclideId, MolAmount>();
        BigDecimal energy = BigDecimal.ZERO;
        BigDecimal tempWeighted = BigDecimal.ZERO;
        BigDecimal tempWeight = BigDecimal.ZERO;
        SolutionState pore = SolutionState.empty();
        for (var r : consumed) {
            pool = pool.add(r.remaining());
            adsorbed = adsorbed.add(r.adsorbed());
            r.nuclides().amounts().forEach((k, v) -> nuclides.merge(k, v, MolAmount::add));
            energy = energy.add(r.thermal().energyJ());
            var w = r.remaining().totalMol().decimal().max(BigDecimal.ONE.movePointLeft(18));
            tempWeighted = tempWeighted.add(r.thermal().temperatureK().multiply(w));
            tempWeight = tempWeight.add(w);
            pore = mergePore(pore, r.poreSolution());
        }
        var temperature = tempWeight.signum() == 0 ? ThermalState.AMBIENT.temperatureK()
                : tempWeighted.divide(tempWeight, MathContext.DECIMAL64);

        var result = new ArrayList<InstanceRecord>();
        // containers take nominal parts first
        var mainIdx = new ArrayList<Integer>();
        Composition[] solid = new Composition[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            if (!s.container()) { mainIdx.add(i); continue; }
            var take = new TreeMap<ResourceLocation, MolAmount>();
            for (var e : s.nominal().amounts().entrySet()) {
                var avail = pool.amounts().getOrDefault(e.getKey(), MolAmount.ZERO);
                var t = avail.compareTo(e.getValue()) < 0 ? avail : e.getValue();
                if (t.compareTo(e.getValue()) < 0) diagnostics.add("container " + s.itemId() + " short of " + e.getKey());
                if (!t.isZero()) take.put(e.getKey(), t);
            }
            solid[i] = new Composition(take);
            pool = pool.subtract(solid[i]);
        }
        Composition unallocated = Composition.EMPTY;
        if (mainIdx.isEmpty() && !specs.isEmpty()) {
            // only containers requested (e.g. emptying a bucket): leftover matter is unallocated residue for
            // an explicit sink, containers keep exactly their nominal part
            unallocated = pool.add(adsorbed);
            if (!unallocated.amounts().isEmpty()) diagnostics.add("container-only output: residue routed to sink");
        } else if (mainIdx.isEmpty()) {
            unallocated = pool.add(adsorbed);
            if (!unallocated.amounts().isEmpty()) diagnostics.add("no primary output: matter routed to sink");
        } else {
            var weights = new ArrayList<BigInteger>();
            for (int i : mainIdx) {
                var q = specs.get(i).nominal().totalMol().quanta();
                weights.add(q.signum() > 0 ? q : BigInteger.ONE);
            }
            var shares = new ArrayList<TreeMap<ResourceLocation, MolAmount>>();
            var adsShares = new ArrayList<TreeMap<ResourceLocation, MolAmount>>();
            for (int k = 0; k < mainIdx.size(); k++) { shares.add(new TreeMap<>()); adsShares.add(new TreeMap<>()); }
            var mainSpecs = mainIdx.stream().map(specs::get).toList();
            split(pool, mainSpecs, weights, shares);
            split(adsorbed, mainSpecs, weights, adsShares);
            for (int k = 0; k < mainIdx.size(); k++) solid[mainIdx.get(k)] = new Composition(shares.get(k));
            var nucShares = new ArrayList<TreeMap<NuclideId, MolAmount>>();
            for (int k = 0; k < mainIdx.size(); k++) nucShares.add(new TreeMap<>());
            for (var e : nuclides.entrySet()) {
                var parts = ExactAllocator.allocate(e.getValue(), weights);
                for (int k = 0; k < parts.size(); k++) if (!parts.get(k).isZero()) nucShares.get(k).put(e.getKey(), parts.get(k));
            }
            var energyParts = splitDecimal(energy, weights);
            int k = 0;
            for (int i = 0; i < specs.size(); i++) {
                boolean main = !specs.get(i).container();
                var ads = main ? new Composition(adsShares.get(k)) : Composition.EMPTY;
                var nuc = main ? new NuclideInventory(nucShares.get(k)) : NuclideInventory.EMPTY;
                var heat = new ThermalState(main ? energyParts.get(k) : BigDecimal.ZERO, temperature, Map.of(), 0);
                var p = main && k == 0 ? pore : SolutionState.empty();
                result.add(new InstanceRecord(derivedId(domain, parents, i), solid[i], origin, 0, heat, nuc, p, ads));
                if (main) k++;
            }
            return new Derivation(result, Composition.EMPTY, diagnostics);
        }
        for (int i = 0; i < specs.size(); i++) {
            // no primary output: the first record carries nuclides, heat and pore water so nothing is lost
            boolean first = i == 0;
            result.add(new InstanceRecord(derivedId(domain, parents, i), solid[i], origin, 0,
                    new ThermalState(first ? energy : BigDecimal.ZERO, temperature, Map.of(), 0),
                    first ? new NuclideInventory(nuclides) : NuclideInventory.EMPTY, first ? pore : SolutionState.empty(), Composition.EMPTY));
        }
        if (specs.isEmpty()) {
            if (!nuclides.isEmpty() || energy.signum() != 0 || pore.volumeLitres().signum() != 0) diagnostics.add("no output record: nuclides/heat/pore water reported via sink composition only");
            unallocated = unallocated.add(pore.dissolved()).add(pore.suspended());
        }
        return new Derivation(result, unallocated, diagnostics);
    }

    /**
     * Species-aware partition: if any output's nominal composition contains the species, it is split
     * among those outputs by their nominal amount of that species; otherwise by total nominal weight.
     */
    private static void split(Composition pool, List<OutputSpec> mains, List<BigInteger> fallback, List<TreeMap<ResourceLocation, MolAmount>> into) {
        for (var e : pool.amounts().entrySet()) {
            var weights = new ArrayList<BigInteger>();
            boolean any = false;
            for (var s : mains) {
                var q = s.nominal().amounts().getOrDefault(e.getKey(), MolAmount.ZERO).quanta();
                weights.add(q);
                any |= q.signum() > 0;
            }
            var parts = ExactAllocator.allocate(e.getValue(), any ? weights : fallback);
            for (int k = 0; k < parts.size(); k++) if (!parts.get(k).isZero()) into.get(k).put(e.getKey(), parts.get(k));
        }
    }

    /** Energy partition with exact sum (remainder to index 0). */
    static List<BigDecimal> splitDecimal(BigDecimal total, List<BigInteger> weights) {
        var sum = weights.stream().reduce(BigInteger.ZERO, BigInteger::add);
        var out = new ArrayList<BigDecimal>();
        var used = BigDecimal.ZERO;
        for (var w : weights) {
            var part = total.multiply(new BigDecimal(w)).divide(new BigDecimal(sum), 18, java.math.RoundingMode.FLOOR);
            out.add(part);
            used = used.add(part);
        }
        out.set(0, out.get(0).add(total.subtract(used)));
        return out;
    }

    static SolutionState mergePore(SolutionState a, SolutionState b) {
        if (b.volumeLitres().signum() == 0) return a;
        if (a.volumeLitres().signum() == 0) return b;
        var vol = a.volumeLitres().add(b.volumeLitres());
        var t = a.temperatureK().multiply(a.volumeLitres()).add(b.temperatureK().multiply(b.volumeLitres())).divide(vol, MathContext.DECIMAL64);
        return new SolutionState(a.waterMol().add(b.waterMol()), a.dissolved().add(b.dissolved()), a.suspended().add(b.suspended()),
                t, vol, a.origin(), Math.max(a.revision(), b.revision()) + 1);
    }

    /** Conservation check on mixed matter, nuclides and energy (exact). */
    public static boolean conserved(List<InstanceRecord> before, List<InstanceRecord> after, Composition sink, ResourceLocation water) {
        Composition a = Composition.EMPTY, b = sink;
        var na = new TreeMap<NuclideId, MolAmount>();
        var nb = new TreeMap<NuclideId, MolAmount>();
        BigDecimal ea = BigDecimal.ZERO, eb = BigDecimal.ZERO;
        for (var r : before) { a = a.add(fullComposition(r, water)); r.nuclides().amounts().forEach((k, v) -> na.merge(k, v, MolAmount::add)); ea = ea.add(r.thermal().energyJ()); }
        for (var r : after) { b = b.add(fullComposition(r, water)); r.nuclides().amounts().forEach((k, v) -> nb.merge(k, v, MolAmount::add)); eb = eb.add(r.thermal().energyJ()); }
        return a.equals(b) && na.equals(nb) && ea.compareTo(eb) == 0;
    }
}

package org.goldgom.chemicalelements.instance;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.nuclear.NuclideInventory;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.api.world.EnvironmentSample;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;

/**
 * Creates instances exactly once per real creation. Composition comes from the data-driven
 * catalog (A); environment variation is a deterministic, seeded, bounded perturbation of
 * trace/impurity species (never of the principal species) so that same-type items from
 * different biomes/depths differ while staying reproducible. Temperature is sampled once from
 * the environment and stored as the instance thermal state.
 */
public final class InstanceSampler {
    private InstanceSampler() {}

    /** Monotonic, persisted-by-world counter source for non-environment identities. */
    private static final AtomicLong SERIAL = new AtomicLong(System.nanoTime());
    private static volatile Optional<IdentitySource> identitySource = Optional.empty();

    public interface IdentitySource { long nextSerial(); long worldSeed(); }

    public static void bindIdentitySource(IdentitySource src) { identitySource = Optional.of(src); }
    public static void unbindIdentitySource() { identitySource = Optional.empty(); }

    static RegistrySnapshot catalog() { return AtomicChemistryCatalog.INSTANCE.snapshot(); }

    public static StackLedger mint(ItemStack stack, int count, CreationContext ctx) {
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return mint(itemId, count, ctx, catalog());
    }

    /** Pure overload (testable): identity derives from context + serial; simulation never advances serial. */
    public static StackLedger mint(ResourceLocation itemId, int count, CreationContext ctx, RegistrySnapshot snapshot) {
        var nominal = snapshot.itemCompositions().get(itemId);
        var origin = withEnvironment(withEpoch(ctx.origin(), snapshot.epoch(), nominal == null), ctx.environment());
        var list = new ArrayList<InstanceRecord>(count);
        long serialBase = ctx.simulation() ? 0 : reserve(count);
        for (int i = 0; i < count; i++) {
            long seed = seed(itemId, ctx, serialBase + i);
            var comp = nominal == null ? Composition.EMPTY : perturb(nominal, ctx.environment(), seed);
            var id = ctx.simulation()
                    ? UUID.nameUUIDFromBytes(("sim|" + itemId + "|" + i).getBytes(StandardCharsets.UTF_8))
                    : new UUID(seed, serialBase + i);
            list.add(new InstanceRecord(id, comp, origin, 0, thermal(ctx.environment()), NuclideInventory.EMPTY, SolutionState.empty(), Composition.EMPTY));
        }
        return LedgerOps.of(0, list);
    }

    /** Explicit zero-matter fill for an audited deficit. Visible as "unaccounted" provenance. */
    static StackLedger unaccounted(ItemStack stack, int n, StackLedger existing, String reason) {
        var parents = existing.instances().stream().map(InstanceRecord::id).toList();
        var list = new ArrayList<InstanceRecord>();
        var origin = new Provenance("unaccounted", reason, 0, catalog().epoch(), Map.of("item", StackLedgers.id(stack)));
        long base = reserve(n);
        for (int i = 0; i < n; i++)
            list.add(new InstanceRecord(LedgerOps.derivedId("unaccounted|" + (base + i), parents, i), Composition.EMPTY, origin, 0));
        return LedgerOps.of(0, list);
    }

    static long reserve(int n) {
        var src = identitySource;
        if (src.isPresent()) { long first = 0; for (int i = 0; i < n; i++) { long v = src.get().nextSerial(); if (i == 0) first = v; } return first; }
        return SERIAL.getAndAdd(n);
    }

    private static Provenance withEpoch(Provenance p, long epoch, boolean unmodeled) {
        var meta = new TreeMap<>(p.metadata());
        if (unmodeled) meta.put("composition", "unmodeled");
        return new Provenance(p.kind(), p.source(), p.seed(), Math.max(p.registryEpoch(), epoch), meta);
    }

    private static Provenance withEnvironment(Provenance p, Optional<EnvironmentSample> environment) {
        if (environment.isEmpty()) return p;
        var e = environment.get();
        var meta = new TreeMap<>(p.metadata());
        meta.put("dimension", e.dimension().toString());
        meta.put("biome", e.biome().toString());
        meta.put("y", Integer.toString(e.y()));
        meta.put("geology", e.geology().toString());
        return new Provenance(p.kind(), p.source(), p.seed(), p.registryEpoch(), meta);
    }

    static long seed(ResourceLocation item, CreationContext ctx, long serial) {
        var env = ctx.environment();
        String key = item + "|" + ctx.origin().kind() + "|" + ctx.origin().source() + "|" + ctx.origin().seed() + "|"
                + env.map(e -> e.dimension() + "|" + e.biome() + "|" + e.y() + "|" + e.geology() + "|" + e.seed()).orElse("-")
                + "|" + serial;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(h).getLong();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    /**
     * Environment variation: principal species (largest amount) is kept exactly; every minor species
     * is scaled by a factor in [0.80, 1.20] derived from seed and environment (depth enriches by up to
     * +10%). Pure function; no randomness outside the seed. Without environment the nominal is returned.
     */
    static Composition perturb(Composition nominal, Optional<EnvironmentSample> env, long seed) {
        if (env.isEmpty() || nominal.amounts().size() < 2) return nominal;
        ResourceLocation principal = null;
        MolAmount best = MolAmount.ZERO;
        for (var e : nominal.amounts().entrySet()) if (e.getValue().compareTo(best) > 0) { best = e.getValue(); principal = e.getKey(); }
        var out = new TreeMap<ResourceLocation, MolAmount>();
        int depthPermille = Math.max(0, Math.min(100, (64 - env.get().y()) / 2));
        int i = 0;
        for (var e : nominal.amounts().entrySet()) {
            if (e.getKey().equals(principal)) { out.put(e.getKey(), e.getValue()); continue; }
            long mix = seed ^ (0x9E3779B97F4A7C15L * (++i)) ^ env.get().biome().hashCode();
            int permille = 800 + (int) Math.floorMod(mix, 401L) + depthPermille;
            out.put(e.getKey(), new MolAmount(e.getValue().quanta().multiply(BigInteger.valueOf(permille)).divide(BigInteger.valueOf(1000))));
        }
        return new Composition(out);
    }

    /** Ambient temperature of the creation site, sampled once; energy is 0 J relative to ambient reference. */
    static ThermalState thermal(Optional<EnvironmentSample> env) {
        if (env.isEmpty()) return ThermalState.AMBIENT;
        var t = env.get().surroundings().get("temperature_k");
        if (t == null) return ThermalState.AMBIENT;
        var k = new BigDecimal(t);
        if (k.signum() <= 0) return ThermalState.AMBIENT;
        return new ThermalState(BigDecimal.ZERO, k, Map.of(), 0);
    }
}

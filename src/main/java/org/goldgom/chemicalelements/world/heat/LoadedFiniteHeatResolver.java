package org.goldgom.chemicalelements.world.heat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.data.*;
import org.goldgom.chemicalelements.instance.materialtx.*;

/** No chunk tickets, getChunk/getBlockEntity fallback, synthetic BE, cache or second heat store.
 * Server code constructs the immutable whitelist. A client-selected port cannot register itself.
 */
public final class LoadedFiniteHeatResolver {
    private final Set<ResourceLocation> allowedPorts;
    public LoadedFiniteHeatResolver(Set<ResourceLocation> serverPorts) { allowedPorts = Set.copyOf(serverPorts); }
    public record Resolution(String status, String detail, Handle handle) {
        public boolean resolved() { return handle != null; }
    }
    public Resolution resolve(ServerLevel level, OwnerKey.FiniteHeatSource key) {
        return resolve(level, key, null);
    }
    /** B must first reconcile the original durable journal operation/hash. This method does not
     * issue a permit; it locates a NEW live candidate. Only post-attach classification can
     * bind the unchanged historical journal images. */
    public Resolution resolveRecovery(ServerLevel level, OwnerKey.FiniteHeatSource key, UUID operation,
            String globalHash, MaterialOwnerSnapshot before, MaterialOwnerSnapshot after) {
        return resolve(level, key, new RecoveryBinding(operation, globalHash, before, after));
    }
    private Resolution resolve(ServerLevel level, OwnerKey.FiniteHeatSource key, RecoveryBinding recovery) {
        Objects.requireNonNull(level); Objects.requireNonNull(key);
        if (!level.getServer().isSameThread()) return rejected("WRONG_THREAD", "Server thread required");
        if (!level.dimension().location().equals(key.dimension())) return rejected("WRONG_DIMENSION", "Dimension mismatch");
        if (!allowedPorts.contains(key.port())) return rejected("UNKNOWN_PORT", "Port is not server-whitelisted");
        var pos = BlockPos.of(key.blockPos());
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return rejected("UNLOADED", "No chunk loading is permitted");
        // Existing map only: do not trigger lazy BE construction or pending NBT promotion.
        var be = chunk.getBlockEntities().get(pos);
        if (be == null || be.isRemoved() || !(be instanceof FiniteHeatBlockEntityRoot root))
            return rejected("UNMODELED", "Existing BE has no authoritative finite-heat root");
        if (!key.incarnation().equals(root.persistentIncarnation())) return rejected("STALE_INCARNATION", "Block was replaced");
        var access = root.materialAccess();
        if (access == null || !access.isLoaded()) return rejected("UNAVAILABLE", "Physical material root unavailable");
        if (access.isolationGap().isPresent()) return rejected("ISOLATION_GAP", access.isolationGap().get());
        // Only physical block roots may back block heat. Never coerce a player slot into blockPos.
        if (!samePhysicalBlock(access.owner(), key)) return rejected("OWNER_MISMATCH", "Heat alias differs from physical root");
        var bundle = AtomicChemistryCatalog.INSTANCE.bundle();
        var handle = new Handle(level, key, be, root, access, root.liveGeneration(), bundle);
        try {
            if (recovery == null) handle.snapshot();
            else {
                handle.validateCandidate(recovery);
                handle.recoveryCandidate = recovery; // locator evidence only, never project authority
            }
            handle.observationRoots();
            return new Resolution(recovery == null ? "RESOLVED" : "RECOVERY_CANDIDATE",
                    "Read-only locator; recovery requires B lease and full guard classification", handle);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return rejected("REJECTED_ROOT", invalid.getMessage());
        }
    }
    private static boolean samePhysicalBlock(OwnerKey physical, OwnerKey.FiniteHeatSource key) {
        if (physical instanceof OwnerKey.WorldBlock p) return p.dimension().equals(key.dimension())
                && p.blockPos() == key.blockPos() && p.incarnation().equals(key.incarnation());
        if (physical instanceof OwnerKey.MachineInventory p) return p.dimension().equals(key.dimension())
                && p.controllerPos() == key.blockPos() && p.incarnation().equals(key.incarnation());
        // Standalone heat roots are canonical only with their own exact whitelisted identity.
        return physical.equals(key);
    }
    private static Resolution rejected(String status, String detail) { return new Resolution(status, detail, null); }

    /** Resolution result: pass physicalOwners and locationGuards together to B. B must retain
     * every locator through its full transaction; extracting only owners is NOT sufficient. */
    public static final class NormalizedOwners {
        private final List<MaterialOwnerAccess> owners;
        private final List<Handle> thermalGuards;
        private NormalizedOwners(List<MaterialOwnerAccess> owners, List<Handle> guards) {
            this.owners = List.copyOf(owners); this.thermalGuards = List.copyOf(guards);
        }
        public List<MaterialOwnerAccess> physicalOwners() { validate(); return owners; }
        public List<Handle> thermalGuards() { return thermalGuards; }
        public List<MaterialLocationGuard> locationGuards() { return List.copyOf(thermalGuards); }
        public void validate() {
            for (var guard : thermalGuards) {
                guard.requireCurrent();
                var candidate = guard.recoveryCandidate;
                if (candidate == null) guard.snapshot();
                else guard.validateCandidate(candidate); // pre-attach: no snapshot or classify
                guard.observationRoots();
            }
        }
    }
    public static NormalizedOwners normalize(Collection<? extends MaterialOwnerAccess> material,
            Collection<Handle> thermal) {
        var physical = new LinkedHashMap<OwnerKey, MaterialOwnerAccess>();
        var guards = new ArrayList<Handle>();
        for (var access : material) {
            if (access instanceof Handle handle) { guards.add(handle); addPhysical(physical, handle.access); }
            else addPhysical(physical, access);
        }
        for (var handle : thermal) { guards.add(handle); addPhysical(physical, handle.access); }
        var result = new NormalizedOwners(new ArrayList<>(physical.values()), guards);
        result.validate();
        return result;
    }
    static void addPhysical(Map<OwnerKey, MaterialOwnerAccess> unique, MaterialOwnerAccess access) {
        Objects.requireNonNull(access);
        var prior = unique.putIfAbsent(access.owner(), access);
        if (prior != null && prior != access) throw new IllegalArgumentException("AMBIGUOUS_PHYSICAL_OWNER_ACCESS");
    }

    /** Immutable journal evidence, not a material store or independent permit. */
    record RecoveryBinding(UUID operation, String globalHash, MaterialOwnerSnapshot before, MaterialOwnerSnapshot after) {
        RecoveryBinding {
            Objects.requireNonNull(operation); Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (globalHash == null || !globalHash.matches("[0-9a-f]{64}")
                    || !before.owner().equals(after.owner()) || !before.liveGeneration().equals(after.liveGeneration())
                    || before.revision() == Long.MAX_VALUE || after.revision() != before.revision() + 1)
                throw new IllegalArgumentException("INVALID_HEAT_RECOVERY_LINEAGE");
        }
        boolean matches(MaterialOwnerSnapshot proposed, MaterialPendingMarker marker, UUID incarnation) {
            return after.equals(proposed) && marker.matches(operation, globalHash)
                    && marker.incarnation().equals(incarnation) && marker.phase() == MaterialPendingMarker.Phase.PROJECTED
                    && marker.beforeRevision() == before.revision() && marker.afterRevision() == after.revision()
                    && marker.beforeHash().equals(before.rootHash()) && marker.afterHash().equals(after.rootHash());
        }
    }

    /** B receives this lifecycle guard, with the PHYSICAL owner key and exact original lock roots.
     * Two thermal aliases therefore cannot become two inventory owners in a B request.
     * No lifetime extension on reload: recovery must resolve a NEW handle and use B lineage checks.
     */
    public static final class Handle implements MaterialOwnerAccess, MaterialLocationGuard {
        private final ServerLevel level;
        private final OwnerKey.FiniteHeatSource alias;
        private final BlockEntity be;
        private final FiniteHeatBlockEntityRoot root;
        private final MaterialOwnerAccess access;
        private final UUID generation;
        private final ChemistryBundle bundle;
        private RecoveryBinding recoveryCandidate;
        private RecoveryBinding recoveryBinding;
        private Handle(ServerLevel level, OwnerKey.FiniteHeatSource alias, BlockEntity be,
                FiniteHeatBlockEntityRoot root, MaterialOwnerAccess access, UUID generation, ChemistryBundle bundle) {
            this.level = level; this.alias = alias; this.be = be; this.root = root;
            this.access = access; this.generation = Objects.requireNonNull(generation); this.bundle = bundle;
        }
        public OwnerKey.FiniteHeatSource alias() { return alias; }
        /** Identity only; B must retain this guard alongside this original physical access. */
        @Override public MaterialOwnerAccess physicalAccess() { return access; }
        @Override public void validate(MaterialLocationGuard.Check check) {
            recoveryBinding = null;
            requireCurrent();
            if (check.physicalAccess() != access) throw new IllegalArgumentException("HEAT_PHYSICAL_ACCESS_MISMATCH");
            var position = classify(check.operation(), check.globalHash(), check.before(), check.after(), check.recovery());
            if ((position != Position.BEFORE && position != Position.AFTER)
                    || (check.expected() != null && check.expected() != position)) {
                recoveryBinding = null;
                throw new IllegalStateException("HEAT_LOCATION_GUARD_" + check.boundary() + ":" + position);
            }
            // Match evidence to the ACTUAL classified image, not invariably to BEFORE.
            // This remains a privileged read via the root contract, never snapshot()/ordinary().
            var image = position == Position.BEFORE ? check.before() : check.after();
            var evidence = root.heatEvidence(alias.port(), bundle);
            if (!FiniteHeatValidator.validate(image, evidence, bundle).valid()) {
                recoveryBinding = null;
                throw new IllegalStateException("HEAT_LOCATION_IMAGE_MISMATCH");
            }
            observationRoots();
        }
        @Override public OwnerKey owner() { return access.owner(); }
        @Override public boolean isLoaded() {
            if (!level.getServer().isSameThread() || be.isRemoved() || be.getLevel() != level) return false;
            var pos = BlockPos.of(alias.blockPos());
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk instanceof org.goldgom.chemicalelements.world.material.WorldChunkAccessBridge bridge
                    && bridge.worldContainsBlockEntity(be)
                    && root.persistentIncarnation().equals(alias.incarnation())
                    && root.liveGeneration().equals(generation) && root.materialAccess() == access && access.isLoaded();
        }
        private void requireCurrent() {
            if (!isLoaded()) throw new IllegalStateException("STALE_FINITE_HEAT_HANDLE");
            if (AtomicChemistryCatalog.INSTANCE.bundle() != bundle) throw new IllegalStateException("STALE_HEAT_BUNDLE");
            if (access.isolationGap().isPresent()) throw new IllegalStateException("FINITE_HEAT_ISOLATION_GAP");
        }
        @Override public Optional<String> isolationGap() { return isLoaded() ? access.isolationGap() : Optional.of("Stale heat handle"); }
        @Override public MaterialOwnerSnapshot snapshot() {
            requireCurrent(); var snapshot = access.snapshot();
            if (!snapshot.owner().equals(owner()) || !snapshot.liveGeneration().equals(generation))
                throw new IllegalStateException("HEAT_ROOT_IDENTITY_MISMATCH");
            var result = FiniteHeatValidator.validate(snapshot, root.heatEvidence(alias.port(), bundle), bundle);
            if (!result.valid()) throw new IllegalStateException(result.status() + ":" + result.detail());
            return snapshot;
        }
        @Override public Collection<?> observationRoots() {
            requireCurrent(); var roots = access.observationRoots();
            if (roots.stream().noneMatch(value -> value == be)) throw new IllegalStateException("BE_NOT_RESERVED");
            return roots;
        }
        /** Checks journal shape, thermal images and live port constraints only. No lineage grant. */
        private void validateCandidate(RecoveryBinding candidate) {
            requireCurrent();
            var before = candidate.before(); var after = candidate.after();
            if (!before.owner().equals(owner()) || !after.owner().equals(owner()))
                throw new IllegalArgumentException("HEAT_CANDIDATE_OWNER_MISMATCH");
            var b = FiniteHeatEvidence.fromRoot(before.canonicalRoot());
            var a = FiniteHeatEvidence.fromRoot(after.canonicalRoot());
            if (!FiniteHeatValidator.validate(before, b, bundle).valid()
                    || !FiniteHeatValidator.validate(after, a, bundle).valid()
                    || !sameConstraints(b, a)
                    || !sameConstraints(b, root.heatEvidence(alias.port(), bundle)))
                throw new IllegalArgumentException("HEAT_CANDIDATE_IMAGE_MISMATCH");
        }
        @Override public Position classify(UUID op, String hash, MaterialOwnerSnapshot before, MaterialOwnerSnapshot after, boolean recovery) {
            recoveryBinding = null; // a failed or unrelated classification revokes prior local evidence
            if (!isLoaded() || AtomicChemistryCatalog.INSTANCE.bundle() != bundle || access.isolationGap().isPresent()) return Position.UNAVAILABLE;
            RecoveryBinding candidate;
            try {
                candidate = new RecoveryBinding(op, hash, before, after);
                if (!recovery && (!before.liveGeneration().equals(generation) || !after.liveGeneration().equals(generation)))
                    return Position.CONFLICT;
                if (recoveryCandidate != null && (!recovery || !recoveryCandidate.equals(candidate)))
                    return Position.CONFLICT;
                validateCandidate(candidate);
            } catch (IllegalArgumentException | IllegalStateException invalid) { return Position.CONFLICT; }
            var position = access.classify(op, hash, before, after, recovery);
            if (position == Position.BEFORE || position == Position.AFTER) {
                var actual = position == Position.BEFORE ? before : after;
                if (!FiniteHeatValidator.validate(actual, root.heatEvidence(alias.port(), bundle), bundle).valid())
                    return Position.CONFLICT;
                observationRoots();
                if (recovery) recoveryBinding = candidate;
            }
            return position;
        }
        @Override public void isolate(MaterialPendingMarker marker) { requireCurrent(); access.isolate(marker); }
        @Override public void project(MaterialOwnerSnapshot after, MaterialPendingMarker marker) {
            requireCurrent();
            boolean historical = !after.liveGeneration().equals(generation);
            if (historical && (recoveryBinding == null || !recoveryBinding.matches(after, marker, alias.incarnation())
                    || access.classify(marker.operation(), marker.globalHash(), recoveryBinding.before(),
                            recoveryBinding.after(), true) != Position.BEFORE))
                throw new IllegalArgumentException("HEAT_RECOVERY_LINEAGE_REQUIRED");
            if (!after.owner().equals(owner())
                    || !marker.incarnation().equals(alias.incarnation()) || !marker.afterHash().equals(after.rootHash())
                    || marker.afterRevision() != after.revision()) throw new IllegalArgumentException("HEAT_PROJECTION_BINDING");
            var proposed = FiniteHeatEvidence.fromRoot(after.canonicalRoot());
            var current = root.heatEvidence(alias.port(), bundle);
            if (!sameConstraints(proposed, current))
                throw new IllegalArgumentException("HEAT_CAPACITY_REFERENCE_CHANGED");
            var validation = FiniteHeatValidator.validate(after, proposed, bundle);
            if (!validation.valid()) throw new IllegalArgumentException(validation.status() + ":" + validation.detail());
            // B checks global energy/material conservation and original operation/globalHash.
            // Physical adapter validates full nonthermal root fields and atomically replaces it.
            access.project(after, marker);
        }
        private static boolean sameConstraints(FiniteHeatEvidence a, FiniteHeatEvidence b) {
            return a.model().equals(b.model()) && a.modelHash().equals(b.modelHash())
                    && a.reference().equals(b.reference()) && a.epoch() == b.epoch() && a.catalogHash().equals(b.catalogHash())
                    && a.maxEnergyJ().compareTo(b.maxEnergyJ()) == 0 && a.maxMol().compareTo(b.maxMol()) == 0
                    && a.maxVolumeM3().compareTo(b.maxVolumeM3()) == 0 && a.pressurePa().compareTo(b.pressurePa()) == 0;
        }
        @Override public void persist() { requireCurrent(); access.persist(); }
        @Override public void complete(UUID op, String hash) { requireCurrent(); access.complete(op, hash); }
        @Override public void cancelIsolation(UUID op, String hash) { requireCurrent(); access.cancelIsolation(op, hash); }
        @Override public void notifyCommitted(UUID op, String hash) { requireCurrent(); access.notifyCommitted(op, hash); }
    }
}

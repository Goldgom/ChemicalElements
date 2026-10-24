package org.goldgom.chemicalelements.content.material;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.content.material.tx.MaterialRootState;
import org.goldgom.chemicalelements.data.ChemistryBundle;
import org.goldgom.chemicalelements.instance.materialtx.*;
import org.goldgom.chemicalelements.registry.ElementMaterials;
import org.goldgom.chemicalelements.world.heat.*;
import org.goldgom.chemicalelements.world.material.*;

/** Real BE with one atomic business/transaction root. No item-handler or secondary balance. */
public final class MaterialBlockEntity extends BlockEntity implements FiniteHeatBlockEntityRoot, WorldMaterialRootBridge {
    private MaterialRootState root;
    private CompoundTag quarantined;
    private UUID generation = UUID.randomUUID();
    private boolean worldAdopted;
    private final MaterialOwnerAccess access = new Access();
    public MaterialBlockEntity(BlockPos pos, BlockState state) {
        super(ElementMaterials.MATERIAL_BE.get(), pos, state);
        root = new MaterialRootState(UUID.randomUUID(), new MaterialProjection(state, 0,
                new StackLedger(StackLedger.CURRENT_SCHEMA, 0, List.of()), Optional.empty()), Optional.empty(), Optional.empty());
    }
    @Override public UUID persistentIncarnation() { return root.incarnation(); }
    @Override public UUID liveGeneration() { return generation; }
    @Override public MaterialOwnerAccess materialAccess() { return access; }
    @Override public MaterialRootState worldReadRoot(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner,
            UUID operation, String globalHash) {
        permit.consumeMaterialRoot(owner, operation, globalHash, this, WorldPrimitivePermit.Primitive.READ_ROOT);
        if (quarantined != null || !root.incarnation().equals(owner.incarnation()))
            throw new IllegalStateException("MATERIAL_ROOT_QUARANTINED_OR_FOREIGN");
        return root;
    }
    @Override public void worldReplaceRoot(WorldPrimitivePermit permit, OwnerKey.WorldBlock owner,
            UUID operation, String globalHash, MaterialRootState replacement) {
        var context = permit.consumeMaterialRoot(owner, operation, globalHash, this, WorldPrimitivePermit.Primitive.REPLACE_ROOT);
        Objects.requireNonNull(replacement);
        if (quarantined != null || !replacement.incarnation().equals(owner.incarnation()))
            throw new IllegalStateException("MATERIAL_ROOT_QUARANTINED_OR_FOREIGN");
        replacement.image().validateUnit(context.registryAccess());
        if (!replacement.image().state().equals(getBlockState())
                || !replacement.image().ledger().instances().isEmpty() && replacement.image().unitStack().isEmpty())
            throw new IllegalArgumentException("world material state/original unit mismatch");
        if (level == null && !worldAdopted) {
            // The candidate has never been installed or loaded. Adopt the position incarnation
            // once; an air position is metadata in its chunk, never a fabricated air BE.
            if (root.image().revision() != 0 || !root.image().ledger().instances().isEmpty()
                    || root.pending().isPresent() || root.last().isPresent()
                    || replacement.pending().filter(m -> m.matches(operation, globalHash)).isEmpty())
                throw new IllegalStateException("INVALID_UNPUBLISHED_MATERIAL_ADOPTION");
        } else {
            root.worldReplacement(operation, globalHash, replacement);
        }
        root = replacement;
        worldAdopted = true;
    }
    private void ordinary() {
        MaterialObservationBarrier.requireObservable(this);
        if (quarantined != null || root.pending().isPresent()) throw new IllegalStateException("MATERIAL_OWNER_ISOLATED");
    }
    public StackLedger ledger() { ordinary(); return root.image().ledger(); }
    @Override public FiniteHeatEvidence heatEvidence(ResourceLocation port, ChemistryBundle bundle) {
        Objects.requireNonNull(bundle);
        if (!port.equals(ElementMaterials.id("material"))) throw new IllegalArgumentException("unsupported heat port");
        if (!access.isLoaded() || quarantined != null) throw new IllegalStateException("unavailable heat root");
        return root.image().finiteHeat().orElseThrow(() -> new IllegalStateException("no evidenced finite heat model"));
    }
    @Override public void onLoad() { super.onLoad(); worldAdopted = true; generation = UUID.randomUUID(); }
    @Override public void setRemoved() { super.setRemoved(); generation = UUID.randomUUID(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("material_root", quarantined != null ? quarantined.copy() : root.encode());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); worldAdopted = true; generation = UUID.randomUUID();
        var original = tag.getCompound("material_root").copy();
        try {
            var loaded = MaterialRootState.decode(original);
            loaded.image().validateUnit(registries);
            if (!loaded.image().state().equals(getBlockState())) throw new IllegalArgumentException("blockstate/root mismatch");
            root = loaded; quarantined = null;
        } catch (RuntimeException invalid) {
            // Never silently replace malformed or pending NBT with an empty material balance.
            quarantined = original;
        }
    }
    private final class Access implements MaterialOwnerAccess {
        public OwnerKey owner() {
            if (level == null) throw new IllegalStateException("unattached material BE");
            return new OwnerKey.WorldBlock(level.dimension().location(), worldPosition.asLong(), root.incarnation());
        }
        public boolean isLoaded() {
            if (!(level instanceof ServerLevel server) || isRemoved() || !server.getServer().isSameThread()) return false;
            var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            return chunk instanceof WorldChunkAccessBridge bridge && bridge.worldContainsBlockEntity(MaterialBlockEntity.this);
        }
        public Optional<String> isolationGap() {
            return Optional.of("Level/LevelChunk observation/replacement/air-owner lifecycle and synchronous chunk persistence not yet integrated");
        }
        private void available() {
            if (!isLoaded() || quarantined != null) throw new IllegalStateException("material root unavailable/quarantined");
        }
        public MaterialOwnerSnapshot snapshot() {
            available(); ordinary(); return directSnapshot();
        }
        private MaterialOwnerSnapshot directSnapshot() {
            return MaterialOwnerSnapshot.of(owner(), generation, root.image().revision(), root.image().canonical());
        }
        public Collection<?> observationRoots() {
            available();
            var chunk = ((ServerLevel)level).getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            return List.of(MaterialBlockEntity.this, chunk);
        }
        public Position classify(UUID op, String hash, MaterialOwnerSnapshot before, MaterialOwnerSnapshot after, boolean recovery) {
            if (!isLoaded() || quarantined != null) return Position.UNAVAILABLE;
            if (!owner().equals(before.owner()) || !owner().equals(after.owner())) return Position.CONFLICT;
            if (!recovery && (!generation.equals(before.liveGeneration()) || !generation.equals(after.liveGeneration()))) return Position.CONFLICT;
            if (root.pending().filter(m -> !m.matches(op, hash)).isPresent()) return Position.CONFLICT;
            var now = directSnapshot();
            java.util.function.Predicate<MaterialPendingMarker> binding = m -> m.matches(op, hash)
                    && m.beforeRevision() == before.revision() && m.afterRevision() == after.revision()
                    && m.beforeHash().equals(before.rootHash()) && m.afterHash().equals(after.rootHash());
            if (root.pending().filter(binding.negate()).isPresent()) return Position.CONFLICT;
            boolean lineage = root.pending().filter(binding).isPresent() || root.last().filter(binding).isPresent();
            if (now.revision() == after.revision() && now.canonicalRoot().equals(after.canonicalRoot()) && lineage)
                return Position.AFTER;
            if (now.revision() == before.revision() && now.canonicalRoot().equals(before.canonicalRoot())
                    && (!recovery || lineage || MaterialRecoveryEvidence.matches(op, hash, before, after,
                            MaterialBlockEntity.this))) return Position.BEFORE;
            return Position.CONFLICT;
        }
        public void isolate(MaterialPendingMarker marker) { available(); root = root.isolate(marker); }
        public void project(MaterialOwnerSnapshot after, MaterialPendingMarker marker) {
            available();
            // Recovery uses journal snapshots from a former live generation. The durable exact
            // RESERVED marker, incarnation and full before hash authorize that lineage, not UUID alone.
            if (!owner().equals(after.owner()) || root.pending().filter(m -> m.projected().equals(marker)).isEmpty())
                throw new IllegalArgumentException("wrong physical root/reservation lineage");
            var image = MaterialProjection.decode(after.canonicalRoot());
            image.validateUnit(level.registryAccess());
            if (!image.ledger().instances().isEmpty() && image.unitStack().isEmpty())
                throw new IllegalArgumentException("nonempty placement requires original item components");
            if (image.revision() != after.revision() || !image.state().equals(getBlockState()))
                throw new IllegalArgumentException("world state projection requires world lifecycle adapter");
            root = root.project(image, marker);
        }
        public void persist() {
            throw new IllegalStateException("SYNC_CHUNK_ROOT_DURABILITY_NOT_INTEGRATED; setChanged is not durability");
        }
        public void complete(UUID op, String hash) { available(); root = root.complete(op, hash); }
        public void cancelIsolation(UUID op, String hash) { available(); root = root.cancel(op, hash); }
        public void notifyCommitted(UUID op, String hash) {
            available(); ordinary();
            if (root.last().filter(m -> m.matches(op, hash)).isEmpty()) throw new IllegalStateException("unknown committed operation");
            // No drops, inventory writes, neighbor updates or other non-idempotent side effects.
        }
    }
}

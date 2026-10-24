package org.goldgom.chemicalelements.world.heat;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.instance.materialtx.*;

/** Pure fixture tests, NOT a real BE/journal/enthalpy success test. Not run in resolver-fix-v1/v2/v3. */
public final class LoadedFiniteHeatResolverTest {
    public static void main(String[] args) {
        exactHistoricalBinding();
        physicalIdentityDeduplication();
        locationGuardContract();
    }
    private static void exactHistoricalBinding() {
        var incarnation = UUID.randomUUID();
        var owner = new OwnerKey.WorldBlock(ResourceLocation.parse("minecraft:overworld"), 0, incarnation);
        var historical = UUID.randomUUID();
        var before = MaterialOwnerSnapshot.of(owner, historical, 3, "{\"revision\":3}");
        var after = MaterialOwnerSnapshot.of(owner, historical, 4, "{\"revision\":4}");
        var operation = UUID.randomUUID(); var hash = "a".repeat(64);
        var binding = new LoadedFiniteHeatResolver.RecoveryBinding(operation, hash, before, after);
        var marker = new MaterialPendingMarker(operation, hash, incarnation, 3, 4,
                before.rootHash(), after.rootHash(), MaterialPendingMarker.Phase.RESERVED);
        require(binding.matches(after, marker.projected(), incarnation), "unchanged historical image accepted");
        require(!binding.matches(after, marker, incarnation), "reserved is not projected");
        require(!binding.matches(after, marker.projected(), UUID.randomUUID()), "replacement incarnation rejected");
        var changedGeneration = MaterialOwnerSnapshot.of(owner, UUID.randomUUID(), 4, after.canonicalRoot());
        require(!binding.matches(changedGeneration, marker.projected(), incarnation), "journal generation cannot be rewritten");
        var wrongOp = new MaterialPendingMarker(UUID.randomUUID(), hash, incarnation, 3, 4,
                before.rootHash(), after.rootHash(), MaterialPendingMarker.Phase.PROJECTED);
        require(!binding.matches(after, wrongOp, incarnation), "wrong operation rejected");
        var wrongHash = new MaterialPendingMarker(operation, "b".repeat(64), incarnation, 3, 4,
                before.rootHash(), after.rootHash(), MaterialPendingMarker.Phase.PROJECTED);
        require(!binding.matches(after, wrongHash, incarnation), "wrong global hash rejected");
        var wrongBefore = new MaterialPendingMarker(operation, hash, incarnation, 3, 4,
                "c".repeat(64), after.rootHash(), MaterialPendingMarker.Phase.PROJECTED);
        require(!binding.matches(after, wrongBefore, incarnation), "wrong full before root rejected");
        var wrongAfter = MaterialOwnerSnapshot.of(owner, historical, 4, "{\"other\":true,\"revision\":4}");
        require(!binding.matches(wrongAfter, marker.projected(), incarnation), "wrong full after root rejected");
        rejects(() -> new LoadedFiniteHeatResolver.RecoveryBinding(operation, hash, before, changedGeneration));
        require(binding.equals(new LoadedFiniteHeatResolver.RecoveryBinding(operation, hash, before, after)),
                "same complete candidate identity");
        require(!binding.equals(new LoadedFiniteHeatResolver.RecoveryBinding(UUID.randomUUID(), hash, before, after)),
                "candidate cannot switch operation at attach");
        require(!binding.equals(new LoadedFiniteHeatResolver.RecoveryBinding(operation, "b".repeat(64), before, after)),
                "candidate cannot switch global payload at attach");
        require(!binding.equals(new LoadedFiniteHeatResolver.RecoveryBinding(operation, hash, before, wrongAfter)),
                "candidate cannot switch complete after image at attach");
    }
    private static void physicalIdentityDeduplication() {
        var key = new OwnerKey.WorldBlock(ResourceLocation.parse("minecraft:overworld"), 0, UUID.randomUUID());
        var access = new FixtureAccess(key);
        var unique = new LinkedHashMap<OwnerKey, MaterialOwnerAccess>();
        // Three validated locators would supply these same original accesses: material + two ports.
        LoadedFiniteHeatResolver.addPhysical(unique, access);
        LoadedFiniteHeatResolver.addPhysical(unique, access);
        LoadedFiniteHeatResolver.addPhysical(unique, access);
        require(unique.size() == 1 && unique.get(key) == access, "one original root, not three wrappers");
        rejects(() -> LoadedFiniteHeatResolver.addPhysical(unique, new FixtureAccess(key)));
        require(unique.size() == 1 && unique.get(key) == access, "ambiguous root did not replace original");
    }
    private static void locationGuardContract() {
        var owner = new OwnerKey.WorldBlock(ResourceLocation.parse("minecraft:overworld"), 0, UUID.randomUUID());
        var access = new FixtureAccess(owner); var generation = UUID.randomUUID();
        var before = MaterialOwnerSnapshot.of(owner, generation, 0, "{\"revision\":0}");
        var after = MaterialOwnerSnapshot.of(owner, generation, 1, "{\"revision\":1}");
        var op = UUID.randomUUID();
        for (var boundary : MaterialLocationGuard.Boundary.values()) {
            var check = new MaterialLocationGuard.Check(access, op, "a".repeat(64), before, after,
                    true, MaterialOwnerAccess.Position.AFTER, boundary);
            require(check.physicalAccess() == access && check.after().equals(after), "guard keeps exact original access and AFTER image");
        }
        rejects(() -> new MaterialLocationGuard.Check(access, op, "a".repeat(64), before, after,
                true, MaterialOwnerAccess.Position.CONFLICT, MaterialLocationGuard.Boundary.PROJECT_AFTER));
        rejects(() -> new MaterialLocationGuard.Check(access, op, "bad-hash", before, after,
                true, null, MaterialLocationGuard.Boundary.ATTACH_RECOVERY));
    }
    private record FixtureAccess(OwnerKey owner) implements MaterialOwnerAccess {
        public boolean isLoaded() { return true; }
        public Optional<String> isolationGap() { return Optional.empty(); }
        public MaterialOwnerSnapshot snapshot() { throw new UnsupportedOperationException("identity fixture only"); }
        public Collection<?> observationRoots() { return List.of(this); }
        public Position classify(UUID op, String hash, MaterialOwnerSnapshot b, MaterialOwnerSnapshot a, boolean r) { throw new UnsupportedOperationException(); }
        public void isolate(MaterialPendingMarker m) { throw new UnsupportedOperationException(); }
        public void project(MaterialOwnerSnapshot a, MaterialPendingMarker m) { throw new UnsupportedOperationException(); }
        public void persist() { throw new UnsupportedOperationException(); }
        public void complete(UUID op, String hash) { throw new UnsupportedOperationException(); }
        public void cancelIsolation(UUID op, String hash) { throw new UnsupportedOperationException(); }
        public void notifyCommitted(UUID op, String hash) { throw new UnsupportedOperationException(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid binding accepted");
    }
}

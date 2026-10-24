# Machine mirror r22 — batch 1 (source stage, not Owner-ready)

Task chem-machine-mirror-r22. Scope exclusively machine production and machine-exclusive tests. No Gradle, javac, client, desktop capture, fixed integration snapshot, classes/resources, formal saves/mods, index writes, or external transfers performed.

## Implemented source
- Actual BE now owns one MachineInventory (Container), with a single NonNullList root. Public transaction projection prepares the full 90-slot replacement and publishes one reference; no second live inventory or journal.
- MachineProjection reuses B MutationTracked mixin clocks and TransactionEncoding.mirror/decodeMirror. Live generation and mutationRevision are distinct from BE display revision and caller versions. Slot-list identity, stack identity/count/components clocks, tank mutation clock, power mutation clock and BE control clock are observed. Missing mixins fail closed.
- Snapshot binds full slots, electrical capacity/store, all persisted job/control state, thermal energy, pressure and real aggregate tank NBT including capacity. Codec decode on every image access isolates stack/component values.
- Actual MaterialTank currently stores Composition, not InstanceRecord parcels. Snapshot therefore carries tankNbt separately; it does NOT invent parcel UUIDs or place material in ControlImage. This is NOT a fully unified B Mirror tank representation; integration contract remains open.
- apply preflights canonical decode, full expected equality, shape, capacity, identity, exact complete instance + item shell conservation, stored heat/nuclides, unchanged tank/power/control and final expected equality before one root publication. Current success domain is local slot relocation only. Cross-owner delivery, energy-consuming chemistry, tank mutation and reaction transformations deliberately reject until journal-bound validation exists.
- Durable machine_projection stores independent projection generation/revision/lastOperation/request/result digest; unknown fields/type/schema retained and quarantined. Load/unload/removal changes live generation. Exact immediate retry checks request and actual result content; conflicting UUID/payload rejects. Marker is metadata, never a journal receipt.
- Legacy materialize preview no longer spends or clears slots. Production entry/tick are closed pending B durable machine transaction implementation.
- Legacy inventory NBT list remains readable. New writes use ContainerHelper Items format. Owner incarnation persists.

## Exact entry points
`MachineBlockEntity.public final MachineProjection projection`
`MachineBlockEntity.inventory(): Container` (was SimpleContainer; searched current callers only depend on Container)
`MachineProjection.snapshot(): Snapshot`
`MachineProjection.image(Snapshot): MachineTransactions.Mirror`
`MachineProjection.marker(): Marker`
`MachineProjection.apply(Snapshot expected, Snapshot replacement, UUID operation): Result`
`MachineProjection.replacement(Snapshot expected, String canonicalMirror, String tankNbt): Snapshot`

Snapshot(UUID incarnation, Version version, String canonicalMirror, String tankNbt, String digest)
Version(UUID generation, long mutationRevision)
Marker(UUID generation, long revision, Optional<UUID> lastOperation, String requestDigest, String resultDigest)
Result: APPLIED / ALREADY_APPLIED / CONFLICT / REJECTED / QUARANTINED

Caller path: server-resolve actual BE -> projection.snapshot -> detached image -> encode replacement -> apply. B must still bind authoritative journal prepare/commit/cancel/recover, catalog, actor authorization and multi-owner full conservation. No B public API package modified.

## Source tests (not compiled or run)
MachineProjectionGameTests six methods: full 90-slot/energy/control/tank image and isolation; list/count/component ABA; tank/power/thermal/pressure/grid ABA; stale expected and loss/codec exception zero-partial-write; actual local projection + duplicate/conflict + NBT reload/unload; unknown metadata quarantine/preservation.

## Checks
`git diff --check -- src/main/java/org/goldgom/chemicalelements/machine` exit 0.
Index SHA256 remains 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69; 13 staged paths.
Six source hashes in manifest.json. No compilation or runtime claim. Integration owner must compile/test this version, including ContainerHelper overloads and required mixin application.

## Remaining work, not external completion
1. B-compatible aggregate-tank representation and journal-bound cross-owner conservation permit needed. Requested B feedback twice with exact signatures. Current local-only projection does not fulfill full multi-owner successful CAS or all replacements.
2. Third-party mutable component *payload object's internal fields* are not intercepted by B's ItemStack/PatchedDataComponentMap clocks. Canonical snapshot catches changed values, but A->B->A inside such a payload between captures remains outside existing clock mechanism. Need B-owned mixin/immutability policy, not reflection or guessed success.
3. Full durable recovery beyond last-operation marker and multi-owner crash coordination remains B; no production enabled, no 25-machine claim.
4. Real compile/GameTest and runtime acceptance remain integration-owned. Static source evidence is not Owner-ready.

Machine writing paused at this batch boundary pending B interface answer; not releasing to a competing writer implicitly. No background editor/build process launched and no outstanding write tool calls at checkpoint. Formal reassignment must preserve this six-file version and remaining obligations.

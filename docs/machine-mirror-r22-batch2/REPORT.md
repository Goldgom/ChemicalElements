# Machine mirror r22 batch2 — source only

Continues checkpoint1; no compilation, Gradle, javac, client, desktop capture or runtime output writes. Machine production stays disabled. No B/API edits. Old batch1 evidence retained.

## Actual signatures
`MachineProjection.Snapshot(OwnerKey.MachineInventory owner, Version version, String canonicalMirror, String mirrorDigest, String tankNbt, long tankMutationRevision, Marker projection, String digest)`
- owner is captured from actual BE dimension / block position / incarnation / machine type.
- Version(UUID generation,long mutationRevision) is live ABA, not persistent journal lineage.
- Marker(UUID generation,long revision,Optional<UUID> lastOperation,String requestDigest,String resultDigest) is persistent projection lineage.
- Marker.lastPayloadHash() aliases requestDigest for the **local operation only**, not the forthcoming full multi-owner journal payload. B must not treat it as the global payload hash.
- snapshot digest binds all these fields. replacement() retains owner/live/tank version/marker, replaces only encoded content.

`MachineCanonicalNbt.encode(Tag): String` explicitly sorts compound keys, preserves list order and tag types. `decode(String): CompoundTag` parses and checks exact canonical re-encoding. Full real aggregate tank NBT includes capacity and tank revision; snapshot additionally binds its live mutation clock. No forged TankImage parcels.

`MachineProjection.inspectRecovery(Snapshot expected,Snapshot replacement,UUID operation): RecoveryView`
BEFORE / AFTER / CONFLICT / QUARANTINED. READ-ONLY classification, not an authorization to write, quarantine a healthy owner, or replace a live generation. BEFORE uses unchanged persistent marker and full persisted content, AFTER uses operation+request hash+projection lineage/revision+complete after content. Unknown/ambiguous state returns CONFLICT, never overwrites. Actual journal-authorized recovery writes/quarantine still require B's unforgeable PREPARE permit.

`apply(expected,replacement,operation)` still admits only exact local full-instance/item-shell-conserving slot relocation, not cross-owner transfers. Now also checks full owner equality, marker equality, tank mutation version and canonical decode. Retry checks request and full replacement content plus persistent lineage, rather than only a last-operation marker. All decoding/capacity checks/counters are before one original-container root publish, with no post-publish callback.

Explicit snapshot codec roundtrip assertion added for clarity; B TransactionEncoding.decodeMirror ALREADY performed strict re-encode equality in batch1. Prior claim that it entirely lacked this check would be incorrect.

## Tests
MachineProjectionGameTests now seven methods. New method validates actual owner/marker/mirror digest, canonical tank key-order independence/type preservation, BEFORE classification after NBT reload with new live generation, refusal to apply stale live expectation, and neither-before-nor-after conflict preservation. Existing full image test updated to explicit canonical tank.

## Remaining
- B new owner subagent_d56a7e594f3447ae93eb60d9e41a26a1 notified immediately with actual signatures. B's new schema3 envelope must include real tank canonical/digest/version; existing Mirror-only payload does not bind it. Schema1/2 untouched.
- B owns unforgeable permit bound to unique durable PREPARE operation/global payload/owner before-after/live object identity. No boolean or publicly constructible cross-owner permission added here.
- Full cross-owner success and journal-authorized recovery mutation/quarantine not implemented; local API cannot pretend to authorize them. Await actual permit signature, then continue machine adapter.
- Mutable third-party component payload internals still need B mutation policy; current clocks cover list/stack/component-map writes only. No blanket ABA completeness claim.
- Source only; integration owner must compile and run these exact seven tests. Not Owner-ready, not 25 machines or completed transactions.

No in-flight writes or background editor/build launched at batch boundary. Machine exclusive ownership retained pending next dependency, not silently transferred.

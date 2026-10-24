# Machine mirror batch 4 — source only

Authoritative task time: 2026-10-24 07:08 EDT. Task chem-machine-mirror-r22.

## Changes
- MachineProjection now persists `lastJournalPayloadSha256` independently from `lastRequest`. `journalPayloadSha256(): String` returns empty when no global proof exists; `matchesJournalProjection(UUID,String): boolean` requires exact operation and a 64-hex full payload digest.
- Permit projection assigns the global digest together with the inventory root/operation/local request/result metadata. Local-only projection writes an empty global digest. Duplicate operation must also match the independent digest; local calls cannot masquerade as journal retries.
- `apply(DurableMachinePermit): Result` consumes B's authenticated expected/replacement/operation. It retains complete conservation and final `permit.require`; it does not create or modify a journal, reissue authority, or release quarantine.
- Machine NBT schema 2 adds `journal_payload_sha256`. Strict schema 1 remains readable with an EMPTY global proof, never inferred from requestDigest. Unknown/malformed metadata is retained and quarantined. This is machine metadata only: no journal schema1/2 edits.
- Marker's existing five fields/toString and Snapshot hashing are intentionally unchanged, preserving B's frozen envelope encoding and old PREPARE digest. Independent proof must be checked by B's AFTER commit/recover path; this is explicitly a remaining B integration dependency, not silently added to an incompatible envelope.
- Read-only inspectRecovery now rejects same-live-generation ABA even when content returns to BEFORE. Cross-generation BEFORE remains classification, not authorization.
- projectionDirty calls super.setChanged directly; no controlMutationRevision increment. Final require was NOT moved.

## Test sources
MachineProjectionGameTests now contains ten methods (eight existing + two new):
1. durableResumeAndIndependentHash: real BE/menu/journal PREPARE, NBT reload, old permit conflict, fresh resume, root apply, original request digest, independent full digest/wrong digest rejection, idempotent retry, NBT persistence and AFTER recovery.
2. legacyMetadataAndSameGenerationAba: same-generation ABA classification conflict; schema1 read without invented proof; schema2 save; corrupt global digest quarantine and exact raw metadata retention.
These tests have NOT been compiled or executed. B must add full-hash gating to commit/recover before claiming end-to-end recovery safety; the existing recover assertion alone does not prove that gating.

## Verified scope
- Read B batch3 manifest SHA256: 6EDDD44F0702E6B85F5ECFED83E2ECD8E2AB2C520A5695419AF70D05C0446820 (MATCH provided frozen version).
- Read actual DurableMachinePermit, MutablePayloadPolicy, MachineJournal classify/commit/recover, MachineOwnerEnvelope marker codec and real B test construction.
- git diff --check -- src/main/java/org/goldgom/chemicalelements/machine: exit 0.
- Index SHA256 remains 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69; 13 cached paths unchanged.
- Initial root instruction discovery returned exit 1 because AGENTS.md/CLAUDE.md were absent at those exact root paths; hash/index reads succeeded. No compile command was attempted.

## Boundaries / next integration
Only two owned machine Java files changed this batch. No instance/persistence/API/client/assets changes; no Gradle/javac/client/screenshots, no fixed integration snapshot or build outputs modified. No git add/reset/commit; no formal mods/saves touched. Water/production stay closed, cross-owner and reactions remain rejected. Not Owner-ready, not 25-machine production completion.

B must consume the exact new getters for AFTER full-payload matching, cover missing/wrong global proof including legacy metadata, and verify recovery authorization at its journal boundary. Unique integration owner must compile/run this exact machine+B version; all runtime outcomes remain pending.

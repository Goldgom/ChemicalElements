# Content cancellation recovery v1 — source-only

Authoritative task time: 2026-10-24 08:49 EDT. Task chem-content-recovery-evidence-r24.

## Baseline and scope
Original docs/material-owner-r24/manifest-v2.json SHA256 verified: ACF4708836CAB99FFB142470CA7D3980BC961A2AD6177F4091026BB5655708E1. Historical files are unchanged. unit_stack not reimplemented. Only the three assigned production/GameTest files and new assigned test/docs paths were written. Index baseline SHA256 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69; 13 staged paths. No git add/reset/commit, build, javac, GameTest, client, screenshot or classes/resources writes.

## Change
MaterialBlockEntity.Access.classify retains owner identity, loaded-main-thread, quarantine, pending conflict and full canonical business equality checks. AFTER still requires matching pending/last marker lineage; journal evidence NEVER authorizes marker-free AFTER. Recovery BEFORE requires marker lineage OR MaterialRecoveryEvidence.matches(op, hash, before, after, MaterialBlockEntity.this). The previous same-live-generation shortcut for recovery was removed. Ordinary non-recovery still requires current generation.

MaterialRootState.cancel clears only RESERVED pending, never records cancellation in last, never changes business image/hash/revision. Already projected and already completed matching operation reject cancellation. Marker-free cancellation remains a local no-op; the coordinator owns global all-BEFORE validation, not this local method. No persistent cancellation marker was invented. Existing schema unchanged.

## Read and evaluated B contract
Read actual MaterialRecoveryEvidence.java: current server/main thread, original-root identity lease bound to op/hash, unique SinkLedger.get(), not read-only, journal kind, MaterialJournal.validateStored, decoded operation/hash and complete before+after Change equality. This is suitable for BEFORE recovery only. It is not durable evidence itself and grants no persistence or commit permission. Journal validator actually called is MaterialJournal.validateStored, not a fictional SinkLedger.validateStored method.

Actual cancelAllBefore order: valid PREPARED/CANCELLED receipt -> require ALL BEFORE -> abortDurably or flushDurably -> guards CANCEL_BEFORE -> cancelIsolation -> guards CANCEL_AFTER -> PERSIST_BEFORE/persist/PERSIST_AFTER -> releaseCommitted (lease-release method name only, not commitDurably) -> forget. No commitDurably/notifyCommitted on cancel. The retained lease and original aborted journal keep classification possible after local pending is removed.

After release, MaterialRecoveryEvidence must return false. Reload rotates generation and contains no cancellation lineage. Proof must be reconstructed by B startup gate + original journal resolution of loaded roots + attachRecovery reservation before observations are enabled. attachRecovery currently checks isolationGap, reserves roots, retains lease and validates guards/classification. MaterialRecoveryBatch routes aborted entries to cancelAllBefore. No inference that a runtime startup gate is wired. BE isolationGap and persist still explicitly fail: synchronous chunk-root durability / world lifecycle integration is NOT complete. No bypass was added.

## Test source coverage (NOT executed)
MaterialRootGameTests.cancellationRecoveryEvidence uses a real installed BE, live authoritative disposable GameTest journal and exact physical root lease. Covers historical generation after NBT load, no-marker PREPARE-before-isolate, no lease, non-recovery historical rejection, wrong op/hash/root/full change/business root, non-main thread, cancellation then post-cancel classification, duplicate cancellation, explicit persist rejection, cancellation NBT roundtrip, released lease rejection, reload plus re-reservation rebuilding proof, and marker-free AFTER refusal. Existing pending test now rejects cancel after completion too.

Important: GameTest journal.prepare/abort are explicitly NON-DURABLE classification setup writes in a disposable world; not a production planner, not a successful transaction, not proof of PREPARE force or world persistence. NBT save/load is memory codec exercise only. Test lease cleanup is releaseUnprojected because no production projection-started lease is acquired; it is not coordinator cancellation-release proof. No fixture replaces the production persist exception. Actual cancel->disk persistence->process restart remains untested and blocked on world integration.

MaterialCancellationRecoveryTest main covers root cancellation codec preservation, duplicate no-op, wrong pending op/hash, projected/completed cancellation rejection, lease identity/hash/thread/release checks. Intended for unique integration owner next same-version batch, not run here.

## Handoff and remaining dependencies
B and heat resolver owners alone own their wiring. B must preserve startup closed gate and original-journal reattachment; no cache substitution. Unique integration owner must compile and run these new sources with matching B/world versions; do not reuse fixed batch 2693 output or older success. Water and machine production gates unchanged/closed. This bounded source fix does not establish material roundtrip, durability, owner readiness or whole-goal completion.

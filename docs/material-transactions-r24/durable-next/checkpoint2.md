# durable-next checkpoint 2 — 2026-10-24 08:46 EDT

Continues checkpoint1 without replaying ownership transfer. Same new task remains owner.

## Implemented source
- MaterialRecoveryEvidence.matches(op, hash, before, after, originalRoot): read-only authoritative SinkLedger exact transaction+change check combined with exact active original-root lease binding. Intended ONLY as additional BEFORE recovery lineage when PREPARE preceded first marker or cancellation cleared marker. Does not relax AFTER evidence or mint permit. Content owner integration requested via report_f4aa524ce7e34b838a4e979140874798; content is not edited here.
- ReservedPlayerSerialization: exact player + original inventory + exact target CompoundTag single-use capability. PlayerProjectionMixin wraps ONLY Inventory.save invocation inside Player.addAdditionalSaveData, replacing it with detached inventory NBT. Other fields use actual full Entity/Player.saveWithoutId. No ThreadLocal, ordinary inventory access remains blocked, recursive serialization to another tag rejects.
- PlayerProjectionState lease-bound isolate/project/finish metadata updates; ReservedPlayerPublication prevalidates marker then publishes actual three inventory lists and metadata with no callbacks under same lease. Complete production player adapter remains unfinished.
- MaterialPlayerStorageAccess + MaterialPlayerPersistence + MaterialPlayerDataStorageMixin: real playerDir strict complete-player save bridge; ordinary save HEAD rejects isolated player outside vanilla catch. JSON only adds MaterialPlayerDataStorageMixin, leaves other order and config unchanged.
- SinkLedger.materialOperationsForPlayer derives original journal UUID list including terminal decisions, for pre-observation load gate. This is currently a lookup API, NOT yet a connected or bounded startup gate. No additional persistent journal or material store.
- One actual-player serialization GameTest source verifies complete tag equality and ordinary observation/save remains blocked. Not run; strict attachment bridge must be implemented first.

## New proven incomplete boundary, exact requested scope
Read actual neoforge-21.1.264-sources.jar AttachmentHolder.serializeAttachments: catches each serializer Exception, logs and skips. deserializeAttachments likewise skips errors. Thus full saveWithoutId returning normally can still silently lose non-inventory data.
B added MaterialAttachmentEncodingAccess begin/finish/abort(Object exactCapability), but implementation requires additional target mixin beyond current five+save scope. ReservedPlayerSerialization explicitly rejects missing bridge; it does NOT report complete durable save.
Requested exact new mixin/MaterialAttachmentSerializationMixin.java + one JSON registration from orchestrator, report_6ef80eba276741469222c70ed7730a95 and message strict-attachment-scope. No ungranted mixin file written. Desired audit catches per-holder serializer errors after swallowed catch, reports failure, and cleans exact capability on exception. No broad ThreadLocal or ordinary-read bypass.

## Remaining scope owned
Implement audit once authorized, full player adapter and strict direct snapshot/lifecycle checks, no-marker pre-observation journal startup gate, public authenticated place/pickup/commit/cancel/recover, full phase planner and real recovery tests. Windows directory force portability still unfinished. Current full file publication is not ready on unsupported providers. World air/observation/chunk durability remains outside B ownership, not ready. No production water or machine enablement.

## Evidence
Static only. git diff --check exit0; exact one save mixin registration parses. Index SHA82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69 unchanged; 13 staged paths retained. No Gradle/javac/GameTest/client/screenshots and no classes/resources writes. Checkpoint1 report/manifest kept unchanged. No background process or unknown write.

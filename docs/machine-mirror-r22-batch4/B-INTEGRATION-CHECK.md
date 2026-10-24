# B + machine batch4 static integration check

Task chem-machine-mirror-r22, authoritative user time 2026-10-24 07:18 EDT.

No source modifications in this continuation. Existing machine batch4 manifest EF22E0BF2B30AE8D7E45E7024FE916F71D3365BFF7F50EA2B9CF58736B0E3437 remains frozen: all eight entries MATCH.

B batch4 manifest A8657ED0CF0521438802532D17B8133B4327515B88DEBE369273D4F939472F82 read and all three entries MATCH. Read actual MachineJournal.java recover/commit/validateStored:
- recover validates stored UUID/payload digest before classification; AFTER requires matchesJournalProjection(operation, entry payload_sha256) before commitDurably; mismatch returns CONFLICT.
- commit checks exact sole-ledger request equality and non-aborted state; requires AFTER plus matchesJournalProjection(operation, SHA256(request encoding)) before commitDurably.
- No journal schema1/2 or marker five-field encoding change is needed on machine side.

This closes the specific B source-wiring dependency identified in REPORT.md. That earlier report is historical batch4 evidence, not altered retroactively. B report records four GameTest methods including fresh-authority resume and wrong/blank/legacy proof rejection; machine has ten test methods. Neither suite has been compiled or run by this worker. No claim of crash recovery, production readiness or multi-owner CAS follows from this static check.

Static diff-check machine exit0; git index remains 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69, 13 staged paths. No build/class/resource/fixed snapshot/client writes.

Handoff: bounded machine full-mirror/projection source task is delivered with exact batch4 version for unique integration owner to compile/run together with B batch4 (which inherits B batch3). All runtime validation remains pending. No own in-flight writes or background scripts. Source frozen; ownership remains under orchestrator-controlled handoff, not implicitly returned to old D. Production/water remain disabled and cross-owner/reaction projections remain rejected.

# durable-next checkpoint 1 (2026-10-24 08:31 EDT user clock)

## Ownership and checked evidence
- Old task queried with task.get returned empty; that was NOT interpreted as safety. Read collaboration_board saved response coordination task: queued/waiting checkpoint4. Read 261024-long-beaver/session.jsonl exact b0828-safe-release body and final applied receipt (047660f2…14e14c06). Old owner explicitly read-only after transfer; latest orchestration message confirms this new task.
- Read full chem-b-five-mixin-release-r24 rev2. JSON currently EA48FDE69405DA5AC98494F4FA4642211460D33A86CE7588F88363AFFC297F97, unchanged. No new save mixin or registration yet.
- checkpoint4 manifest unchanged SHA 0398C388F694F4644623B7D54188A5E2FAADB0453ABF9F00178035050B3E59F0. Index 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69; 13 staged paths retained.
- Read software-development skill; no root/src AGENTS/CLAUDE found. All project operations explicitly E:/Projects/ChemicalElements.

## Source implemented in this stage
- MaterialLocationGuard contract: original physical access identity, op/hash/full before/after/recovery binding, explicit lifecycle boundaries and expected BEFORE/AFTER. No lease passed; validation cannot authorize bypass or arbitrary mutation.
- Coordinator retains independent alias guards; verifies them before/after classify, isolation, projection, persistence, completion and cancellation. prepare/attachRecovery accept explicit guard list. Existing recovery rejects replacement access/guard instances. Physical same-key different-instance rejection preserved.
- attachRecovery now rejects cross-owner shared observation roots like prepare. First attempted durable PREPARE retains original lease/ACTIVE even if durable call fails before isolate; this fixes same-process observation window, NOT restart gate.
- Recovery batch can resolve guards for original transaction; phase recovery with default resolver refuses instead of silently dropping guards.
- Lease can internally reserve newly decoded replacement child objects before publication, retaining all old aliases. ReservedPlayerPublication now reserves non-EMPTY replacement stacks/components before swapping original lists. Added isolation fixture test for old/new alias blocking and foreign-lease collision. This is not yet a complete player adapter.
- MaterialPlayerFilePublication is a strict full-NBT file primitive, not yet connected player save/adapter. It checks UUID/Inventory/Pos, copies complete tag, writes/readbacks compressed temporary image, forces file, preserves a uniquely named old-file copy, atomic replaces, forces file+directory, verifies full NBT. No safeReplace void success assumption, no fallback non-atomic replace, no swallowing failure or blind rollback. Backups/temps are not a second material/journal source.
- Three GameTest source methods use actual mock ServerPlayer serialization plus real files under engineering test run directory: full field preservation, replace failure preserves old target, post-replace fault propagates despite visible after image. Test directory-force is explicitly injected, hence NOT power-loss evidence.

## Direct vanilla source findings
Read build/moddev/artifacts/neoforge-21.1.264-sources.jar entries PlayerDataStorage.java and Util.java without extracting/building:
- save calls player.saveWithoutId, NbtIo.writeCompressed, Util.safeReplaceFile, saving event inside catch(Exception) that only logs; a normal save return cannot prove durability.
- safeReplaceFile discards boolean safeReplaceOrMoveFile. The latter retries backup/remove/replace, tries restore on replace failure, returns false; failed restore result also not surfaced as a successful new image. None of that path forces data/metadata.
- load tries .dat then .dat_old and invokes player.load followed by loading event. Restart isolation must precede that event, including no-marker PREPARE.
- Windows Java directory force remains unsupported/unverified. SYSTEM primitive fails before replacement when directory barrier probe fails; this is an UNFINISHED portability implementation, not success or a permanent scope reduction.

## Still owned and unfinished (do not accept task)
Complete player business+metadata publication; narrow safe complete NBT serialization while isolated; save/load mixin+single JSON registration; no-marker restart journal gate; authenticated public place/pickup/commit/cancel/recover and full phase planner; real integration/fault/alias tests. Guard contract sent as progress report 689bfb311c0c44af87ad9d000d0f792f for resolver owner because communication hop budget is zero. Resolver owner has now implemented Handle implements MaterialLocationGuard and NormalizedOwners.locationGuards(); read validate(Check) confirming original access identity and actual classified BEFORE/AFTER heat image checks. B ordered explicitly rejects wrapper Handle as physical owner. Production caller/mandatory thermal guard coverage still unfinished; no claim of success.
World report/ADDENDUM-v2 remain preparatory, world lifecycle/air identity/chunk durability not ready. B4, API/content/world/heat/machine not modified. Water and production remain disabled; complete 1290/25-machine/cross-chunk tombstone goals retained.

## Verification level
Final new manifest has 8/8 MATCH, SHA 055D3F96F6863AD9043798648418877FA998BA22ACD5008B4F04F891574D0A4E. All unaffected checkpoint4 entries MATCH; git diff --check exit0. Only source edits and static inspection/diff checks. No Gradle, javac, GameTest, client, screenshots, classes/resources changes. No successful durable business transaction is claimed. No background process launched.

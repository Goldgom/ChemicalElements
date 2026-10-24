# Machine batch3: permit adapter (source only)

Formal chem-machine-mirror-r22 continuation. Only MachineProjection.java and MachineProjectionGameTests.java changed this batch. No build/runtime commands, index changes or production enablement.

Actual new entry: public Result apply(Snapshot expected, Snapshot replacement, UUID operation, DurableMachinePermit permit). Null permit rejects. Full existing local conservation, canonical codec, capacity, expected CAS and counters are preflighted; permit.require(actual BE, expected, replacement, operation) is the final call before the single original inventory root publish. No callbacks after publish. This does not widen the domain to cross-owner movement.

Re-read actual B DurableMachinePermit on this turn: it now includes actor/fresh authority checks and projectionRequestDigest(). The adapter persists that ORIGINAL request digest rather than recomputing from rebound live snapshots. Global payloadSha256 remains distinct, managed by B. Current marker schema unchanged.

Snapshot and prepared replacement stack validation call B MutablePayloadPolicy.requireSupported. Unknown mutable payloads fail closed rather than claiming internal ABA coverage. Sent B a concrete risk: full getComponents includes vanilla default Rarity/ItemEnchantments/etc.; its conservative allowlist may reject ordinary iron ingots. B owns that policy and must review exact immutable types, not allow all third-party values. No local bypass added.

Added real BE GameTest method missingPermitAndMutablePayloadRejected: null permit leaves full snapshot unchanged; CUSTOM_DATA payload snapshot rejects without deleting input. Prior batch had seven methods; this adds one, totaling eight methods (annotation count checked in manifest generation). Success permit, authority revocation and restart recovery tests are B journal-owned and not replaced by mocks here. B's existing tests were read only.

Remaining: dedicated authenticated recovery/isolation handshake and real same-version successful prepare/apply/commit tests; cross-owner remains rejected and full CAS unfinished. Three-argument local-only apply retained for isolated local projection tests; it is not a journal transaction or production entry. No runtime claim, NOT Owner-ready. No in-flight/background writer at batch boundary.

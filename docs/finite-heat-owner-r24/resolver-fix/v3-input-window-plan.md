# v3 short input window — pending coordination, not implemented

2026-10-24 09:06 EDT. release rev3 formally resumed original task. Integration owner 9023 has been asked to confirm no input snapshot is active; coordinator notified. Source remains v2 until confirmed. No compiler/test execution in this worker.

Exact intended changes (no new production model):
1. Handle holds a distinct recoveryCandidate evidence object, NOT recoveryBinding. resolveRecovery validates current location, original physical identity, both detached thermal images and current port constraints, sets candidate only, returns RECOVERY_CANDIDATE. resolved() remains a locator-present indicator, explicitly not recovery success.
2. normalize/physicalOwners for a recovery candidate recheck only candidate location/evidence (never ordinary snapshot or full classify before B establishes lease). All guards are retained alongside original physical access.
3. classify continues full original physical lineage classification. A successful result binds recoveryBinding only AFTER thermal validation and physical classification. Candidate operation/hash/before/after must match later Check exactly; different transaction attempts are rejected, not silently rebound.
4. project for candidate handle requires a successful exact recovery binding regardless of whether current generation accidentally equals journal generation; no candidate itself permits mutation. Reclassification immediately before historical project remains.
5. guard.validate at B ATTACH and every subsequent boundary still invokes full classify and compares current thermal evidence to the classified BEFORE/AFTER. No caching replaces stage validation; no lease is released by resolver.
6. Test additions: candidate status is distinct from validated resolution; wrong binding rejected; source/static path check confirms no snapshot/classify in candidate validation. Existing real-BE lifecycle tests stay labelled white-box; no success phase claim.

Content now accepts MaterialRecoveryEvidence only on full BEFORE in recovery; AFTER still marker-bound. Source read confirmed this in prior message turn. That removes the content cancellation-source blocker, but does not itself execute recovery tests or supply a production enthalpy model. Original v1/v2 manifests remain historical.

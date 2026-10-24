# Resolver fix v3 — 2026-10-24 09:12 EDT task continuation

Scope: only the released resolver, dedicated GameTests/unit source and this docs directory. Integration owner 9023 explicitly released a short input window before snapshot/build. No Gradle, javac, GameTest, client or screenshot was run.

## Change
- resolveRecovery now returns RECOVERY_CANDIDATE after loaded location/access identity, immutable journal pair shape, thermal before/after evidence and current port constraints checks. It never calls ordinary snapshot or physical classify before B attach.
- recoveryCandidate is separate from recoveryBinding. normalize/physicalOwners repeat candidate checks, not snapshot/classify. Original physical access and all guards are retained; same owner/different instance still rejected.
- B MaterialCommitCoordinator.attachRecovery reserves original observation roots, retains its lease, then invokes ATTACH_RECOVERY guards. Resolver guard classify checks exact candidate operation/hash/full images, delegates full lineage to physical access, checks actual BEFORE/AFTER thermal evidence and roots, then binds history. Failed/unrelated classifications revoke local authority. Later boundaries reclassify; cached candidate is not proof.
- Historical project continues to require complete marker/image binding plus a fresh physical BEFORE classify. Old lifecycle handles remain invalid. Journal generation is not rewritten.

## Dependency inspected
MaterialBlockEntity recovery BEFORE requires full current before image plus pending/last lineage or MaterialRecoveryEvidence.matches with the real journal/lease. AFTER still requires marker. Content report177 is frozen input, not reimplemented. B attach code inspected for reserve → retain → guard ordering. No B/content edits.

## Test sources (NOT RUN)
- Existing real BE onLoad/setRemoved lifecycle and in-memory content marker BEFORE→AFTER tests retained.
- New white-box candidateDoesNotGrantHistoricalProjection uses real registered BE but reflectively installs locator evidence; asserts no successful binding and no content recovery without marker/journal lease. It is not admitted thermal recovery.
- Pure binding fixture adds equality rejection for changed operation/hash/full after image; existing physical access identity deduplication and different-instance rejection retained.

## Limits
No compiler or runtime validation. No admitted real thermal recovery or successful two-port single debit demonstrated: production whitelist/enthalpy admission remains unavailable. Test fixtures do not substitute for these. Real persistence/restart, B startup gate and world durability remain outside this bounded resolver implementation. Water/machine production remain disabled. Original v1/v2 manifests are not replaced. No git index mutations or classes/resources changes.

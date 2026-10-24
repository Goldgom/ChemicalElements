# material-r24 checkpoint3 — source only

Task: chem-b-material-transactions-r24. Sole project: E:\Projects\ChemicalElements.

## Implemented in this stage
- New material-kind codec explicitly handles the real A OwnerKey variants, including signed packed positions. Old MultiOwnerRequest encoding for existing player/machine variants is unchanged; new variants explicitly rejected there.
- SinkLedger loader validates the new kind in the same UUID namespace; old rejected UUID cannot be rebound through MaterialJournal.
- Business snapshot excludes pending/last operation metadata, removing recursive hash binding.
- Physical access aliases normalize by owner, accepting only repeated references to the same adapter object.
- Coordinator retains failed in-process reservations; attachRecovery and an internal maximum-eight-operation recovery batch use only supplied already-loaded roots.
- Original NonNullList get/mutations, ItemStack count/components reads and mutations, PatchedDataComponentMap get/mutations now consult the isolation barrier. This is NOT complete alias coverage.
- PlayerProjectionState reads/writes strict pending/last metadata alongside original player data and preserves corrupt original metadata. No public marker fabrication setter.
- MaterialPlayerSnapshot reads actual player inventory/FIFO, persistent lineage and mutation version; handSlot resolves original main/offhand slot.
- Three isolated GameTest fixture methods cover barrier and marker behavior. Not real place/pickup/phase success tests.

## Verification
- No Gradle, javac, GameTest, client, screenshots or fixed integration outputs used.
- Static git diff --check produced no whitespace errors (only existing line-ending warnings).
- git index SHA256 remains 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69; 13 staged paths remain.
- checkpoint3-manifest.json records 16 source paths/hashes. Manifest SHA256: B8AD0BCB6CDA5F15804BC98177B24B2624921E6C2454A0697585B238FD267CD1 (16/16 hashes reread MATCH).

## Not complete / next work
- No public authenticated production entry or successful real owner projection is installed.
- Content directory at last inspection contained only MaterialAdmission/MaterialBlockItem plus scripts; real BE root/air-position incarnation and atomic publication depend on assigned content owner.
- Player atomic direct root replacement, detached serialization/persistence with guards, complete exposed-alias coverage and lifecycle isolation remain necessary. Ordinary setItem cannot bypass guard. No broad ThreadLocal bypass will be treated as atomicity.
- Existing material metadata currently has no mutation setter by design: a safe original inventory + metadata publication path must supply it.
- SinkLedger now offers an at-most-eight-entry insertion-order recovery cursor (including committed/aborted cleanup candidates); no full list copy. Runtime event hookup and durable released-owner policy remain incomplete; internal batch alone is not active recovery.
- PHASE permits one physical owner after same-root thermal alias normalization; PLACE/PICKUP still require at least two. Codec support alone is not validated phase semantics.
- Live loaded-generation and physical incarnation reconciliation, full policy/bundle/thermal evidence integration, every-projection fault tests, true success roundtrip/phase and restart tests remain incomplete.
- Production phase enthalpy data is zero; no invented phase success.
- World/thermal/content/API/machine files remain other owners; B4 MachineJournal/test untouched. Shared helper/mixin edits constitute this new version, not unchanged B4.
- Water/production gates remain closed. Full water tombstones, 1290 recipes and 25 machines remain outside this partial result.

No final completion claim. All writes synchronous; no background build or script remains.

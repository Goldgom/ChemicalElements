# material-owner-r24-v1 — independent root stage, NOT Owner-ready

Only E:\Projects\ChemicalElements. No Gradle/javac/GameTest/client/screenshots executed. Fixed 2693/classes/resources untouched. Water/machine production stays closed.

## Implemented source
- MaterialProjection: canonical full blockstate + persistent revision + original StackLedger FIFO via LedgerJson; includes thermal, nuclides, pore solution and adsorption without re-minting records. Optional finite_heat is evidence checked against that same FIFO, not another inventory; missing model stays missing.
- MaterialRootState: single immutable business + pending + last unit; canonical NBT; original operation/globalHash/revisions/hashes validated; RESERVED preserves original business; PROJECTED replaces root in one assignment; complete and cancel duplicates; corrupt marker rejected.
- MaterialBlockEntity: actual BE and stable MaterialOwnerAccess/FiniteHeatBlockEntityRoot; persistent incarnation, independent live generation per load/removal; loaded map identity (no lazy creation); quarantined original material_root NBT retained; ordinary ledger observation blocked under pending/lease; full-image classify and restart lineage checks.
- MaterialBlock + ElementMaterials necessary BE registration and supplier replacement only. Existing 118+4 IDs, CONTENT/BLOCK_ITEMS and categories unchanged. Constructor empty root is zero material, not an air-position owner or placement.
- Three MaterialRootGameTests source cases: nonempty original FIFO roundtrip, actual BE NBT/replacement ABA, pending NBT/bad hash/cancel/duplicate/fault recovery. Existing MaterialAdmissionGameTests real place rejection remains; successful place/pickup tests still require actual public B entry and world durability integration.

## Concrete remaining integration (not disguised as completed fail-closed implementation)
1. Level#getBlockState/getBlockEntity/setBlock and LevelChunk#setBlockState/removeBlockEntity/map aliases plus chunk unload/reload must participate in observation/reservation and lifecycle. BE-only code cannot intercept external replacements or air-position ABA. These files/mixins are outside this task's released write set; orchestrator was notified with exact methods.
2. Original chunk business + marker need synchronous durable write/confirmation and recovery binding. Access.persist deliberately reports missing integration, never substitutes setChanged. Needs world owner implementation, no generic fake Port/in-memory durability callback.
3. B's package-private MaterialCommitCoordinator is not public authority. Server-authenticated slot/permission checked place/pickup planner and unique PREPARE/commit/cancel/recover caller are still needed. No super.place/shrink or independent setItem/setBlock transaction was introduced.
4. Real air target persistent incarnation/occupancy and atomic world projection/notification must be provided by the world lifecycle owner, not synthetic air BE. Current Access.project handles same blockstate root only and rejects a different world projection until that adapter exists.
5. Finite heat constructor needs actual model/reference/capacity evidence from production planner; no thermodynamic constants invented. C interface wired, root reports current concrete isolation gap.

## Evidence levels
Source written + static inspection only. No compilation, no GameTest execution, no durable commit proof, no full placement/pickup success, no Owner-ready claim. New version is separate from r23 historic manifests and 2693. Integration must compile and run this exact source together with current B/C/public OwnerKey and future world boundary fixes. Do not use prior batches as validation.

Initial index SHA256 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69; 13 cached paths observed. No git add/reset/commit used.

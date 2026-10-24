# Finite heat owner r24 v1 — source only, production NOT ready

Task: chem-finite-heat-owner-r24. Authoritative task date 2026-10-24 07:50 EDT.

## Implemented path

`ThermalRuntime.resolve(ServerLevel, OwnerKey.FiniteHeatSource)` -> immutable server whitelist (currently EMPTY; no production root exists) -> `LoadedFiniteHeatResolver.resolve` -> `ServerChunkCache.getChunkNow` -> existing `LevelChunk.getBlockEntities().get(pos)` (no lazy creation) -> actual BE implementing `FiniteHeatBlockEntityRoot` -> persistent incarnation / live generation / physical `MaterialOwnerAccess` -> full `MaterialOwnerSnapshot` -> `FiniteHeatValidator`.

A's actual public `OwnerKey.WorldBlock` and `FiniteHeatSource` now exist and are used directly. No parallel public identity was created. Existing generic ThermalRuntime.Port was unbound; it was removed rather than claiming it was a real owner. Existing bootstrap tick signature remains, ready=false, tick performs no mutation.

The handle delegates B's actual snapshot, classify, isolate, project, persist, complete, cancelIsolation and notifyCommitted methods to the original physical root. Every lifecycle check uses current loaded BE object identity + persistent incarnation + load generation + original access identity. Old handles cannot survive unload/reload/replacement. PlayerInventory cannot masquerade as a block source. Material/machine aliases return the physical owner key and the same original observation roots; BE itself must be a reserved root. No standalone map inventory, random incarnation or second CAS implementation exists.

Snapshot `canonicalRoot.finite_heat` contains full FIFO LedgerJson including thermal, nuclide, pore-solution, adsorption and provenance, plus exact stored energy via records, independent J/K, max J, max mol, occupied/max m³, pressure, reference, catalog epoch/hash, model identity/content digest. Evidence must equal the detached authoritative root section. Same-temperature EnthalpyPlanner validation reuses existing normal-domain full enthalpy, isotope, reference and pore checks. Unknown models, latent plateaus and unsupported mixtures reject unchanged. No production coefficients or Cp/latent/volume values were invented. BEFORE and AFTER validate at classify before PREPARE; project validates again. Global conservation remains B/planner responsibility, not two individual commits.

## Exact outstanding integration, not completed functionality

1. Content currently has only MaterialAdmission and MaterialBlockItem; no real content BE root was found. Content owner must add its actual BE and implement `FiniteHeatBlockEntityRoot.persistentIncarnation`, `liveGeneration`, `materialAccess`, `heatEvidence`. Incarnation must persist across load and rotate upon replacement; generation rotates every load; revision is independent and persistent.
2. That same physical access must guard all normal blockstate/BE/contents observation and mutation, expose actual aliases to MaterialObservationBarrier, atomically replace full root plus marker bound to original operation/globalHash, and synchronously persist. setChanged is insufficient. This batch cannot implement those methods inside content/machine due to explicit ownership boundaries. Machine B4 remains frozen.
3. B `MaterialCommitCoordinator` currently package-private; no authenticated production world entry available at inspection. Its inspected marker implementation supports PlayerInventory only and must reject unsupported variants before PREPARE, then support A variants. B notified. Snapshot's documented pending inclusion versus afterHash marker self-reference requires B resolution. Do not install an adapter while isolationGap remains.
4. Content physical root must ensure full root FIFO and finite_heat evidence agree (not two divergent inventories), physical capacities/geometry are authoritative, and last operation/globalHash + full images are compared by classify. C checks evidence but cannot prove arbitrary adapter implementations honest.
5. No production port whitelisted. After actual root and B authenticated entry are delivered, wire server-owned port registration and joint planner settlement in a new explicit version; do not enable old Port or skip durable journal. Full phase/place/pickup and water recovery remain separate unmet goals.

## Verification and next unique integration batch

No Gradle, javac, GameTest, client, screenshots, classes/resources writes or fixed-2693 changes were performed. Source-only tests: `FiniteHeatEvidenceTest.main`; GameTests `unknownPortNeverCreatesHeatRoot`, `unloadedLookupDoesNotLoadChunk`. These are rejection/codec tests, NOT successful production owner or recovery evidence. Real successful resolve, duplicate-alias joint accounting, unload/reload and crash recovery tests await real content root and B integration.

Static inspection: A public key signatures, B four interfaces and subsequently actual coordinator read; only authorized seven Java files written. Git cached paths count remains 13; index SHA256 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69. No git add/reset/commit. All writes within authorized project paths. Manifest lists exact file hashes. Water and machine production remain disabled.

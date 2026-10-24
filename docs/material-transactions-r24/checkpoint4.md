# checkpoint4 — source and static evidence only

Continuation of checkpoint3, not final delivery.

- Read actual content MaterialBlockEntity, MaterialProjection and MaterialRootState. Prior 'no BE' statement is historical and superseded. BE has stable physical access and real root, but isolationGap/persist still explicitly block production.
- MaterialTransferValidation decodes actual player and content root schemas and compares complete unique InstanceRecord sets (thermal/nuclear/pore/adsorption included); finite_heat is evidence, never counted twice.
- MaterialPlayerSlotPlan removes exact FIFO head or appends preserved complete unit stack into an authority slot; full/incompatible slot rejects without changing actual Inventory. Unit shell is required in content root for lossless pickup (request sent to content owner).
- MaterialPlacementPreparation.inspect(BlockPlaceContext) and MaterialPickupPreparation.inspect(ServerPlayer,BlockPos,int) perform actual server/player/loaded-target/permission checks and capture real snapshots. These are public read-only preparation, NOT production commit.
- Added three actual-player plan GameTests (FIFO plan roundtrip/full-slot preserves input/ABA). They use explicitly synthetic test records and do not prove durable transaction or phase success. Fixed three metadata fixture template annotations.
- Coordinator now enforces current bundle/full-record relocation gate before PREPARE, checks bundle after flush, and isolates all BEFORE owners on recovery before first material projection. Phase requires thermal validation but still explicitly blocks absent joint planner.
- Read Inventory.java and NonNullList.java from the project's actual neoforge-21.1.264-sources.jar. Added ReservedListAccess to the original NonNullList mixin; validates exact lease then swaps private backing without setItem/hooks/callbacks. ReservedPlayerPublication prebuilds detached 41 slots then publishes three original guarded lists. This primitive is not complete atomic persistence: player metadata/serialization/lifecycle and full stack/component alias coverage still need implementation.

## Evidence
No Gradle/javac/GameTest/client/screenshots executed. git diff --check returned no whitespace errors (existing LF warnings only). Index SHA remains 82DB786AA6A4F64D0B38F02BE6E7EAF7A1D35E9E905E7EFE0C29713593CF1B69.
checkpoint4-manifest.json: 24/24 source hashes reread MATCH; manifest SHA 0398C388F694F4644623B7D54188A5E2FAADB0453ABF9F00178035050B3E59F0.

## Remaining ownership and work
B: actual player MaterialOwnerAccess with persistent atomic business+marker and synchronous persistence, all aliases/lifecycle fencing, authenticated commit/cancel/recover public service, complete phase planner, runtime recovery registration, true entry success and per-projection crash tests. These remain B implementation responsibility, not user configuration.
Content: preserve original unit stack shell in one root, expose lossless codec, wire actual place/pickup. World assigned boundary: air incarnation/position lifecycle, observation and block root replacement, synchronous chunk persistence. C: resolve alias identity and reload-generation recovery guards.
All changes remain scoped; no B4/machine/world/thermal/content/API edits by this worker; no production gate enabled. No background work or unknown writes.

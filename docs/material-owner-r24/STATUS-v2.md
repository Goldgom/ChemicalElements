# material-owner-r24-v2 — full original unit evidence

Increment over v1; source/static only. No compilation, GameTest, client, screenshot or production enablement.

B's actual MaterialPlayerSlotPlan.removeHead(snapshot,slot,registries) returns Removed(after,unitStack,instance). v2 retains that original unitStack in MaterialProjection's optional fifth component. canonicalRoot.unit_stack is the complete canonical ItemStack.CODEC JSON including all non-chemistry components. It is evidence of the same one-instance ledger, NOT another inventory/material balance. Transfer accounting must count root.ledger once, not unit_stack or finite_heat again.

MaterialProjection.validateUnit(registries) rejects noncanonical/lossy CODEC roundtrips, empty or count!=1 stacks, and any original StackLedger inequality including FIFO, revisions, thermal, nuclides, pore and adsorbed fields. BE load and project invoke it; nonempty projection requires original unit evidence. Missing evidence remains allowed for independent legacy root codec fixtures but does not authorize pickup or production projection. Full item components must be retained/revalidated by any later transformation planner; no default new BlockItem on pickup.

Test source extends completeOriginalFifoCodec with CUSTOM_NAME retention and ledger revision mismatch rejection. Nothing executed. Original v1 manifest is historical and no longer hashes the three changed source files. v2 manifest is the current source snapshot.

Concrete remaining dependencies unchanged: authenticated B public place/pickup/commit/cancel/recover service; real air target persistent identity; Level/LevelChunk observation/replacement guards; synchronous original chunk+marker durability; successful world projection/notification; actual end-to-end tests. World boundary preparation was assigned separately, not evidence of implementation. Current persist/isolationGap remains explicit and is not successful delivery. Same-blockstate root publication only. Water/machine production remains closed.

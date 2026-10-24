package org.goldgom.chemicalelements.api.tx;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Owner value identity only. Revision, snapshots and durable CAS belong to the instance service.
 * Session identifiers and live loading generations never belong here. Codecs must encode
 * variants explicitly; this type does not change schema1/schema2 or existing schema3 encoding.
 * An incarnation must be supplied by the authoritative persistent owner lifecycle, not minted
 * per request. Constructing a key neither authenticates an owner nor proves durable CAS.
 */
public sealed interface OwnerKey permits OwnerKey.PlayerInventory, OwnerKey.MachineInventory,
        OwnerKey.WorldBlock, OwnerKey.FiniteHeatSource {
    record PlayerInventory(UUID player) implements OwnerKey {
        public PlayerInventory { Objects.requireNonNull(player, "player"); }
    }
    record MachineInventory(ResourceLocation dimension, long controllerPos, UUID incarnation,
                            ResourceLocation machineType) implements OwnerKey {
        public MachineInventory {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(incarnation, "incarnation");
            Objects.requireNonNull(machineType, "machineType");
        }
    }

    /** A world block incarnation, distinct from a machine inventory even at the same position. */
    record WorldBlock(ResourceLocation dimension, long blockPos, UUID incarnation) implements OwnerKey {
        public WorldBlock {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(incarnation, "incarnation");
        }
    }

    /** A finite block/BE heat port, not a heat budget or a non-block source identity.
     * The adapter must validate the stable server-recognized port and normalize aliases of
     * the same physical material/machine root to one owner/lock before any transaction.
     * Distinct port values alone do not establish independent reservoirs or CAS authority.
     */
    record FiniteHeatSource(ResourceLocation dimension, long blockPos, UUID incarnation,
                            ResourceLocation port) implements OwnerKey {
        public FiniteHeatSource {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(incarnation, "incarnation");
            Objects.requireNonNull(port, "port");
        }
    }
}

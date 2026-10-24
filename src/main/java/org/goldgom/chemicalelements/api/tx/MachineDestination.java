package org.goldgom.chemicalelements.api.tx;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Block-machine identity; controllerPos is the packed BlockPos, incarnation rejects replacements.
 * This value does not alter or implement the existing MachineTransactions port.
 */
public record MachineDestination(ResourceLocation dimension, long controllerPos, UUID incarnation,
                                 ResourceLocation machineType) implements TransactionDestination {
    public MachineDestination {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(incarnation, "incarnation");
        Objects.requireNonNull(machineType, "machineType");
    }
}

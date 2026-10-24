package org.goldgom.chemicalelements.api.tx;

import java.util.Objects;
import net.minecraft.world.Container;

/** Server-side location only: never encode the Container reference or accept it from a packet.
 * The resolver must revalidate actor/session and the real menu Slot mapping on every request.
 * This is not a snapshot, a revision token, a grant, or a second inventory.
 */
public record AuthoritySlot(OwnerKey owner, Container container, int containerSlot,
                            int menuSlot, int layoutVersion) {
    public AuthoritySlot {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(container, "container");
        if (containerSlot < 0 || containerSlot >= container.getContainerSize())
            throw new IllegalArgumentException("containerSlot out of bounds");
        if (menuSlot < 0 || layoutVersion < 0)
            throw new IllegalArgumentException("negative menuSlot/layoutVersion");
    }
}

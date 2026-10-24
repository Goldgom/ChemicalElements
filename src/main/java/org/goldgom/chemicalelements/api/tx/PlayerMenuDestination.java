package org.goldgom.chemicalelements.api.tx;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Player owns matter; session only authorizes a server-issued, never-reused menu lifetime.
 * layoutVersion binds menu-to-authoritative-slot mapping. ownerRevision is the authoritative
 * inventory CAS revision, not a menu update counter. Callers must verify both against the server.
 * Closing invalidates new submissions, not existing journal operations. Death/unload must resolve
 * ownership through that same journal; unavailable projections remain held there. A receipt never
 * authorizes appending items. A committed operation projects only its decided after image.
 */
public record PlayerMenuDestination(UUID player, UUID session, ResourceLocation menuType,
                                    long layoutVersion, long ownerRevision) implements TransactionDestination {
    public PlayerMenuDestination {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(menuType, "menuType");
        if (layoutVersion < 0 || ownerRevision < 0) throw new IllegalArgumentException("negative destination revision");
    }
}

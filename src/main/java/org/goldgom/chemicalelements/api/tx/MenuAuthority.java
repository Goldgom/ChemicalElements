package org.goldgom.chemicalelements.api.tx;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Server-issued menu authorization. Not a persistent inventory identity or a client credential. */
public record MenuAuthority(UUID player, UUID session, ResourceLocation menuType, int layoutVersion) {
    public MenuAuthority {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(menuType, "menuType");
        if (layoutVersion < 0) throw new IllegalArgumentException("negative layoutVersion");
    }
}

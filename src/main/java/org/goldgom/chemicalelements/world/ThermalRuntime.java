package org.goldgom.chemicalelements.world;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.world.heat.LoadedFiniteHeatResolver;

/** No generic caller-supplied commit Port: the former interface had no production binding,
 * authentication, durable physical root or B transaction. Pure exchange plans are not commits.
 * Production scheduling stays disabled until content roots and B journal settlement are wired.
 */
public final class ThermalRuntime {
    private ThermalRuntime() {}
    // Empty until a real server-owned BE port is delivered. Never whitelist a request string.
    private static final LoadedFiniteHeatResolver RESOLVER = new LoadedFiniteHeatResolver(Set.of());
    public static LoadedFiniteHeatResolver.Resolution resolve(ServerLevel level, OwnerKey.FiniteHeatSource key) {
        return RESOLVER.resolve(level, key);
    }
    public static boolean ready() { return false; }
    public static void tick(LevelTickEvent.Post event) {
        // Existing bootstrap entry intentionally does not schedule or mutate incomplete owners.
        // Missing production BE root and joint transaction wiring are recorded in the r24 manifest.
    }
}

package org.goldgom.chemicalelements.instance;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.goldgom.chemicalelements.machine.MachineServices;
import org.goldgom.chemicalelements.persistence.ChemistryComponents;
import org.goldgom.chemicalelements.persistence.SinkLedger;
import org.goldgom.chemicalelements.persistence.WorldChemistryStore;
import org.slf4j.Logger;

/**
 * Package B entry point. Call once from the mod constructor: {@code InstanceBootstrap.bootstrap(modBus)}.
 * Registers the instance ledger component and chunk attachment, server lifecycle hooks (persistent id
 * serial, journal recovery), the death/drop boundary audit, binds C's water store port and installs B's
 * StackChemistry/Settlement into D's MachineServices. A self-probe at server start checks that the item
 * mixins are active; if not, chemistry is reported disabled instead of silently losing matter.
 */
public final class InstanceBootstrap {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean installed;
    private static boolean lifecycleRegistered;
    private static boolean readServicesRegistered;
    private static volatile boolean probeOk;

    private InstanceBootstrap() {}

    public static synchronized void bootstrap(IEventBus modBus) {
        if (installed) return;
        ChemistryComponents.register(modBus);
        registerLifecycle();
        WaterStorePort.bindIfAbsent();
        registerReadServices();
        installed = true;
    }

    /** Install real ledger queries and pure synthesis/materialization previews. This does
     * not enable water, machine ticking or durable material commits. */
    public static synchronized void registerReadServices() {
        if (readServicesRegistered) return;
        MachineServices.installProduction(InstanceStackChemistry.INSTANCE, ProductSettlement.INSTANCE);
        readServicesRegistered = true;
    }

    /** Identity/probe/drop listeners only; no component registration, water binding or production install. */
    public static synchronized void registerLifecycle() {
        if (lifecycleRegistered) return;
        NeoForge.EVENT_BUS.addListener(InstanceBootstrap::serverStarted);
        NeoForge.EVENT_BUS.addListener(InstanceBootstrap::serverStopped);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, InstanceBootstrap::entityJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, InstanceBootstrap::livingDrops);
        lifecycleRegistered = true;
    }

    /** True once the server-side self probe confirmed the copy/merge mixins are applied. */
    public static boolean chemistryEnabled() { return probeOk; }

    static void serverStarted(ServerStartedEvent e) {
        var sink = SinkLedger.get();
        InstanceSampler.bindIdentitySource(new InstanceSampler.IdentitySource() {
            public long nextSerial() { return sink.nextSerial(); }
            public long worldSeed() { return e.getServer().overworld().getSeed(); }
        });
        probeOk = probe();
        if (!probeOk) LOG.error("ChemicalElements instance mixins are NOT active: chemistry ledger disabled (no transfers settled)");
    }

    /** Explicit legacy journal recovery; not part of the safe lifecycle entry point. */
    public static void recoverLegacy(net.minecraft.server.MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            int n = WorldChemistryStore.recover(level);
            if (n > 0) LOG.info("Recovered {} journaled water transaction(s) in {}", n, level.dimension().location());
        }
    }

    static void serverStopped(ServerStoppedEvent e) { InstanceSampler.unbindIdentitySource(); probeOk = false; }

    static void chunkLoad(ChunkEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel level && !SinkLedger.get().pending().isEmpty()) WorldChemistryStore.recover(level);
    }

    /** Probe: a two-item stack copied with count 1 must carry exactly one instance (copy hook applied). */
    public static boolean probe() {
        try {
            var s = new ItemStack(Items.STONE, 2);
            StackLedgers.resolveObserved(s, "probe");
            var c = s.copyWithCount(1);
            boolean ok = StackLedgers.get(c).map(l -> l.instances().size() == 1).orElse(false);
            return ok;
        } catch (RuntimeException ex) {
            LOG.error("instance probe failed", ex);
            return false;
        }
    }

    /**
     * Any item entity entering a server level outside a settlement scope (commands, loot, mob drops,
     * structure chests opened elsewhere) gets its instances resolved once with an explicit origin.
     * Entities spawned inside a scope were already settled by it.
     */
    static void entityJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ItemEntity ie) || e.loadedFromDisk()) return;
        var stack = ie.getItem();
        if (stack.isEmpty() || StackLedgers.hasLedger(stack) || Settlement.active()) return;
        StackLedgers.resolveObserved(stack, "entity_spawn");
    }

    /** Death drops: each dropped entity already holds the instances it was split from; audit only. */
    static void livingDrops(LivingDropsEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        for (var d : e.getDrops()) StackLedgers.normalize(d.getItem(), "death_drop");
    }

    static BlockPos pos(long p) { return BlockPos.of(p); }
}

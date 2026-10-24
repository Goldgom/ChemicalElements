package org.goldgom.chemicalelements.world;

import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;

/** A calls once from common initialization. This guard supplements, not replaces, finite-flow mixins. */
public final class SimulationBootstrap {
    private static boolean installed;
    public static final org.goldgom.chemicalelements.reaction.SolubilityLibrary SOLUBILITY = new org.goldgom.chemicalelements.reaction.SolubilityLibrary();
    public static final org.goldgom.chemicalelements.reaction.ReactionLibrary REACTIONS = new org.goldgom.chemicalelements.reaction.ReactionLibrary();
    public record Models(long epoch, org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine.Library reactions, java.util.List<org.goldgom.chemicalelements.reaction.PhaseEquilibrium.Salt> salts) {
        public Models { salts=java.util.List.copyOf(salts);java.util.Objects.requireNonNull(reactions); }
    }
    private static volatile java.util.function.Supplier<Models> models;
    /** A owns the one atomic catalog+models reference. Bind before reload listeners register. */
    public static synchronized void bindModels(java.util.function.Supplier<Models> supplier){
        if(models!=null&&models!=supplier)throw new IllegalStateException("models already bound");
        models=java.util.Objects.requireNonNull(supplier);
    }
    public static Models models(){var supplier=models;if(supplier==null)throw new IllegalStateException("Unified catalog/model publisher not installed");return supplier.get();}
    public static final org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine SYNTHESIS = org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine.atomic(()->{
        var captured=models();
        return new org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine.VersionedLibrary(captured.epoch(),captured.reactions());
    });
    private SimulationBootstrap() {}
    public static synchronized void bootstrap(IEventBus modBus) {
        if(installed)return;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SimulationBootstrap::denyInfiniteWater);
        NeoForge.EVENT_BUS.addListener(SimulationBootstrap::reload);
        NeoForge.EVENT_BUS.addListener(ChunkWaterInitializationRuntime::load);
        NeoForge.EVENT_BUS.addListener(ChunkWaterInitializationRuntime::unload);
        NeoForge.EVENT_BUS.addListener(ChunkWaterInitializationRuntime::unloadLevel);
        NeoForge.EVENT_BUS.addListener(ChunkWaterInitializationRuntime::tick);
        NeoForge.EVENT_BUS.addListener(FiniteWaterRuntime::unload);
        NeoForge.EVENT_BUS.addListener(FiniteWaterRuntime::unloadLevel);
        NeoForge.EVENT_BUS.addListener(FiniteWaterRuntime::tick);
        NeoForge.EVENT_BUS.addListener(DissolutionRuntime::tick);
        NeoForge.EVENT_BUS.addListener(NuclearRuntime::tick);
        NeoForge.EVENT_BUS.addListener(AtmosphereRuntime::tick);
        NeoForge.EVENT_BUS.addListener(SorptionRuntime::tick);
        NeoForge.EVENT_BUS.addListener(ThermalRuntime::tick);
        installed=true;
    }
    private static void reload(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(org.goldgom.chemicalelements.nuclear.DecayReloadListener.INSTANCE);
        event.addListener(org.goldgom.chemicalelements.fluid.SorptionLibrary.INSTANCE);
        // A alone prepares, validates and publishes species/reactions/solubility together.
        // Never register independent fallback listeners that could publish mismatched epochs.
    }
    private static void denyInfiniteWater(CreateFluidSourceEvent event) {
        if(event.getFluidState().is(FluidTags.WATER))event.setCanConvert(false);
    }
}

package org.goldgom.chemicalelements.persistence;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** B-owned registrations: per-stack instance ledger component and per-chunk chemistry store. */
public final class ChemistryComponents {
    public static final String MODID = "chemicalelements";
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    /** Persistent and network-synchronized: vanilla equality/hash/sync keep full semantics. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LedgerPayload>> LEDGER =
            COMPONENTS.registerComponentType("instance_ledger",
                    b -> b.persistent(LedgerPayload.CODEC).networkSynchronized(LedgerPayload.STREAM_CODEC));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChunkChemistry>> CHUNK =
            ATTACHMENTS.register("chunk_chemistry",
                    () -> AttachmentType.builder(() -> new ChunkChemistry()).serialize(new ChunkChemistry.Serializer()).build());

    private static boolean registered;

    private ChemistryComponents() {}

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        registered = true;
    }

    /** True once registries are frozen and the component can be used. */
    public static boolean ready() { return LEDGER.isBound(); }
}

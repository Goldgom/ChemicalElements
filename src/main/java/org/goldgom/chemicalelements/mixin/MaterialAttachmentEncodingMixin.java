package org.goldgom.chemicalelements.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.attachment.*;
import org.goldgom.chemicalelements.instance.materialtx.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Audit the original serializer invocation once, including exceptions NeoForge logs
 * and swallows. No second encoding and no relaxation of inventory observation barriers. */
@Mixin(value = AttachmentHolder.class, remap = false)
public abstract class MaterialAttachmentEncodingMixin implements MaterialAttachmentEncodingAccess {
    @Unique private Object chemicalelements$audit;
    @Unique private boolean chemicalelements$encoding, chemicalelements$encoded;
    @Unique private RuntimeException chemicalelements$failure;

    @Override public void chemicalelements$beginAttachmentAudit(Object capability) {
        if (!(capability instanceof ReservedPlayerSerialization serialization))
            throw new IllegalArgumentException("INVALID_ATTACHMENT_AUDIT_CAPABILITY");
        serialization.requirePlayer(this);
        if (chemicalelements$audit != null) throw new IllegalStateException("REENTRANT_ATTACHMENT_AUDIT");
        chemicalelements$audit = capability;
        chemicalelements$encoded = false;
        chemicalelements$failure = null;
    }
    @Override public void chemicalelements$finishAttachmentAudit(Object capability) {
        if (chemicalelements$audit != capability || chemicalelements$encoding || !chemicalelements$encoded)
            throw new IllegalStateException("ATTACHMENT_AUDIT_INCOMPLETE");
        if (chemicalelements$failure != null) throw chemicalelements$failure;
        chemicalelements$audit = null;
    }
    @Override public void chemicalelements$abortAttachmentAudit(Object capability) {
        if (chemicalelements$audit == null) return;
        if (chemicalelements$audit != capability) throw new IllegalStateException("FOREIGN_ATTACHMENT_AUDIT");
        chemicalelements$audit = null;
        chemicalelements$failure = null;
        chemicalelements$encoded = chemicalelements$encoding = false;
    }
    @WrapMethod(method = "serializeAttachments")
    private CompoundTag chemicalelements$encode(HolderLookup.Provider provider, Operation<CompoundTag> original) {
        if (chemicalelements$audit == null) return original.call(provider);
        if (chemicalelements$encoding || chemicalelements$encoded)
            throw new IllegalStateException("REENTRANT_ATTACHMENT_ENCODING");
        chemicalelements$encoding = true;
        try {
            var result = original.call(provider);
            chemicalelements$encoded = true;
            return result;
        } finally { chemicalelements$encoding = false; }
    }
    @WrapOperation(method = "serializeAttachments", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/attachment/IAttachmentSerializer;write(Ljava/lang/Object;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/Tag;"))
    private Tag chemicalelements$captureFailure(IAttachmentSerializer<?, ?> serializer, Object value,
            HolderLookup.Provider provider, Operation<Tag> original) {
        try { return original.call(serializer, value, provider); }
        catch (RuntimeException failure) {
            if (chemicalelements$audit != null && chemicalelements$failure == null)
                chemicalelements$failure = failure;
            throw failure;
        }
    }
}

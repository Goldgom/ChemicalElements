package org.goldgom.chemicalelements.instance.materialtx;

/** Required strict audit on the actual AttachmentHolder. The mixin implementation must capture
 * every swallowed serializer exception for this exact holder/call, then propagate it after the
 * vanilla catch. It must not enable ordinary owner reads or hide errors in nested serialization.
 * Implemented on AttachmentHolder by MaterialAttachmentEncodingMixin.
 */
public interface MaterialAttachmentEncodingAccess {
    void chemicalelements$beginAttachmentAudit(Object exactCapability);
    void chemicalelements$finishAttachmentAudit(Object exactCapability);
    /** Clear only this exact audit after failure; idempotent after successful finish. */
    void chemicalelements$abortAttachmentAudit(Object exactCapability);
}

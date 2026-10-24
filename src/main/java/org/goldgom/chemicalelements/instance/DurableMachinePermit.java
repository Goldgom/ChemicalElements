package org.goldgom.chemicalelements.instance;

import java.util.*;
import net.minecraft.nbt.*;
import org.goldgom.chemicalelements.machine.*;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Unforgeable through the public Java API. Issued only after a synchronous sole-journal barrier.
 * Not serializable: after restart the coordinator must reconcile actual owners and reissue.
 * A permit is bound to the actual BE object, not merely a caller-provided OwnerKey or boolean. */
public final class DurableMachinePermit {
    private final SinkLedger ledger;
    private final UUID operation;
    private final String payload;
    private final MachineBlockEntity machine;
    private final MachineOwnerEnvelope envelope;
    private final MachineProjection.Snapshot liveBefore, liveAfter;
    private final net.minecraft.server.level.ServerPlayer actor;
    private final org.goldgom.chemicalelements.api.tx.MenuAuthority authority;
    private DurableMachinePermit(SinkLedger ledger, UUID operation, String payload,
            MachineBlockEntity machine, MachineOwnerEnvelope envelope,
            MachineProjection.Snapshot liveBefore, MachineProjection.Snapshot liveAfter,
            net.minecraft.server.level.ServerPlayer actor, org.goldgom.chemicalelements.api.tx.MenuAuthority authority) {
        this.ledger = ledger; this.operation = operation; this.payload = payload;
        this.machine = machine; this.envelope = envelope; this.liveBefore = liveBefore; this.liveAfter = liveAfter;
        this.actor = actor; this.authority = authority;
    }
    // Package-private issuance: not an API for arbitrary callers to confer authority.
    static DurableMachinePermit issue(SinkLedger ledger, UUID operation, String payload,
            MachineBlockEntity machine, MachineOwnerEnvelope envelope,
            MachineProjection.Snapshot liveBefore, MachineProjection.Snapshot liveAfter,
            net.minecraft.server.level.ServerPlayer actor, org.goldgom.chemicalelements.api.tx.MenuAuthority authority) {
        var request = MachineJournal.Request.decode(payload, actor.registryAccess());
        MachineJournal.validate(request, actor.registryAccess());
        if (!request.machine().equals(envelope) || !request.intent().operation().equals(operation)
                || MachineJournal.resolve(actor, request, authority) != machine
                || MachineJournal.classify(machine, envelope, operation) != MachineJournal.Recovery.BEFORE
                || !MachineJournal.sameContent(machine, liveBefore, envelope.before())
                || !MachineJournal.sameContent(machine, liveAfter, envelope.after()))
            throw new IllegalStateException("INVALID_PERMIT_ISSUANCE");
        ledger.flushDurably();
        var permit = new DurableMachinePermit(ledger, operation, payload, machine, envelope, liveBefore, liveAfter, actor, authority);
        permit.require(machine, liveBefore, liveAfter, operation);
        return permit;
    }
    /** Machine adapter invokes this immediately before publication, after its no-fail preflight.
     * Full journal payload and real object identity are rechecked; an aborted/replaced/stale permit fails. */
    public void require(MachineBlockEntity actual, MachineProjection.Snapshot before,
            MachineProjection.Snapshot after, UUID op) {
        if (actual != machine || !operation.equals(op) || !liveBefore.equals(before) || !liveAfter.equals(after)
                || SinkLedger.get() != ledger || ledger.readOnly() || actual.getLevel() == null
                || actual.getLevel().isClientSide || actual.getLevel().getServer() == null
                || !actual.getLevel().getServer().isSameThread() || actual.isRemoved()
                || actual.getLevel().getBlockEntity(actual.getBlockPos()) != actual)
            throw new IllegalStateException("INVALID_DURABLE_MACHINE_PERMIT");
        var request = MachineJournal.Request.decode(payload, actor.registryAccess());
        if (MachineJournal.resolve(actor, request, authority) != actual)
            throw new IllegalStateException("PERMIT_AUTHORITY_EXPIRED");
        CompoundTag entry = ledger.journalEntry(operation).orElseThrow(() -> new IllegalStateException("MISSING_PREPARE"));
        if (!MachineJournal.KIND.equals(entry.getString("kind")) || entry.getBoolean("aborted")
                || !payload.equals(entry.getString("payload"))
                || !TransactionEncoding.sha256(payload).equals(entry.getString("payload_sha256")))
            throw new IllegalStateException("PREPARE_NO_LONGER_VALID");
        for (int slot = 0; slot < actual.inventory().getContainerSize(); slot++)
            MutablePayloadPolicy.requireSupported(actual.inventory().getItem(slot));
        if (!envelope.owner().incarnation().equals(actual.incarnation())
                || !envelope.owner().dimension().equals(actual.getLevel().dimension().location())
                || envelope.owner().controllerPos() != actual.getBlockPos().asLong()
                || !envelope.owner().machineType().getPath().equals(actual.kind().id())
                || !envelope.marker().equals(actual.projection.marker())
                || !before.equals(actual.projection.snapshot()))
            throw new IllegalStateException("PREPARE_OWNER_CONFLICT");
    }
    /** The machine must persist this ORIGINAL projection request digest, not one recomputed from
     * rebound live snapshots. It is distinct from the full journal payloadSha256. */
    public String projectionRequestDigest() {
        return TransactionEncoding.sha256(operation + "\n" + envelope.before().digest() + "\n" + envelope.after().digest());
    }
    public MachineProjection.Snapshot expected() { return liveBefore; }
    public MachineProjection.Snapshot replacement() { return liveAfter; }
    public UUID operation() { return operation; }
    public String payloadSha256() { return TransactionEncoding.sha256(payload); }
}

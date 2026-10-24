package org.goldgom.chemicalelements.instance;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import org.goldgom.chemicalelements.machine.*;
import org.goldgom.chemicalelements.persistence.SinkLedger;

/** Bounded machine-only relocation slice in the existing UUID namespace. Multi-owner player
 * projection remains separate work: this entry rejects it rather than issuing an incomplete permit. */
public final class MachineJournal {
    public static final String KIND = "machine_owner_prepared_v3";
    private MachineJournal() {}
    public enum Recovery { BEFORE, AFTER, CONFLICT, CANCELLED }
    public record Request(MultiOwnerRequest intent, MachineOwnerEnvelope machine) {
        public String encode(HolderLookup.Provider registries) {
            var root = new JsonObject(); root.addProperty("schema", 3);
            root.addProperty("intent", intent.encode(registries)); root.addProperty("machine", machine.encode());
            return TransactionEncoding.canonical(root);
        }
        public static Request decode(String text, HolderLookup.Provider registries) {
            if (text.length() > 32 * 1024 * 1024) throw new IllegalArgumentException("journal budget");
            var root = JsonParser.parseString(text).getAsJsonObject();
            var result = new Request(MultiOwnerRequest.decode(root.get("intent").getAsString(), registries),
                    MachineOwnerEnvelope.decode(root.get("machine").getAsString(), registries));
            if (!text.equals(result.encode(registries))) throw new IllegalArgumentException("noncanonical machine journal");
            return result;
        }
    }
    private static MachineBlockEntity resolve(ServerPlayer actor, Request request) {
        return resolve(actor, request, request.intent().authority());
    }
    static MachineBlockEntity resolve(ServerPlayer actor, Request request, org.goldgom.chemicalelements.api.tx.MenuAuthority auth) {
        if (!auth.player().equals(request.intent().authority().player())) throw new IllegalStateException("RECOVERY_ACTOR_MISMATCH");
        if (!actor.server.isSameThread() || actor.hasDisconnected() || !actor.isAlive()
                || !(actor.containerMenu instanceof MachineMenu menu)
                || menu.transactionAuthority(actor).filter(auth::equals).isEmpty()
                || !actor.level().dimension().location().equals(request.machine().owner().dimension())
                || menu.position().asLong() != request.machine().owner().controllerPos()
                || !(actor.level().getBlockEntity(menu.position()) instanceof MachineBlockEntity machine)
                || !machine.incarnation().equals(request.machine().owner().incarnation())
                || !request.machine().owner().machineType().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements", machine.kind().id())))
            throw new IllegalStateException("INVALID_MACHINE_AUTHORITY");
        var catalog = org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE.snapshot();
        if (catalog.epoch() != request.intent().catalog().epoch()
                || !catalog.contentHash().equals(request.intent().catalog().sha256()))
            throw new IllegalStateException("STALE_CATALOG");
        return machine;
    }
    static void validate(Request request, HolderLookup.Provider registries) {
        OwnerTransferValidation.validate(request.intent(), registries);
        if (request.intent().owners().size() != 1) throw new IllegalStateException("MULTI_OWNER_PROJECTION_PENDING");
        var change = request.intent().owners().getFirst(); var m = request.machine();
        if (!change.owner().equals(m.owner()) || !change.generation().equals(m.before().version().generation())
                || !TransactionEncoding.mirror(change.before(), registries).equals(m.before().canonicalMirror()))
            throw new IllegalStateException("INTENT_MACHINE_BEFORE_MISMATCH");
        // Request revision advances once; MachineProjection's replacement holds the expected live revision.
        var after = change.after();
        var normalized = new MachineTransactions.Mirror(change.before().revision(), after.slots(), after.tanks(), after.energy(), after.control());
        if (!TransactionEncoding.mirror(normalized, registries).equals(m.after().canonicalMirror())
                || !m.beforeTank().equals(m.afterTank())) throw new IllegalStateException("INTENT_MACHINE_AFTER_MISMATCH");
        // Do not claim third-party mutable component payload ABA safety from a digest.
        for (var image : List.of(change.before(), change.after())) for (var slot : image.slots())
            MutablePayloadPolicy.requireSupported(slot.stack());
    }
    private static CompoundTag body(String payload) {
        var body = new CompoundTag(); body.putString("kind", KIND); body.putString("payload", payload);
        body.putString("payload_sha256", TransactionEncoding.sha256(payload)); return body;
    }
    public static DurableMachinePermit prepare(ServerPlayer actor, Request request) {
        var machine = resolve(actor, request); validate(request, actor.registryAccess());
        var envelope = request.machine();
        if (!machine.projection.snapshot().equals(envelope.before()) || !machine.projection.marker().equals(envelope.marker()))
            throw new IllegalStateException("MACHINE_BEFORE_CONFLICT");
        String payload = request.encode(actor.registryAccess()); Request.decode(payload, actor.registryAccess());
        var ledger = SinkLedger.get(); ledger.prepareDurably(request.intent().operation(), body(payload));
        resolve(actor, request);
        return DurableMachinePermit.issue(ledger, request.intent().operation(), payload, machine, envelope, envelope.before(), envelope.after(), actor, request.intent().authority());
    }
    /** Reauthorization after reload. Caller supplies only operation ID and a freshly authenticated
     * menu session, never a replacement durable intent. COMMITTED+BEFORE may be projected forward
     * only after exact actual BEFORE+lineage validation. No journal payload is rewritten. */
    public static DurableMachinePermit resume(ServerPlayer actor, UUID operation,
            org.goldgom.chemicalelements.api.tx.MenuAuthority currentAuthority) {
        var ledger = SinkLedger.get();
        var entry = ledger.journalEntry(operation).orElseThrow(() -> new IllegalStateException("UNKNOWN_OPERATION"));
        validateStored(operation, entry, actor.registryAccess());
        if (ledger.readOnly() || entry.getBoolean("aborted")) throw new IllegalStateException("CANCELLED_OR_QUARANTINED");
        String payload = entry.getString("payload");
        var request = Request.decode(payload, actor.registryAccess());
        var machine = resolve(actor, request, currentAuthority);
        if (classify(machine, request.machine(), operation) != Recovery.BEFORE)
            throw new IllegalStateException("RECOVERY_NOT_UNTOUCHED_BEFORE");
        var current = machine.projection.snapshot();
        var original = machine.projection.image(request.machine().after());
        var mirror = new MachineTransactions.Mirror(current.version().mutationRevision(), original.slots(), original.tanks(), original.energy(), original.control());
        var replacement = MachineProjection.replacement(current,
                TransactionEncoding.mirror(mirror, actor.registryAccess()), request.machine().after().tankNbt());
        return DurableMachinePermit.issue(ledger, operation, payload, machine, request.machine(), current, replacement, actor, currentAuthority);
    }
    /** Cancel only untouched PREPARE; never refunds or overwrites partially projected content. */
    public static void cancel(ServerPlayer actor, Request request) {
        var machine = resolve(actor, request); var ledger = SinkLedger.get();
        String payload = request.encode(actor.registryAccess());
        if (!ledger.matches(request.intent().operation(), body(payload))) throw new IllegalStateException("UUID_PAYLOAD_CONFLICT");
        if (ledger.committed(request.intent().operation())) throw new IllegalStateException("ALREADY_COMMITTED");
        if (classify(machine, request.machine(), request.intent().operation()) != Recovery.BEFORE)
            throw new IllegalStateException("CANNOT_CANCEL_PROJECTED_OR_CONFLICTED_OWNER");
        ledger.abortDurably(request.intent().operation());
    }
    /** Compare persisted lineage plus actual content, separately from reload-sensitive live versions.
     * BEFORE is observation only: a restart must obtain fresh authorization before permit reissue. */
    public static Recovery classify(MachineBlockEntity machine, MachineOwnerEnvelope expected, UUID operation) {
        var current = machine.projection.snapshot(); var marker = machine.projection.marker();
        if (!current.incarnation().equals(expected.owner().incarnation())
                || !marker.generation().equals(expected.marker().generation())) return Recovery.CONFLICT;
        if (marker.equals(expected.marker()) && sameContent(machine, current, expected.before())) {
            if (current.version().generation().equals(expected.before().version().generation())
                    && !current.equals(expected.before())) return Recovery.CONFLICT;
            return Recovery.BEFORE;
        }
        String requestDigest = TransactionEncoding.sha256(operation + "\n" + expected.before().digest() + "\n" + expected.after().digest());
        if (expected.marker().revision() != Long.MAX_VALUE && marker.revision() == expected.marker().revision() + 1
                && marker.lastOperation().filter(operation::equals).isPresent() && marker.requestDigest().equals(requestDigest)
                && marker.resultDigest().equals(contentDigest(machine, expected.after()))
                && sameContent(machine, current, expected.after())) return Recovery.AFTER;
        return Recovery.CONFLICT;
    }
    /** Restart entry: journal is the sole source of intent. Does not apply BEFORE automatically;
     * obtain fresh actor/catalog authority through resume and explicitly apply its rebound permit. */
    public static Recovery recover(MachineBlockEntity machine, UUID operation) {
        var ledger = SinkLedger.get();
        var entry = ledger.journalEntry(operation).orElseThrow(() -> new IllegalStateException("UNKNOWN_OPERATION"));
        validateStored(operation, entry, machine.getLevel().registryAccess());
        var request = Request.decode(entry.getString("payload"), machine.getLevel().registryAccess());
        var owner = request.machine().owner();
        if (machine.getLevel().getBlockEntity(machine.getBlockPos()) != machine
                || !owner.dimension().equals(machine.getLevel().dimension().location())
                || owner.controllerPos() != machine.getBlockPos().asLong()
                || !owner.machineType().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements", machine.kind().id())))
            return Recovery.CONFLICT;
        Recovery actual = classify(machine, request.machine(), operation);
        if (entry.getBoolean("aborted")) return actual == Recovery.BEFORE ? Recovery.CANCELLED : Recovery.CONFLICT;
        if (actual == Recovery.AFTER) {
            if (!machine.projection.matchesJournalProjection(operation, entry.getString("payload_sha256")))
                return Recovery.CONFLICT;
            ledger.commitDurably(operation);
        }
        // COMMITTED + BEFORE requires explicit forward recovery, never a blind overwrite.
        if (entry.getBoolean("committed") && actual == Recovery.BEFORE) return Recovery.CONFLICT;
        return actual;
    }
    private static String contentDigest(MachineBlockEntity machine, MachineProjection.Snapshot s) {
        var m = machine.projection.image(s);
        return TransactionEncoding.sha256(TransactionEncoding.mirror(new MachineTransactions.Mirror(0, m.slots(), m.tanks(), m.energy(), m.control()), machine.getLevel().registryAccess()) + "\n" + s.tankNbt());
    }
    static boolean sameContent(MachineBlockEntity machine, MachineProjection.Snapshot a, MachineProjection.Snapshot b) {
        // Full canonical values, not digest equality, are the inventory conflict check.
        var x = machine.projection.image(a); var y = machine.projection.image(b);
        var registries = machine.getLevel().registryAccess();
        return TransactionEncoding.mirror(new MachineTransactions.Mirror(0,x.slots(),x.tanks(),x.energy(),x.control()), registries)
                .equals(TransactionEncoding.mirror(new MachineTransactions.Mirror(0,y.slots(),y.tanks(),y.energy(),y.control()), registries))
                && AggregateTankImage.fromNbt(a.tankNbt()).equals(AggregateTankImage.fromNbt(b.tankNbt()));
    }
    /** Only acknowledges actual AFTER + marker, including retries after a failed journal flush. */
    public static void commit(ServerPlayer actor, Request request) {
        var machine = resolve(actor, request); validate(request, actor.registryAccess()); var ledger = SinkLedger.get();
        if (!ledger.matches(request.intent().operation(), body(request.encode(actor.registryAccess()))) || ledger.aborted(request.intent().operation()))
            throw new IllegalStateException("UUID_PAYLOAD_CONFLICT");
        if (classify(machine, request.machine(), request.intent().operation()) != Recovery.AFTER
                || !machine.projection.matchesJournalProjection(request.intent().operation(),
                        TransactionEncoding.sha256(request.encode(actor.registryAccess()))))
            throw new IllegalStateException("MACHINE_PROJECTION_NOT_CONFIRMED");
        ledger.commitDurably(request.intent().operation());
    }
    public static void validateStored(UUID operation, CompoundTag entry, HolderLookup.Provider registries) {
        var keys = new HashSet<>(Set.of("kind", "payload", "payload_sha256", "committed"));
        if (entry.contains("aborted")) keys.add("aborted");
        if (!entry.getAllKeys().equals(keys) || !KIND.equals(entry.getString("kind"))
                || !entry.contains("payload", Tag.TAG_STRING) || !entry.contains("payload_sha256", Tag.TAG_STRING)
                || !entry.contains("committed", Tag.TAG_BYTE) || (entry.getByte("committed") != 0 && entry.getByte("committed") != 1)
                || (entry.contains("aborted") && (!entry.contains("aborted", Tag.TAG_BYTE) || entry.getByte("aborted") != 1))
                || (entry.getBoolean("committed") && entry.getBoolean("aborted"))) throw new IllegalArgumentException("machine journal shape");
        var request = Request.decode(entry.getString("payload"), registries);
        if (!operation.equals(request.intent().operation()) || !TransactionEncoding.sha256(entry.getString("payload")).equals(entry.getString("payload_sha256")))
            throw new IllegalArgumentException("machine journal UUID/hash");
        validate(request, registries);
    }
}

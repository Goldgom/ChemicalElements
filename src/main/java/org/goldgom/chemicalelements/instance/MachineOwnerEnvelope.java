package org.goldgom.chemicalelements.instance;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.tx.OwnerKey;
import org.goldgom.chemicalelements.machine.*;

/** Schema3 adjunct: independent aggregate tank and durable projection lineage. This is data,
 * never projection authority. Live generation may change on reload without changing lineage. */
public record MachineOwnerEnvelope(OwnerKey.MachineInventory owner,
        MachineProjection.Snapshot before, MachineProjection.Snapshot after,
        MachineProjection.Marker marker, AggregateTankImage beforeTank, AggregateTankImage afterTank) {
    public MachineOwnerEnvelope {
        Objects.requireNonNull(owner); Objects.requireNonNull(before); Objects.requireNonNull(after);
        Objects.requireNonNull(marker); Objects.requireNonNull(beforeTank); Objects.requireNonNull(afterTank);
        MachineCanonicalNbt.decode(before.tankNbt()); MachineCanonicalNbt.decode(after.tankNbt());
        if (before.version().mutationRevision() < 0 || after.version().mutationRevision() < 0)
            throw new IllegalArgumentException("negative machine version");
        if (!owner.equals(before.owner()) || !owner.equals(after.owner()) || !marker.equals(before.projection()) || !marker.equals(after.projection())
                || !owner.incarnation().equals(before.incarnation()) || !before.incarnation().equals(after.incarnation())
                || !before.version().equals(after.version()) || marker.revision() < 0
                || !beforeTank.equals(AggregateTankImage.fromNbt(before.tankNbt()))
                || !afterTank.equals(AggregateTankImage.fromNbt(after.tankNbt())))
            throw new IllegalArgumentException("machine envelope identity/tank mismatch");
    }
    public static MachineOwnerEnvelope capture(MachineBlockEntity machine, MachineProjection.Snapshot after) {
        var before = machine.projection.snapshot(); var marker = machine.projection.marker();
        var tank = AggregateTankImage.capture(machine.tank);
        var key = new OwnerKey.MachineInventory(machine.getLevel().dimension().location(),
                machine.getBlockPos().asLong(), machine.incarnation(),
                ResourceLocation.fromNamespaceAndPath("chemicalelements", machine.kind().id()));
        if (!before.equals(machine.projection.snapshot()) || !marker.equals(machine.projection.marker()))
            throw new IllegalStateException("MACHINE_CHANGED_DURING_CAPTURE");
        return new MachineOwnerEnvelope(key, before, after, marker, tank, AggregateTankImage.fromNbt(after.tankNbt()));
    }
    public String encode() {
        var root = new JsonObject(); root.addProperty("schema", 3);
        root.addProperty("dimension", owner.dimension().toString()); root.addProperty("position", owner.controllerPos());
        root.addProperty("incarnation", owner.incarnation().toString()); root.addProperty("type", owner.machineType().toString());
        root.add("before", snapshot(before)); root.add("after", snapshot(after));
        root.addProperty("before_tank", beforeTank.encode()); root.addProperty("before_tank_sha256", beforeTank.digest());
        root.addProperty("after_tank", afterTank.encode()); root.addProperty("after_tank_sha256", afterTank.digest());
        var m = new JsonObject(); m.addProperty("lineage", marker.generation().toString()); m.addProperty("revision", marker.revision());
        m.addProperty("operation", marker.lastOperation().map(UUID::toString).orElse(""));
        m.addProperty("request", marker.requestDigest()); m.addProperty("result", marker.resultDigest()); root.add("marker", m);
        return TransactionEncoding.canonical(root);
    }
    private static JsonObject snapshot(MachineProjection.Snapshot value) {
        var s = new JsonObject(); s.addProperty("incarnation", value.incarnation().toString());
        s.addProperty("live_generation", value.version().generation().toString()); s.addProperty("live_revision", value.version().mutationRevision());
        s.addProperty("mirror", value.canonicalMirror()); s.addProperty("tank_nbt", value.tankNbt()); s.addProperty("tank_mutation_revision", value.tankMutationRevision()); s.addProperty("digest", value.digest()); return s;
    }
    private static MachineProjection.Snapshot snapshot(JsonObject s, HolderLookup.Provider registries, OwnerKey.MachineInventory owner, MachineProjection.Marker marker) {
        String mirror = s.get("mirror").getAsString(); TransactionEncoding.decodeMirror(mirror, registries);
        if (!owner.incarnation().equals(UUID.fromString(s.get("incarnation").getAsString()))) throw new IllegalArgumentException("snapshot incarnation");
        return new MachineProjection.Snapshot(owner,
                new MachineProjection.Version(UUID.fromString(s.get("live_generation").getAsString()), s.get("live_revision").getAsBigDecimal().longValueExact()),
                mirror, TransactionEncoding.sha256(mirror), s.get("tank_nbt").getAsString(), s.get("tank_mutation_revision").getAsBigDecimal().longValueExact(), marker, s.get("digest").getAsString());
    }
    public static MachineOwnerEnvelope decode(String encoded, HolderLookup.Provider registries) {
        if (encoded.length() > 16 * 1024 * 1024) throw new IllegalArgumentException("machine envelope budget");
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        if (root.get("schema").getAsInt() != 3) throw new IllegalArgumentException("machine envelope schema");
        var owner = new OwnerKey.MachineInventory(ResourceLocation.parse(root.get("dimension").getAsString()),
                root.get("position").getAsBigDecimal().longValueExact(), UUID.fromString(root.get("incarnation").getAsString()),
                ResourceLocation.parse(root.get("type").getAsString()));
        var m = root.getAsJsonObject("marker"); String op = m.get("operation").getAsString();
        var marker = new MachineProjection.Marker(UUID.fromString(m.get("lineage").getAsString()),
                m.get("revision").getAsBigDecimal().longValueExact(), op.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(op)),
                m.get("request").getAsString(), m.get("result").getAsString());
        var result = new MachineOwnerEnvelope(owner, snapshot(root.getAsJsonObject("before"), registries, owner, marker),
                snapshot(root.getAsJsonObject("after"), registries, owner, marker), marker,
                AggregateTankImage.decode(root.get("before_tank").getAsString()), AggregateTankImage.decode(root.get("after_tank").getAsString()));
        if (!encoded.equals(result.encode())) throw new IllegalArgumentException("noncanonical machine envelope");
        return result;
    }
}

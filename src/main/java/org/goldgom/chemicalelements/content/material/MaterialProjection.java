package org.goldgom.chemicalelements.content.material;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.world.level.block.state.BlockState;
import org.goldgom.chemicalelements.api.instance.StackLedger;
import org.goldgom.chemicalelements.data.LedgerJson;
import org.goldgom.chemicalelements.instance.TransactionEncoding;
import org.goldgom.chemicalelements.world.heat.FiniteHeatEvidence;

/** Immutable full business image. Transaction metadata is deliberately not hashed into itself.
 * The ledger is the ONLY material balance. finite_heat is detached, checked evidence of that ledger.
 */
public record MaterialProjection(BlockState state, long revision, StackLedger ledger,
                                 Optional<FiniteHeatEvidence> finiteHeat, Optional<String> unitStack) {
    public MaterialProjection(BlockState state, long revision, StackLedger ledger, Optional<FiniteHeatEvidence> heat) {
        this(state, revision, ledger, heat, Optional.empty());
    }
    public MaterialProjection {
        Objects.requireNonNull(state); Objects.requireNonNull(ledger); Objects.requireNonNull(finiteHeat);
        Objects.requireNonNull(unitStack);
        unitStack.ifPresent(s -> {
            if (s.length() > 16 * 1024 * 1024 || !TransactionEncoding.canonical(JsonParser.parseString(s)).equals(s))
                throw new IllegalArgumentException("noncanonical original item evidence");
            if (ledger.instances().size() != 1) throw new IllegalArgumentException("unit evidence requires one original instance");
        });
        if (revision < 0) throw new IllegalArgumentException("negative material revision");
        finiteHeat.ifPresent(e -> {
            if (!e.instances().equals(ledger.instances()))
                throw new IllegalArgumentException("heat evidence does not describe original FIFO");
        });
    }
    public String canonical() {
        var root = new JsonObject(); root.addProperty("schema", 1); root.addProperty("revision", revision);
        root.add("block_state", BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow());
        root.add("ledger", LedgerJson.encode(ledger));
        finiteHeat.ifPresent(e -> root.add("finite_heat", JsonParser.parseString(e.canonical())));
        unitStack.ifPresent(s -> root.add("unit_stack", JsonParser.parseString(s)));
        return TransactionEncoding.canonical(root);
    }
    /** Registry-aware validation is mandatory before accepting loaded/projected evidence.
     * This is the original one-item shell, not an independently spendable material balance. */
    public void validateUnit(net.minecraft.core.HolderLookup.Provider registries) {
        unitStack.ifPresent(s -> {
            var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, registries);
            var unit = net.minecraft.world.item.ItemStack.CODEC.parse(ops, JsonParser.parseString(s)).getOrThrow();
            if (unit.isEmpty() || unit.getCount() != 1 || !TransactionEncoding.canonical(
                    net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, unit).getOrThrow()).equals(s)
                    || !org.goldgom.chemicalelements.instance.StackLedgers.get(unit).filter(ledger::equals).isPresent())
                throw new IllegalArgumentException("unit shell/FIFO mismatch or lossy components");
        });
    }
    public static MaterialProjection decode(String canonical) {
        if (canonical.length() > 16 * 1024 * 1024) throw new IllegalArgumentException("material root budget");
        var root = JsonParser.parseString(canonical).getAsJsonObject();
        if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("material root schema");
        var result = new MaterialProjection(BlockState.CODEC.parse(JsonOps.INSTANCE, root.get("block_state")).getOrThrow(),
                root.get("revision").getAsLong(), LedgerJson.decode(root.getAsJsonObject("ledger")),
                root.has("finite_heat") ? Optional.of(FiniteHeatEvidence.fromRoot(canonical)) : Optional.empty(),
                root.has("unit_stack") ? Optional.of(TransactionEncoding.canonical(root.get("unit_stack"))) : Optional.empty());
        // Reject unknown fields, lossy integer parsing and noncanonical nested ledgers instead of discarding them.
        if (!result.canonical().equals(canonical)) throw new IllegalArgumentException("noncanonical material root");
        return result;
    }
}

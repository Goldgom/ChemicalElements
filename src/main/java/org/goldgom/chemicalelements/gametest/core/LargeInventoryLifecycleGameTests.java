package org.goldgom.chemicalelements.gametest.core;

import java.math.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.nuclear.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.persistence.*;

/** Container save/load and uncached compressed payload recovery, not a server restart test. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class LargeInventoryLifecycleGameTests {
    @GameTest(template="core_empty", timeoutTicks=100)
    public static void multiSlotLargeInventoryRecovery(GameTestHelper helper) {
        var slots = NonNullList.withSize(27, ItemStack.EMPTY);
        var expected = new TreeMap<Integer, StackLedger>();
        for (int slot : new int[]{0, 13, 26}) {
            var records = new ArrayList<InstanceRecord>();
            for (int n = 0; n < 16; n++) {
                var amount = new MolAmount(BigInteger.ONE.shiftLeft(8192).add(BigInteger.valueOf(slot * 16L + n + 1)));
                var matter = new Composition(Map.of(ResourceLocation.parse("chemicalelements:water"), amount));
                var origin = new Provenance("test", "large container fixture", -123, 7, Map.of("slot", "" + slot));
                var heat = new ThermalState(new BigDecimal("99999999999999999999.125"), new BigDecimal("302.5"), Map.of(Phase.LIQUID, BigDecimal.ONE), n);
                var pore = new SolutionState(amount, matter, matter, new BigDecimal("302.5"), new BigDecimal("19.25"), origin, n);
                records.add(new InstanceRecord(new UUID(slot + 1, n + 1), matter, origin, n, heat,
                        new NuclideInventory(Map.of(new NuclideId(1,2,0), amount)), pore, matter));
            }
            var ledger = new StackLedger(StackLedger.CURRENT_SCHEMA, 32 + slot, records);
            expected.put(slot, ledger);
            var stack = new ItemStack(Items.IRON_INGOT, records.size());
            // Discard of()'s decoded cache so compressed storage must actually decode.
            stack.set(ChemistryComponents.LEDGER.get(), new LedgerPayload(LedgerPayload.of(ledger).encoded()));
            slots.set(slot, stack);
        }
        var corrupt = new LedgerPayload("z1:not-base64!");
        var evidence = new ItemStack(Items.PAPER);
        evidence.set(ChemistryComponents.LEDGER.get(), corrupt);
        slots.set(7, evidence);
        var saved = ContainerHelper.saveAllItems(new CompoundTag(), slots, helper.getLevel().registryAccess());
        var restored = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(saved.copy(), restored, helper.getLevel().registryAccess());
        for (var entry : expected.entrySet()) {
            var stack = restored.get(entry.getKey());
            helper.assertTrue(stack.getCount() == 16, "Stack count survives container storage");
            var payload = stack.get(ChemistryComponents.LEDGER.get());
            helper.assertTrue(payload != null && payload.ledger().orElseThrow().equals(entry.getValue()),
                    "Container slot retains ordered UUIDs and every 8193-bit field");
            var buffer = Unpooled.buffer();
            try {
                LedgerPayload.STREAM_CODEC.encode(buffer, payload);
                var decoded = LedgerPayload.STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.ledger().orElseThrow().equals(entry.getValue()) && !buffer.isReadable(),
                        "Saved multi-instance payload survives actual stream codec without cache");
            } finally { buffer.release(); }
        }
        var isolated = restored.get(7).get(ChemistryComponents.LEDGER.get());
        helper.assertTrue(isolated != null && isolated.quarantined() && isolated.encoded().equals(corrupt.encoded()),
                "Corrupt compressed slot stays byte-identical and does not erase valid neighbors");
        helper.assertTrue(restored.get(1).isEmpty() && restored.get(25).isEmpty(), "Empty slots stay empty; no phantom inventory");
        var second = ContainerHelper.saveAllItems(new CompoundTag(), restored, helper.getLevel().registryAccess());
        var again = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(second, again, helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.listMatches(restored, again), "Repeated container save/load is idempotent including quarantined payload");
        helper.succeed();
    }
}

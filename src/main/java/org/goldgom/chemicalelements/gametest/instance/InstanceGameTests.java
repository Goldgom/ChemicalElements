package org.goldgom.chemicalelements.gametest.instance;

import com.mojang.serialization.JsonOps;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.instance.Provenance;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.instance.InstanceBootstrap;
import org.goldgom.chemicalelements.instance.LedgerOps;
import org.goldgom.chemicalelements.instance.StackLedgers;
import org.goldgom.chemicalelements.persistence.ChunkChemistry;
import org.goldgom.chemicalelements.persistence.WorldChemistryStore;

/** Package B in-game tests: real vanilla code paths with mixins applied. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class InstanceGameTests {
    private InstanceGameTests() {}

    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void waterPayloadBinding(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var journal = org.goldgom.chemicalelements.persistence.SinkLedger.get();
        var zero = java.math.BigInteger.ZERO;
        var cell = new ChunkChemistry.WaterCell(zero, new TreeMap<>(), zero, zero, "test", 0);
        var operation = UUID.randomUUID();
        var changes = List.of(new WorldChemistryStore.WaterChange(pos, -1, cell));
        h.assertTrue(WorldChemistryStore.commitWater(level, operation, changes), "first empty commit");
        h.assertTrue(WorldChemistryStore.water(level, pos).orElseThrow().revision() == 0, "empty retains revision");
        var saved = WorldChemistryStore.data(level, pos).orElseThrow().write();
        h.assertTrue(ChunkChemistry.read(saved).water(pos.asLong()).orElseThrow().revision() == 0, "empty NBT retains revision");
        h.assertTrue(WorldChemistryStore.commitWater(level, operation, changes), "same committed payload returns success");
        var other = new ChunkChemistry.WaterCell(java.math.BigInteger.ONE, new TreeMap<>(), zero, zero, "test", 0);
        h.assertFalse(WorldChemistryStore.commitWater(level, operation, List.of(new WorldChemistryStore.WaterChange(pos, -1, other))), "different committed payload rejected");
        var pending = UUID.randomUUID();
        var payload = journal.journalEntry(operation).orElseThrow();
        payload.remove("committed");
        journal.prepare(pending, payload);
        var before = journal.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        h.assertFalse(WorldChemistryStore.commitWater(level, pending, List.of(new WorldChemistryStore.WaterChange(pos, 0, other))), "different pending payload rejected");
        h.assertTrue(before.equals(journal.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())), "conflict does not mutate journal");
        var loaded = org.goldgom.chemicalelements.persistence.SinkLedger.load(before, level.registryAccess());
        h.assertTrue(loaded.matches(pending, payload), "payload binding survives NBT reload");
        var changed = payload.copy(); changed.putString("dim", "invalid:dimension");
        boolean rejected = false;
        try { loaded.prepare(pending, changed); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "reloaded pending rejects changed payload");
        loaded.abort(pending);
        h.assertTrue(loaded.aborted(pending) && loaded.matches(pending, payload), "abort retains payload tombstone");
        journal.abort(pending);
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void transactionEncoding(GameTestHelper h) {
        var regs = h.getLevel().registryAccess();
        var record = rec(301, "2");
        var item = stack(Items.IRON_INGOT, record);
        var slot = new org.goldgom.chemicalelements.machine.MachineTransactions.SlotImage(0, item, List.of(record));
        var energy = new org.goldgom.chemicalelements.machine.MachineTransactions.EnergyImage(java.math.BigInteger.TEN, java.math.BigInteger.TEN, 0);
        var control = new org.goldgom.chemicalelements.machine.MachineTransactions.ControlImage("idle", 0, 0, Map.of("z", "2", "a", "1"));
        var mirror = new org.goldgom.chemicalelements.machine.MachineTransactions.Mirror(0, List.of(slot), List.of(), energy, control);
        String original = org.goldgom.chemicalelements.instance.TransactionEncoding.mirror(mirror, regs);
        var decoded = org.goldgom.chemicalelements.instance.TransactionEncoding.decodeMirror(original, regs);
        h.assertTrue(original.equals(org.goldgom.chemicalelements.instance.TransactionEncoding.mirror(decoded, regs)), "full mirror recovery roundtrip");
        h.assertTrue(decoded.slots().getFirst().instances().equals(List.of(record)), "decoded full instance state preserved");
        var malformed = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
        malformed.addProperty("revision", new java.math.BigDecimal("0.5"));
        boolean malformedRejected = false;
        try { org.goldgom.chemicalelements.instance.TransactionEncoding.decodeMirror(org.goldgom.chemicalelements.instance.TransactionEncoding.canonical(malformed), regs); } catch (RuntimeException expected) { malformedRejected = true; }
        h.assertTrue(malformedRejected, "fractional revision rejected without truncation");
        malformed = com.google.gson.JsonParser.parseString(original).getAsJsonObject();malformed.addProperty("unrecognized", true);
        malformedRejected = false;
        try { org.goldgom.chemicalelements.instance.TransactionEncoding.decodeMirror(org.goldgom.chemicalelements.instance.TransactionEncoding.canonical(malformed), regs); } catch (RuntimeException expected) { malformedRejected = true; }
        h.assertTrue(malformedRejected, "unknown fields cannot be silently lost");
        item.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        h.assertTrue(original.equals(org.goldgom.chemicalelements.instance.TransactionEncoding.mirror(mirror, regs)), "request copied caller stack");
        var changedSlot = new org.goldgom.chemicalelements.machine.MachineTransactions.SlotImage(0, item, List.of(record));
        var changed = new org.goldgom.chemicalelements.machine.MachineTransactions.Mirror(0, List.of(changedSlot), List.of(), energy, control);
        h.assertFalse(original.equals(org.goldgom.chemicalelements.instance.TransactionEncoding.mirror(changed, regs)), "nonchemistry component bound");
        h.assertTrue(original.contains("thermal") && original.contains("nuclides") && original.contains("pore_solution") && original.contains("adsorbed"), "complete instance fields encoded");
        var p = new org.goldgom.chemicalelements.api.reaction.ReactionProposal(id("test"), record.remaining(), record.remaining(), Composition.EMPTY, MolAmount.parseDecimal("1"), BigDecimal.ZERO, BigDecimal.ONE, 0, "test", List.of());
        var dest = new org.goldgom.chemicalelements.machine.MachineTransactions.Destination(h.getLevel().dimension().location(), h.absolutePos(BlockPos.ZERO).asLong(), UUID.randomUUID(), id("test"));
        var op = UUID.randomUUID();
        var key = new org.goldgom.chemicalelements.machine.MachineTransactions.OperationKey(op, dest, "0".repeat(64));
        var catalog = new org.goldgom.chemicalelements.machine.MachineTransactions.CatalogVersion(0, "0".repeat(64));
        var playerId = UUID.randomUUID();var sessionId = UUID.randomUUID();
        var playerDestination = new org.goldgom.chemicalelements.api.tx.PlayerMenuDestination(playerId, sessionId, id("test_menu"), 0, 0);
        var playerRequest = new org.goldgom.chemicalelements.instance.TransactionRequest(op, playerDestination, catalog, p, mirror, mirror);
        var reopened = new org.goldgom.chemicalelements.instance.TransactionRequest(op, new org.goldgom.chemicalelements.api.tx.PlayerMenuDestination(playerId, UUID.randomUUID(), id("test_menu"), 0, 0), catalog, p, mirror, mirror);
        h.assertFalse(playerRequest.payloadSha256(regs).equals(reopened.payloadSha256(regs)), "player session bound to full payload");
        h.assertTrue(playerRequest.checkedEncoding(playerRequest.payloadSha256(regs), regs).contains("player_menu"), "player request uses explicit destination kind");
        boolean ownerRejected = false;
        try { new org.goldgom.chemicalelements.instance.TransactionRequest(op, new org.goldgom.chemicalelements.api.tx.PlayerMenuDestination(playerId, sessionId, id("test_menu"), 0, 1), catalog, p, mirror, mirror); } catch (IllegalArgumentException expected) { ownerRejected = true; }
        h.assertTrue(ownerRejected, "owner revision must match before image");
        var request = new org.goldgom.chemicalelements.machine.MachineTransactions.Request(key, catalog, p, org.goldgom.chemicalelements.instance.TransactionEncoding.proposal(p), mirror, mirror);
        var text = org.goldgom.chemicalelements.instance.TransactionEncoding.request(request, regs);
        var bound = new org.goldgom.chemicalelements.machine.MachineTransactions.OperationKey(op, dest, org.goldgom.chemicalelements.instance.TransactionEncoding.sha256(text));
        var valid = new org.goldgom.chemicalelements.machine.MachineTransactions.Request(bound, catalog, p, request.canonicalProposal(), mirror, mirror);
        h.assertTrue(text.equals(org.goldgom.chemicalelements.instance.TransactionEncoding.checkedRequest(valid, regs)), "hash excludes only own hash field");
        boolean rejected = false;
        try { org.goldgom.chemicalelements.instance.TransactionEncoding.checkedRequest(request, regs); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "incorrect request hash refused");
        h.succeed();
    }

    static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath("chemicalelements", p); }

    static InstanceRecord rec(long n, String fe) {
        return new InstanceRecord(new UUID(0xB, n), new Composition(Map.of(id("iron"), MolAmount.parseDecimal(fe))), Provenance.of("gametest", "b"), 0,
                new ThermalState(BigDecimal.ZERO, new BigDecimal(290 + n), Map.of(), 0),
                org.goldgom.chemicalelements.api.nuclear.NuclideInventory.EMPTY, org.goldgom.chemicalelements.api.fluid.SolutionState.empty(), Composition.EMPTY);
    }

    static ItemStack stack(net.minecraft.world.item.Item item, InstanceRecord... recs) {
        var s = new ItemStack(item, recs.length);
        StackLedgers.set(s, LedgerOps.of(0, List.of(recs)));
        return s;
    }

    static List<UUID> ids(ItemStack s) { return StackLedgers.get(s).map(l -> l.instances().stream().map(InstanceRecord::id).toList()).orElse(List.of()); }

    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void mixinsActive(GameTestHelper h) {
        h.assertTrue(InstanceBootstrap.probe(), "instance mixin self-probe failed");
        var s = stack(Items.IRON_INGOT, rec(1, "1.0"), rec(2, "1.1"), rec(3, "1.2"));
        var c = s.copyWithCount(2);
        h.assertTrue(ids(c).equals(List.of(new UUID(0xB, 1), new UUID(0xB, 2))), "copyWithCount keeps head slice");
        h.assertTrue(ids(s).size() == 3, "copy never moves ownership");
        h.succeed();
    }

    /** Different compositions stack normally via menu clicks; split takes the head; every instance stays distinct. */
    @GameTest(template = "core_empty", timeoutTicks = 40)
    public static void menuMergeAndSplit(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var inv = player.getInventory();
        inv.setItem(9, stack(Items.IRON_INGOT, rec(10, "1.0")));
        inv.setItem(10, stack(Items.IRON_INGOT, rec(11, "2.0")));
        var menu = player.inventoryMenu;
        menu.clicked(10, 0, ClickType.PICKUP, player);   // carry B
        menu.clicked(9, 0, ClickType.PICKUP, player);    // drop onto A -> merge
        var merged = inv.getItem(9);
        h.assertTrue(merged.getCount() == 2, "different compositions must stack normally, got " + merged.getCount());
        h.assertTrue(ids(merged).equals(List.of(new UUID(0xB, 10), new UUID(0xB, 11))), "merge appends source after target: " + ids(merged));
        h.assertTrue(menu.getCarried().isEmpty(), "carried emptied");
        menu.clicked(9, 1, ClickType.PICKUP, player);    // right click: take half (1)
        h.assertTrue(ids(menu.getCarried()).equals(List.of(new UUID(0xB, 10))), "split carries head: " + ids(menu.getCarried()));
        h.assertTrue(ids(inv.getItem(9)).equals(List.of(new UUID(0xB, 11))), "slot keeps tail");
        menu.clicked(36, 0, ClickType.PICKUP, player);   // place carried into hotbar 0
        menu.clicked(9, 0, ClickType.QUICK_MOVE, player);// shift-click tail into hotbar -> merges
        var hot = inv.getItem(0);
        h.assertTrue(hot.getCount() == 2 && new HashSet<>(ids(hot)).equals(Set.of(new UUID(0xB, 10), new UUID(0xB, 11))), "quick move preserves instances: " + ids(hot));
        h.succeed();
    }

    /** Non-chemistry components still block stacking (name, enchantments...). */
    @GameTest(template = "core_empty", timeoutTicks = 20)
    public static void otherComponentsStillMatter(GameTestHelper h) {
        var a = stack(Items.IRON_INGOT, rec(20, "1.0"));
        var b = stack(Items.IRON_INGOT, rec(21, "1.5"));
        h.assertTrue(StackLedgers.canMerge(a, b), "chemistry-only difference merges");
        h.assertFalse(ItemStack.isSameItemSameComponents(a, b), "vanilla global equality keeps chemistry component");
        b.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        h.assertFalse(StackLedgers.canMerge(a, b), "custom name still prevents merge");
        h.succeed();
    }

    /** Hopper moves instances one by one without averaging or duplication. */
    @GameTest(template = "core_empty", timeoutTicks = 200)
    public static void hopperTransfer(GameTestHelper h) {
        var chestPos = new BlockPos(1, 1, 1);
        var hopperPos = chestPos.above();
        h.setBlock(chestPos, Blocks.CHEST);
        h.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = h.getBlockEntity(hopperPos);
        hopper.setItem(0, stack(Items.IRON_INGOT, rec(30, "1.0"), rec(31, "1.3"), rec(32, "1.7")));
        h.succeedWhen(() -> {
            ChestBlockEntity chest = h.getBlockEntity(chestPos);
            var s = chest.getItem(0);
            h.assertTrue(s.getCount() == 3, "waiting for transfer");
            h.assertTrue(ids(s).equals(List.of(new UUID(0xB, 30), new UUID(0xB, 31), new UUID(0xB, 32))), "FIFO instances: " + ids(s));
            h.assertTrue(hopper.getItem(0).isEmpty(), "hopper drained");
        });
    }

    /** Item entities with different compositions merge on the ground keeping all instances. */
    @GameTest(template = "instance_merge_room", timeoutTicks = 200)
    public static void itemEntityMerge(GameTestHelper h) {
        // core_empty is 1x1x1 with no explicit air blocks: (1,2,1) was outside its cleared area.
        // A dedicated 7x5x7 template reserves spacing and clears every interior cell.
        var p = h.absolutePos(new BlockPos(3, 2, 3));
        var level = h.getLevel();
        var e1 = new ItemEntity(level, p.getX() + .5, p.getY(), p.getZ() + .5, stack(Items.IRON_INGOT, rec(40, "1.0")), 0, 0, 0);
        var e2 = new ItemEntity(level, p.getX() + .5, p.getY(), p.getZ() + .5, stack(Items.IRON_INGOT, rec(41, "3.0"), rec(42, "3.1")), 0, 0, 0);
        // Keep both tracked entities at the test site; nearby mock players must not pick them up.
        e1.setNoGravity(true); e2.setNoGravity(true);
        e1.setPickUpDelay(1000); e2.setPickUpDelay(1000);
        h.assertTrue(level.noCollision(e1, e1.getBoundingBox()) && level.noCollision(e2, e2.getBoundingBox()), "merge spawn must be collision-free at " + p);
        h.assertTrue(level.getFluidState(p).isEmpty(), "merge spawn must be dry at " + p);
        h.assertTrue(level.addFreshEntity(e1) && level.addFreshEntity(e2), "both entities spawned");
        h.succeedWhen(() -> {
            var live = java.util.stream.Stream.of(e1, e2).filter(e -> e.isAlive()).toList();
            var allIds = live.stream().flatMap(e -> ids(e.getItem()).stream()).toList();
            String detail = " e1=" + e1.isAlive() + "@" + e1.position() + ":" + ids(e1.getItem())
                    + " e2=" + e2.isAlive() + "@" + e2.position() + ":" + ids(e2.getItem())
                    + " v1=" + e1.getDeltaMovement() + " v2=" + e2.getDeltaMovement()
                    + " block1=" + level.getBlockState(e1.blockPosition()) + " block2=" + level.getBlockState(e2.blockPosition())
                    + " fluid1=" + level.getFluidState(e1.blockPosition()) + " fluid2=" + level.getFluidState(e2.blockPosition());
            h.assertTrue(allIds.size() == 3 && new HashSet<>(allIds).equals(Set.of(new UUID(0xB, 40), new UUID(0xB, 41), new UUID(0xB, 42))), "tracked entity conservation" + detail);
            h.assertTrue(live.size() == 1, "waiting for actual merge" + detail);
            h.assertTrue(live.getFirst().getItem().getCount() == 3, "merged count exactly three" + detail);
        });
    }

    /** A placed block keeps its instance; breaking drops exactly that instance (no template refill). */
    @GameTest(template = "core_empty", timeoutTicks = 100)
    public static void placeBreakRoundTrip(GameTestHelper h) {
        var rel = new BlockPos(1, 1, 1);
        var abs = h.absolutePos(rel);
        h.setBlock(rel, Blocks.OAK_PLANKS);
        var r = rec(50, "0.0");
        h.assertTrue(WorldChemistryStore.putBlock(h.getLevel(), abs, new ChunkChemistry.BlockInstance("minecraft:oak_planks", "minecraft:oak_planks", r, 0)), "store block");
        h.assertTrue(h.getLevel().destroyBlock(abs, true), "real block destruction succeeded");
        h.succeedWhen(() -> {
            var list = h.getLevel().getEntities(EntityType.ITEM, new AABB(abs).inflate(2), e -> e.isAlive());
            h.assertTrue(!list.isEmpty(), "waiting for drop");
            h.assertTrue(ids(list.getFirst().getItem()).equals(List.of(r.id())), "broken block drops its own instance: " + ids(list.getFirst().getItem()));
            h.assertTrue(WorldChemistryStore.block(h.getLevel(), abs).isEmpty(), "block instance removed from chunk store");
        });
    }

    @GameTest(template = "core_empty", timeoutTicks = 20)
    public static void sampledProvenance(GameTestHelper h) {
        var dim = net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        var biome = net.minecraft.resources.ResourceLocation.parse("minecraft:plains");
        var env = new org.goldgom.chemicalelements.api.world.EnvironmentSample(dim, biome, -32, "granite", 123L, Map.of());
        var origin = new org.goldgom.chemicalelements.api.instance.Provenance("test", "sampling", 77L, 0, Map.of("custom", "retained"));
        var ctx = new org.goldgom.chemicalelements.api.instance.CreationContext(origin, java.util.Optional.of(env), true);
        var ledger = org.goldgom.chemicalelements.instance.InstanceSampler.mint(new ItemStack(Items.IRON_INGOT), 1, ctx);
        var actual = ledger.instances().getFirst().origin();
        h.assertTrue(actual.metadata().entrySet().containsAll(Map.of("dimension", dim.toString(), "biome", biome.toString(), "y", "-32", "geology", "granite", "custom", "retained").entrySet()), "sampling metadata preserved");
        h.assertTrue(actual.seed() == 77L && actual.source().equals("sampling"), "original provenance preserved");
        h.succeed();
    }

    /** Real ItemStackHandler contract: only returned remainder remains caller-owned. */
    @GameTest(template = "core_empty", timeoutTicks = 20)
    public static void handlerInsertRemainders(GameTestHelper h) {
        var handler = new net.neoforged.neoforge.items.ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot) { return 2; }
        };
        var a = rec(301, "1.1"); var b = rec(302, "1.2"); var c = rec(303, "1.3");
        var input = stack(Items.IRON_INGOT, a, b, c);
        var simulated = handler.insertItem(0, input, true);
        h.assertTrue(handler.getStackInSlot(0).isEmpty() && ids(input).equals(List.of(a.id(), b.id(), c.id())), "simulation preserves inputs");
        h.assertTrue(simulated.getCount() == 1, "simulation predicts remainder");
        var rest = handler.insertItem(0, input, false);
        h.assertTrue(ids(handler.getStackInSlot(0)).equals(List.of(a.id(), b.id())), "partial insert owns exact head");
        h.assertTrue(ids(rest).equals(List.of(c.id())), "returned remainder owns exact tail");
        var rejected = handler.insertItem(0, rest, false);
        h.assertTrue(ids(rejected).equals(List.of(c.id())) && ids(handler.getStackInSlot(0)).equals(List.of(a.id(), b.id())), "full slot rejects without changing identities");
        var empty = new net.neoforged.neoforge.items.ItemStackHandler(1);
        h.assertTrue(empty.insertItem(0, rejected, false).isEmpty(), "full insertion returns empty");
        h.assertTrue(ids(empty.getStackInSlot(0)).equals(List.of(c.id())), "full insertion preserves identity");
        h.succeed();
    }

    @GameTest(template = "core_empty", timeoutTicks = 20)
    public static void largeStoredWaterAndSink(GameTestHelper h) {
        var huge = java.math.BigInteger.ONE.shiftLeft(1024);
        var root = new net.minecraft.nbt.CompoundTag(); root.putInt("schema_version", ChunkChemistry.SCHEMA);
        var cell = new net.minecraft.nbt.CompoundTag(); cell.putLong("pos", 1L);
        for (var key : List.of("volume", "energy", "capacity")) cell.putString(key, huge.toString());
        var species = new net.minecraft.nbt.CompoundTag(); species.putString("chemicalelements:water", huge.toString()); cell.put("species", species);
        var cells = new net.minecraft.nbt.ListTag(); cells.add(cell); root.put("water", cells);
        var water = ChunkChemistry.read(root);
        h.assertFalse(water.readOnly(), "1025bit water is valid storage");
        h.assertTrue(ChunkChemistry.read(water.write()).water(1L).orElseThrow().volume().equals(huge), "water exact roundtrip");
        var sink = new org.goldgom.chemicalelements.persistence.SinkLedger();
        var amount = new org.goldgom.chemicalelements.api.chem.MolAmount(huge);
        sink.depositMatter("large", new org.goldgom.chemicalelements.api.chem.Composition(Map.of(net.minecraft.resources.ResourceLocation.parse("chemicalelements:water"), amount)));
        var saved = sink.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        var loaded = org.goldgom.chemicalelements.persistence.SinkLedger.load(saved, h.getLevel().registryAccess());
        h.assertTrue(!loaded.readOnly() && loaded.totalSunk().equals(sink.totalSunk()), "1025bit sink exact roundtrip");
        var bad = saved.copy(); bad.getCompound("sinks").getCompound("large").putString("chemicalelements:water", "-1");
        var isolated = org.goldgom.chemicalelements.persistence.SinkLedger.load(bad, h.getLevel().registryAccess());
        h.assertTrue(isolated.readOnly() && isolated.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()).equals(bad), "corrupt original preserved, no empty fallback");
        boolean refused = false; try { isolated.nextSerial(); } catch (IllegalStateException expected) { refused = true; }
        h.assertTrue(refused, "quarantined storage rejects mutation");
        h.succeed();
    }

    /** Persistent codec path used for saves (registry ops), including 64 distinct instances. */
    @GameTest(template = "core_empty", timeoutTicks = 20)
    public static void saveCodecRoundTrip(GameTestHelper h) {
        var recs = new InstanceRecord[64];
        for (int i = 0; i < 64; i++) recs[i] = rec(100 + i, "1." + i);
        var s = stack(Items.IRON_INGOT, recs);
        var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var json = ItemStack.CODEC.encodeStart(ops, s).getOrThrow();
        var back = ItemStack.CODEC.parse(ops, json).getOrThrow();
        h.assertTrue(ItemStack.matches(s, back), "saved stack identical");
        h.assertTrue(ids(back).size() == 64 && StackLedgers.get(back).orElseThrow().equals(StackLedgers.get(s).orElseThrow()), "64 distinct instances survive save");
        h.succeed();
    }
}

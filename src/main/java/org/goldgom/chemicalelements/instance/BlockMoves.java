package org.goldgom.chemicalelements.instance;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.data.LedgerJson;
import org.goldgom.chemicalelements.persistence.ChunkChemistry;
import org.goldgom.chemicalelements.persistence.SinkLedger;
import org.goldgom.chemicalelements.persistence.WorldChemistryStore;

/** Block instances follow their block through pistons and falling-block entities; nothing is duplicated. */
public final class BlockMoves {
    private static final String KEY = "chemicalelements:block_instance";

    private BlockMoves() {}

    /** Lift all instances first, then re-insert at destinations (handles chains where a destination was a source). */
    public static void shift(ServerLevel level, List<BlockPos> pushed, Direction dir) {
        if (!StackLedgers.authoritative() || pushed.isEmpty()) return;
        var lifted = new LinkedHashMap<BlockPos, ChunkChemistry.BlockInstance>();
        for (var p : pushed) WorldChemistryStore.takeBlock(level, p).ifPresent(b -> lifted.put(p.immutable(), b));
        for (var e : lifted.entrySet()) {
            var dest = e.getKey().relative(dir);
            var b = e.getValue();
            if (!WorldChemistryStore.putBlock(level, dest, new ChunkChemistry.BlockInstance(b.blockId(), b.itemId(), b.record(), b.revision() + 1))) {
                // destination chunk unavailable/read-only: keep at source rather than lose it
                WorldChemistryStore.putBlock(level, e.getKey(), b);
            }
        }
    }

    public static void lift(ServerLevel level, BlockPos pos, FallingBlockEntity entity) {
        if (!StackLedgers.authoritative() || entity == null) return;
        WorldChemistryStore.takeBlock(level, pos).ifPresent(b -> {
            var tag = entity.getPersistentData();
            tag.putString(KEY + ".block", b.blockId());
            tag.putString(KEY + ".item", b.itemId());
            tag.putString(KEY, LedgerJson.encode(LedgerOps.of(0, List.of(b.record()))).toString());
        });
    }

    static Optional<ChunkChemistry.BlockInstance> carried(FallingBlockEntity entity) {
        var tag = entity.getPersistentData();
        if (!tag.contains(KEY)) return Optional.empty();
        var l = LedgerJson.decode(JsonParser.parseString(tag.getString(KEY)).getAsJsonObject());
        if (l.instances().size() != 1) return Optional.empty();
        return Optional.of(new ChunkChemistry.BlockInstance(tag.getString(KEY + ".block"), tag.getString(KEY + ".item"), l.instances().getFirst(), 0));
    }

    static void clear(FallingBlockEntity entity) {
        var tag = entity.getPersistentData();
        tag.remove(KEY); tag.remove(KEY + ".block"); tag.remove(KEY + ".item");
    }

    public static void land(ServerLevel level, BlockPos pos, FallingBlockEntity entity) {
        carried(entity).ifPresent(b -> {
            if (WorldChemistryStore.putBlock(level, pos, b)) clear(entity);
        });
    }

    /** Fell onto something it cannot replace: the carried instance becomes the dropped item's instance. */
    public static void dropFallen(FallingBlockEntity entity, ItemStack drop) {
        var b = carried(entity);
        if (b.isEmpty()) return;
        clear(entity);
        if (drop.isEmpty() || !StackLedgers.authoritative()) { SinkLedger.depositGlobal("falling_block_lost", List.<InstanceRecord>of(b.get().record())); return; }
        var cur = StackLedgers.get(drop).map(l -> new ArrayList<>(l.instances())).orElseGet(ArrayList::new);
        if (cur.isEmpty()) cur.add(b.get().record()); else { SinkLedger.depositGlobal("falling_block_replaced_drop", List.of(cur.getFirst())); cur.set(0, b.get().record()); }
        StackLedgers.set(drop, LedgerOps.of(0, cur));
        StackLedgers.normalize(drop, "falling_block_drop");
    }
}

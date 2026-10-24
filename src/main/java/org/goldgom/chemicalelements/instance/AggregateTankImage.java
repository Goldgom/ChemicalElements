package org.goldgom.chemicalelements.instance;

import com.google.gson.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.machine.MaterialTank;

/** Actual Composition tank, not an invented parcel inventory. Revision is the persisted tank
 * revision; the enclosing owner separately binds the live mutation clock and durable lineage. */
public record AggregateTankImage(MolAmount capacity, long revision, Composition contents) {
    public AggregateTankImage {
        Objects.requireNonNull(capacity); Objects.requireNonNull(contents);
        if (revision < 0 || contents.totalMol().compareTo(capacity) > 0)
            throw new IllegalArgumentException("tank capacity/revision");
    }
    public static AggregateTankImage capture(MaterialTank tank) {
        long clock = tank.mutationRevision();
        var image = new AggregateTankImage(tank.capacity(), tank.revision(), tank.contents());
        if (clock != tank.mutationRevision()) throw new IllegalStateException("TANK_CHANGED_DURING_CAPTURE");
        return image;
    }
    public String encode() {
        var root = new JsonObject(); root.addProperty("schema", 1);
        root.addProperty("capacity_quanta", capacity.quanta().toString());
        root.addProperty("revision", revision);
        var amounts = new JsonObject();
        contents.amounts().forEach((id, amount) -> amounts.addProperty(id.toString(), amount.quanta().toString()));
        root.add("amounts", amounts); return TransactionEncoding.canonical(root);
    }
    public String digest() { return TransactionEncoding.sha256(encode()); }
    public static AggregateTankImage decode(String encoded) {
        if (encoded.length() > 1024 * 1024) throw new IllegalArgumentException("tank budget");
        var root = JsonParser.parseString(encoded).getAsJsonObject();
        if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("tank schema");
        var amounts = new TreeMap<ResourceLocation, MolAmount>();
        var values = root.getAsJsonObject("amounts");
        for (var id : values.keySet()) amounts.put(ResourceLocation.parse(id), quanta(values.get(id).getAsString()));
        var image = new AggregateTankImage(quanta(root.get("capacity_quanta").getAsString()),
                root.get("revision").getAsBigDecimal().longValueExact(), new Composition(amounts));
        if (!image.encode().equals(encoded)) throw new IllegalArgumentException("noncanonical tank image");
        return image;
    }
    private static MolAmount quanta(String value) {
        if (value.length() > 16384 || !value.matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("tank quanta");
        return new MolAmount(new BigInteger(value));
    }
    /** Strict existing MaterialTank.save representation. No coercion of unknown/missing NBT. */
    public static AggregateTankImage fromNbt(String encoded) {
        try {
            CompoundTag tag = TagParser.parseTag(encoded);
            if (!tag.getAllKeys().equals(Set.of("schema", "capacity_quanta", "revision", "amounts"))
                    || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                    || !tag.contains("capacity_quanta", Tag.TAG_STRING)
                    || !tag.contains("revision", Tag.TAG_LONG) || !tag.contains("amounts", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("tank NBT shape");
            var amounts = new TreeMap<ResourceLocation, MolAmount>();
            var values = tag.getCompound("amounts");
            for (var id : values.getAllKeys()) {
                if (!values.contains(id, Tag.TAG_STRING)) throw new IllegalArgumentException("tank amount type");
                String text = values.getString(id);
                if (text.length() > 16384 || !text.matches("(0|[1-9][0-9]*)(\\.[0-9]+)?"))
                    throw new IllegalArgumentException("tank amount");
                amounts.put(ResourceLocation.parse(id), MolAmount.parseDecimal(text));
            }
            var image = new AggregateTankImage(quanta(tag.getString("capacity_quanta")), tag.getLong("revision"), new Composition(amounts));
            if (!image.toNbt().equals(tag)) throw new IllegalArgumentException("lossy tank NBT");
            return image;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException("tank NBT", invalid);
        }
    }
    public CompoundTag toNbt() {
        var tag = new CompoundTag(); tag.putInt("schema", 1);
        tag.putString("capacity_quanta", capacity.quanta().toString()); tag.putLong("revision", revision);
        var values = new CompoundTag();
        contents.amounts().forEach((id, amount) -> values.putString(id.toString(), amount.decimal().toPlainString()));
        tag.put("amounts", values); return tag;
    }
}

package org.goldgom.chemicalelements.gametest.core;

import java.math.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.nuclear.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.data.LedgerJson;
import org.goldgom.chemicalelements.persistence.LedgerPayload;

/** Exercises the actual B component codecs, not a parallel inventory implementation.
 * Codec roundtrip is not claimed as world-save/restart or settlement validation.
 */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class LedgerCodecGameTests {
 @GameTest(template="core_empty", timeoutTicks=100)
 public static void fullInventoryCodecs(GameTestHelper helper) {
  var amount=new MolAmount(BigInteger.ONE.shiftLeft(4096).add(BigInteger.valueOf(17)));
  var matter=new Composition(Map.of(ResourceLocation.parse("chemicalelements:water"),amount));
  var origin=new Provenance("test","component codec integration",Long.MIN_VALUE,7,Map.of("source_pack","test:integration"));
  var thermal=new ThermalState(new BigDecimal("12345.6789"),new BigDecimal("301.125"),Map.of(Phase.LIQUID,BigDecimal.ONE),13);
  var nuclei=new NuclideInventory(Map.of(new NuclideId(1,2,0),amount));
  var pore=new SolutionState(amount,matter,matter,new BigDecimal("301.125"),new BigDecimal("7.125"),origin,15);
  var ledger=new StackLedger(StackLedger.CURRENT_SCHEMA,17,List.of(new InstanceRecord(new UUID(27,31),matter,origin,11,thermal,nuclei,pore,matter)));
  helper.assertTrue(org.goldgom.chemicalelements.persistence.ChemistryComponents.ready(),"Production ledger component registered through public mod entry");
  var json=LedgerJson.encode(ledger);
  helper.assertTrue(LedgerJson.decode(com.google.gson.JsonParser.parseString(json.toString()).getAsJsonObject()).equals(ledger),"JSON text roundtrip retains every field and 4097-bit inventory");
  var numericMatter=json.deepCopy();
  numericMatter.getAsJsonArray("instances").get(0).getAsJsonObject().getAsJsonObject("remaining").addProperty("chemicalelements:water",1.0d);
  helper.assertTrue(new LedgerPayload(numericMatter.toString()).quarantined(),"JSON numeric matter is rejected rather than rounded into inventory");
  var fractionalRevision=json.deepCopy();fractionalRevision.addProperty("revision",new BigDecimal("17.5"));
  helper.assertTrue(new LedgerPayload(fractionalRevision.toString()).quarantined(),"Fractional owner revision is rejected rather than truncated");
  var overflowRevision=json.deepCopy();overflowRevision.addProperty("revision",BigInteger.ONE.shiftLeft(64).add(BigInteger.valueOf(17)));
  helper.assertTrue(new LedgerPayload(overflowRevision.toString()).quarantined(),"Overflow owner revision cannot wrap into a valid CAS revision");
  var original=LedgerPayload.of(ledger);
  var stack=new net.minecraft.world.item.ItemStack(org.goldgom.chemicalelements.registry.ElementItems.MATERIAL_SAMPLE.get());
  stack.set(org.goldgom.chemicalelements.persistence.ChemistryComponents.LEDGER.get(),original);
  var stackTag=stack.save(helper.getLevel().registryAccess());
  var restored=net.minecraft.world.item.ItemStack.parseOptional(helper.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)stackTag);
  helper.assertTrue(restored.get(org.goldgom.chemicalelements.persistence.ChemistryComponents.LEDGER.get()).ledger().orElseThrow().equals(ledger),"Actual ItemStack save/load keeps full registered component");
  var tag=LedgerPayload.CODEC.encodeStart(NbtOps.INSTANCE,original).getOrThrow();
  var saved=LedgerPayload.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();
  helper.assertTrue(saved.ledger().orElseThrow().equals(ledger),"NBT component codec retains every field and 4097-bit inventory");
  var buffer=Unpooled.buffer();
  try {
   LedgerPayload.STREAM_CODEC.encode(buffer,saved);
   var received=LedgerPayload.STREAM_CODEC.decode(buffer);
   helper.assertTrue(received.ledger().orElseThrow().equals(ledger),"Actual network codec retains complete inventory");
   helper.assertTrue(!buffer.isReadable(),"Network frame consumed exactly");
  } finally {buffer.release();}
  var future=LedgerJson.encode(ledger);future.addProperty("schema_version",99);
  var unknown=new LedgerPayload(future.toString());
  helper.assertTrue(unknown.quarantined(),"Unknown schema quarantined without template reconstruction");
  var unknownTag=LedgerPayload.CODEC.encodeStart(NbtOps.INSTANCE,unknown).getOrThrow();
  var preserved=LedgerPayload.CODEC.parse(NbtOps.INSTANCE,unknownTag).getOrThrow();
  helper.assertTrue(preserved.encoded().equals(unknown.encoded())&&preserved.quarantined(),"Quarantined save keeps source bytes");
  helper.assertTrue(new LedgerPayload(original.encoded()).ledger().orElseThrow().equals(ledger),"Valid payload still decodes after rejection");
  helper.succeed();
 }
}

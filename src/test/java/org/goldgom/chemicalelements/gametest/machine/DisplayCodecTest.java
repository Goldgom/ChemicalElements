package org.goldgom.chemicalelements.gametest.machine;
import java.math.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.network.*;
import org.goldgom.chemicalelements.machine.MaterialTank;
import org.goldgom.chemicalelements.api.chem.*;
public final class DisplayCodecTest {
 public static void main(String[] args){int checks=0;
  for(int bits:new int[]{1025,4096,65536}){
   var quanta=BigInteger.ONE.shiftLeft(bits).add(BigInteger.valueOf(123));var exact=new BigDecimal(quanta,18);
   var rows=List.of("Header total "+DisplayNumbers.format(exact,18)+" mol","Mass "+DisplayNumbers.format(exact.multiply(new BigDecimal("18.015")),18)+" g","Adsorbed "+DisplayNumbers.format(exact,8)+" mol","Pore volume "+DisplayNumbers.format(exact,3)+" L");
   var page=new CompositionPages.Page("OK",9,1,0,rows);var sent=new ChemistryNetwork.Reply(UUID.randomUUID(),17,page);
   var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   try{ChemistryNetwork.Reply.CODEC.encode(buffer,sent);var received=ChemistryNetwork.Reply.CODEC.decode(buffer);if(!received.equals(sent)||buffer.readableBytes()!=0)throw new AssertionError("Reply roundtrip mismatch");checks++;}finally{buffer.release();}
   if(!exact.unscaledValue().equals(quanta))throw new AssertionError("display mutated authority");checks++;
  }
  for(int length:new int[]{1023,1024}){
   var sent=new ChemistryNetwork.Reply(UUID.randomUUID(),1,new CompositionPages.Page("OK",1,1,0,List.of("界".repeat(length))));var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   try{ChemistryNetwork.Reply.CODEC.encode(buffer,sent);if(!ChemistryNetwork.Reply.CODEC.decode(buffer).equals(sent))throw new AssertionError("UTF boundary");checks++;}finally{buffer.release();}
  }
  try{new CompositionPages.Page("OK",1,1,0,List.of("界".repeat(1025)));throw new AssertionError("oversized line accepted");}catch(IllegalArgumentException expected){checks++;}
  try{new CompositionPages.Page("OK",1,1,0,Collections.nCopies(24,"x".repeat(1024)));throw new AssertionError("oversized total accepted");}catch(IllegalArgumentException expected){checks++;}
  var amount=new MolAmount(BigInteger.ONE.shiftLeft(1025));var capacity=new MolAmount(amount.quanta().multiply(BigInteger.TWO));var material=new Composition(Map.of(ResourceLocation.parse("chemicalelements:water"),amount));
  var tank=new MaterialTank(capacity);tank.restore(material,42);var restored=new MaterialTank(capacity);restored.load(tank.save());if(!restored.contents().equals(material)||restored.revision()!=42)throw new AssertionError("legal 1025bit tank NBT loss");checks++;
  for(String text:List.of("缺件 port at 7,69,9 🧪", "长原因".repeat(2000), "cancelled", "")){
   var sent=new MachineStatus(41,new net.minecraft.core.BlockPos(6,69,9),text);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   try{MachineStatus.CODEC.encode(buffer,sent);if(!MachineStatus.CODEC.decode(buffer).equals(sent)||buffer.readableBytes()!=0)throw new AssertionError("machine UTF atomic roundtrip");checks++;}finally{buffer.release();}
  }
  var partial=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{MachineStatus.CODEC.encode(partial,new MachineStatus(41,net.minecraft.core.BlockPos.ZERO,"中文 🧪"));partial.writerIndex(partial.writerIndex()-1);try{MachineStatus.CODEC.decode(partial);throw new AssertionError("partial diagnostic published");}catch(io.netty.handler.codec.DecoderException expected){checks++;}}finally{partial.release();}
  System.out.println(checks+" actual codecs / legal-capacity tank assertions passed; presentation is not instance synchronization");
 }
}

package org.goldgom.chemicalelements.reaction;
import com.google.gson.*;import net.minecraft.resources.ResourceLocation;import org.goldgom.chemicalelements.fluid.*;
import java.nio.file.*;import java.util.*;
public final class SorptionDataTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  var files=new TreeMap<ResourceLocation,JsonElement>();
  try(var paths=Files.list(Path.of("src/main/resources/data/chemicalelements/chemicalelements/sorption"))){for(var p:paths.filter(p->p.toString().endsWith(".json")).sorted().toList())files.put(ResourceLocation.fromNamespaceAndPath("chemicalelements",p.getFileName().toString().replace(".json","")),JsonParser.parseString(Files.readString(p)));}
  var parsed=SorptionLibrary.parse(files);check(parsed.get(ResourceLocation.parse("minecraft:dirt")).poreCapacity().signum()>0);check(parsed.get(ResourceLocation.parse("minecraft:iron_block")).poreCapacity().signum()>0);
  boolean rejected=false;try{parsed.clear();}catch(UnsupportedOperationException expected){rejected=true;}check(rejected);
  var duplicate=new TreeMap<>(files);duplicate.put(ResourceLocation.parse("fixture:duplicate"),files.get(ResourceLocation.parse("chemicalelements:soil")));rejected=false;try{SorptionLibrary.parse(duplicate);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  var bad=files.get(ResourceLocation.parse("chemicalelements:soil")).deepCopy().getAsJsonObject();bad.addProperty("pore_litres","-1");rejected=false;try{SorptionLibrary.parse(Map.of(ResourceLocation.parse("fixture:bad"),bad));}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
  check(parsed.equals(SorptionLibrary.parse(files)));
  System.out.println("PASS: production sorption files="+files.size()+" blocks="+parsed.size()+"; immutable snapshot; duplicate and negative capacity rejected; deterministic reparse");
 }
}

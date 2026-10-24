package org.goldgom.chemicalelements.data;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.gametest.core.MaterialReloadGameTests;

/** Run by the sole integration owner with the test runtime classpath; no new Gradle task. */
public final class MaterialBehaviorLibraryTest {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("src/main/resources/data/chemicalelements/chemicalelements");
        var raw=new TreeMap<ResourceLocation,String>();var files=new TreeMap<ResourceLocation,JsonElement>();
        var sources=new TreeMap<ResourceLocation,String>();
        try(var paths=Files.walk(root)) {
            for(var p:paths.filter(f->f.toString().endsWith(".json")).toList()) {
                String path=root.relativize(p).toString().replace('\\','/');
                var id=ResourceLocation.parse("chemicalelements:"+path.substring(0,path.length()-5));
                String text=Files.readString(p);raw.put(id,text);files.put(id,MaterialBehaviorLibrary.parseRaw(text));sources.put(id,"disk-test:not-a-pack");
            }
        }
        var target=new AtomicChemistryCatalog();target.reload(files,sources,raw);var old=target.bundle();
        MaterialReloadGameTests.checkQueries(old);
        var key=ResourceLocation.parse("chemicalelements:material_behaviors/iron_model");
        var mismatched=new TreeMap<>(raw);mismatched.put(key,"{}");
        expectFailure(()->target.reload(files,sources,mismatched));
        MaterialReloadGameTests.require(target.bundle()==old,"Raw mismatch preserves whole bundle");
        var duplicate=new TreeMap<>(raw);duplicate.put(key,raw.get(key).replace("\"schema_version\": 1","\"schema_version\": 1, \"schema_version\": 1"));
        expectFailure(()->target.reload(files,sources,duplicate));
        MaterialReloadGameTests.require(target.bundle()==old,"Raw duplicate survives no JsonElement normalization");
        expectFailure(()->MaterialBehaviorLibrary.parseRaw("{} {}"));
        expectFailure(()->MaterialBehaviorLibrary.parseRaw("{\"a\":{\"x\":1,\"x\":2}}"));
        var bad=new TreeMap<>(sources);bad.remove(key);expectFailure(()->target.reload(files,bad,raw));
        target.reload(files,sources,raw);
        MaterialReloadGameTests.require(target.bundle().epoch()==old.epoch()+1,"Recovery increments epoch once");
        System.out.println("MaterialBehaviorLibraryTest PASS real disk resources, queries, strict raw and atomic failures");
    }
    private static void expectFailure(Runnable operation) {
        boolean rejected=false;try{operation.run();}catch(RuntimeException expected){rejected=true;}
        MaterialReloadGameTests.require(rejected,"Expected rejection");
    }
}

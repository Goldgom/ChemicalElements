package org.goldgom.chemicalelements.content.material;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.goldgom.chemicalelements.registry.ElementMaterials;

/** Registered GameTest: accessing the deferred content registry requires a loaded mod. */
@net.neoforged.neoforge.gametest.GameTestHolder("chemicalelements")
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class MaterialAssetsTest {
    @net.minecraft.gametest.framework.GameTest(template="core_empty")
    public static void packagedAssets(net.minecraft.gametest.framework.GameTestHelper helper) throws Exception {
        main(new String[0]);
        helper.succeed();
    }
    private static JsonObject load(String path) throws IOException {
        try(var stream=MaterialAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null) throw new AssertionError("Missing packaged asset "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static void main(String[] args) throws Exception {
        var en=load("assets/chemicalelements/lang/en_us.json");
        var zh=load("assets/chemicalelements/lang/zh_cn.json");
        for(String path:ElementMaterials.CONTENT.keySet()) {
            var key="block.chemicalelements."+path;
            if(!en.has(key)||!zh.has(key)) throw new AssertionError("Missing language "+key);
            var model=load("assets/chemicalelements/models/block/"+path+".json");
            if(model.getAsJsonArray("elements").size()<7) throw new AssertionError("Missing frame geometry");
            var item=load("assets/chemicalelements/models/item/"+path+".json");
            if(!item.get("parent").getAsString().equals("chemicalelements:block/"+path)) throw new AssertionError("Item parent");
            var state=load("assets/chemicalelements/blockstates/"+path+".json");
            if(!state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString().equals("chemicalelements:block/"+path)) throw new AssertionError("Blockstate reference");
            if(!load("data/chemicalelements/loot_table/blocks/"+path+".json").getAsJsonArray("pools").isEmpty()) throw new AssertionError("No nominal-matter drops permitted");
        }
        System.out.println("122 material candidate assets and two languages checked (not rendering proof)");
    }
}

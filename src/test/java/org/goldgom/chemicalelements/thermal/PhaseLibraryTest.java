package org.goldgom.chemicalelements.thermal;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.data.CatalogParser;

/** Integration owner runs with test runtime classpath. No JUnit or new build configuration. */
public final class PhaseLibraryTest {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("src/main/resources/data/chemicalelements/chemicalelements");
        var catalogDocs=new TreeMap<ResourceLocation,JsonElement>();
        for(String section:List.of("elements","species","item_compositions")) {
            try(var paths=Files.list(root.resolve(section))) {
                for(var p:paths.filter(f->f.toString().endsWith(".json")).toList()) {
                    String name=p.getFileName().toString();
                    catalogDocs.put(ResourceLocation.parse("chemicalelements:"+section+"/"+name.substring(0,name.length()-5)),JsonParser.parseString(Files.readString(p)));
                }
            }
        }
        var catalog=CatalogParser.parse(23,catalogDocs);
        var docs=new TreeMap<ResourceLocation,String>();
        try(var paths=Files.list(root.resolve("phase_models"))) {
            for(var p:paths.filter(f->f.toString().endsWith(".json")).toList()) {
                String name=p.getFileName().toString();docs.put(ResourceLocation.parse("chemicalelements:"+name.substring(0,name.length()-5)),Files.readString(p));
            }
        }
        var library=PhaseLibrary.load(catalog,docs);
        ThermalPhaseGameTests.require(library.models().size()==122,"122 actual resource documents");
        ThermalPhaseGameTests.require(library.models().values().stream().noneMatch(PhaseModel::hasEnthalpy),"No invented full physical model");
        ThermalPhaseGameTests.arithmeticChecks(catalog);
        System.out.println("PhaseLibraryTest PASS disk catalog/phase models + fictional arithmetic boundaries; live mint separate GameTest");
    }
}

package org.goldgom.chemicalelements.thermal;

import com.google.gson.*;
import java.math.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.api.nuclear.*;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;
import org.goldgom.chemicalelements.instance.InstanceSampler;
import org.goldgom.chemicalelements.registry.ElementItems;

@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class ThermalPhaseGameTests {
    static ResourceLocation id(String s){return ResourceLocation.parse("chemicalelements:"+s);}
    /** Resource-manager path is real. This does NOT install a phase reload listener. */
    public static PhaseLibrary loadResources(net.minecraft.server.packs.resources.ResourceManager manager,
            org.goldgom.chemicalelements.api.data.RegistrySnapshot catalog) throws java.io.IOException {
        var docs=new TreeMap<ResourceLocation,String>();
        for(var e:manager.listResources("chemicalelements/phase_models",p->p.getPath().endsWith(".json")).entrySet()) {
            String path=e.getKey().getPath();String name=path.substring("chemicalelements/phase_models/".length(),path.length()-5);
            try(var r=e.getValue().openAsReader()){var b=new StringBuilder();char[] c=new char[4096];int n;while((n=r.read(c))!=-1)b.append(c,0,n);docs.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),name),b.toString());}
        }
        return PhaseLibrary.load(catalog,docs);
    }
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void realCatalogMintUnknownRetainsInput(GameTestHelper h) throws Exception {
        var catalog=AtomicChemistryCatalog.INSTANCE.snapshot();
        var lib=loadResources(h.getLevel().getServer().getResourceManager(),catalog);
        h.assertTrue(lib.models().size()==122,"118 unknown + four legacy descriptors");
        for(String name:ElementItems.NAMES) {
            var stack=new ItemStack(ElementItems.ELEMENTS.get(name).get());
            var provenance=new Provenance("gametest","thermal-r23-real-mint",23L,catalog.epoch(),Map.of());
            var record=InstanceSampler.mint(stack,1,new CreationContext(provenance,Optional.empty(),false)).instances().get(0);
            var plan=EnthalpyPlanner.plan(record,new EnthalpyPlanner.Request(id(name+"_unknown"),catalog.epoch(),null,new BigDecimal("101325"),new BigDecimal("300"),BigDecimal.TEN,BigDecimal.TEN),lib);
            h.assertTrue(plan.status().equals("UNKNOWN") && plan.expected()==record && plan.allocation().equals(record.remaining())
                    && plan.heatSourceDebitJ().signum()==0 && plan.heatSinkCreditJ().signum()==0 && stack.getCount()==1,"Unknown retains whole input "+name);
        }
        var input=new Composition(Map.of(id("ice"),MolAmount.ONE));
        var adapted=EnthalpyPlanner.legacyWater(input,id("water_melt_legacy"),new BigDecimal("273.15"),new BigDecimal("101325"),new BigDecimal("3005"),BigDecimal.ZERO,MolAmount.ONE,lib);
        var old=PhaseProcess.apply(input,WaterPhaseModels.melt(),new BigDecimal("273.15"),new BigDecimal("101325"),new BigDecimal("3005"),BigDecimal.ZERO,MolAmount.ONE,catalog);
        h.assertTrue(adapted.equals(old),"Exact legacy behavior retained");
        h.assertTrue(AtomicChemistryCatalog.INSTANCE.snapshot()==catalog,"No live catalog replacement");h.succeed();
    }
    /** Deliberately fictional Cp/latent for numerical contract only, NEVER production water data. */
    public static String fixture() {
        return """
        {"schema_version":1,"id":"chemicalelements:test_enthalpy","subject":"chemicalelements:ice","allotrope":"TEST_ONLY",
        "kind":"CONSTANT_CP_TWO_PHASE","reason":"Numerical fixture, not water physics",
        "domain":{"min_k":"200","max_k":"400","min_pa":"100000","max_pa":"102000","ensemble":"ISOBARIC_APPROXIMATION"},
        "energy_reference":"test-only:low-at-200K","low_species":"chemicalelements:ice","high_species":"chemicalelements:water",
        "transition_k":"300","cp_low_j_mol_k":"2","cp_high_j_mol_k":"4","latent_j_mol":"10","legacy_operation":null,
        "sources":[{"id":"fixture","uri":"urn:test:arithmetic","citation":"Original fictional arithmetic fixture","locator":"ThermalPhaseGameTests.fixture","accessed":"2026-10-24","basis":"GAME_CONVENTION","method":"Chosen solely for exact arithmetic testing","pressure_evidence":"Fictional test domain, not measured water","uncertainty":"Not a physical prediction","license_status":"CLEARED","units":"K;Pa;J/mol;J/mol/K","limitations":"TEST ONLY; must never ship as production physical data"}]}
        """;
    }
    public static void arithmeticChecks(org.goldgom.chemicalelements.api.data.RegistrySnapshot catalog) {
        var key=id("test_enthalpy");var lib=PhaseLibrary.load(catalog,Map.of(key,fixture()));
        var origin=new Provenance("gametest","fictional-numeric-fixture",1,catalog.epoch(),Map.of());
        var record=new InstanceRecord(UUID.randomUUID(),new Composition(Map.of(id("ice"),MolAmount.ONE)),origin,0,
                new ThermalState(new BigDecimal("200"),new BigDecimal("300"),Map.of(),0),NuclideInventory.EMPTY,
                org.goldgom.chemicalelements.api.fluid.SolutionState.empty(),Composition.EMPTY);
        var req=new EnthalpyPlanner.Request(key,catalog.epoch(),"test-only:low-at-200K",new BigDecimal("101325"),new BigDecimal("310"),new BigDecimal("5"),BigDecimal.ZERO);
        var p=EnthalpyPlanner.plan(record,req,lib);
        require(p.ready() && p.allocation().amounts().get(id("water")).equals(MolAmount.parseDecimal("0.5")),"half latent quantum allocation");
        require(p.materialEnergyJ().compareTo(new BigDecimal("205"))==0 && p.temperatureK().compareTo(new BigDecimal("300"))==0,"latent plateau");
        require(p.matches(record,lib,req.energyReference()) && !p.matches(record,PhaseLibrary.load(catalog,Map.of(key,fixture())),req.energyReference()),"library identity stale protection");
        var badHeat=new InstanceRecord(record.id(),record.remaining(),origin,0,ThermalState.AMBIENT,record.nuclides(),record.poreSolution(),record.adsorbed());
        require(!EnthalpyPlanner.plan(badHeat,req,lib).ready(),"legacy heat not migrated");
        var badN=new InstanceRecord(record.id(),record.remaining(),origin,0,record.thermal(),new NuclideInventory(Map.of(new NuclideId(1,1,0),MolAmount.parseDecimal("2.000000000000000001"))),record.poreSolution(),record.adsorbed());
        require(EnthalpyPlanner.plan(badN,req,lib).status().equals("NUCLIDE_MISMATCH"),"one quantum isotope excess");
        var mixed=new InstanceRecord(record.id(),record.remaining().add(new Composition(Map.of(id("iron"),MolAmount.ONE))),origin,0,record.thermal(),record.nuclides(),record.poreSolution(),record.adsorbed());
        require(EnthalpyPlanner.plan(mixed,req,lib).status().equals("UNMODELED"),"mixture refusal independent of basis string");
        for(String budget:List.of("0","0.000000000000000001","0.00000000000000001","10","50")) {
            var q=new EnthalpyPlanner.Request(key,catalog.epoch(),req.energyReference(),req.pressurePa(),req.targetK(),new BigDecimal(budget),BigDecimal.ZERO);
            var result=EnthalpyPlanner.plan(record,q,lib);
            require(result.heatSourceDebitJ().compareTo(new BigDecimal(budget))<=0 && result.allocation().totalMol().equals(MolAmount.ONE),"bounded heat/mol "+budget);
            require(result.materialEnergyJ().subtract(record.thermal().energyJ()).compareTo(result.heatSourceDebitJ())==0,"exact energy "+budget);
        }
        var partial=new InstanceRecord(record.id(),p.allocation(),origin,1,new ThermalState(p.materialEnergyJ(),p.temperatureK(),Map.of(),1),record.nuclides(),record.poreSolution(),record.adsorbed());
        var reverse=EnthalpyPlanner.plan(partial,new EnthalpyPlanner.Request(key,catalog.epoch(),req.energyReference(),req.pressurePa(),new BigDecimal("290"),BigDecimal.ZERO,new BigDecimal("5")),lib);
        require(reverse.ready() && reverse.allocation().equals(record.remaining()) && reverse.materialEnergyJ().compareTo(record.thermal().energyJ())==0,"closed latent heat cycle");
        for(String invalid:List.of(fixture().replace("K;Pa;J/mol;J/mol/K","K;bar;J/mol;J/mol/K"),fixture().replace("\"CLEARED\"","\"UNKNOWN\""),fixture().replace("\"cp_low_j_mol_k\":\"2\"","\"cp_low_j_mol_k\":\"0\""),fixture().replace("\"schema_version\":1","\"schema_version\":1,\"schema_version\":1"),fixture().replace("\"method\":","\"missing_method\":"))) {
            boolean rejected=false;try{PhaseLibrary.load(catalog,Map.of(key,invalid));}catch(RuntimeException expected){rejected=true;}require(rejected,"strict loader rejection");
        }
    }
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @GameTest(template="core_empty",timeoutTicks=100)
    public static void fictionalEnthalpyFixtureBoundaries(GameTestHelper h){arithmeticChecks(AtomicChemistryCatalog.INSTANCE.snapshot());h.succeed();}
}

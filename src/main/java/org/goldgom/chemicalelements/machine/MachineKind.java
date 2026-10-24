package org.goldgom.chemicalelements.machine;

import java.math.BigDecimal;
import java.util.Set;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.reaction.ReactionContext;

/** Equipment capabilities are supplied by the server, never by a client packet. */
public enum MachineKind {
 ANALYZER("analyzer",Set.of("analysis"),298.15,101325),
 SAMPLER("sampler",Set.of("sampling"),298.15,101325),
 GRINDER("grinder",Set.of("grinding"),298.15,101325),
 DISSOLVER("dissolver",Set.of("aqueous","mixing"),298.15,101325),
 FILTER("filter",Set.of("filtration"),298.15,101325),
 CENTRIFUGE("centrifuge",Set.of("centrifugation"),298.15,101325),
 EVAPORATOR("evaporator",Set.of("heating","evaporation"),373.15,101325),
 CRYSTALLIZER("crystallizer",Set.of("cooling","crystallization"),278.15,101325),
 DISTILLER("distiller",Set.of("heating","distillation","condensation"),373.15,101325),
 REACTOR("reactor",Set.of("heating","pressure","catalyst","mixing"),600,500000),
 ELECTROLYZER("electrolyzer",Set.of("electricity","electrolysis","aqueous"),298.15,101325),
 HEAT_EXCHANGER("heat_exchanger",Set.of("heat_exchange"),298.15,101325),
 CONDENSER("condenser",Set.of("cooling","condensation"),278.15,101325),
 GAS_COLLECTOR("gas_collector",Set.of("gas_collection","gas"),298.15,101325),
 STORAGE_TANK("storage_tank",Set.of("storage","sealed"),298.15,101325),
 PUMP("pump",Set.of("transport","pressure"),298.15,200000),
 PIPE("pipe",Set.of("transport","sealed"),298.15,101325),
 DRYER("dryer",Set.of("adsorption","drying"),298.15,101325),
 VACUUM_CHAMBER("vacuum_chamber",Set.of("vacuum","heating"),373.15,1000),
 HIGH_TEMPERATURE_FURNACE("high_temperature_furnace",Set.of("heating","smelting"),1800,101325),
 FRACTIONATION_TOWER("fractionation_tower",Set.of("distillation","fractionation","heating","condensation"),500,101325),
 RESEARCH_CONTROLLER("research_controller",Set.of("research","analysis","electricity"),298.15,101325),
 ISOTOPE_SEPARATOR("isotope_separator",Set.of("isotope_separation","electricity"),298.15,101325),
 SHIELDED_REACTOR("shielded_reactor",Set.of("nuclear","shielding","cooling"),600,200000),
 AMBIENT_WORKBENCH("ambient_workbench",Set.of("ambient_workbench"),298.15,101325);

 private final String id; private final Set<String> capabilities;
 private final BigDecimal designTemperature,designPressure;
 MachineKind(String id,Set<String> caps,double t,double p){this.id=id;capabilities=caps;designTemperature=BigDecimal.valueOf(t);designPressure=BigDecimal.valueOf(p);}
 public String id(){return id;} public Set<String> capabilities(){return capabilities;}
 public BigDecimal designTemperature(){return designTemperature;} public BigDecimal designPressure(){return designPressure;}
 /** Ambient bench is immutable; equipment setpoints still require physical energy/ports. */
 public ReactionContext conditions(Composition input,BigDecimal actualTemperature,BigDecimal actualPressure,BigDecimal energy,Set<net.minecraft.resources.ResourceLocation> catalysts){
  if(this==AMBIENT_WORKBENCH)return ReactionContext.workbench(input);
  return new ReactionContext(input,actualTemperature,actualPressure,energy,catalysts,capabilities,BigDecimal.ZERO);
 }
 public static MachineKind byId(String id){for(var k:values())if(k.id.equals(id))return k;throw new IllegalArgumentException("unknown machine "+id);}
}

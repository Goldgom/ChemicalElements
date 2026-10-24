package org.goldgom.chemicalelements.thermal;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
/** Narrow 1-atm water models, not an EOS. No extrapolation outside declared domains. */
public final class WaterPhaseModels {
 private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("chemicalelements",path);}
 private static PhaseProcess.Transition transition(String from,String to,String min,String max,String latent,String source){return new PhaseProcess.Transition(id(from),id(to),new BigDecimal(min),new BigDecimal(max),new BigDecimal("101000"),new BigDecimal("101650"),new BigDecimal(latent),"empirical_approximation_with_game_domain",source);}
 public static PhaseProcess.Transition vaporize(){return transition("water","water_vapor","373.15","373.65","40650","Water normal-boiling molar vaporization enthalpy approximately 40.65 kJ/mol at 373.15 K; NIST Chemistry WebBook water phase-change data (CAS 7732-18-5). Constant latent heat across this narrow interval is an approximation; pressure/temperature window is game tolerance. Sensible heating must be paid separately.");}
 public static PhaseProcess.Transition condense(){return transition("water_vapor","water","372.65","373.15","-40650","Reverse of narrow normal-boiling water model; requires explicit cooling sink, releases approximately 40.65 kJ/mol, not free heat deletion.");}
 public static PhaseProcess.Transition melt(){return transition("ice","water","273.15","273.65","6010","Water molar fusion enthalpy approximately 6.01 kJ/mol at normal melting point (standard thermochemical approximation). Constant latent heat, pure-water/no-salt model; game pressure/temperature tolerance, sensible heating accounted separately.");}
 public static PhaseProcess.Transition freeze(){return transition("water","ice","272.65","273.15","-6010","Reverse pure-water fusion model; explicit cooling sink, approximately 6.01 kJ/mol release. Salt-containing water requires phase equilibrium/freezing-point model and is not covered.");}
 private WaterPhaseModels(){}
}

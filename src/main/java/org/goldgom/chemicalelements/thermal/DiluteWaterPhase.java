package org.goldgom.chemicalelements.thermal;

import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;

/** Ideal nonvolatile dilute solution, at approximately 1 atm. Dissociated ions count separately.
 * Kf=1.86 K kg/mol, Kb=0.512 K kg/mol; linear colligative approximation limited to 0.1 mol/kg.
 * Does not describe volatile mixtures, eutectics, hydrates or nonideal concentrated brines. */
public final class DiluteWaterPhase {
 private static final ResourceLocation WATER=ResourceLocation.parse("chemicalelements:water");
 private static final BigDecimal KG_PER_MOL=new BigDecimal("0.01801528"), MAX_MOLALITY=new BigDecimal("0.1");
 private static final MathContext MC=MathContext.DECIMAL128;
 private DiluteWaterPhase(){}
 public static PhaseProcess.Plan separate(Composition input,boolean freeze,BigDecimal kelvin,BigDecimal pressure,BigDecimal energyBudget,BigDecimal coolingBudget,MolAmount capacity,RegistrySnapshot catalog){
  BigInteger water=input.amounts().getOrDefault(WATER,MolAmount.ZERO).quanta(),solute=BigInteger.ZERO;
  for(var e:input.amounts().entrySet())if(!e.getKey().equals(WATER)){
   var definition=catalog.species().get(e.getKey());
   if(definition==null)return reject(input,"UNMODELED","Unknown solute "+e.getKey()+"; input retained unchanged");
   if(definition.phase()!=Phase.AQUEOUS)return reject(input,"UNMODELED","Only nonvolatile aqueous solutes are modeled; other phases retained unchanged");
   solute=solute.add(e.getValue().quanta());
  }
  if(water.signum()==0)return reject(input,"NO_SOLUTION","No liquid water");
  if(solute.signum()==0)return PhaseProcess.apply(input,freeze?WaterPhaseModels.freeze():WaterPhaseModels.vaporize(),kelvin,pressure,energyBudget,coolingBudget,capacity,catalog);
  if(energyBudget.signum()<0||coolingBudget.signum()<0)throw new IllegalArgumentException("negative heat budget");
  if(pressure.compareTo(new BigDecimal("101000"))<0||pressure.compareTo(new BigDecimal("101650"))>0)return reject(input,"UNMODELED","Dilute model supports approximately one atmosphere only");
  if(!allowed(water,solute,kelvin,freeze))return reject(input,"CONDITIONS_NOT_MET","Outside dilute colligative temperature/concentration domain (maximum 0.1 mol/kg); inputs unchanged");
  BigDecimal latent=new BigDecimal(freeze?"6010":"40650");
  BigInteger budget=(freeze?coolingBudget:energyBudget).movePointRight(MolAmount.SCALE).divideToIntegralValue(latent).toBigInteger();
  BigInteger low=BigInteger.ZERO,high=water.subtract(BigInteger.ONE).min(capacity.quanta()).min(budget);
  while(low.compareTo(high)<0){BigInteger mid=low.add(high).add(BigInteger.ONE).shiftRight(1);if(allowed(water.subtract(mid),solute,kelvin,freeze))low=mid;else high=mid.subtract(BigInteger.ONE);}
  if(low.signum()==0)return reject(input,"NO_SOLUTION","No finite phase extent within heat, output and concentration limits");
  var outputId=ResourceLocation.parse(freeze?"chemicalelements:ice":"chemicalelements:water_vapor");
  var consumed=new Composition(Map.of(WATER,new MolAmount(low)));var output=new Composition(Map.of(outputId,new MolAmount(low)));
  if(!consumed.inventory(catalog).equals(output.inventory(catalog)))throw new IllegalArgumentException("invalid water phase species");
  BigDecimal heat=new BigDecimal(low,MolAmount.SCALE).multiply(latent);
  return new PhaseProcess.Plan("READY",input.subtract(consumed),output,freeze?BigDecimal.ZERO:heat,freeze?heat:BigDecimal.ZERO,"Ideal dilute colligative approximation; solutes retained; latent heat and both inventories must settle atomically; sensible heat separate");
 }
 private static boolean allowed(BigInteger water,BigInteger solute,BigDecimal k,boolean freeze){
  if(water.signum()<=0)return false;
  BigDecimal molality=new BigDecimal(solute).divide(new BigDecimal(water).multiply(KG_PER_MOL),MC);
  if(molality.compareTo(MAX_MOLALITY)>0)return false;
  BigDecimal transition=freeze?new BigDecimal("273.15").subtract(new BigDecimal("1.86").multiply(molality)):new BigDecimal("373.15").add(new BigDecimal("0.512").multiply(molality));
  // Keep the constant-latent approximation in a narrow half-kelvin interval.
  return freeze?k.compareTo(transition)<=0&&k.compareTo(new BigDecimal("272.65"))>=0:k.compareTo(transition)>=0&&k.compareTo(new BigDecimal("373.65"))<=0;
 }
 private static PhaseProcess.Plan reject(Composition input,String code,String reason){return new PhaseProcess.Plan(code,input,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,reason);}
}

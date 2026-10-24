package org.goldgom.chemicalelements.thermal;

import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import net.minecraft.resources.ResourceLocation;
import java.math.*;
import java.util.*;

/** Pure physical process plans. D/B atomically settle input, output and energy together. */
public final class PhaseProcess {
 public record Transition(ResourceLocation from,ResourceLocation to,BigDecimal minK,BigDecimal maxK,BigDecimal minPa,BigDecimal maxPa,BigDecimal joulesPerMol,String basis,String source){
  public Transition{if(minK.signum()<=0||minK.compareTo(maxK)>0||minPa.signum()<=0||minPa.compareTo(maxPa)>0||joulesPerMol.signum()==0||basis.isBlank()||source.isBlank())throw new IllegalArgumentException("invalid phase model");}
 }
 public record Plan(String status,Composition remaining,Composition output,BigDecimal energyConsumed,BigDecimal heatReleased,String explanation) {}
 public static Plan apply(Composition input,Transition transition,BigDecimal kelvin,BigDecimal pressure,BigDecimal availableJ,BigDecimal coolingCapacityJ,MolAmount outputCapacity,RegistrySnapshot snapshot){
  if(availableJ.signum()<0||coolingCapacityJ.signum()<0)throw new IllegalArgumentException("negative energy budget");
  if(transition.basis.equals("empirical_approximation_with_game_domain")&&input.amounts().size()!=1)return new Plan("UNMODELED",input,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,"Pure-water phase model does not model mixtures, boiling elevation or freezing depression");
  var from=snapshot.requireSpecies(transition.from);var to=snapshot.requireSpecies(transition.to);
  if(!from.atoms().equals(to.atoms())||from.charge()!=to.charge())throw new IllegalArgumentException("phase transition changes chemical identity");
  if(kelvin.compareTo(transition.minK)<0||kelvin.compareTo(transition.maxK)>0||pressure.compareTo(transition.minPa)<0||pressure.compareTo(transition.maxPa)>0)return new Plan("CONDITIONS_NOT_MET",input,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,"Outside phase model temperature/pressure domain");
  BigDecimal budget=transition.joulesPerMol.signum()>0?availableJ:coolingCapacityJ;
  BigInteger limit=budget.movePointRight(MolAmount.SCALE).divideToIntegralValue(transition.joulesPerMol.abs()).toBigInteger();
  BigInteger amount=input.amounts().getOrDefault(transition.from,MolAmount.ZERO).quanta().min(outputCapacity.quanta()).min(limit);
  if(amount.signum()==0)return new Plan("NO_SOLUTION",input,Composition.EMPTY,BigDecimal.ZERO,BigDecimal.ZERO,"Insufficient material, output space or heat/cooling budget");
  Composition consumed=new Composition(Map.of(transition.from,new MolAmount(amount))),output=new Composition(Map.of(transition.to,new MolAmount(amount)));
  BigDecimal energy=new BigDecimal(amount,MolAmount.SCALE).multiply(transition.joulesPerMol);
  return new Plan("READY",input.subtract(consumed),output,energy.max(BigDecimal.ZERO),energy.min(BigDecimal.ZERO).negate(),transition.basis+": "+transition.source);
 }
}

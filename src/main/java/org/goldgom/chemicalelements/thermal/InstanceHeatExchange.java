package org.goldgom.chemicalelements.thermal;
import java.math.*;
import java.util.*;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
/** No bath at an imposed temperature: both finite heat reservoirs participate in one transaction. */
public final class InstanceHeatExchange {
 public record Capacity(BigDecimal joulesPerKelvin,BigDecimal minK,BigDecimal maxK,String source){
  public Capacity{if(joulesPerKelvin.signum()<=0||minK.signum()<0||minK.compareTo(maxK)>0||source.isBlank())throw new IllegalArgumentException("invalid heat capacity");}
 }
 public record Plan(String status,ThermalState a,ThermalState b,BigDecimal transferredJ,String detail){}
 private InstanceHeatExchange(){}
 public static Plan exchange(ThermalState a,ThermalState b,Capacity ca,Capacity cb,BigDecimal budget){
  if(budget.signum()<0)throw new IllegalArgumentException("negative heat transfer budget");
  if(!inside(a.temperatureK(),ca)||!inside(b.temperatureK(),cb))return new Plan("UNMODELED",a,b,BigDecimal.ZERO,"Outside heat capacity domain; unchanged");
  BigDecimal difference=a.temperatureK().subtract(b.temperatureK());
  BigDecimal equalization=difference.abs().multiply(ca.joulesPerKelvin).multiply(cb.joulesPerKelvin).divide(ca.joulesPerKelvin.add(cb.joulesPerKelvin),MathContext.DECIMAL128);
  BigDecimal moved=equalization.min(budget).min(difference.signum()>=0?a.energyJ():b.energyJ());
  if(difference.signum()<0)moved=moved.negate();
  BigDecimal ta=a.temperatureK().subtract(moved.divide(ca.joulesPerKelvin,MathContext.DECIMAL128)),tb=b.temperatureK().add(moved.divide(cb.joulesPerKelvin,MathContext.DECIMAL128));
  if(!inside(ta,ca)||!inside(tb,cb))return new Plan("UNMODELED",a,b,BigDecimal.ZERO,"Proposed temperature leaves model domain; unchanged");
  if(moved.signum()==0)return new Plan("NO_CHANGE",a,b,BigDecimal.ZERO,"At equilibrium or zero budget");
  return new Plan("READY",new ThermalState(a.energyJ().subtract(moved),ta,a.phaseFractions(),Math.addExact(a.revision(),1)),new ThermalState(b.energyJ().add(moved),tb,b.phaseFractions(),Math.addExact(b.revision(),1)),moved,"Exact energy debit/credit; temperatures use constant-capacity approximation; no implicit phase change");
 }
 private static boolean inside(BigDecimal t,Capacity c){return t.compareTo(c.minK)>=0&&t.compareTo(c.maxK)<=0;}
}

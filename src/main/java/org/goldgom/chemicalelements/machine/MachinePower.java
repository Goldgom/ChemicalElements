package org.goldgom.chemicalelements.machine;

import java.math.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Game convention: 1 FE = 1 J; controller heat capacity = 1000 J/K, explicitly not an empirical material model. */
public final class MachinePower implements IEnergyStorage {
 private int electrical;
 private long thermalMilliJ=298150000;
 private int pressurePa=101325;
 private final Runnable dirty;
 private long mutationRevision;
 public long mutationRevision(){return mutationRevision;}
 public long thermalMilliJ(){return thermalMilliJ;}
 public int pressurePa(){return pressurePa;}
 private void changed(){mutationRevision=Math.incrementExact(mutationRevision);dirty.run();}
 public MachinePower(Runnable dirty){this.dirty=dirty;}
 @Override public int receiveEnergy(int max,boolean simulate){int n=Math.max(0,Math.min(max,1000000-electrical));if(!simulate&&n>0){electrical+=n;changed();}return n;}
 @Override public int extractEnergy(int max,boolean simulate){return 0;}
 @Override public int getEnergyStored(){return electrical;}@Override public int getMaxEnergyStored(){return 1000000;}@Override public boolean canExtract(){return false;}@Override public boolean canReceive(){return true;}
 public BigDecimal temperature(){return BigDecimal.valueOf(thermalMilliJ,6);}public BigDecimal pressure(){return BigDecimal.valueOf(pressurePa);}public BigDecimal available(){return BigDecimal.valueOf(electrical);}
 public boolean spend(int joules){if(joules<0||electrical<joules)return false;electrical-=joules;changed();return true;}
 public void tick(MachineKind kind){
  if(kind==MachineKind.AMBIENT_WORKBENCH)return;
  if(kind.capabilities().contains("heating")&&temperature().compareTo(kind.designTemperature())<0){int n=Math.min(1000,electrical);if(n>0){electrical-=n;thermalMilliJ+=1000L*n;changed();}}
  if((kind.capabilities().contains("pressure")||kind.capabilities().contains("vacuum"))&&electrical>=10){int target=kind.designPressure().intValueExact();if(pressurePa!=target){electrical-=10;pressurePa+=Integer.signum(target-pressurePa)*Math.min(1000,Math.abs(target-pressurePa));changed();}}
 }
 public void save(CompoundTag tag){tag.putInt("electrical_j",electrical);tag.putLong("thermal_millij",thermalMilliJ);tag.putInt("pressure_pa",pressurePa);}
 public void load(CompoundTag tag){int e=tag.getInt("electrical_j");long t=tag.contains("thermal_millij")?tag.getLong("thermal_millij"):298150000;int p=tag.contains("pressure_pa")?tag.getInt("pressure_pa"):101325;if(e<0||e>1000000||t<=0||p<=0)throw new IllegalStateException("invalid machine energy state");long next=Math.incrementExact(mutationRevision);electrical=e;thermalMilliJ=t;pressurePa=p;mutationRevision=next;dirty.run();}
}

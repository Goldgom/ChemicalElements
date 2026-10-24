package org.goldgom.chemicalelements.machine;

import net.minecraft.core.Direction;
import org.goldgom.chemicalelements.api.chem.MolAmount;

/** Finite, loaded-neighbor transport. Each edge has a single source on each server tick.
 * Cross-chunk edges are paused until the shared durable journal is connected. */
public final class MachineTransport {
 private static final MolAmount FLOW=MolAmount.parseDecimal("0.05");
 private MachineTransport(){}
 public static void tick(MachineBlockEntity machine){
  var level=machine.getLevel();if(level==null||level.isClientSide||machine.running())return;
  var kind=machine.kind();if(kind!=MachineKind.PIPE&&kind!=MachineKind.PUMP)return;
  if(machine.power.getEnergyStored()<1&&kind==MachineKind.PUMP)return;
  // Fixed downward outlet, upward inlet; no client payload controls quantity.
  var from=machine.getBlockPos().relative(Direction.UP);var to=machine.getBlockPos().relative(Direction.DOWN);
  if(!level.hasChunkAt(from)||!level.hasChunkAt(to))return;
  if((from.getX()>>4)!=(to.getX()>>4)||(from.getZ()>>4)!=(to.getZ()>>4))return;
  if(!(level.getBlockEntity(from) instanceof MachineBlockEntity source)||!(level.getBlockEntity(to) instanceof MachineBlockEntity destination)||source.running()||destination.running())return;
  var plan=source.tank.preview(destination.tank,FLOW,id->true);
  if(plan.material().amounts().isEmpty())return;
  if(source.tank.commit(destination.tank,plan)){
   if(kind==MachineKind.PUMP)machine.power.spend(1);
   source.setChanged();destination.setChanged();machine.setChanged();
  }
 }
}

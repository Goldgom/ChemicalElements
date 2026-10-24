package org.goldgom.chemicalelements.reaction;

import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.tx.*;
import net.minecraft.resources.ResourceLocation;
import java.math.*;
import java.util.*;

/** Same source port for ItemEntity instances and partial world blocks. No anonymous solid-copy ingestion. */
public final class DissolutionTransaction implements ChemistryTransaction {
 public record Snapshot(String solidIdentity,long solidRevision,long solutionRevision,Composition solid,Composition aqueous,BigDecimal litres,BigDecimal temperature) {}
 public interface Owner {
  Snapshot snapshot();
  /** Atomic CAS of solid remainder + aqueous + precipitates. Receipt ID prevents repeated settlement. */
  TransactionResult commit(UUID id,Snapshot expected,Composition solidRemainder,Composition aqueous);
 }
 private final Owner owner;private final Snapshot before;private final Composition solids,aqueous;private final boolean converged;
 private UUID receipt;private TransactionResult settled;
 public DissolutionTransaction(Owner owner,RegistrySnapshot catalog,List<PhaseEquilibrium.Salt> salts,int passes){
  this.owner=owner;before=owner.snapshot();
  for(var salt:salts){TreeMap<ResourceLocation,MolAmount> ions=new TreeMap<>();salt.ions().forEach((id,n)->ions.put(ResourceLocation.parse(id),new MolAmount(BigInteger.valueOf(n))));var solid=new Composition(Map.of(ResourceLocation.parse(salt.solid()),new MolAmount(BigInteger.ONE)));if(!solid.inventory(catalog).equals(new Composition(ions).inventory(catalog)))throw new IllegalArgumentException("unbalanced dissolution: "+salt.solid());}
  var result=PhaseEquilibrium.solve(raw(before.aqueous),raw(before.solid),before.litres,before.temperature,salts,passes);solids=composition(result.solids());aqueous=composition(result.aqueous());converged=result.converged();
  if(!before.solid.add(before.aqueous).inventory(catalog).equals(solids.add(aqueous).inventory(catalog)))throw new IllegalStateException("dissolution ledger mismatch");
 }
 public TransactionResult preview(){return converged?new TransactionResult(false,"preview","Conservative solid/aqueous exchange; source identity="+before.solidIdentity):TransactionResult.rejected("NOT_CONVERGED","No inventory change: equilibrium solver did not converge");}
 public TransactionResult validate(){if(!converged)return preview();return before.equals(owner.snapshot())?new TransactionResult(false,"validated",""):TransactionResult.rejected("STALE_SOURCE","Solid or solution changed; no exchange");}
 public synchronized TransactionResult commit(){if(settled!=null)return settled;var valid=validate();if(!valid.code().equals("validated"))return valid;if(receipt==null)receipt=UUID.randomUUID();var outcome=owner.commit(receipt,before,solids,aqueous);if(outcome.committed())settled=outcome;return outcome;}
 private static Map<String,BigInteger> raw(Composition c){TreeMap<String,BigInteger> out=new TreeMap<>();c.amounts().forEach((id,n)->out.put(id.toString(),n.quanta()));return out;}
 private static Composition composition(Map<String,BigInteger> c){TreeMap<ResourceLocation,MolAmount> out=new TreeMap<>();c.forEach((id,n)->out.put(ResourceLocation.parse(id),new MolAmount(n)));return new Composition(out);}
}

package org.goldgom.chemicalelements.reaction;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.tx.TransactionResult;
import java.math.*;
import java.util.*;
public final class DissolutionInvariantTest {
 static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("test",s);}
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Composition c(String id,String amount){return new Composition(Map.of(id(id),MolAmount.parseDecimal(amount)));}
 static final class Owner implements DissolutionTransaction.Owner {
  DissolutionTransaction.Snapshot state=new DissolutionTransaction.Snapshot("item-uuid",1,1,c("salt","3"),Composition.EMPTY,BigDecimal.ONE,new BigDecimal("298"));int writes;
  public DissolutionTransaction.Snapshot snapshot(){return state;}
  public TransactionResult commit(UUID receipt,DissolutionTransaction.Snapshot expected,Composition solid,Composition aq){if(!state.equals(expected))return TransactionResult.rejected("STALE","");writes++;state=new DissolutionTransaction.Snapshot(state.solidIdentity(),2,2,solid,aq,state.litres(),state.temperature());return TransactionResult.success();}
 }
 public static void main(String[] args){
  var e=new ElementDefinition(id("x"),1,"X",BigDecimal.ONE,DataBasis.GAME_CONVENTION,"fixture");
  var catalog=new RegistrySnapshot(1,"fixture",Map.of(id("x"),e),Map.of(id("salt"),new SpeciesDefinition(id("salt"),Map.of(id("x"),2),0,new BigDecimal("2"),Phase.SOLID,DataBasis.GAME_CONVENTION,"fixture"),id("plus"),new SpeciesDefinition(id("plus"),Map.of(id("x"),1),1,BigDecimal.ONE,Phase.AQUEOUS,DataBasis.GAME_CONVENTION,"fixture"),id("minus"),new SpeciesDefinition(id("minus"),Map.of(id("x"),1),-1,BigDecimal.ONE,Phase.AQUEOUS,DataBasis.GAME_CONVENTION,"fixture")),Map.of());
  var salt=new PhaseEquilibrium.Salt("test:salt",Map.of("test:plus",1,"test:minus",1),List.of(new PhaseEquilibrium.Point(new BigDecimal("298"),BigDecimal.ONE)),"synthetic Ksp fixture");
  Owner owner=new Owner();var tx=new DissolutionTransaction(owner,catalog,List.of(salt),16);tx.preview();tx.validate();check(owner.writes==0);check(tx.commit().committed());check(owner.state.solid().equals(c("salt","2")));check(owner.state.aqueous().equals(c("plus","1").add(c("minus","1"))));check(tx.commit().committed());check(owner.writes==1);
  Owner stale=new Owner();var staleTx=new DissolutionTransaction(stale,catalog,List.of(salt),16);stale.state=new DissolutionTransaction.Snapshot("changed",2,2,c("salt","2"),Composition.EMPTY,BigDecimal.ONE,new BigDecimal("298"));check(!staleTx.commit().committed());check(stale.writes==0);
  boolean rejected=false;try{new DissolutionTransaction(new Owner(),catalog,List.of(new PhaseEquilibrium.Salt("test:salt",Map.of("test:plus",2),salt.table(),"fixture")),16);}catch(IllegalArgumentException ex){rejected=true;}check(rejected);
  System.out.println("PASS: dissolution preview no writes; saturated partial remainder; atomic owner exchange; duplicate commit idempotent; stale source rejected; charge imbalance rejected");
 }
}

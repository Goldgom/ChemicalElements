package org.goldgom.chemicalelements.core;

import java.math.*;
import java.util.*;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;

/** Mechanical recipe material redistribution, not a chemical reaction predictor.
 * Item shapes/components are selected by the real vanilla recipe; their instance matter is rewritten.
 * Returned containers are reserved before products. No nominal output matter is ever minted.
 */
public final class ConservativeCrafting {
 private ConservativeCrafting(){}
 public record Result(List<Composition> products,Composition containers,Composition residue,long epoch){
  public Result {products=List.copyOf(products);}
  public Composition exits(){return products.stream().reduce(containers.add(residue),Composition::add);}
 }
 public static Result rewrite(Composition consumed,List<Composition> returnedContainers,
                              List<Composition> nominalProducts,RegistrySnapshot catalog){
  Objects.requireNonNull(consumed);Objects.requireNonNull(catalog);
  if(nominalProducts.isEmpty()||nominalProducts.size()>4096)throw new IllegalArgumentException("invalid output count");
  Composition containers=returnedContainers.stream().reduce(Composition.EMPTY,Composition::add);
  // This subtraction also fails closed if a dynamic recipe asks to return unavailable container matter.
  Composition available=consumed.subtract(containers);
  var weights=nominalProducts.stream().map(c->c.totalMol().quanta()).toList();
  // Every material output needs a modeled allocation weight. A modeled sibling must
  // not hide an unknown product by assigning it an empty instance composition.
  if(weights.stream().anyMatch(w->w.signum()==0))throw new IllegalArgumentException("unmodeled output composition");
  List<Map<net.minecraft.resources.ResourceLocation,MolAmount>> allocations=new ArrayList<>();
  for(int i=0;i<weights.size();i++)allocations.add(new TreeMap<>());
  available.amounts().forEach((species,amount)->{
   var shares=ExactAllocator.allocate(amount,weights);
   for(int i=0;i<shares.size();i++)allocations.get(i).put(species,shares.get(i));
  });
  var products=allocations.stream().map(Composition::new).toList();
  var result=new Result(products,containers,Composition.EMPTY,catalog.epoch());
  if(!consumed.equals(result.exits()))throw new IllegalStateException("species conservation failure");
  if(!consumed.inventory(catalog).equals(result.exits().inventory(catalog)))throw new IllegalStateException("element conservation failure");
  return result;
 }
}

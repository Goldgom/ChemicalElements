package org.goldgom.chemicalelements.gametest.machine;
import java.util.*;
import org.goldgom.chemicalelements.compat.*;
import org.goldgom.chemicalelements.network.ReactionCatalogSync.Page;
public final class ReactionViewSyncTest {
 public static void main(String[] args){
  RecipeViews.clear();UUID token=UUID.randomUUID();var first=new ReactionDisplay("test:one",List.of("test only"));var second=new ReactionDisplay("test:two",List.of("test only"));
  RecipeViews.accept(new Page(token,"a",0,2,List.of(first)));check(RecipeViews.reactions().isEmpty());
  RecipeViews.accept(new Page(token,"a",1,2,List.of(second)));check(RecipeViews.reactions().equals(List.of(first,second)));
  RecipeViews.accept(new Page(UUID.randomUUID(),"b",0,1,List.of(second)));check(RecipeViews.reactions().equals(List.of(second)));
  RecipeViews.accept(new Page(token,"a",1,2,List.of(second)));check(RecipeViews.reactions().isEmpty());
  RecipeViews.accept(new Page(UUID.randomUUID(),"c",0,1,List.of()));check(RecipeViews.reactions().isEmpty());
  RecipeViews.publish("d",List.of(first));RecipeViews.clear();check(RecipeViews.reactions().isEmpty());
  boolean rejected=false;try{new Page(token,"a",0,513,List.of());}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  System.out.println("ReactionViewSyncTest PASS: complete/replace/out-of-order/empty/disconnect/bounds");
 }
 private static void check(boolean value){if(!value)throw new AssertionError();}
}

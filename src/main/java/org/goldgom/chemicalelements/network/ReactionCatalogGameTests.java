package org.goldgom.chemicalelements.network;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import org.goldgom.chemicalelements.compat.RecipeViews;
import org.goldgom.chemicalelements.data.AtomicChemistryCatalog;

/** Uses the real loaded validated catalog, not production fixtures or synthesized proposals. */
@GameTestHolder("chemicalelements")
@PrefixGameTestTemplate(false)
public final class ReactionCatalogGameTests {
 @GameTest(template="core_empty",timeoutTicks=40)
 public static void reactionCatalogDocumentationCodec(GameTestHelper h){
  var bundle=AtomicChemistryCatalog.INSTANCE.bundle();var entries=ReactionCatalogSync.document(bundle);
  h.assertTrue(!entries.isEmpty(),"Real data pack supplies documented reactions");
  h.assertTrue(entries.size()==bundle.reactions().pathways().size(),"Every validated pathway is documented exactly once");
  int pages=(entries.size()+7)/8;UUID token=UUID.randomUUID();String key=bundle.catalog().contentHash()+":"+bundle.reactions().hash();
  try {
   RecipeViews.clear();
   for(int index=0;index<pages;index++){
    var expected=new ReactionCatalogSync.Page(token,key,index,pages,entries.subList(index*8,Math.min(index*8+8,entries.size())));
    var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
    try {ReactionCatalogSync.Page.CODEC.encode(buffer,expected);var decoded=ReactionCatalogSync.Page.CODEC.decode(buffer);h.assertTrue(expected.equals(decoded),"Public catalog codec roundtrip page "+index);RecipeViews.accept(decoded);}finally{buffer.release();}
    if(index+1<pages)h.assertTrue(RecipeViews.reactions().isEmpty(),"No partial rule catalog is published");
   }
   h.assertTrue(RecipeViews.reactions().equals(entries),"Real catalog fully published without inventory or proposal");
   var ids=new HashSet<String>();for(var e:entries){h.assertTrue(ids.add(e.id()),"Unique real rule ID");h.assertTrue(e.lines().stream().anyMatch(l->l.startsWith("Source: ")),"Rule retains real provenance");}
   RecipeViews.clear();h.assertTrue(RecipeViews.reactions().isEmpty(),"Lifecycle clears public documentation");
  } finally {RecipeViews.clear();}
  h.succeed();
 }
}

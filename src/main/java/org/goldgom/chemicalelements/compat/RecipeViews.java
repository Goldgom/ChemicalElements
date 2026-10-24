package org.goldgom.chemicalelements.compat;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.goldgom.chemicalelements.network.ReactionCatalogSync;

/** Viewer-independent public documentation; never executable reaction proposals. Client main thread only. */
public final class RecipeViews {
 public interface Adapter {String viewerId();void replaceSnapshot(String hash,List<ReactionDisplay> entries);void clear();}
 private static final List<Adapter> ADAPTERS=new CopyOnWriteArrayList<>();
 private static String hash;
 private static List<ReactionDisplay> reactions=List.of();
 private static UUID pending;
 private static String pendingHash;
 private static int next,pages;
 private static final List<ReactionDisplay> buffer=new ArrayList<>();
 private RecipeViews(){}
 public static synchronized void attach(Adapter adapter){ADAPTERS.removeIf(a->a.viewerId().equals(adapter.viewerId()));ADAPTERS.add(adapter);if(hash!=null)adapter.replaceSnapshot(hash,reactions);}
 public static synchronized void detach(String id){ADAPTERS.removeIf(a->a.viewerId().equals(id));}
 public static synchronized void accept(ReactionCatalogSync.Page page){
  if(page.index()==0){clear();pending=page.token();pendingHash=page.hash();pages=page.pages();}
  if(!page.token().equals(pending)||!page.hash().equals(pendingHash)||page.pages()!=pages||page.index()!=next){clear();return;}
  buffer.addAll(page.entries());next++;
  if(next==pages){var entries=List.copyOf(buffer);String key=pendingHash;buffer.clear();pending=null;publish(key,entries);}
 }
 public static synchronized void publish(String key,List<ReactionDisplay> entries){hash=Objects.requireNonNull(key);reactions=List.copyOf(entries);for(var adapter:ADAPTERS)adapter.replaceSnapshot(key,reactions);}
 public static synchronized void clear(){hash=null;reactions=List.of();pending=null;pendingHash=null;next=0;pages=0;buffer.clear();for(var adapter:ADAPTERS)adapter.clear();}
 public static synchronized List<ReactionDisplay> reactions(){return reactions;}
}

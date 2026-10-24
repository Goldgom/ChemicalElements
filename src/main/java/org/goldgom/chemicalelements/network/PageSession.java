package org.goldgom.chemicalelements.network;
import java.util.UUID;
/** Ephemeral screen-scoped fence, never serialized. */
public final class PageSession {
 private final UUID id=UUID.randomUUID();private long sequence;private long emission=-1;private boolean active=true;
 public UUID id(){return id;}public long next(){if(!active)throw new IllegalStateException("closed page");return ++sequence;}
 public boolean accepts(UUID session,long responseSequence){return active&&id.equals(session)&&sequence==responseSequence;}
 /** Server emissions within one request must also be ordered: revocation cannot be undone by a late page. */
 public boolean accepts(UUID session,long responseSequence,long responseEmission){
  if(!accepts(session,responseSequence)||responseEmission<=emission)return false;
  emission=responseEmission;return true;
 }
 public void close(){active=false;sequence++;}
}

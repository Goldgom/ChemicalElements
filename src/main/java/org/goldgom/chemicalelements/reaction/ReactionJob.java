package org.goldgom.chemicalelements.reaction;

import java.util.*;

/** Immutable, persistable job. Owner atomically persists this state with its reserved inputs. */
public record ReactionJob(UUID id,String ruleId,String catalogHash,int elapsed,int duration,State state) {
    public enum State { RESERVED, PAUSED, READY, COMMITTED, CANCELLED }
    public ReactionJob { Objects.requireNonNull(id);Objects.requireNonNull(ruleId);Objects.requireNonNull(catalogHash);Objects.requireNonNull(state);if(duration<=0||elapsed<0||elapsed>duration)throw new IllegalArgumentException("invalid progress"); }
    public static ReactionJob start(String rule,String hash,int duration){return new ReactionJob(UUID.randomUUID(),rule,hash,0,duration,State.RESERVED);}
    public ReactionJob tick(boolean loaded,boolean conditionsValid,String currentHash){
        if(state==State.COMMITTED||state==State.CANCELLED||state==State.READY)return this;
        if(!loaded||!conditionsValid||!catalogHash.equals(currentHash))return new ReactionJob(id,ruleId,catalogHash,elapsed,duration,State.PAUSED);
        int next=Math.min(duration,elapsed+1);return new ReactionJob(id,ruleId,catalogHash,next,duration,next==duration?State.READY:State.RESERVED);
    }
    public interface AtomicOwner {
        /** Atomically release reservation OR exchange it for all products, gas and residues, and persist job terminal state.
         * Return persisted terminal job. Repeating id must return same result, not run the exchange again. */
        ReactionJob settle(ReactionJob expected,boolean complete);
    }
    public ReactionJob finish(AtomicOwner owner,boolean validNow,String hash){
        if(state==State.COMMITTED||state==State.CANCELLED)return this;
        if(state!=State.READY||!validNow||!catalogHash.equals(hash))return this;
        return terminal(owner.settle(this,true),State.COMMITTED);
    }
    public ReactionJob cancel(AtomicOwner owner){if(state==State.COMMITTED||state==State.CANCELLED)return this;return terminal(owner.settle(this,false),State.CANCELLED);}
    private ReactionJob terminal(ReactionJob result,State expected){if(!id.equals(result.id)||result.state!=expected)throw new IllegalStateException("owner did not persist atomic terminal receipt");return result;}
}

package org.goldgom.chemicalelements.world;
import java.math.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.goldgom.chemicalelements.atmosphere.*;
/** Finite loaded-cell exchange. Never treats far field as an infinite sink or constant gas source. */
public final class AtmosphereRuntime {
 public enum Mode {DIFFUSE,COLLECT,DEPOSIT}
 public record Exchange(String id,long revision,long lastTick,AtmosphereTransport.Cell source,AtmosphereTransport.Cell target,Mode mode,Set<String> filter,BigInteger capacity,BigInteger flux){public Exchange{filter=Set.copyOf(filter);Objects.requireNonNull(id);Objects.requireNonNull(mode);}}
 public interface Port {
  /** Only unique owned loaded pairs, with current revisions and target capacity. */
  Collection<Exchange> loaded(ServerLevel level);
  /** Persist both inventories, energy and lastTick with the receipt, or mutate neither. */
  boolean commit(ServerLevel level,UUID receipt,Exchange expected,AtmosphereTransport.Cell source,AtmosphereTransport.Cell target,long tick);
  void diagnostic(ServerLevel level,String id,String reason);
 }
 private static volatile Port port;
 private AtmosphereRuntime(){}
 public static synchronized void bind(Port value){if(port!=null&&port!=value)throw new IllegalStateException("atmosphere owner already bound");port=Objects.requireNonNull(value);}
 public static boolean ready(){return port!=null;}
 public static void tick(LevelTickEvent.Post event){
  var owner=port;if(owner==null||!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%20!=0)return;
  long tick=level.getGameTime();Set<String> seen=new HashSet<>();
  for(var exchange:owner.loaded(level).stream().sorted(Comparator.comparing(Exchange::id)).toList()){
   if(!seen.add(exchange.id)||exchange.lastTick>=tick)continue;
   try{
    GasCollection.Plan result;
    if(exchange.mode==Mode.DIFFUSE){
     var requested=new TreeMap<String,BigInteger>();BigInteger budget=exchange.flux.min(exchange.capacity.subtract(exchange.target.gases().values().stream().reduce(BigInteger.ZERO,BigInteger::add)));
     if(budget.signum()<0)throw new IllegalArgumentException("negative transport capacity");
     for(String id:new TreeSet<>(exchange.source.gases().keySet())){
      BigDecimal a=new BigDecimal(exchange.source.gases().get(id)),b=new BigDecimal(exchange.target.gases().getOrDefault(id,BigInteger.ZERO));
      BigInteger equilibrium=a.multiply(exchange.target.volume()).subtract(b.multiply(exchange.source.volume())).divide(exchange.source.volume().add(exchange.target.volume()),0,RoundingMode.FLOOR).toBigInteger().max(BigInteger.ZERO);
      BigInteger moved=equilibrium.min(budget);requested.put(id,moved);budget=budget.subtract(moved);
     }
     var transferred=AtmosphereTransport.transfer(exchange.source,exchange.target,requested);
     result=new GasCollection.Plan(transferred.source(),transferred.destination(),requested,"diffusion");
    }else result=exchange.mode==Mode.DEPOSIT?GasCollection.deposit(exchange.source,exchange.target,exchange.filter,exchange.capacity,exchange.flux):GasCollection.collect(exchange.source,exchange.target,exchange.filter,exchange.capacity,exchange.flux);
    UUID receipt=UUID.nameUUIDFromBytes((level.dimension().location()+"/atmosphere/"+exchange.id+"/"+exchange.revision+"/"+tick).getBytes(StandardCharsets.UTF_8));
    if(!owner.commit(level,receipt,exchange,result.atmosphere(),result.captured(),tick))owner.diagnostic(level,exchange.id,"Stale/capacity conflict: neither inventory changed");
   }catch(IllegalArgumentException invalid){owner.diagnostic(level,exchange.id,"UNMODELED: "+invalid.getMessage());}
  }
 }
}

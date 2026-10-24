package org.goldgom.chemicalelements.fluid.waterbody;

import java.math.*;
import java.util.*;
import org.goldgom.chemicalelements.fluid.SorptionExchange;

/** Standalone Java21 test. Deliberately NOT a GameTest or a durable Owner test. */
public final class WaterBodyModelTest {
    private static int checks;
    private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    private static WaterVolume q(long n){return WaterVolume.ofSubquanta(BigInteger.valueOf(n));}
    private static WaterBody body(long v,long salt){return new WaterBody(q(v),Map.of("salt",BigInteger.valueOf(salt)),BigInteger.valueOf(11));}
    private static SorptionExchange.Material material(long cap,long rate){return new SorptionExchange.Material(BigInteger.valueOf(cap),BigDecimal.ONE,BigInteger.valueOf(rate),"game_convention","test fixture, not physical data");}
    private static WaterWetting.Target target(String id,long cap){return new WaterWetting.Target(id,1,true,true,false,WaterBody.empty(),material(cap,1));}
    public static void main(String[] args) {
        for(long n:new long[]{0,1,999,1000,1001,Long.MAX_VALUE}) {
            var decoded=WaterVolumeCodec.planLegacy(1,"ChunkChemistry.WaterCell:litres-scale18",Long.toString(n));
            check(!decoded.quarantined(),"known schema");var v=decoded.volume().orElseThrow();
            check(v.legacyLitreQuanta().equals(BigInteger.valueOf(n)),"lossless old round trip "+n);
            check(WaterVolumeCodec.decode(WaterVolumeCodec.encode(v)).volume().orElseThrow().equals(v),"SI codec round trip");
        }
        check(q(1).cubicMetreQuanta().signum()==0&&q(1).residual()==1,"tiny remainder retained");
        check(WaterVolume.BUCKET.cubicMetres().compareTo(BigDecimal.ONE)==0&&WaterVolume.BUCKET.litres().compareTo(new BigDecimal("1000"))==0,"bucket SI");
        var unknown=Map.of("format","future","opaque","KEEP");check(WaterVolumeCodec.decode(unknown).raw().equals(unknown)&&WaterVolumeCodec.decode(unknown).quarantined(),"unknown raw preserved");
        check(WaterVolumeCodec.planLegacy(8,"unknown","0001").quarantined(),"unknown legacy isolated");
        check(WaterVolumeCodec.decode(Map.of("format",WaterVolumeCodec.FORMAT,"m3_q18","0","m3_q18_remainder_1000","1000")).quarantined(),"noncanonical rejected");
        var merged=body(7,13).plus(body(3,2));var allocation=merged.allocate(Map.of("b",q(3),"a",q(2)));
        var recovered=allocation.remainder();for(var p:allocation.destinations().values())recovered=recovered.plus(p);
        check(recovered.equals(merged),"split merge conservation");
        check(allocation.destinations().get("a").solutes().get("salt").equals(BigInteger.valueOf(3)),"shared diluted concentration");
        var full=merged.allocate(Map.of("b",q(5),"a",q(5)));
        check(full.remainder().equals(WaterBody.empty())&&full.destinations().get("a").plus(full.destinations().get("b")).equals(merged),"full split residual");
        var zeroLast=merged.allocate(Map.of("a",q(10),"z",q(0)));
        check(zeroLast.destinations().get("a").equals(merged)&&zeroLast.destinations().get("z").equals(WaterBody.empty()),"zero recipient gets no residue");
        var evaporation=merged.evaporate(merged.volume(),merged.energy());
        check(evaporation.liquidAndResidue().solutes().equals(merged.solutes())&&evaporation.vapor().solutes().isEmpty(),"nonvolatile remains at dryness");
        var first=WaterWetting.initialize(merged,false,List.of(target("rock",4),target("rock",4),target("soil",2)));
        check(first.status()==WaterWetting.Status.READY&&first.pores().size()==2&&first.newTombstones().size()==2,"multi face dedup");
        check(first.pores().get("rock").volume().equals(q(4)),"immediate saturation not rate limited");
        check(first.remaining().plus(first.pores().get("rock")).plus(first.pores().get("soil")).equals(merged),"wetting water solute heat conservation");
        var retry=WaterWetting.initialize(merged,true,List.of(target("rock",4)));
        check(retry.status()==WaterWetting.Status.ALREADY_INITIALIZED&&retry.remaining().equals(merged)&&retry.pores().isEmpty(),"repeated admission refuses");
        var insufficient=WaterWetting.initialize(body(1,1),false,List.of(target("a",1),target("b",1)));
        check(insufficient.status()==WaterWetting.Status.INSUFFICIENT_WATER&&insufficient.deficit().equals(q(1))&&insufficient.pores().isEmpty()&&insufficient.newTombstones().isEmpty(),"insufficient no side effects counterexample");
        var partial=WaterWetting.progressive(merged,List.of(target("new",4)));
        check(partial.pores().get("new").volume().equals(q(1)),"new contact progressive");
        var marked=new WaterWetting.Target("rock",2,true,true,true,WaterBody.empty(),material(4,1));
        check(WaterWetting.initialize(merged,false,List.of(marked)).remaining().equals(merged),"exhausted target tombstone no reinitialization");
        var unloaded=new WaterWetting.Target("x",1,false,true,false,WaterBody.empty(),material(4,1));
        check(WaterWetting.initialize(merged,false,List.of(unloaded)).status()==WaterWetting.Status.UNLOADED,"unloaded wetting paused");
        var cubic=new WaterBody(WaterVolume.BUCKET,Map.of("salt",BigInteger.valueOf(9)),BigInteger.TEN);
        var seed=new WaterGeometry.Pos(0,0,0);
        WaterGeometry.LoadedGeometry pit=p->WaterGeometry.Cell.of(p.y()==0&&p.z()==0&&(p.x()==0||p.x()==1)?WaterGeometry.Kind.SPACE:WaterGeometry.Kind.SOLID);
        var retained=WaterGeometry.plan(cubic,seed,1,30,pit);
        check(retained.status()==WaterGeometry.Status.RETAINED,"pit retains");
        check(retained.level().numerator().multiply(BigInteger.TWO).equals(retained.level().denominator()),"cross section gives half metre");
        var drain=WaterGeometry.plan(cubic,seed,1,30,p->p.equals(new WaterGeometry.Pos(0,-1,0))?new WaterGeometry.Cell(WaterGeometry.Kind.RECEIVER,"lower-body"):pit.read(p));
        check(drain.status()==WaterGeometry.Status.OUTFLOW&&drain.outgoing().equals(cubic)&&drain.retained().equals(WaterBody.empty()),"gravity explicit lower receiver");
        check(WaterGeometry.plan(cubic,seed,1,1,pit).status()==WaterGeometry.Status.BUDGET,"budget hard stop");
        check(WaterGeometry.plan(cubic,seed,1,30,p->WaterGeometry.Cell.of(WaterGeometry.Kind.UNLOADED)).status()==WaterGeometry.Status.PAUSED_UNLOADED,"unloaded boundary not supply");
        var open=WaterGeometry.plan(cubic,seed,1,30,p->WaterGeometry.Cell.of(WaterGeometry.Kind.SPACE));
        check(open.status()==WaterGeometry.Status.BUDGET&&open.retained().equals(cubic)&&open.outgoing().equals(WaterBody.empty()),"open unknown domain preserves water not deletion");
        var snapshot=new ConnectedWaterBodies.Snapshot("one-body",4,merged);
        var connected=ConnectedWaterBodies.discover(seed,32,p->new ConnectedWaterBodies.Cell(true,p.y()==0&&p.z()==0&&(p.x()==0||p.x()==1)?Optional.of(snapshot):Optional.empty()));
        check(connected.status()==ConnectedWaterBodies.Status.READY&&connected.combined().orElseThrow().equals(merged)&&connected.expected().size()==1,"body counted once for two voxels");
        check(ConnectedWaterBodies.discover(seed,1,p->new ConnectedWaterBodies.Cell(true,Optional.of(snapshot))).status()==ConnectedWaterBodies.Status.BUDGET,"body traversal budget");
        check(ConnectedWaterBodies.discover(seed,32,p->new ConnectedWaterBodies.Cell(false,Optional.empty())).status()==ConnectedWaterBodies.Status.UNLOADED,"body traversal unloaded pause");
        for(int volume=1;volume<=40;volume++)for(int take=0;take<=volume;take++) {
            var original=body(volume,volume*3+1);var result=original.allocate(Map.of("first",q(take),"second",q(volume-take)));
            check(result.destinations().get("first").plus(result.destinations().get("second")).plus(result.remainder()).equals(original),"exhaustive integer residual conservation");
        }
        System.out.println("WaterBodyModelTest PASS checks="+checks);
    }
}

package org.goldgom.chemicalelements.fluid;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterTransportTest {
    @Test void aWaterfallDoesNotStarveAnIndependentPuddle(){
        var high=new WaterTransport.Position(10,3,0);var drop=new WaterTransport.Position(10,2,0);
        var puddle=new WaterTransport.Position(0,0,0);var side=new WaterTransport.Position(1,0,0);
        var cells=Map.of(high,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0),drop,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0),
                puddle,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0),side,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0));
        var edges=List.of(new WaterTransport.Edge(high,drop),new WaterTransport.Edge(puddle,side),new WaterTransport.Edge(side,puddle));
        var plan=WaterTransport.stage(cells,edges,n(1000),n(50));
        assertEquals(n(1000),plan.replacements().get(drop).volume());
        assertEquals(n(500),plan.replacements().get(side).volume());assertEquals(n(500),plan.replacements().get(puddle).volume());
        assertEquals(parcel(1000).plus(parcel(1000)),total(cells,plan));
    }
    @Test void fallingColumnRefillsFromAboveWithoutLeavingAlternatingAirPockets(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int y=0;y<4;y++){var p=new WaterTransport.Position(0,y,0);cells.put(p,new WaterTransport.Cell(y==0?FiniteVolume.Parcel.empty():parcel(y==3?200:1000),n(1000),true,true,0));
            if(y>0)edges.add(new WaterTransport.Edge(p,new WaterTransport.Position(0,y-1,0)));}
        var plan=WaterTransport.stage(cells,edges,n(1000),n(50));
        for(int y=0;y<4;y++){
            var p=new WaterTransport.Position(0,y,0);
            assertEquals(n(y<2?1000:y==2?200:0),plan.replacements().getOrDefault(p,cells.get(p).parcel()).volume());
        }
        assertEquals(parcel(1000).plus(parcel(1000)).plus(parcel(200)),total(cells,plan));
    }
    @Test void clippedUnderwaterBodyDoesNotDebitAnArtificialSurface(){
        var left=new WaterTransport.Position(0,0,0);var right=new WaterTransport.Position(1,0,0);var cap=new WaterTransport.Position(0,1,0);var pore=new WaterTransport.Position(2,0,0);
        var cells=Map.of(left,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0),right,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0),
                cap,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0,null,"minecraft:water",true),pore,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0,n(500)));
        var edges=List.of(new WaterTransport.Edge(left,right),new WaterTransport.Edge(right,left),new WaterTransport.Edge(left,cap),new WaterTransport.Edge(cap,left),new WaterTransport.Edge(right,pore));
        var plan=WaterTransport.stage(cells,edges,n(1000),n(50));
        assertFalse(plan.replacements().containsKey(cap),"a snapshot cut through water cannot be drained as though it were the lake surface");
        assertEquals(n(750),plan.replacements().get(left).volume());assertEquals(n(750),plan.replacements().get(right).volume());
        assertEquals(parcel(1000).plus(parcel(1000)).plus(parcel(1000)),total(cells,plan));
    }
    @Test void subthresholdAdjacentGradientsDoNotWakeAnEntireLake(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<3;x++){var p=new WaterTransport.Position(x,0,0);cells.put(p,new WaterTransport.Cell(parcel(300-30*x),n(1000),true,true,0));
            if(x>0){var q=new WaterTransport.Position(x-1,0,0);edges.add(new WaterTransport.Edge(p,q));edges.add(new WaterTransport.Edge(q,p));}}
        assertTrue(WaterTransport.stage(cells,edges,n(1000),n(50)).replacements().isEmpty());
    }
    @Test void ledgeOutletKeepsDrainingWithoutAStableUnsupportedFilm(){
        var source=new WaterTransport.Position(0,1,0);var lip=new WaterTransport.Position(1,1,0);var base=new WaterTransport.Position(1,0,0);
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>(Map.of(source,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0),
                lip,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0),base,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0)));
        var edges=List.of(new WaterTransport.Edge(source,lip),new WaterTransport.Edge(lip,source),new WaterTransport.Edge(lip,base),new WaterTransport.Edge(base,lip));
        for(int i=0;i<20;i++){var plan=WaterTransport.stage(cells,edges,n(1000),n(50));assertEquals(parcel(1000),total(cells,plan));
            plan.replacements().forEach((p,v)->cells.put(p,new WaterTransport.Cell(v,n(1000),true,true,0)));}
        assertEquals(BigInteger.ZERO,cells.get(lip).parcel().volume());assertTrue(cells.get(source).parcel().volume().compareTo(n(50))<0);
        assertTrue(cells.get(base).parcel().volume().compareTo(n(950))>0);
        assertTrue(WaterTransport.stage(cells,edges,n(1000),n(50)).replacements().isEmpty());
    }
    @Test void visibleGravityStageMovesOnlyOneCellAndIgnoresTheLateralDeadband(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int y=0;y<5;y++){var p=new WaterTransport.Position(0,y,0);cells.put(p,new WaterTransport.Cell(y==4?parcel(20):FiniteVolume.Parcel.empty(),n(1000),true,true,0));
            if(y>0)edges.add(new WaterTransport.Edge(p,new WaterTransport.Position(0,y-1,0)));}
        var plan=WaterTransport.stage(cells,edges,n(1000),n(50));
        assertEquals(n(20),plan.replacements().get(new WaterTransport.Position(0,3,0)).volume());
        assertFalse(plan.replacements().containsKey(new WaterTransport.Position(0,2,0)));assertEquals(parcel(20),total(cells,plan));
    }
    @Test void visibleHorizontalStageAdmitsOnlyOneDryFrontier(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<5;x++){var p=new WaterTransport.Position(x,0,0);cells.put(p,new WaterTransport.Cell(x==0?parcel(1000):FiniteVolume.Parcel.empty(),n(1000),true,true,0));
            if(x>0){var q=new WaterTransport.Position(x-1,0,0);edges.add(new WaterTransport.Edge(p,q));edges.add(new WaterTransport.Edge(q,p));}}
        var plan=WaterTransport.stage(cells,edges,n(1000),n(50));
        assertEquals(n(500),plan.replacements().get(new WaterTransport.Position(1,0,0)).volume());
        assertFalse(plan.replacements().containsKey(new WaterTransport.Position(2,0,0)));assertEquals(parcel(1000),total(cells,plan));
    }
    @Test void submergedConnectionEqualizesSeparateSurfaceColumns(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<3;x++)cells.put(new WaterTransport.Position(x,0,0),new WaterTransport.Cell(parcel(1000),n(1000),true,true,0));
        cells.put(new WaterTransport.Position(0,1,0),new WaterTransport.Cell(parcel(1000),n(1000),true,true,0));
        cells.put(new WaterTransport.Position(0,2,0),new WaterTransport.Cell(parcel(500),n(1000),true,true,0));
        cells.put(new WaterTransport.Position(2,1,0),new WaterTransport.Cell(parcel(100),n(1000),true,true,0));
        for(var p:cells.keySet())for(var q:cells.keySet())if(Math.abs(p.x()-q.x())+Math.abs(p.y()-q.y())==1)edges.add(new WaterTransport.Edge(p,q));
        var plan=WaterTransport.equilibrate(cells,edges,n(1000),n(50));
        assertEquals(n(800),plan.replacements().get(new WaterTransport.Position(0,1,0)).volume());
        assertEquals(n(800),plan.replacements().get(new WaterTransport.Position(2,1,0)).volume());
        assertEquals(BigInteger.ZERO,plan.replacements().get(new WaterTransport.Position(0,2,0)).volume());
        assertEquals(cells.values().stream().map(WaterTransport.Cell::parcel).reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus),total(cells,plan));
    }
    @Test void bodySolveKeepsImmiscibleFluidsSeparateAndPoreIntakeBounded(){
        var water=new WaterTransport.Position(0,0,0);var lava=new WaterTransport.Position(1,0,0);var pore=new WaterTransport.Position(0,1,0);
        var cells=Map.of(water,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0,null,"minecraft:water"),
                lava,new WaterTransport.Cell(parcel(400),n(1000),true,true,0,null,"minecraft:lava"),
                pore,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0,n(7)));
        var edges=List.of(new WaterTransport.Edge(water,lava),new WaterTransport.Edge(lava,water),new WaterTransport.Edge(water,pore));
        var plan=WaterTransport.equilibrate(cells,edges,n(1000),n(50));
        assertFalse(plan.replacements().containsKey(lava));assertEquals(n(7),plan.replacements().get(pore).volume());
        assertEquals("minecraft:water",plan.fluidTypes().get(pore));assertEquals(parcel(1000).plus(parcel(400)),total(cells,plan));
    }
    @Test void connectedLakeSettlesAsOneBodyWithoutCheckerboardAndThenSleeps(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)cells.put(new WaterTransport.Position(x,0,z),new WaterTransport.Cell(parcel((x+z)%2==0?1000:200),n(1000),true,true,0));
        for(var p:cells.keySet())for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){var q=new WaterTransport.Position(p.x()+d[0],0,p.z()+d[1]);if(cells.containsKey(q))edges.add(new WaterTransport.Edge(p,q));}
        var before=total(cells,new WaterTransport.Plan(Map.of(),Map.of()));var plan=WaterTransport.equilibrate(cells,edges,n(1000),n(50));
        assertEquals(before,total(cells,plan));
        for(var p:cells.keySet()){var v=plan.replacements().get(p);assertEquals(n(600),v.volume());var c=cells.get(p);cells.put(p,new WaterTransport.Cell(v,c.capacity(),true,true,1));}
        assertTrue(WaterTransport.equilibrate(cells,edges,n(1000),n(50)).replacements().isEmpty());
    }
    @Test void bodyFrontContinuesPastFourRoundsUntilTheDifferenceCannotDriveIt(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<=10;x++){var p=new WaterTransport.Position(x,0,0);cells.put(p,new WaterTransport.Cell(x==0?parcel(1000):FiniteVolume.Parcel.empty(),n(1000),true,true,0));
            if(x>0){var q=new WaterTransport.Position(x-1,0,0);edges.add(new WaterTransport.Edge(p,q));edges.add(new WaterTransport.Edge(q,p));}}
        var plan=WaterTransport.equilibrate(cells,edges,n(1000),n(50));
        assertTrue(plan.replacements().get(new WaterTransport.Position(10,0,0)).volume().signum()>0);
        assertEquals(parcel(1000),total(cells,plan));
        var shortCells=new TreeMap<>(cells);shortCells.put(new WaterTransport.Position(0,0,0),new WaterTransport.Cell(parcel(49),n(1000),true,true,0));
        assertTrue(WaterTransport.equilibrate(shortCells,edges,n(1000),n(50)).replacements().isEmpty());
    }
    @Test void lateralArrivalsFallAndMergeBeforeProjection(){
        var source=new WaterTransport.Position(0,1,0);var arrival=new WaterTransport.Position(1,1,0);var lower=new WaterTransport.Position(1,0,0);
        var cells=Map.of(source,new WaterTransport.Cell(parcel(200),n(1000),true,true,0),
                arrival,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,-1),
                lower,new WaterTransport.Cell(parcel(100),n(1000),true,true,0));
        var plan=WaterTransport.advance(cells,List.of(new WaterTransport.Edge(source,arrival),new WaterTransport.Edge(arrival,lower)),n(1000),n(50),1);
        assertEquals(BigInteger.ZERO,plan.replacements().getOrDefault(arrival,cells.get(arrival).parcel()).volume());
        assertEquals(n(200),plan.replacements().get(lower).volume());
        assertEquals(parcel(200).plus(parcel(100)),total(cells,plan));
    }
    @Test void verticalStackPacksIntoLowestCellsRegardlessOfLateralThreshold(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int y=0;y<9;y++){
            var p=new WaterTransport.Position(0,y,0);
            cells.put(p,new WaterTransport.Cell(y%2==0?parcel(250):FiniteVolume.Parcel.empty(),n(1000),true,true,y));
            if(y>0)edges.add(new WaterTransport.Edge(p,new WaterTransport.Position(0,y-1,0)));
        }
        var plan=WaterTransport.advance(cells,edges,n(1000),n(1000),1);
        assertEquals(n(1000),plan.replacements().get(new WaterTransport.Position(0,0,0)).volume());
        assertEquals(n(250),plan.replacements().get(new WaterTransport.Position(0,1,0)).volume());
        for(int y=2;y<9;y++)assertEquals(BigInteger.ZERO,plan.replacements().getOrDefault(new WaterTransport.Position(0,y,0),cells.get(new WaterTransport.Position(0,y,0)).parcel()).volume());
        assertEquals(cells.values().stream().map(WaterTransport.Cell::parcel).reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus),total(cells,plan));
    }
    private static BigInteger n(long n) { return BigInteger.valueOf(n); }
    private static FiniteVolume.Parcel parcel(long volume) {
        return new FiniteVolume.Parcel(n(volume), Map.of("salt", n(137)), n(591));
    }
    @Test void gravityTakesPriorityOverLateralSpreading(){
        var source=new WaterTransport.Position(0,1,0);var lower=new WaterTransport.Position(0,0,0);var side=new WaterTransport.Position(1,1,0);
        var cells=Map.of(source,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0),lower,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,-1),side,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,-1));
        var plan=WaterTransport.plan(cells,List.of(new WaterTransport.Edge(source,side),new WaterTransport.Edge(source,lower)),n(1000));
        assertEquals(parcel(1000),plan.replacements().get(lower));assertEquals(BigInteger.ZERO,plan.replacements().get(source).volume());assertFalse(plan.replacements().containsKey(side));
    }
    @Test void traceEvaporationIsExplicitAndNeverConsumesPoreWater(){
        var free=new WaterTransport.Position(0,0,0);var pore=new WaterTransport.Position(1,0,0);var boundary=new WaterTransport.Position(2,0,0);
        var trace=parcel(9);var cells=Map.of(free,new WaterTransport.Cell(trace,n(1000),true,true,1),pore,new WaterTransport.Cell(trace,n(100),true,true,2,n(1)),boundary,new WaterTransport.Cell(parcel(10),n(1000),true,true,3));
        var plan=WaterTransport.evaporateTraces(cells,new WaterTransport.Plan(Map.of(),Map.of()),n(10));
        assertEquals(trace,plan.evaporated().get(free));assertEquals(FiniteVolume.Parcel.empty(),plan.replacements().get(free));assertFalse(plan.replacements().containsKey(pore));assertFalse(plan.replacements().containsKey(boundary));
        assertEquals(trace.plus(trace).plus(parcel(10)),total(cells,plan).plus(plan.evaporated().values().stream().reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus)));
    }
    @Test void thinLateralFilmStopsInsteadOfWettingAnEndlessChannel() {
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<100;x++){
            var p=new WaterTransport.Position(x,0,0);cells.put(p,new WaterTransport.Cell(x==0?parcel(1000):FiniteVolume.Parcel.empty(),n(1000),true,true,0));
            if(x>0){var previous=new WaterTransport.Position(x-1,0,0);edges.add(new WaterTransport.Edge(previous,p));edges.add(new WaterTransport.Edge(p,previous));}
        }
        for(int tick=0;tick<500;tick++){
            var plan=WaterTransport.plan(cells,edges,n(125),n(50));assertEquals(parcel(1000),total(cells,plan));
            plan.replacements().forEach((p,v)->cells.put(p,new WaterTransport.Cell(v,n(1000),true,true,tickRevision(cells.get(p)))));
        }
        assertEquals(BigInteger.ZERO,cells.get(new WaterTransport.Position(12,0,0)).parcel().volume());
    }
    @Test void lateralDifferenceThresholdHasAnInclusiveBoundaryAndCanBeDisabled() {
        var a=new WaterTransport.Position(0,0,0);var b=new WaterTransport.Position(1,0,0);
        var edges=List.of(new WaterTransport.Edge(a,b));
        for(long difference:new long[]{49,50,51}) {
            var cells=Map.of(a,new WaterTransport.Cell(parcel(200+difference),n(1000),true,true,0),
                    b,new WaterTransport.Cell(parcel(200),n(1000),true,true,0));
            var plan=WaterTransport.plan(cells,edges,n(1000),n(50));
            assertEquals(difference<50,plan.replacements().isEmpty());
            assertEquals(parcel(200+difference).plus(parcel(200)),total(cells,plan));
            assertFalse(WaterTransport.plan(cells,edges,n(1000),BigInteger.ZERO).replacements().isEmpty());
        }
        // A 60 L film can now move: the old 125 L visual-layer floor must not override the setting.
        var thin=Map.of(a,new WaterTransport.Cell(parcel(60),n(1000),true,true,0),
                b,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,0));
        assertEquals(n(30),WaterTransport.plan(thin,edges,n(1000),n(50)).replacements().get(b).volume());
    }
    private static long tickRevision(WaterTransport.Cell cell){return cell.revision()+1;}
    @Test void multipleRoundsAdvanceTheFrontWithoutMultiplyingPoreIntakeOrInventory(){
        var cells=new TreeMap<WaterTransport.Position,WaterTransport.Cell>();var edges=new ArrayList<WaterTransport.Edge>();
        for(int x=0;x<4;x++){
            var p=new WaterTransport.Position(x,0,0);cells.put(p,new WaterTransport.Cell(x==0?parcel(1000):FiniteVolume.Parcel.empty(),n(1000),true,true,0));
            if(x>0){var before=new WaterTransport.Position(x-1,0,0);edges.add(new WaterTransport.Edge(before,p));edges.add(new WaterTransport.Edge(p,before));}
        }
        var accelerated=WaterTransport.advance(cells,edges,n(1000),n(50),4);
        assertEquals(parcel(1000),total(cells,accelerated));
        assertTrue(accelerated.replacements().get(new WaterTransport.Position(3,0,0)).volume().signum()>0);
        assertFalse(WaterTransport.plan(cells,edges,n(1000),n(50)).replacements().containsKey(new WaterTransport.Position(3,0,0)));
        var pore=new WaterTransport.Position(0,1,0);var source=new WaterTransport.Position(0,0,0);
        var wet=Map.of(source,cells.get(source),pore,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,-1,n(7)));
        var uptake=WaterTransport.advance(wet,List.of(new WaterTransport.Edge(source,pore)),n(1000),n(50),4);
        assertEquals(n(7),uptake.replacements().get(pore).volume());assertEquals(parcel(1000),total(wet,uptake));
        assertEquals(-1L,uptake.expectedRevisions().get(pore));
    }
    @Test void distinctFluidsCannotMixAndAnEmptyCellHasOneDeterministicFluidIdentity(){
        var left=new WaterTransport.Position(-1,0,0);var middle=new WaterTransport.Position(0,0,0);var right=new WaterTransport.Position(1,0,0);
        var cells=Map.of(left,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0,null,"minecraft:lava"),
                middle,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,-1),
                right,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0,null,"example:oil"));
        var edges=new ArrayList<>(List.of(new WaterTransport.Edge(left,middle),new WaterTransport.Edge(right,middle)));
        var plan=WaterTransport.plan(cells,edges,n(1000),n(50));Collections.reverse(edges);
        assertEquals(plan,WaterTransport.plan(cells,edges,n(1000),n(50)));
        assertEquals("minecraft:lava",plan.fluidTypes().get(middle));assertFalse(plan.replacements().containsKey(right));
        assertEquals(parcel(1000).plus(parcel(1000)),total(cells,plan));
        var filled=new HashMap<>(cells);filled.put(middle,new WaterTransport.Cell(parcel(500),n(1000),true,true,0,null,"minecraft:lava"));
        assertTrue(WaterTransport.plan(filled,List.of(new WaterTransport.Edge(right,middle)),n(1000)).replacements().isEmpty());
    }
    @Test void conservativeGravityAndUptakeApplyToRegisteredFluidIdentities(){
        for(String fluid:List.of("minecraft:water","minecraft:lava","example:oil")){
            var source=new WaterTransport.Position(0,1,0);var below=new WaterTransport.Position(0,0,0);
            var cells=Map.of(source,new WaterTransport.Cell(parcel(1000),n(1000),true,true,0,null,fluid),
                    below,new WaterTransport.Cell(FiniteVolume.Parcel.empty(),n(1000),true,true,-1));
            var plan=WaterTransport.plan(cells,List.of(new WaterTransport.Edge(source,below)),n(1000),n(1000));
            assertEquals(parcel(1000),plan.replacements().get(below));assertEquals(fluid,plan.fluidTypes().get(below));
            assertEquals(parcel(1000),total(cells,plan));
        }
    }
    @Test void manySourcesShareOnePoreIntakeLimit() {
        var left = new WaterTransport.Position(-1, 0, 0);
        var right = new WaterTransport.Position(1, 0, 0);
        var pore = new WaterTransport.Position(0, 0, 0);
        var cells = Map.of(left, new WaterTransport.Cell(parcel(1000), n(1000), true, true, 1),
                right, new WaterTransport.Cell(parcel(1000), n(1000), true, true, 2),
                pore, new WaterTransport.Cell(FiniteVolume.Parcel.empty(), n(100), true, true, -1, n(7)));
        var plan = WaterTransport.plan(cells, List.of(new WaterTransport.Edge(left, pore), new WaterTransport.Edge(right, pore)), n(125));
        assertEquals(n(7), plan.replacements().get(pore).volume());
        assertEquals(parcel(1000).plus(parcel(1000)), total(cells, plan));
    }
    @Test void poresAbsorbUpwardAndDoNotBehaveAsFlowingSources() {
        var source = new WaterTransport.Position(0, 0, 0);
        var pore = new WaterTransport.Position(0, 1, 0);
        var cells = Map.of(source, new WaterTransport.Cell(parcel(1000), n(1000), true, true, 1),
                pore, new WaterTransport.Cell(FiniteVolume.Parcel.empty(), n(100), true, true, -1, n(10)));
        var plan = WaterTransport.plan(cells, List.of(new WaterTransport.Edge(source, pore)), n(125));
        assertEquals(n(10), plan.replacements().get(pore).volume());
        var filled = new HashMap<>(cells);
        filled.put(source, new WaterTransport.Cell(FiniteVolume.Parcel.empty(), n(1000), true, true, 2));
        filled.put(pore, new WaterTransport.Cell(plan.replacements().get(pore), n(100), true, true, 0, n(10)));
        assertTrue(WaterTransport.plan(filled, List.of(new WaterTransport.Edge(pore, source)), n(125)).replacements().isEmpty());
    }
    @Test void repeatedMultidirectionalFlowConservesEveryParcelComponent() {
        var random = new Random(627);
        var cells = new TreeMap<WaterTransport.Position, WaterTransport.Cell>();
        var edges = new ArrayList<WaterTransport.Edge>();
        for (int x=0;x<5;x++) for (int y=0;y<3;y++) {
            var p = new WaterTransport.Position(x,y,0);
            cells.put(p, new WaterTransport.Cell(parcel(1+random.nextInt(1000)), n(1000), true, true, 0));
        }
        for(var a:cells.keySet()) for(var b:cells.keySet())
            if(Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())==1) edges.add(new WaterTransport.Edge(a,b));
        var initial = cells.values().stream().map(WaterTransport.Cell::parcel).reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus);
        for(int i=0;i<300;i++) {
            Collections.shuffle(edges, random);
            var plan = WaterTransport.plan(cells, edges, n(125));
            assertEquals(initial, total(cells,plan));
            plan.replacements().forEach((p,v)->{assertTrue(v.volume().compareTo(n(1000))<=0);var old=cells.get(p);cells.put(p,new WaterTransport.Cell(v,old.capacity(),true,true,old.revision()+1));});
        }
    }
    private static FiniteVolume.Parcel total(Map<WaterTransport.Position,WaterTransport.Cell> cells, WaterTransport.Plan plan) {
        return cells.entrySet().stream().map(e->plan.replacements().getOrDefault(e.getKey(),e.getValue().parcel()))
                .reduce(FiniteVolume.Parcel.empty(),FiniteVolume.Parcel::plus);
    }
}

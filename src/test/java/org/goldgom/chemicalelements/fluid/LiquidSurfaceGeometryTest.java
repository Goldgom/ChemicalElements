package org.goldgom.chemicalelements.fluid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiquidSurfaceGeometryTest {
    @Test void aSeparateDiagonalPoolCannotLiftThisPuddle(){
        var mesh=LiquidSurfaceGeometry.smoothMesh(new int[]{10,0,0,0,1,0,0,0,0},0,0);
        var top=mesh.stream().filter(q->q.face()==LiquidSurfaceGeometry.Face.UP).findFirst().orElseThrow();
        for(var p:top.points())assertEquals(0.1F,p.y());
    }
    @Test void adjacentDifferentBandsShareExactlyTheSameSurfaceEdge(){
        int[] left={0,10,2,0,10,2,0,10,2},right={10,2,0,10,2,0,10,2,0};
        var a=LiquidSurfaceGeometry.smoothMesh(left,0,10);var b=LiquidSurfaceGeometry.smoothMesh(right,0,10);
        assertTrue(a.stream().noneMatch(q->q.face()==LiquidSurfaceGeometry.Face.EAST));
        assertTrue(b.stream().noneMatch(q->q.face()==LiquidSurfaceGeometry.Face.WEST));
        var atop=a.stream().filter(q->q.face()==LiquidSurfaceGeometry.Face.UP).findFirst().orElseThrow();
        var btop=b.stream().filter(q->q.face()==LiquidSurfaceGeometry.Face.UP).findFirst().orElseThrow();
        assertEquals(atop.points().get(3).y(),btop.points().get(0).y());
        assertEquals(atop.points().get(2).y(),btop.points().get(1).y());
        assertEquals(0.6F,atop.points().get(2).y());
    }
    @Test void everyBandHasAFlatTopAndCorrectBoxVolume(){
        for(int step=1;step<=10;step++){
            var mesh=LiquidSurfaceGeometry.mesh(step,0,0,0,0,0,0);assertEquals(6,mesh.size());
            var top=mesh.stream().filter(q->q.face()==LiquidSurfaceGeometry.Face.UP).findFirst().orElseThrow();
            for(var p:top.points())assertEquals(step/10F,p.y());
            for(var q:mesh)for(var p:q.points())assertTrue(p.y()>=0&&p.y()<=step/10F);
        }
        assertTrue(LiquidSurfaceGeometry.mesh(0,0,0,0,0,0,0).isEmpty());
    }
    @Test void neighborsSmoothCornersWithoutAboveFluidInflationOrInternalWalls(){
        var low=LiquidSurfaceGeometry.mesh(2,10,10,10,10,10,10);
        assertEquals(1,low.size());assertEquals(LiquidSurfaceGeometry.Face.UP,low.getFirst().face());
        for(var p:low.getFirst().points())assertTrue(p.y()>0.2F&&p.y()<1F);
        var high=LiquidSurfaceGeometry.mesh(8,2,8,8,8,0,10);
        assertEquals(1,high.size());assertEquals(LiquidSurfaceGeometry.Face.UP,high.getFirst().face());
    }
    @Test void stackedFullColumnsHaveNoInternalHorizontalFaces(){
        var bottom=LiquidSurfaceGeometry.mesh(10,0,0,0,0,3,10);
        assertTrue(bottom.stream().noneMatch(q->q.face()==LiquidSurfaceGeometry.Face.UP||q.face()==LiquidSurfaceGeometry.Face.DOWN));
        var top=LiquidSurfaceGeometry.mesh(3,0,0,0,0,0,10);
        assertTrue(top.stream().noneMatch(q->q.face()==LiquidSurfaceGeometry.Face.DOWN));
        assertEquals(0.3F,top.stream().filter(q->q.face()==LiquidSurfaceGeometry.Face.UP).findFirst().orElseThrow().top());
    }
}

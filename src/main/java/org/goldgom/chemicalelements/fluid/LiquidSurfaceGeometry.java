package org.goldgom.chemicalelements.fluid;

import java.util.*;

/** Shared water-surface vertices derived from synchronized ten-band volume samples. */
public final class LiquidSurfaceGeometry {
    public enum Face {UP,DOWN,NORTH,SOUTH,WEST,EAST}
    public record Point(float x,float y,float z){}
    public record Quad(Face face,float bottom,float top,List<Point> points){}
    private LiquidSurfaceGeometry(){}
    /** Shared vertex averages use face-connected columns among the four touching a corner. Both sides
     * of every wet edge therefore produce identical vertices without an internal vertical wall. */
    public static List<Quad> smoothMesh(int[] grid,int above,int below){
        if(grid.length!=9)throw new IllegalArgumentException("3x3 surface grid");
        for(int step:grid)if(step<0||step>10)throw new IllegalArgumentException("volume step");
        int step=grid[4];if(step==0)return List.of();
        float nw=corner(grid,-1,-1),sw=corner(grid,-1,0),se=corner(grid,0,0),ne=corner(grid,0,-1);
        float max=Math.max(Math.max(nw,sw),Math.max(se,ne));var faces=new ArrayList<Quad>();
        if(step<10||above==0)faces.add(new Quad(Face.UP,max,max,List.of(p(0,nw,0),p(0,sw,1),p(1,se,1),p(1,ne,0))));
        if(below<10)faces.add(new Quad(Face.DOWN,0,0,List.of(p(0,0,1),p(0,0,0),p(1,0,0),p(1,0,1))));
        if(grid[1]==0)faces.add(new Quad(Face.NORTH,0,max,List.of(p(1,0,0),p(0,0,0),p(0,nw,0),p(1,ne,0))));
        if(grid[7]==0)faces.add(new Quad(Face.SOUTH,0,max,List.of(p(0,0,1),p(1,0,1),p(1,se,1),p(0,sw,1))));
        if(grid[3]==0)faces.add(new Quad(Face.WEST,0,max,List.of(p(0,0,0),p(0,0,1),p(0,sw,1),p(0,nw,0))));
        if(grid[5]==0)faces.add(new Quad(Face.EAST,0,max,List.of(p(1,0,1),p(1,0,0),p(1,ne,0),p(1,se,1))));
        return List.copyOf(faces);
    }
    private static float corner(int[] grid,int x,int z){
        int[] touching={(z+1)*3+x+1,(z+1)*3+x+2,(z+2)*3+x+1,(z+2)*3+x+2};
        int connected=0;for(int i=0;i<4;i++)if(touching[i]==4)connected=1<<i;
        // Diagonal water separated by two dry/solid cells is a different surface. Only
        // average the face-connected component touching this vertex; both wet sides agree.
        for(int pass=0;pass<3;pass++)for(int i=0;i<4;i++)if(grid[touching[i]]>0&&((connected&(1<<(i^1)))!=0||(connected&(1<<(i^2)))!=0))connected|=1<<i;
        int total=0,count=0;for(int i=0;i<4;i++)if((connected&(1<<i))!=0){total+=grid[touching[i]];count++;}
        return total/(10F*count);
    }
    public static List<Quad> mesh(int step,int north,int south,int west,int east,int above,int below){
        for(int value:new int[]{step,north,south,west,east,above,below})if(value<0||value>10)throw new IllegalArgumentException("volume step");
        return smoothMesh(new int[]{0,north,0,west,step,east,0,south,0},above,below);
    }
    private static Point p(float x,float y,float z){return new Point(x,y,z);}
}

package dev.mtsgps.nav;

/** Stops at dismount/teleport; never joins recordings across gaps or dimensions. */
public final class RoadRecorder {
    private int lastNode=-1;
    private Position lastSample;
    private boolean active, bothWays;
    public boolean active() { return active; }
    public void start(boolean bothWays) { active=true; this.bothWays=bothWays; lastNode=-1; lastSample=null; }
    public void stop() { active=false; lastNode=-1; lastSample=null; }
    public boolean sample(RoadGraph graph,Position p,double spacing,double snap) {
        if(!active) return false;
        if(lastSample!=null && lastSample.distance(p)>24) { stop(); return false; }
        if(lastSample!=null && lastSample.distance(p)<spacing) return false;
        int node=graph.recordPoint(p,snap);
        if(lastNode>=0) graph.connect(lastNode,node,bothWays);
        lastNode=node; lastSample=p;
        return true;
    }
    public void junction(RoadGraph graph,Position p,double radius) {
        int node=graph.junction(p,radius);
        if(active && lastNode>=0 && lastSample.distance(p)<24) graph.connect(lastNode,node,bothWays);
        if(active) { lastNode=node; lastSample=p; }
    }
}

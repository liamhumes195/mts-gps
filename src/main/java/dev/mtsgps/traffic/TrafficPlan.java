package dev.mtsgps.traffic;

import dev.mtsgps.nav.Position;
import dev.mtsgps.nav.RoadGraph;
import java.util.*;

/** A directed road walk. It never invents edges, reverses at a dead end, or uses mob pathfinding. */
public final class TrafficPlan {
    public record Aim(Position position, double offRoad, double remaining, boolean atEnd) {}
    private final RoadGraph graph;
    private final Map<Integer,List<Integer>> outgoing=new HashMap<>();
    private final List<Integer> path=new ArrayList<>();
    private final Random random;
    private int segment;
    private boolean deadEnd;

    public TrafficPlan(RoadGraph graph,RoadGraph.Edge start,Random random) {
        if(!graph.edges().contains(start)) throw new IllegalArgumentException("Traffic must start on a recorded edge");
        this.graph=graph; this.random=random;
        for(var edge:graph.edges()) outgoing.computeIfAbsent(edge.from(),key->new ArrayList<>()).add(edge.to());
        path.add(start.from()); path.add(start.to()); extend();
    }
    private Position point(int index) { return graph.node(path.get(index)).position(); }
    private void extend() {
        double distance=0;
        for(int i=segment+1;i<path.size();i++) distance+=point(i-1).distance(point(i));
        while(!deadEnd && path.size()-segment<64 && distance<48) {
            int previous=path.get(path.size()-2),current=path.get(path.size()-1);
            var choices=new ArrayList<Integer>();
            for(int next:outgoing.getOrDefault(current,List.of())) if(next!=previous) choices.add(next);
            if(choices.isEmpty()) { deadEnd=true; break; }
            int next=choices.get(random.nextInt(choices.size()));
            distance+=graph.node(current).position().distance(graph.node(next).position());
            path.add(next);
        }
    }
    public Aim aim(Position car,double lookAhead) {
        for(int hops=0;hops<32 && segment+2<path.size();hops++) {
            Position a=point(segment),b=point(segment+1);
            var projection=RoadGraph.project(car,a,b);
            double along=(car.x()-b.x())*(b.x()-a.x())+(car.z()-b.z())*(b.z()-a.z());
            double nextDistance=RoadGraph.project(car,b,point(segment+2)).distance();
            if(car.horizontal(b)<1.3 || (along>=0 && (projection.distance()<4 || nextDistance<3))) segment++;
            else break;
        }
        if(segment>32) { path.subList(0,segment).clear(); segment=0; }
        extend();
        var projection=RoadGraph.project(car,point(segment),point(segment+1));
        Position cursor=projection.position(),target=cursor;
        double remaining=0,wanted=Math.max(.5,lookAhead);
        for(int i=segment+1;i<path.size();i++) {
            Position next=point(i);
            double length=cursor.distance(next);
            remaining+=length;
            if(wanted>0) {
                if(length>=wanted) { target=cursor.interpolate(next,wanted/Math.max(length,1e-9)); wanted=0; }
                else { wanted-=length; target=next; }
            }
            cursor=next;
        }
        return new Aim(target,projection.distance(),remaining,deadEnd && remaining<1.5);
    }
}

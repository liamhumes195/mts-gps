package dev.mtsgps.nav;

import java.util.*;

public final class Router {
    public record Route(List<Position> points, double distance, double startOffset, double endOffset, List<Guidance.Turn> turns) {}
    public record Result(Route route,String message) { public boolean found() { return route!=null; } }
    private record Candidate(int node,double score) {}

    public static Result find(RoadGraph original,Position start,Position destination,double attachRadius) {
        if(original.edges().isEmpty()) return new Result(null,"Record roads first");
        RoadGraph graph=original.copy();
        var startSnap=graph.nearest(start,attachRadius,4);
        if(startSnap==null) return new Result(null,"Car is too far from recorded roads");
        int begin=graph.attach(startSnap);
        // Snap AFTER splitting the start edge: both ends may be inside the same edge.
        var endSnap=graph.nearest(destination,attachRadius,4);
        if(endSnap==null) return new Result(null,"Destination is too far from recorded roads");
        int end=graph.attach(endSnap);
        Map<Integer,List<Integer>> adjacency=new HashMap<>();
        for(var e:graph.edges()) adjacency.computeIfAbsent(e.from(),key->new ArrayList<>()).add(e.to());
        Map<Integer,Double> cost=new HashMap<>();
        Map<Integer,Integer> previous=new HashMap<>();
        Set<Integer> closed=new HashSet<>();
        PriorityQueue<Candidate> queue=new PriorityQueue<>(Comparator.comparingDouble(Candidate::score));
        cost.put(begin,0d); queue.add(new Candidate(begin,0));
        while(!queue.isEmpty()) {
            int current=queue.remove().node;
            if(!closed.add(current)) continue;
            if(current==end) {
                LinkedList<Position> points=new LinkedList<>();
                for(int at=end;;at=previous.get(at)) {
                    points.addFirst(graph.node(at).position()); if(at==begin) break;
                }
                return new Result(new Route(List.copyOf(points),cost.get(end),startSnap.distance(),endSnap.distance(),Guidance.turns(points)),
                        "Route follows recorded roads");
            }
            for(int next:adjacency.getOrDefault(current,List.of())) {
                double nextCost=cost.get(current)+graph.node(current).position().distance(graph.node(next).position());
                if(nextCost<cost.getOrDefault(next,Double.POSITIVE_INFINITY)) {
                    cost.put(next,nextCost); previous.put(next,current);
                    double estimate=graph.node(next).position().distance(graph.node(end).position());
                    queue.add(new Candidate(next,nextCost+estimate));
                }
            }
        }
        return new Result(null,"No connected driving route - record or connect the junction");
    }
    private Router() {}
}

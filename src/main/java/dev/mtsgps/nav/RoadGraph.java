package dev.mtsgps.nav;

import java.util.*;

/** Directed, geometric road network. Every edge originates in a driven recording. */
public final class RoadGraph {
    public static final int MAX_NODES=50_000, MAX_EDGES=150_000;
    public record Node(int id, Position position) {}
    public record Edge(int from, int to) {}
    public record Projection(Position position, double fraction, double distance) {}
    public record Snap(int node, Edge edge, Position position, double distance) {}
    private final Map<Integer,Node> nodes = new LinkedHashMap<>();
    private final Set<Edge> edges = new LinkedHashSet<>();
    private int nextId;
    private long revision;

    public Collection<Node> nodes() { return Collections.unmodifiableCollection(nodes.values()); }
    public Set<Edge> edges() { return Collections.unmodifiableSet(edges); }
    public Node node(int id) { return nodes.get(id); }
    public long revision() { return revision; }

    public RoadGraph copy() {
        RoadGraph g=new RoadGraph();
        g.nodes.putAll(nodes); g.edges.addAll(edges); g.nextId=nextId; g.revision=revision;
        return g;
    }
    public static RoadGraph restore(Collection<Node> nodes, Collection<Edge> edges) {
        if(nodes==null || edges==null || nodes.size()>MAX_NODES || edges.size()>MAX_EDGES)
            throw new IllegalArgumentException("Road network exceeds limits or is incomplete");
        RoadGraph g=new RoadGraph();
        for(Node n:nodes) {
            if(n==null || n.id<0 || n.id>=Integer.MAX_VALUE || n.position==null || !n.position.valid()
                    || g.nodes.putIfAbsent(n.id,n)!=null) throw new IllegalArgumentException("Invalid road node");
            g.nextId=Math.max(g.nextId,n.id+1);
        }
        for(Edge e:edges) {
            if(e==null || e.from==e.to || !g.nodes.containsKey(e.from) || !g.nodes.containsKey(e.to))
                throw new IllegalArgumentException("Invalid road edge");
            g.edges.add(e);
        }
        return g;
    }
    public int add(Position p) {
        if(!p.valid()) throw new IllegalArgumentException("Invalid coordinates");
        if(nodes.size()>=MAX_NODES) throw new IllegalStateException("Road network node limit reached");
        int id=nextId++; nodes.put(id,new Node(id,p)); revision++; return id;
    }
    public void connect(int from,int to,boolean bothWays) {
        if(from==to) return;
        if(!nodes.containsKey(from)||!nodes.containsKey(to)) throw new IllegalArgumentException("Missing road node");
        if(edges.size()+2>MAX_EDGES) throw new IllegalStateException("Road network edge limit reached");
        if(edges.add(new Edge(from,to))) revision++;
        if(bothWays && edges.add(new Edge(to,from))) revision++;
    }
    public Snap nearest(Position p,double radius,double maxHeightDifference) {
        Snap best=null;
        for(Node n:nodes.values()) {
            double d=n.position.distance(p);
            if(d<=radius && Math.abs(n.position.y()-p.y())<=maxHeightDifference && (best==null||d<best.distance))
                best=new Snap(n.id,null,n.position,d);
        }
        for(Edge e:edges) {
            if(e.from>e.to && edges.contains(new Edge(e.to,e.from))) continue;
            Position a=nodes.get(e.from).position, b=nodes.get(e.to).position;
            Projection projection=project(p,a,b);
            double d=projection.distance;
            if(d<=radius && Math.abs(projection.position.y()-p.y())<=maxHeightDifference
                    && (best==null || d<best.distance-1e-6))
                best=new Snap(-1,e,projection.position,d);
        }
        return best;
    }
    public int attach(Snap snap) {
        if(snap.node>=0) return snap.node;
        Edge e=snap.edge;
        if(snap.position.distance(nodes.get(e.from).position)<.05) return e.from;
        if(snap.position.distance(nodes.get(e.to).position)<.05) return e.to;
        int id=add(snap.position);
        split(e,id);
        return id;
    }
    public int recordPoint(Position p,double radius) {
        Snap snap=nearest(p,radius,1.25);
        return snap==null ? add(p) : attach(snap);
    }
    /** Explicitly connects every same-height road passing within radius of the junction. */
    public int junction(Position p,double radius) {
        int id=recordPoint(p,radius);
        Position center=nodes.get(id).position;
        for(Edge e:new ArrayList<>(edges)) {
            if(e.from==id||e.to==id||!edges.contains(e)) continue;
            Projection projection=project(center,nodes.get(e.from).position,nodes.get(e.to).position);
            if(projection.distance<=radius && Math.abs(projection.position.y()-center.y())<=1.25)
                split(e,id);
        }
        return id;
    }
    private void split(Edge e,int id) {
        if(e.from==id || e.to==id || !edges.contains(e)) return;
        boolean reverse=edges.remove(new Edge(e.to,e.from));
        edges.remove(e); revision++;
        connect(e.from,id,false); connect(id,e.to,false);
        if(reverse) { connect(e.to,id,false); connect(id,e.from,false); }
    }
    public static Projection project(Position p,Position a,Position b) {
        double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z();
        double length=dx*dx+dy*dy+dz*dz;
        double t=length<1e-9 ? 0 : Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.y()-a.y())*dy+(p.z()-a.z())*dz)/length));
        Position on=a.interpolate(b,t);
        return new Projection(on,t,p.distance(on));
    }
}

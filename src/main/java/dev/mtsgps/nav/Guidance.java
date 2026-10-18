package dev.mtsgps.nav;

import java.util.List;
import java.util.ArrayList;

public final class Guidance {
    public record Instruction(String turn,double distance,int vertex,double remaining,double offRoute) {}
    public record Turn(int vertex,String instruction) {}
    /** Combine consecutive bends into one stable maneuver, including smoothly recorded corners. */
    public static List<Turn> turns(List<Position> path) {
        var result=new ArrayList<Turn>();
        var vertices=new ArrayList<Integer>();
        var angles=new ArrayList<Double>();
        double sum=0;
        int last=-1;
        for(int i=1;i<path.size()-1;i++) {
            Position a=path.get(i-1),b=path.get(i),c=path.get(i+1);
            double ax=b.x()-a.x(),az=b.z()-a.z(),bx=c.x()-b.x(),bz=c.z()-b.z();
            double angle=Math.toDegrees(Math.atan2(ax*bz-az*bx,ax*bx+az*bz));
            boolean significant=Math.abs(angle)>2.5;
            if(!vertices.isEmpty() && ((significant && Math.signum(angle)!=Math.signum(sum))
                    || (!significant && path.get(i).distance(path.get(last))>6))) {
                addTurn(result,vertices,angles,sum); vertices.clear(); angles.clear(); sum=0;
            }
            if(significant) { vertices.add(i); angles.add(angle); sum+=angle; last=i; }
        }
        addTurn(result,vertices,angles,sum);
        return List.copyOf(result);
    }
    private static void addTurn(List<Turn> turns,List<Integer> vertices,List<Double> angles,double total) {
        if(Math.abs(total)<28 || vertices.isEmpty()) return;
        double accumulated=0;
        int center=vertices.get(0);
        for(int i=0;i<vertices.size();i++) {
            accumulated+=angles.get(i); center=vertices.get(i);
            if(Math.abs(accumulated)>=Math.abs(total)/2) break;
        }
        turns.add(new Turn(center,Math.abs(total)>150?"U-TURN":total>0?"TURN RIGHT":"TURN LEFT"));
    }
    public static Instruction at(Router.Route route,Position current,int fromSegment) {
        return at(route.points(),route.turns(),current,fromSegment);
    }
    public static Instruction at(List<Position> path,Position current,int fromSegment) {
        return at(path,turns(path),current,fromSegment);
    }
    private static Instruction at(List<Position> path,List<Turn> turns,Position current,int fromSegment) {
        if(path.isEmpty()) return new Instruction("NO ROUTE",0,0,0,Double.POSITIVE_INFINITY);
        if(path.size()==1) return new Instruction("ROAD END",0,0,0,current.distance(path.get(0)));
        int segment=Math.min(fromSegment,path.size()-2);
        RoadGraph.Projection best=RoadGraph.project(current,path.get(segment),path.get(segment+1));
        // Only a short consecutive window; a later crossing must not skip a whole route loop.
        for(int i=segment+1;i<Math.min(segment+4,path.size()-1);i++) {
            var projection=RoadGraph.project(current,path.get(i),path.get(i+1));
            if(projection.distance()+.1<best.distance()) { segment=i; best=projection; }
        }
        double distance=best.position().distance(path.get(segment+1));
        double remaining=distance;
        for(int i=segment+1;i<path.size()-1;i++) remaining+=path.get(i).distance(path.get(i+1));
        for(Turn turn:turns) {
            if(turn.vertex<=segment) continue;
            double toTurn=distance;
            for(int i=segment+1;i<turn.vertex;i++) toTurn+=path.get(i).distance(path.get(i+1));
            return new Instruction(turn.instruction,toTurn,turn.vertex,remaining,best.distance());
        }
        return new Instruction("ROAD END",remaining,path.size()-1,remaining,best.distance());
    }
    public static int segment(List<Position> path,Position current,int fromSegment) {
        int best=Math.min(fromSegment,Math.max(0,path.size()-2));
        if(path.size()<2) return best;
        double distance=RoadGraph.project(current,path.get(best),path.get(best+1)).distance();
        for(int i=best+1;i<Math.min(fromSegment+4,path.size()-1);i++) {
            double candidate=RoadGraph.project(current,path.get(i),path.get(i+1)).distance();
            if(candidate+.1<distance) { distance=candidate; best=i; }
        }
        return best;
    }
    private Guidance() {}
}

package dev.mtsgps;

import dev.mtsgps.nav.*;
import dev.mtsgps.client.MtsBridge;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.baseclasses.TransformationMatrix;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Deterministic behavioral tests. These do not launch or claim to test Minecraft. */
public final class NavigationTests {
    private static int checks;
    private static Position p(double x,double z) { return new Position(x,0,z); }
    private static void check(boolean condition,String description) {
        if(!condition) throw new AssertionError(description); checks++;
    }
    private static void near(double actual,double expected,String description) { check(Math.abs(actual-expected)<.001,description+": "+actual+" vs "+expected); }
    private static RoadGraph line(boolean bothWays) {
        var g=new RoadGraph(); int a=g.add(p(0,0)),b=g.add(p(100,0)); g.connect(a,b,bothWays); return g;
    }
    public static void main(String[] args) throws Exception {
        check(!Router.find(new RoadGraph(),p(0,0),p(100,0),10).found(),"No walking route fallback");
        RoadGraph g=line(true);
        var result=Router.find(g,p(20,0),p(80,0),10);
        check(result.found(),"Interior-to-interior route"); near(result.route().distance(),60,"Same edge is split correctly");
        check(g.nodes().size()==2&&g.edges().size()==2,"Routing leaves recorded graph unchanged");
        near(Router.find(g,p(80,0),p(20,0),10).route().distance(),60,"Two-way reverse route");
        check(!Router.find(line(false),p(80,0),p(20,0),10).found(),"One-way restrictions survive splits");
        check(Router.find(line(false),p(20,0),p(80,0),10).found(),"One-way forward route");
        check(!Router.find(g,p(20,40),p(80,0),10).found(),"Reject far-away start");
        check(!Router.find(g,p(20,0),p(80,40),10).found(),"Reject far-away destination");
        near(Router.find(g,p(20,3),p(80,4),10).route().endOffset(),4,"Report unrecorded destination offset");

        var disconnected=new RoadGraph();
        int a=disconnected.add(p(-20,0)),b=disconnected.add(p(20,0)); disconnected.connect(a,b,true);
        int c=disconnected.add(p(0,-20)),d=disconnected.add(p(0,20)); disconnected.connect(c,d,true);
        check(!Router.find(disconnected,p(-18,0),p(0,18),3).found(),"Crossing geometry alone is not an invented junction");
        disconnected.junction(p(0,0),2);
        var joined=Router.find(disconnected,p(-18,0),p(0,18),3);
        check(joined.found(),"Explicit junction connects both road interiors"); near(joined.route().distance(),36,"Junction road distance");
        int nodes=disconnected.nodes().size(); disconnected.junction(p(0,0),2);
        check(disconnected.nodes().size()==nodes,"Repeated junction operation is stable");

        var bridge=new RoadGraph();
        int groundA=bridge.add(p(-20,0)),groundB=bridge.add(p(20,0)); bridge.connect(groundA,groundB,true);
        int upperA=bridge.add(new Position(0,8,-20)),upperB=bridge.add(new Position(0,8,20)); bridge.connect(upperA,upperB,true);
        bridge.junction(p(0,0),3);
        check(!Router.find(bridge,p(-18,0),new Position(0,8,18),3).found(),"Intersection does not join bridge decks");
        check(bridge.nearest(new Position(0,4,0),3,1.25)==null,"Recording snap respects height separation");

        var detour=new RoadGraph(); int n0=detour.add(p(0,0)),n1=detour.add(p(0,30)),n2=detour.add(p(30,30)),n3=detour.add(p(30,0));
        detour.connect(n0,n1,true); detour.connect(n1,n2,true); detour.connect(n2,n3,true);
        near(Router.find(detour,p(0,0),p(30,0),2).route().distance(),90,"Road detour is used instead of direct line");
        detour.connect(n0,n3,false);
        near(Router.find(detour,p(0,0),p(30,0),2).route().distance(),30,"A* chooses shortest permitted road");
        near(Router.find(detour,p(30,0),p(0,0),2).route().distance(),90,"A* respects directed shortcut");

        var recorder=new RoadRecorder(); var recorded=new RoadGraph(); recorder.start(true);
        recorder.sample(recorded,p(0,0),3,1);
        check(!recorder.sample(recorded,p(1,0),3,1),"Stationary/small movement does not create edges");
        recorder.sample(recorded,p(3,0),3,1);
        check(recorded.nodes().size()==2&&recorded.edges().size()==2,"Two-way drive creates both directions");
        recorder.sample(recorded,p(100,0),3,1);
        check(!recorder.active()&&recorded.edges().size()==2,"Teleport stops recording without drawing a false road");
        recorder.start(false); recorder.sample(recorded,p(100,0),3,1); recorder.sample(recorded,p(103,0),3,1);
        check(recorded.edges().size()==3,"New recording does not bridge the previous recording gap");
        check(!Router.find(recorded,p(100,0),p(3,0),1).found(),"Separate recording remains disconnected");
        var auto=line(true); recorder.start(true); recorder.sample(auto,p(50,-6),3,2); recorder.sample(auto,p(50,-3),3,2); recorder.sample(auto,p(50,0),3,2);
        check(Router.find(auto,p(50,-6),p(80,0),2).found(),"Driving onto an existing road splits and connects it");

        var right=List.of(p(0,20),p(0,0),p(20,0));
        var instruction=Guidance.at(right,p(0,15),0);
        check(instruction.turn().equals("TURN RIGHT"),"North-to-east turn is right"); near(instruction.distance(),15,"Next turn distance follows road");
        check(Guidance.at(List.of(p(0,0),p(0,20),p(20,20)),p(0,5),0).turn().equals("TURN LEFT"),"South-to-east turn is left");
        check(Guidance.at(List.of(p(0,0),p(0,20),p(0,0)),p(0,5),0).turn().equals("U-TURN"),"U-turn detection");
        check(Guidance.segment(right,p(6,0),0)==1,"Progress moves past the completed turn");
        check(Guidance.at(right,p(-20,0),0).offRoute()>10,"Missed turn is detected by road distance");
        var curve=new ArrayList<Position>(); curve.add(p(0,-10));
        for(int i=0;i<=18;i++) { double angle=Math.PI/2*i/18; curve.add(p(20-20*Math.cos(angle),20*Math.sin(angle))); }
        curve.add(p(30,20));
        check(Guidance.turns(curve).size()==1&&Guidance.turns(curve).get(0).instruction().equals("TURN LEFT"),"Smoothly driven corner produces one stable turn instruction");

        var missed=new RoadGraph(); int south=missed.add(p(0,30)),cross=missed.add(p(0,0)),north=missed.add(p(0,-30)),east=missed.add(p(30,0));
        missed.connect(south,cross,true);missed.connect(cross,north,true);missed.connect(cross,east,true);
        var oldRoute=Router.find(missed,p(0,25),p(30,0),2).route();
        check(Guidance.at(oldRoute,p(0,-20),0).offRoute()>10,"Driving straight past planned turn exceeds recalc threshold");
        var recalculated=Router.find(missed,p(0,-20),p(30,0),2);
        check(recalculated.found(),"Missed-turn recalculation finds connected road back");near(recalculated.route().distance(),50,"Recalculation uses actual current road location");

        // Test the real MTS-to-JOML matrix convention used by the dashboard attachment.
        var rotation=new RotationMatrix().rotateY(37).rotateX(12).rotateZ(-5);
        var mtsPoint=new Point3D(.4,.8,2).rotate(rotation);
        var jomlPoint=MtsBridge.rotation(rotation).transformPosition(new Vector3f(.4f,.8f,2));
        near(jomlPoint.x,mtsPoint.x,"MTS/JOML rotation X");near(jomlPoint.y,mtsPoint.y,"MTS/JOML rotation Y");near(jomlPoint.z,mtsPoint.z,"MTS/JOML rotation Z");
        var transform=new TransformationMatrix().applyTranslation(4,5,6).applyRotation(rotation).applyScaling(2,3,4);
        mtsPoint=new Point3D(.4,.8,2).transform(transform);
        jomlPoint=MtsBridge.transform(transform).transformPosition(new Vector3f(.4f,.8f,2));
        near(jomlPoint.x,mtsPoint.x,"MTS body matrix X");near(jomlPoint.y,mtsPoint.y,"MTS body matrix Y");near(jomlPoint.z,mtsPoint.z,"MTS body matrix Z");

        Path folder=Path.of("build/test-gps"); Files.createDirectories(folder);
        Path file=folder.resolve("roundtrip-"+UUID.randomUUID()+".json");
        var destinations=Map.of("Home / café",p(30,0));
        GpsStore.save(file,missed,destinations); var loaded=GpsStore.load(file);
        check(loaded.graph().edges().equals(missed.edges()),"Directed graph persists exactly");
        check(loaded.destinations().equals(destinations),"Destination names and coordinates persist");
        GpsStore.save(file,missed,destinations); Files.writeString(file,"{corrupt");
        check(GpsStore.load(file).recovered(),"Corrupt primary data recovers validated backup");
        Path invalid=folder.resolve("invalid-"+UUID.randomUUID()+".json"); Files.writeString(invalid,"{\"format\":999}");
        boolean rejected=false; try { GpsStore.load(invalid); } catch(java.io.IOException expected) { rejected=true; }
        check(rejected&&Files.readString(invalid).contains("999"),"Unsupported data fails without overwriting user's file");
        Files.writeString(invalid,"{}"); rejected=false;
        try { GpsStore.load(invalid); } catch(java.io.IOException expected) { rejected=true; }
        check(rejected,"Incomplete JSON cannot silently erase the road network");
        Files.delete(file);
        check(GpsStore.load(file).recovered(),"Missing primary file recovers validated backup");
        rejected=false;try {RoadGraph.restore(List.of(new RoadGraph.Node(1,p(0,0))),List.of(new RoadGraph.Edge(1,99)));} catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Corrupt edge references rejected");
        rejected=false;try {RoadGraph.restore(List.of(new RoadGraph.Node(1,new Position(Double.NaN,0,0))),List.of());} catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Non-finite coordinates rejected");
        System.out.println("MTS GPS: "+checks+" behavioral checks passed (no Minecraft runtime test).");
    }
}

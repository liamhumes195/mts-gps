package dev.mtsgps;

import dev.mtsgps.nav.Position;
import dev.mtsgps.nav.RoadGraph;
import dev.mtsgps.traffic.*;
import mcinterface1201.WrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.Random;

@GameTestHolder(MtsGps.ID)
@PrefixGameTestTemplate(false)
public final class TrafficGameTests {
    private static boolean originalMobSpawning;
    @BeforeBatch(batch="traffic") public static void before(ServerLevel level) {
        originalMobSpawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
    }
    @AfterBatch(batch="traffic") public static void after(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(originalMobSpawning,level.getServer());
    }
    @GameTest(template="traffic_empty",timeoutTicks=500,batch="traffic")
    public static void mustangDrivesRecordedCorner(GameTestHelper helper) { driveCorner(helper,0,"fordmustang69"); }
    @GameTest(template="traffic_empty",timeoutTicks=500,batch="traffic")
    public static void mercedesDrivesRecordedCorner(GameTestHelper helper) { driveCorner(helper,2,"merc230"); }
    @GameTest(template="traffic_empty",timeoutTicks=500,batch="traffic")
    public static void scoutDrivesRecordedCorner(GameTestHelper helper) { driveCorner(helper,3,"scout"); }
    private static void driveCorner(GameTestHelper helper,int seed,String expectedModel) {
        var level=helper.getLevel();
        helper.killAllEntities();
        for(int x=0;x<48;x++) for(int z=0;z<48;z++) helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var origin=helper.absolutePos(new BlockPos(12,1,8));
        var graph=new RoadGraph();
        double y=origin.getY()+.45;
        Position start=new Position(origin.getX()+.5,y,origin.getZ()+.5);
        int first=graph.add(start),previous=first;
        for(int i=1;i<=5;i++) { int node=graph.add(new Position(start.x(),y,start.z()+i*2)); graph.connect(previous,node,false); previous=node; }
        for(int i=1;i<=8;i++) {
            double angle=Math.PI/2*i/8;
            int node=graph.add(new Position(start.x()+8-8*Math.cos(angle),y,start.z()+10+8*Math.sin(angle)));
            graph.connect(previous,node,false); previous=node;
        }
        for(int i=1;i<=6;i++) { int node=graph.add(new Position(start.x()+8+i*2,y,start.z()+18)); graph.connect(previous,node,false); previous=node; }
        var player=FakePlayerFactory.getMinecraft(level);
        var away=helper.absolutePos(new BlockPos(1,1,1)); player.moveTo(away.getX(),away.getY(),away.getZ(),0,0);
        TrafficVehicles.Spawned spawned;
        try { spawned=TrafficVehicles.spawn(level,WrapperPlayer.getWrapperFor(player),start,0,new Random(seed)); }
        catch(RuntimeException failure) { MtsGps.LOGGER.error("Traffic test assembly failed",failure); throw failure; }
        helper.assertTrue(spawned.car().definition.systemName.equals(expectedModel),"The fixture must exercise the intended randomly selected car model");
        var saved=spawned.car().save(InterfaceManager.coreInterface.getNewNBTWrapper());
        helper.assertTrue(saved.getStrings("variables").contains(TrafficVehicles.MARKER) && saved.getDouble(TrafficVehicles.MARKER)==1,"The traffic cleanup marker must survive MTS NBT serialization");
        // This fixture drives the production controller directly without a client traffic session.
        spawned.car().getOrCreateVariable(TrafficVehicles.MARKER).setTo(0,false);
        spawned.driver().removeTag(TrafficVehicles.DRIVER_TAG);
        var plan=new TrafficPlan(graph,new RoadGraph.Edge(first,first+1),new Random(2));
        int[] ticks={0},stoppedTicks={0}; double[] furthest={0};
        Villager[] obstacle={null}; boolean[] obstaclePlaced={false},obstacleChecked={seed!=2};
        helper.onEachTick(()->{
            try {
                ticks[0]++;
                helper.assertTrue(spawned.car().isValid,"The physical MTS car must remain valid");
                helper.assertTrue(spawned.seat().rider!=null && spawned.seat().rider.getID().equals(spawned.driver().getUUID()),"A real villager must remain in the controller seat");
                TrafficManager.drive(level,spawned,plan);
                Position current=new Position(spawned.car().position.x,spawned.car().position.y,spawned.car().position.z);
                furthest[0]=Math.max(furthest[0],current.horizontal(start));
                double speed=Math.hypot(spawned.car().motion.x,spawned.car().motion.z)*spawned.car().speedFactor*20;
                if(seed==2 && !obstaclePlaced[0] && furthest[0]>3) {
                    obstaclePlaced[0]=true;
                    obstacle[0]=EntityType.VILLAGER.create(level);
                    obstacle[0].setNoAi(true); obstacle[0].setInvulnerable(true);
                    obstacle[0].moveTo(current.x()+spawned.car().orientation.m02*9,current.y(),current.z()+spawned.car().orientation.m22*9,0,0);
                    level.addFreshEntity(obstacle[0]);
                }
                if(obstacle[0]!=null && speed<.2) {
                    helper.assertTrue(spawned.car().brakeVar.currentValue>.5,"A living obstacle must cause braking");
                    helper.assertTrue(current.horizontal(new Position(obstacle[0].getX(),obstacle[0].getY(),obstacle[0].getZ()))>2,"The car must stop before hitting the obstacle");
                    if(++stoppedTicks[0]>=20) { obstacle[0].discard(); obstacle[0]=null; obstacleChecked[0]=true; }
                }
                if(ticks[0]%40==0) MtsGps.LOGGER.info("Traffic integration tick {}: model={}, position={}, travel={}, throttle={}, brake={}, rudder={}, enginesRunning={}, aim={}, obstacle={}, heading={}, gear={}, rpm={}",ticks[0],spawned.car().definition.systemName,current,furthest[0],spawned.car().throttleVar.currentValue,spawned.car().brakeVar.currentValue,spawned.car().rudderInputVar.currentValue,spawned.car().enginesRunning,plan.aim(current,5),TrafficManager.obstacleReason(level,spawned,Math.hypot(spawned.car().motion.x,spawned.car().motion.z)*spawned.car().speedFactor*20),spawned.car().orientation.angles.y,spawned.car().engines.get(0).currentGearVar.currentValue,spawned.car().engines.get(0).rpm);
                if(ticks[0]>80) helper.assertTrue(graph.nearest(current,4,3)!=null,"The car must stay within four blocks of the recorded road");
                if(current.x()>start.x()+18 && current.z()>start.z()+14 && speed<.25) {
                    helper.assertTrue(furthest[0]>15,"The engine-driven car must travel through the corner");
                    helper.assertTrue(plan.aim(current,4).atEnd(),"The car must brake at the recorded road end");
                    helper.assertTrue(obstacleChecked[0],"The Mercedes must stop for a real mob and resume after it leaves");
                    TrafficVehicles.remove(spawned); helper.succeed();
                }
                if(ticks[0]>=480) {
                    if(obstacle[0]!=null) obstacle[0].discard();
                    TrafficVehicles.remove(spawned); helper.fail("Traffic did not complete the recorded turn; maximum travel="+furthest[0]);
                }
            } catch(RuntimeException failure) {
                if(obstacle[0]!=null) obstacle[0].discard();
                TrafficVehicles.remove(spawned); throw failure;
            }
        });
    }
    private TrafficGameTests() {}
}

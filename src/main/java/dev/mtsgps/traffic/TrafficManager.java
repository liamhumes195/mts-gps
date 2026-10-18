package dev.mtsgps.traffic;

import dev.mtsgps.MtsGps;
import dev.mtsgps.nav.Position;
import dev.mtsgps.nav.RoadGraph;
import mcinterface1201.WrapperPlayer;
import mcinterface1201.WrapperWorld;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.entities.instances.PartSeat;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid=MtsGps.ID)
public final class TrafficManager {
    private static final Map<ResourceKey<Level>,Fleet> FLEETS=new HashMap<>();
    private static final Map<UUID,Integer> REQUEST_TIMES=new HashMap<>();
    private static final class Session {
        final UUID owner; final int count; final RoadGraph graph; final List<RoadGraph.Edge> edges; final Random random=new Random();
        long retry,warned;
        Session(UUID owner,int count,RoadGraph graph) { this.owner=owner; this.count=count; this.graph=graph.copy(); this.edges=new ArrayList<>(this.graph.edges()); }
    }
    private record Fleet(Map<UUID,Session> sessions,Map<UUID,Driver> cars) {
        Fleet() { this(new LinkedHashMap<>(),new LinkedHashMap<>()); }
    }
    private static final class Driver {
        final UUID owner; final TrafficVehicles.Spawned spawned; final TrafficPlan plan;
        Position last; int stuck,end,offRoad,age;
        Driver(UUID owner,TrafficVehicles.Spawned spawned,TrafficPlan plan) {
            this.owner=owner; this.spawned=spawned; this.plan=plan; last=position(spawned.car());
        }
    }
    public static void request(ServerPlayer player,int count,RoadGraph graph) {
        var level=player.serverLevel();
        var fleet=FLEETS.computeIfAbsent(level.dimension(),key->new Fleet());
        if(count==0) { stop(fleet,player.getUUID()); tell(player,"Your traffic cars were removed"); return; }
        if(!TrafficConfig.ENABLED.get()) { tell(player,"Traffic is disabled in the server config"); return; }
        if(!player.hasPermissions(2) && !player.server.isSingleplayerOwner(player.getGameProfile())) {
            tell(player,"Starting traffic requires the single-player owner or operator permission level 2"); return;
        }
        int now=player.server.getTickCount();
        Integer previous=REQUEST_TIMES.get(player.getUUID());
        if(previous!=null && now-previous<40) { tell(player,"Wait two seconds before starting traffic again"); return; }
        if(count<1 || count>8 || graph==null || graph.edges().isEmpty()) { tell(player,"Record roads before starting traffic"); return; }
        if(graph.nodes().size()>TrafficNetwork.MAX_NODES || graph.edges().size()>TrafficNetwork.MAX_EDGES) return;
        REQUEST_TIMES.put(player.getUUID(),now);
        stop(fleet,player.getUUID());
        fleet.sessions().put(player.getUUID(),new Session(player.getUUID(),Math.min(count,TrafficConfig.MAX_CARS.get()),graph));
        tell(player,"Traffic enabled: up to "+Math.min(count,TrafficConfig.MAX_CARS.get())+" villager drivers. Cars need clear, loaded recorded roads 18-64 blocks away");
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        var server=ServerLifecycleHooks.getCurrentServer(); if(server==null) return;
        for(var level:server.getAllLevels()) {
            var fleet=FLEETS.get(level.dimension());
            if(fleet!=null) update(level,fleet);
            if(server.getTickCount()%40==0) cleanupOrphans(level,fleet);
        }
    }
    private static void update(ServerLevel level,Fleet fleet) {
        for(UUID owner:new ArrayList<>(fleet.sessions().keySet())) {
            var player=level.getServer().getPlayerList().getPlayer(owner);
            if(player==null || player.serverLevel()!=level || !TrafficConfig.ENABLED.get()) stop(fleet,owner);
        }
        for(var iterator=fleet.cars().values().iterator();iterator.hasNext();) {
            var driver=iterator.next(); var car=driver.spawned.car();
            var player=level.getServer().getPlayerList().getPlayer(driver.owner);
            boolean keep=false;
            try {
                keep=player!=null && car.isValid && driver.spawned.driver().isAlive()
                        && player.distanceToSqr(car.position.x,car.position.y,car.position.z)<112*112
                        && level.hasChunkAt(BlockPos.containing(car.position.x,car.position.y,car.position.z))
                        && drive(level,driver);
            } catch(RuntimeException failure) { MtsGps.LOGGER.error("Traffic car stopped after a control error",failure); }
            if(!keep) { TrafficVehicles.remove(driver.spawned); iterator.remove(); }
        }
        for(var session:fleet.sessions().values()) {
            long owned=fleet.cars().values().stream().filter(driver->driver.owner.equals(session.owner)).count();
            if(owned>=session.count || fleet.cars().size()>=TrafficConfig.MAX_CARS.get() || level.getGameTime()<session.retry) continue;
            session.retry=level.getGameTime()+40;
            var player=level.getServer().getPlayerList().getPlayer(session.owner);
            if(player==null) continue;
            try {
                if(!spawn(level,player,session,fleet) && level.getGameTime()-session.warned>240) {
                    tell(player,"No clear loaded road for traffic nearby. Record a longer road and move away from the spawn section"); session.warned=level.getGameTime();
                }
            } catch(RuntimeException failure) {
                session.retry=level.getGameTime()+400;
                MtsGps.LOGGER.error("Could not assemble an Official Pack traffic car",failure);
                tell(player,"A traffic car could not be assembled; see latest.log");
            }
        }
    }
    private static boolean spawn(ServerLevel level,ServerPlayer player,Session session,Fleet fleet) {
        var world=WrapperWorld.getWrapperFor(level);
        for(int attempt=0;attempt<40;attempt++) {
            var edge=session.edges.get(session.random.nextInt(session.edges.size()));
            Position at=session.graph.node(edge.from()).position(),to=session.graph.node(edge.to()).position();
            double distance=player.distanceToSqr(at.x(),at.y(),at.z());
            if(distance<18*18 || distance>64*64 || at.horizontal(to)<.1 || !level.getWorldBorder().isWithinBounds(BlockPos.containing(at.x(),at.y(),at.z()))) continue;
            BlockPos min=BlockPos.containing(at.x()-7,at.y()-2,at.z()-7),max=BlockPos.containing(at.x()+7,at.y()+4,at.z()+7);
            if(!level.hasChunksAt(min,max)) continue;
            var plan=new TrafficPlan(session.graph,edge,session.random);
            if(plan.aim(at,5).remaining()<14) continue;
            boolean occupied=false;
            for(var other:world.getEntitiesOfType(EntityVehicleF_Physics.class))
                if(other.isValid && Math.abs(other.position.y-at.y())<3 && Math.hypot(other.position.x-at.x(),other.position.z-at.z())<11) { occupied=true; break; }
            if(occupied || !level.getEntitiesOfClass(LivingEntity.class,new AABB(at.x()-8,at.y()-2,at.z()-8,at.x()+8,at.y()+4,at.z()+8)).isEmpty()) continue;
            AABB clearance=new AABB(at.x()-2.5,at.y()+.75,at.z()-2.5,at.x()+2.5,at.y()+3,at.z()+2.5);
            if(level.getBlockCollisions(null,clearance).iterator().hasNext()) continue;
            var spawned=TrafficVehicles.spawn(level,WrapperPlayer.getWrapperFor(player),at,Math.toDegrees(Math.atan2(to.x()-at.x(),to.z()-at.z())),session.random);
            fleet.cars().put(spawned.car().uniqueUUID,new Driver(session.owner,spawned,plan));
            return true;
        }
        return false;
    }
    /** Exposed for the Forge server integration test; contains the same controls used by normal traffic. */
    public static boolean drive(ServerLevel level,TrafficVehicles.Spawned spawned,TrafficPlan plan) {
        return applyControls(level,spawned,plan);
    }
    private static boolean drive(ServerLevel level,Driver driver) {
        var car=driver.spawned.car(); Position current=position(car);
        for(var part:car.allParts) if(part instanceof PartSeat seat && seat.rider instanceof IWrapperPlayer) {
            TrafficVehicles.set(car.throttleVar,0); TrafficVehicles.set(car.brakeVar,1); return true;
        }
        if(driver.spawned.seat().rider==null || !driver.spawned.seat().rider.getID().equals(driver.spawned.driver().getUUID())) return false;
        var aim=driver.plan.aim(current,5);
        driver.end=aim.atEnd()?driver.end+1:0; driver.offRoad=aim.offRoad()>6?driver.offRoad+1:0;
        driver.stuck=current.horizontal(driver.last)<.01?driver.stuck+1:0; driver.last=current; driver.age++;
        return driver.end<80 && driver.offRoad<60 && driver.stuck<600 && driver.age<6000 && applyControls(level,driver.spawned,driver.plan);
    }
    private static boolean applyControls(ServerLevel level,TrafficVehicles.Spawned spawned,TrafficPlan plan) {
        var car=spawned.car(); if(!car.isValid || !spawned.driver().isAlive()) return false;
        car.isCreative=true; car.electricPower=Math.max(12,car.electricPower);
        TrafficVehicles.set(car.parkingBrakeVar,0);
        for(var engine:car.engines) if(!engine.running && car.ticksExisted%60==0) engine.autoStartEngine();
        Position current=position(car);
        double speed=Math.hypot(car.motion.x,car.motion.z)*car.speedFactor*20;
        var aim=plan.aim(current,Math.max(3.5,Math.min(8,3+speed*.7)));
        boolean blocked=obstacleReason(level,spawned,speed)!=null;
        spawned.braking().observe(blocked,speed);
        var input=TrafficControl.drive(current,car.orientation.m02,car.orientation.m22,speed,aim,spawned.wheelbase(),TrafficConfig.SPEED.get(),blocked);
        TrafficVehicles.set(car.rudderInputVar,input.rudder()*EntityVehicleF_Physics.MAX_RUDDER_ANGLE);
        TrafficVehicles.set(car.leftTurnLightVar,input.rudder()<-.15?1:0);
        TrafficVehicles.set(car.rightTurnLightVar,input.rudder()>.15?1:0);
        TrafficVehicles.set(car.throttleVar,input.throttle()); TrafficVehicles.set(car.brakeVar,input.brake());
        return true;
    }
    public static String obstacleReason(ServerLevel level,TrafficVehicles.Spawned spawned,double speed) {
        var car=spawned.car(); Position current=position(car);
        double hx=car.orientation.m02,hz=car.orientation.m22,range=spawned.braking().range(speed);
        for(var other:car.world.getEntitiesOfType(EntityVehicleF_Physics.class))
            if(other!=car && other.isValid && TrafficControl.ahead(current,hx,hz,position(other),range,2.8)) return "vehicle "+other.definition.systemName+" at "+position(other);
        var area=new AABB(current.x()-range,current.y()-2,current.z()-range,current.x()+range,current.y()+4,current.z()+range);
        for(var entity:level.getEntitiesOfClass(LivingEntity.class,area))
            if(entity!=spawned.driver() && TrafficControl.ahead(current,hx,hz,new Position(entity.getX(),entity.getY(),entity.getZ()),range,2)) return "living entity "+entity.getType()+" at "+entity.position();
        Vec3 from=new Vec3(current.x(),current.y()+.9,current.z());
        Vec3 to=from.add(hx*range,0,hz*range);
        var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,spawned.driver()));
        return hit.getType()==HitResult.Type.MISS?null:"block or fluid at "+hit.getBlockPos();
    }
    private static Position position(EntityVehicleF_Physics car) { return new Position(car.position.x,car.position.y,car.position.z); }
    private static void stop(Fleet fleet,UUID owner) {
        fleet.sessions().remove(owner);
        fleet.cars().values().removeIf(driver->{ if(!driver.owner.equals(owner)) return false; TrafficVehicles.remove(driver.spawned); return true; });
    }
    private static void cleanupOrphans(ServerLevel level,Fleet fleet) {
        var activeCars=fleet==null?Set.<UUID>of():fleet.cars().keySet();
        var activeDrivers=new HashSet<UUID>();
        if(fleet!=null) for(var driver:fleet.cars().values()) activeDrivers.add(driver.spawned.driver().getUUID());
        for(var car:WrapperWorld.getWrapperFor(level).getEntitiesOfType(EntityVehicleF_Physics.class))
            if(car.isValid && car.containsVariable(TrafficVehicles.MARKER) && car.getOrCreateVariable(TrafficVehicles.MARKER).isActive && !activeCars.contains(car.uniqueUUID)) car.remove();
        for(var entity:level.getAllEntities())
            if(entity instanceof Villager && entity.getTags().contains(TrafficVehicles.DRIVER_TAG) && !activeDrivers.contains(entity.getUUID())) entity.discard();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity().level().isClientSide) return;
        for(var fleet:FLEETS.values()) stop(fleet,event.getEntity().getUUID());
        REQUEST_TIMES.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for(var fleet:FLEETS.values()) for(var driver:fleet.cars().values()) TrafficVehicles.remove(driver.spawned);
        FLEETS.clear(); REQUEST_TIMES.clear();
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("mtsgpstraffic").requires(source->source.hasPermission(2))
                .then(literal("clear").executes(context->{
                    for(var fleet:FLEETS.values()) for(UUID owner:new ArrayList<>(fleet.sessions().keySet())) stop(fleet,owner);
                    context.getSource().sendSuccess(()->Component.literal("MTS GPS: All active traffic cleared"),false); return 1;
                })));
    }
    private static void tell(ServerPlayer player,String message) { player.sendSystemMessage(Component.literal("MTS GPS: "+message)); }
    private TrafficManager() {}
}

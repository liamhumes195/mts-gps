package dev.mtsgps.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.mtsgps.GpsConfig;
import dev.mtsgps.MtsGps;
import dev.mtsgps.nav.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static net.minecraft.commands.Commands.*;

@Mod.EventBusSubscriber(modid=MtsGps.ID,value=Dist.CLIENT)
public final class GpsClient {
    public static final GpsClient INSTANCE=new GpsClient();
    public static final KeyMapping OPEN=new KeyMapping("key.mtsgps.open",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.mtsgps");
    public RoadGraph graph=new RoadGraph();
    public final Map<String,Position> destinations=new TreeMap<>();
    public final RoadRecorder recorder=new RoadRecorder();
    public Position position=new Position(0,0,0), picked;
    public double headingX,headingZ=1;
    public Router.Route route;
    public Guidance.Instruction instruction;
    public String destinationName="",status="Enter the Mercedes to use GPS";
    public boolean bothWays=true;
    private final Speech speech=new Speech();
    private Path file;
    private String worldKey="";
    private boolean dirty, loadFailed;
    private UUID recordingVehicle;
    private Position target;
    private int tick,segment,offRouteTicks,retryTicks;
    private final Set<String> spoken=new HashSet<>();

    @Mod.EventBusSubscriber(modid=MtsGps.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(OPEN); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase==TickEvent.Phase.END) INSTANCE.update();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { INSTANCE.unload(); }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(literal("mtsgps")
                .executes(c->{ INSTANCE.open(); return 1; })
                .then(literal("record").executes(c->{INSTANCE.toggleRecording();return 1;}))
                .then(literal("junction").executes(c->{INSTANCE.junction();return 1;}))
                .then(literal("save").then(argument("name",StringArgumentType.greedyString()).executes(c->{INSTANCE.saveCarHere(StringArgumentType.getString(c,"name"));return 1;})))
                .then(literal("go").then(argument("name",StringArgumentType.greedyString()).executes(c->{String name=StringArgumentType.getString(c,"name"); INSTANCE.navigate(name,INSTANCE.destinations.get(name));return 1;})))
                .then(literal("stop").executes(c->{INSTANCE.stopRoute();return 1;}))
                .then(literal("test").executes(c->{GpsConfig.TEST.set(!GpsConfig.TEST.get());GpsConfig.SPEC.save();return 1;})));
    }
    public void open() {
        if(MtsBridge.car()==null) { message("Sit in an Official Pack merc230 first"); return; }
        Minecraft.getInstance().setScreen(new GpsScreen());
    }
    private void update() {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) return;
        tick++;
        String key=identity()+"/"+mc.level.dimension().location();
        if(!key.equals(worldKey)) { unload(); load(key); }
        while(OPEN.consumeClick()) open();
        if(mc.isPaused()) return;
        var car=MtsBridge.car();
        if(car==null) {
            if(recorder.active()) { recorder.stop(); dirty=true; message("Road recording stopped: left the car"); }
            speech.clear();
            if(tick%100==0) flush();
            return;
        }
        position=new Position(car.position.x,car.position.y,car.position.z);
        headingX=car.orientation.m02; headingZ=car.orientation.m22;
        if(recorder.active()) {
            if(!MtsBridge.driver() || !car.uniqueUUID.equals(recordingVehicle)) {
                recorder.stop(); message("Road recording stopped: driver or vehicle changed");
            } else {
                try {
                    if(recorder.sample(graph,position,GpsConfig.SAMPLE.get(),GpsConfig.SNAP.get())) dirty=true;
                    if(!recorder.active()) message("Road recording stopped: movement gap exceeded 24 blocks");
                } catch(RuntimeException failure) { recorder.stop(); message(failure.getMessage()); }
            }
        }
        if(target!=null) updateNavigation();
        if(tick%4==0) GpsMapTexture.INSTANCE.update(this);
        if(tick%100==0) flush();
    }
    private void updateNavigation() {
        retryTicks++;
        if(route==null) {
            if(retryTicks>=60) calculate(true);
            return;
        }
        segment=Guidance.segment(route.points(),position,segment);
        instruction=Guidance.at(route,position,segment);
        if(instruction.offRoute()>GpsConfig.OFF_ROUTE.get()) offRouteTicks++;
        else offRouteTicks=0;
        if(offRouteTicks>=30 && retryTicks>=60) {
            speech.say("Recalculating route"); calculate(true); return;
        }
        if(instruction.remaining()<5 && instruction.offRoute()<8) {
            String arrival=position.distance(target)<=8 ? "Destination reached" : "Road end: destination "+Math.round(position.distance(target))+" meters away";
            status=arrival;
            if(spoken.add("arrival")) speech.say(arrival);
            return;
        }
        int band=instruction.distance()<15 ? 0 : instruction.distance()<50 ? 50 : instruction.distance()<120 ? 120 : -1;
        if(band>=0 && !instruction.turn().equals("ROAD END") && spoken.add(instruction.vertex()+":"+band))
            speech.say(band==0 ? instruction.turn()+" now" : "In "+Math.round(instruction.distance())+" meters, "+instruction.turn());
        status=instruction.turn()+" in "+Math.round(instruction.distance())+" m";
    }
    public void navigate(String name,Position destination) {
        if(MtsBridge.car()==null) { message("Enter the Mercedes first"); return; }
        if(destination==null) { message("Select or save a destination first"); return; }
        target=destination; destinationName=name; spoken.clear(); calculate(false);
    }
    private void calculate(boolean recalculating) {
        Router.Result result=Router.find(graph,position,target,GpsConfig.ROAD_ATTACH.get());
        route=result.route(); instruction=null; segment=0; offRouteTicks=0; retryTicks=0;
        status=result.message();
        if(result.found()) {
            spoken.clear();
            speech.say((recalculating ? "Route recalculated. " : "Route ready. ")+Math.round(route.distance())+" meters along recorded roads");
        } else if(!recalculating) message(status);
    }
    public void stopRoute() { target=null; route=null; instruction=null; speech.clear(); destinationName=""; status="Navigation stopped"; }
    public void toggleRecording() {
        if(recorder.active()) { recorder.stop(); if(flush()) message("Road recording saved"); return; }
        if(!MtsBridge.driver()) { message("Road recording requires the Mercedes driver seat"); return; }
        if(loadFailed) { message("GPS data could not be read; repair the file before recording"); return; }
        recordingVehicle=MtsBridge.car().uniqueUUID;
        recorder.start(bothWays); message("Recording "+(bothWays?"two-way":"one-way")+" roads - drive along the road center");
    }
    public void junction() {
        if(!MtsBridge.driver() || loadFailed) { message("Enter the driver seat with valid GPS data first"); return; }
        try { recorder.junction(graph,position,GpsConfig.SNAP.get()+.5); dirty=true; flush(); message("Connected same-height road segments at this intersection"); }
        catch(RuntimeException failure) { message(failure.getMessage()); }
    }
    public void saveCarHere(String name) {
        var car=MtsBridge.car();
        if(car==null) { message("Sit in an Official Pack merc230 before saving the car location"); return; }
        saveDestination(name,new Position(car.position.x,car.position.y,car.position.z));
    }
    public void saveDestination(String name,Position where) {
        if(loadFailed) { message("GPS data could not be read; destinations cannot be saved"); return; }
        if(where==null) { message("Click inside the map to place a pin before clicking Save map pin"); return; }
        if(!where.valid()) { message("Destination position is outside the supported world bounds"); return; }
        name=name==null?"":name.strip();
        if(name.isEmpty()) { message("Type a destination name in the box above the Save buttons"); return; }
        if(name.length()>64) { message("Destination name must be 64 characters or fewer"); return; }
        if(destinations.size()>=1000 && !destinations.containsKey(name)) { message("Destination limit reached"); return; }
        destinations.put(name,where); dirty=true; if(flush()) message("Saved destination: "+name);
    }
    public void removeDestination(String name) { if(destinations.remove(name)!=null) { dirty=true; flush(); } }
    public void message(String value) {
        status=value;
        var player=Minecraft.getInstance().player;
        if(player!=null) player.displayClientMessage(Component.literal("MTS GPS: "+value),false);
    }
    public String speechStatus() { return !GpsConfig.SPEECH.get()?"Speech off":speech.available()?"Speech ready":"Speech unavailable on this system"; }
    public void testSpeech() { speech.say("MTS GPS. In fifty meters, turn right."); }
    private void load(String key) {
        worldKey=key;
        file=FMLPaths.CONFIGDIR.get().resolve("mtsgps/data").resolve(hash(key)+".json");
        MtsGps.LOGGER.info("MTS GPS data file {} for {}",file,key);
        try {
            var loaded=GpsStore.load(file); graph=loaded.graph(); destinations.putAll(loaded.destinations());
            status=loaded.recovered()?"Recovered GPS data from backup":"GPS ready - F8 for destinations";
            if(loaded.recovered()) message(status);
        } catch(Exception failure) {
            loadFailed=true; status="GPS file unreadable; recording disabled"; MtsGps.LOGGER.error("Could not load {}",file,failure); message(status);
        }
    }
    public boolean flush() {
        if(!dirty) return true;
        if(file==null || loadFailed) return false;
        try { GpsStore.save(file,graph,destinations); dirty=false; return true; }
        catch(Exception failure) { MtsGps.LOGGER.error("Could not save GPS data to {}",file,failure); message("GPS SAVE FAILED - check available disk space and permissions"); return false; }
    }
    private void unload() {
        flush(); recorder.stop(); speech.close(); file=null; worldKey=""; dirty=false; loadFailed=false;
        graph=new RoadGraph(); destinations.clear(); target=null; route=null; instruction=null; picked=null;
        destinationName=""; spoken.clear(); GpsMapTexture.INSTANCE.resetTerrain();
    }
    private String identity() {
        var mc=Minecraft.getInstance();
        if(mc.getSingleplayerServer()!=null) return "local:"+mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return "server:"+(mc.getCurrentServer()!=null?mc.getCurrentServer().ip:"unknown");
    }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private GpsClient() {}
}

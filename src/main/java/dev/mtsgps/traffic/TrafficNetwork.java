package dev.mtsgps.traffic;

import dev.mtsgps.MtsGps;
import dev.mtsgps.nav.Position;
import dev.mtsgps.nav.RoadGraph;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

public final class TrafficNetwork {
    public static final int MAX_NODES=4096,MAX_EDGES=12288;
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(MtsGps.ID,"traffic"),
            ()->"1",TrafficNetwork::compatible,TrafficNetwork::compatible);
    private static boolean compatible(String version) {
        return "1".equals(version) || NetworkRegistry.ABSENT.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }
    public record Request(ResourceLocation dimension,int count,RoadGraph graph) {}
    public static void init() {
        CHANNEL.registerMessage(0,Request.class,TrafficNetwork::encode,TrafficNetwork::decode,TrafficNetwork::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    private static void encode(Request request,FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(request.dimension()); buffer.writeVarInt(request.count());
        if(request.count()==0) return;
        RoadGraph graph=request.graph();
        if(graph.nodes().size()>MAX_NODES || graph.edges().size()>MAX_EDGES) throw new IllegalArgumentException("Traffic network limit exceeded");
        buffer.writeVarInt(graph.nodes().size());
        for(var node:graph.nodes()) { buffer.writeVarInt(node.id()); buffer.writeDouble(node.position().x()); buffer.writeDouble(node.position().y()); buffer.writeDouble(node.position().z()); }
        buffer.writeVarInt(graph.edges().size());
        for(var edge:graph.edges()) { buffer.writeVarInt(edge.from()); buffer.writeVarInt(edge.to()); }
    }
    private static int count(FriendlyByteBuf buffer,int max) {
        int count=buffer.readVarInt();
        if(count<0 || count>max) throw new IllegalArgumentException("Traffic request exceeds limits");
        return count;
    }
    private static Request decode(FriendlyByteBuf buffer) {
        var dimension=buffer.readResourceLocation(); int cars=count(buffer,8);
        if(cars==0) return new Request(dimension,0,null);
        int nodeCount=count(buffer,MAX_NODES); var nodes=new ArrayList<RoadGraph.Node>(nodeCount);
        for(int i=0;i<nodeCount;i++) nodes.add(new RoadGraph.Node(buffer.readVarInt(),new Position(buffer.readDouble(),buffer.readDouble(),buffer.readDouble())));
        int edgeCount=count(buffer,MAX_EDGES); var edges=new ArrayList<RoadGraph.Edge>(edgeCount);
        for(int i=0;i<edgeCount;i++) edges.add(new RoadGraph.Edge(buffer.readVarInt(),buffer.readVarInt()));
        return new Request(dimension,cars,RoadGraph.restore(nodes,edges));
    }
    private static void handle(Request request,Supplier<NetworkEvent.Context> context) {
        var ctx=context.get();
        ctx.enqueueWork(()->{
            var player=ctx.getSender();
            if(player==null || !player.serverLevel().dimension().location().equals(request.dimension())) return;
            try { TrafficManager.request(player,request.count(),request.graph()); }
            catch(RuntimeException failure) {
                MtsGps.LOGGER.error("Traffic request failed",failure);
                player.sendSystemMessage(Component.literal("MTS GPS: Traffic could not start; see latest.log"));
            }
        });
        ctx.setPacketHandled(true);
    }
    private TrafficNetwork() {}
}

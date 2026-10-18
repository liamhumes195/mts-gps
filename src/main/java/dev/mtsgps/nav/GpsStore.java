package dev.mtsgps.nav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class GpsStore {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static final class Data {
        public int format=1;
        public List<RoadGraph.Node> nodes=new ArrayList<>();
        public List<RoadGraph.Edge> edges=new ArrayList<>();
        public Map<String,Position> destinations=new TreeMap<>();
    }
    public record Loaded(RoadGraph graph,Map<String,Position> destinations,boolean recovered) {}
    public static Loaded load(Path path) throws IOException {
        if(!Files.exists(path)) {
            Path backup=path.resolveSibling(path.getFileName()+".bak");
            return Files.exists(backup)?read(backup,true):new Loaded(new RoadGraph(),new TreeMap<>(),false);
        }
        try { return read(path,false); }
        catch(Exception failure) {
            Path backup=path.resolveSibling(path.getFileName()+".bak");
            if(Files.exists(backup)) {
                try { return read(backup,true); } catch(Exception ignored) { failure.addSuppressed(ignored); }
            }
            throw new IOException("Invalid GPS file; preserved for recovery: "+path,failure);
        }
    }
    private static Loaded read(Path path,boolean recovered) throws IOException {
        if(Files.size(path)>32L*1024*1024) throw new IOException("GPS file exceeds 32 MiB");
        var document=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
        if(!document.has("format") || !document.has("nodes") || !document.has("edges") || !document.has("destinations"))
            throw new IOException("Incomplete GPS document");
        Data data=GSON.fromJson(document,Data.class);
        if(data==null || data.format!=1 || data.destinations==null || data.destinations.size()>1000)
            throw new IOException("Unsupported or incomplete GPS data");
        for(var entry:data.destinations.entrySet())
            if(entry.getKey()==null || entry.getKey().isBlank() || entry.getKey().length()>64
                    || entry.getValue()==null || !entry.getValue().valid()) throw new IOException("Invalid destination");
        return new Loaded(RoadGraph.restore(data.nodes,data.edges),new TreeMap<>(data.destinations),recovered);
    }
    public static void save(Path path,RoadGraph graph,Map<String,Position> destinations) throws IOException {
        Data data=new Data();
        data.nodes.addAll(graph.nodes()); data.edges.addAll(graph.edges()); data.destinations.putAll(destinations);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temp,GSON.toJson(data),StandardCharsets.UTF_8);
        // Back up only a validated prior document; corrupt input must never replace a good backup.
        if(Files.exists(path)) {
            try { read(path,false); Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING); }
            catch(RuntimeException | IOException ignored) { /* preserve the last good backup */ }
        }
        try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
    }
    private GpsStore() {}
}

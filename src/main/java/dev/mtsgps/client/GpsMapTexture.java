package dev.mtsgps.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import dev.mtsgps.GpsConfig;
import dev.mtsgps.nav.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GpsMapTexture {
    public static final GpsMapTexture INSTANCE = new GpsMapTexture();
    private DynamicTexture texture;
    private ResourceLocation location, standby, white;
    private int terrainCursor;
    private record Terrain(int color,long time) {}
    private final Map<Long,Terrain> terrain = new LinkedHashMap<>(4096,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Terrain> eldest) { return size()>65_536; }
    };
    private static final int LEFT=4, RIGHT=251, TOP=20, BOTTOM=164, CX=128, CY=92;
    public double blocksPerPixel() { return GpsConfig.MAP_RADIUS.get()/72d; }
    public Position pick(double x,double y,Position center) {
        return new Position(center.x()+(x-CX)*blocksPerPixel(),center.y(),center.z()+(y-CY)*blocksPerPixel());
    }
    public boolean inMap(double x,double y) { return x>=LEFT&&x<=RIGHT&&y>=TOP&&y<=BOTTOM; }
    public void resetTerrain() { terrain.clear(); terrainCursor=0; }
    private void init() {
        if (texture != null) return;
        var manager = Minecraft.getInstance().getTextureManager();
        texture = new DynamicTexture(256,192,true);
        location = manager.register("mtsgps/map", texture);
        var idle = new DynamicTexture(256,192,true);
        fill(idle.getPixels(), 0xff182126);
        RasterFont.text(idle.getPixels(), "MTS GPS", 84, 74, 0xff50ddd0, 2);
        RasterFont.text(idle.getPixels(), "ENTER CAR - F8", 86, 112, 0xffeeeeee, 1);
        idle.upload();
        standby = manager.register("mtsgps/standby", idle);
        var solid = new DynamicTexture(1,1,true);
        solid.getPixels().setPixelRGBA(0,0,0xffffffff);
        solid.upload();
        white = manager.register("mtsgps/white", solid);
        test();
    }
    public ResourceLocation location() { init(); return location; }
    public ResourceLocation standby() { init(); return standby; }
    public ResourceLocation white() { init(); return white; }
    public NativeImage pixels() { init(); return texture.getPixels(); }
    public void upload() { texture.upload(); }
    public void update(GpsClient gps) {
        init();
        if(GpsConfig.TEST.get()) { test(); return; }
        NativeImage image=texture.getPixels();
        fill(image,0xff111c24);
        RasterFont.text(image,"MTS GPS",7,7,0xff61e1d2,1);
        RasterFont.text(image,gps.recorder.active()?"REC":"NORTH UP",198,7,gps.recorder.active()?0xffff6969:0xffabbec9,1);
        var level=Minecraft.getInstance().level;
        double scale=blocksPerPixel();
        long now=level==null?0:level.getGameTime();
        var block=new BlockPos.MutableBlockPos();
        // Rotate the sampling cursor so unloaded columns at one edge cannot starve the car's area.
        int columns=(RIGHT-LEFT)/2+1,rows=(BOTTOM-TOP)/2+1,samples=0;
        if(GpsConfig.TERRAIN.get() && level!=null) for(int attempts=0;attempts<columns*rows&&samples<256;attempts++) {
            int cell=Math.floorMod(terrainCursor++,columns*rows);
            int px=LEFT+(cell%columns)*2,py=TOP+(cell/columns)*2;
            int wx=Math.floorDiv((int)Math.floor(gps.position.x()+(px-CX)*scale),4)*4;
            int wz=Math.floorDiv((int)Math.floor(gps.position.z()+(py-CY)*scale),4)*4;
            long key=((long)wx<<32)|(wz&0xffffffffL);
            Terrain cached=terrain.get(key);
            if(cached!=null&&now-cached.time<=600) continue;
            samples++;
            // hasChunk only checks the client cache; this never requests or generates chunks.
            if(level.hasChunk(wx>>4,wz>>4)) {
                var chunk=level.getChunk(wx>>4,wz>>4);
                int height=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,wx&15,wz&15);
                block.set(wx,height,wz);
                int color=chunk.getBlockState(block).getMapColor(level,block).col;
                terrain.put(key,new Terrain(color==0?0xff293744:0xff000000|color,now));
            } else terrain.put(key,new Terrain(0xff293744,now-580));
        }
        for(int y=TOP;y<=BOTTOM;y+=2) for(int x=LEFT;x<=RIGHT;x+=2) {
            int wx=Math.floorDiv((int)Math.floor(gps.position.x()+(x-CX)*scale),4)*4;
            int wz=Math.floorDiv((int)Math.floor(gps.position.z()+(y-CY)*scale),4)*4;
            long key=((long)wx<<32)|(wz&0xffffffffL);
            Terrain cached=terrain.get(key);
            int color=GpsConfig.TERRAIN.get()&&cached!=null?cached.color:(((wx/16+wz/16)&1)==0?0xff24343c:0xff293c43);
            for(int iy=0;iy<2;iy++) for(int ix=0;ix<2;ix++) mapPixel(image,x+ix,y+iy,color);
        }
        for(var edge:gps.graph.edges()) {
            Position a=gps.graph.node(edge.from()).position(),b=gps.graph.node(edge.to()).position();
            line(image,mx(a,gps.position),my(a,gps.position),mx(b,gps.position),my(b,gps.position),0xffc3c7bc,1);
        }
        if(gps.route!=null) {
            var path=gps.route.points();
            for(int i=1;i<path.size();i++) line(image,mx(path.get(i-1),gps.position),my(path.get(i-1),gps.position),
                    mx(path.get(i),gps.position),my(path.get(i),gps.position),0xff39f0d0,3);
        }
        for(var dest:gps.destinations.values()) marker(image,dest,gps.position,0xffffcf66);
        if(gps.picked!=null) marker(image,gps.picked,gps.position,0xffff7beb);
        double norm=Math.max(.001,Math.hypot(gps.headingX,gps.headingZ));
        double hx=gps.headingX/norm,hz=gps.headingZ/norm;
        double fx=CX+hx*10,fy=CY+hz*10,bx=CX-hx*6,by=CY-hz*6;
        line(image,fx,fy,bx-hz*5,by+hx*5,0xffffffff,2);
        line(image,bx-hz*5,by+hx*5,bx+hz*5,by-hx*5,0xffffffff,2);
        line(image,bx+hz*5,by-hx*5,fx,fy,0xffffffff,2);
        RasterFont.text(image,"N",8,25,0xffffffff,1);
        String instruction=gps.instruction==null?gps.status:gps.instruction.turn()+"  "+Math.round(gps.instruction.distance())+" M";
        if(gps.status.startsWith("Destination reached")||gps.status.startsWith("Road end:")) instruction=gps.status;
        RasterFont.text(image,clip(instruction,40),6,171,0xffffffff,1);
        String footer=gps.route==null?"F8: DESTINATIONS / ROADS":Math.round(gps.instruction==null?gps.route.distance():gps.instruction.remaining())+" M LEFT - "+gps.destinationName;
        RasterFont.text(image,clip(footer,40),6,183,0xff61e1d2,1);
        texture.upload();
    }
    private static String clip(String value,int count) { return value.length()>count?value.substring(0,count):value; }
    private double mx(Position p,Position center) { return CX+(p.x()-center.x())/blocksPerPixel(); }
    private double my(Position p,Position center) { return CY+(p.z()-center.z())/blocksPerPixel(); }
    private void marker(NativeImage image,Position p,Position center,int color) {
        int x=(int)Math.round(mx(p,center)),y=(int)Math.round(my(p,center));
        for(int dx=-3;dx<=3;dx++) for(int dy=-3;dy<=3;dy++) if(Math.abs(dx)+Math.abs(dy)<=3) mapPixel(image,x+dx,y+dy,color);
    }
    private static void mapPixel(NativeImage image,int x,int y,int color) {
        if(x>=LEFT&&x<=RIGHT&&y>=TOP&&y<=BOTTOM) pixel(image,x,y,color);
    }
    /** Clip before rasterizing, so a road outside the map cannot cause an enormous pixel loop. */
    private static void line(NativeImage image,double x0,double y0,double x1,double y1,int color,int width) {
        double dx=x1-x0,dy=y1-y0,lo=0,hi=1;
        double[] p={-dx,dx,-dy,dy},q={x0-LEFT,RIGHT-x0,y0-TOP,BOTTOM-y0};
        for(int i=0;i<4;i++) {
            if(Math.abs(p[i])<1e-9) { if(q[i]<0) return; }
            else {
                double t=q[i]/p[i];
                if(p[i]<0) lo=Math.max(lo,t); else hi=Math.min(hi,t);
                if(lo>hi) return;
            }
        }
        int ax=(int)Math.round(x0+lo*dx),ay=(int)Math.round(y0+lo*dy);
        int bx=(int)Math.round(x0+hi*dx),by=(int)Math.round(y0+hi*dy);
        int steps=Math.max(Math.abs(bx-ax),Math.abs(by-ay));
        for(int i=0;i<=steps;i++) {
            double f=steps==0?0:(double)i/steps;
            int x=(int)Math.round(ax+(bx-ax)*f),y=(int)Math.round(ay+(by-ay)*f);
            for(int ox=-width/2;ox<=width/2;ox++) for(int oy=-width/2;oy<=width/2;oy++) mapPixel(image,x+ox,y+oy,color);
        }
    }
    public void test() {
        var image = texture.getPixels();
        for (int y=0;y<192;y++) for (int x=0;x<256;x++)
            pixel(image,x,y,((x/16+y/16)%2==0) ? 0xff1c3544 : 0xff245368);
        RasterFont.text(image,"MTS GPS TEST",20,24,0xffffffff,2);
        RasterFont.text(image,"TOP - DRIVER SIDE",22,6,0xff66ffbb,1);
        RasterFont.text(image,"LEFT",6,90,0xffffffff,1);
        RasterFont.text(image,"RIGHT",216,90,0xffffffff,1);
        RasterFont.text(image,"PHYSICAL DASHBOARD SCREEN",18,161,0xff66ffbb,1);
        RasterFont.text(image,"F8 TO CONFIGURE",78,177,0xffffffff,1);
        texture.upload();
    }
    public static void fill(NativeImage image, int argb) {
        image.fillRect(0,0,image.getWidth(),image.getHeight(),abgr(argb));
    }
    public static int abgr(int argb) { return (argb&0xff00ff00) | ((argb>>16)&255) | ((argb&255)<<16); }
    public static void pixel(NativeImage image,int x,int y,int color) {
        if (x>=0 && y>=0 && x<image.getWidth() && y<image.getHeight()) image.setPixelRGBA(x,y,abgr(color));
    }
    private GpsMapTexture() {}
}

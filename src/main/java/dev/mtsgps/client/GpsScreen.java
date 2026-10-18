package dev.mtsgps.client;

import dev.mtsgps.GpsConfig;
import dev.mtsgps.nav.Position;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Optional control panel. The in-car world surface continues displaying the map when closed. */
public final class GpsScreen extends Screen {
    private final GpsClient gps=GpsClient.INSTANCE;
    private EditBox name;
    private int selected,mapX,mapY,panelX,panelWidth;
    private float scale;
    public GpsScreen() { super(Component.literal("MTS GPS")); }
    @Override protected void init() {
        String draftName=name==null?selection():name.getValue();
        mapX=12; mapY=32;
        scale=(float)Math.min(1,Math.min((width-230)/256d,(height-90)/192d));
        scale=Math.max(.3f,scale);
        panelX=mapX+(int)(256*scale)+12; panelWidth=width-panelX-12;
        int half=(panelWidth-4)/2;
        name=new EditBox(font,panelX,32,panelWidth,18,Component.literal("Destination name"));
        name.setMaxLength(64); name.setHint(Component.literal("e.g. Home")); name.setValue(draftName); addRenderableWidget(name);
        button("Save car here",panelX,54,half,()->gps.saveCarHere(name.getValue()));
        button("Save map pin",panelX+half+4,54,half,()->gps.saveDestination(name.getValue(),gps.picked));
        button("<",panelX,76,22,()->{selected--; refreshName();});
        button("Next saved >",panelX+26,76,panelWidth-26,()->{selected++;refreshName();});
        button("Navigate saved",panelX,98,half,()->{String key=selection();gps.navigate(key,gps.destinations.get(key));});
        button("Delete saved",panelX+half+4,98,half,()->{gps.removeDestination(selection());refreshName();});
        button(gps.recorder.active()?"Stop recording":"Record roads",panelX,120,panelWidth,()->{gps.toggleRecording(); rebuildWidgets();});
        button(gps.bothWays?"Two way":"One way",panelX,142,half,()->{
            if(gps.recorder.active()) gps.message("Stop recording before changing road direction");
            else {gps.bothWays=!gps.bothWays;rebuildWidgets();}
        });
        button("Join junction",panelX+half+4,142,half,gps::junction);
        button("Stop route",panelX,164,half,gps::stopRoute);
        button("Test voice",panelX+half+4,164,half,gps::testSpeech);
        button("Mount settings",panelX,186,half,()->minecraft.setScreen(new MountScreen(this)));
        button("Close",panelX+half+4,186,half,this::onClose);
        setInitialFocus(name);
    }
    private void button(String title,int x,int y,int w,Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(title),b->action.run()).bounds(x,y,w,20).build());
    }
    private List<String> names() { return new ArrayList<>(gps.destinations.keySet()); }
    private String selection() {
        var names=names(); return names.isEmpty()?"":names.get(Math.floorMod(selected,names.size()));
    }
    private void refreshName() { if(name!=null) name.setValue(selection()); }
    @Override public boolean mouseClicked(double x,double y,int button) {
        double tx=(x-mapX)/scale,ty=(y-mapY)/scale;
        if(button==0 && GpsMapTexture.INSTANCE.inMap(tx,ty)) {
            Position raw=GpsMapTexture.INSTANCE.pick(tx,ty,gps.position);
            var snap=gps.graph.nearest(raw,12,4);
            gps.picked=snap==null?raw:snap.position();
            setFocused(name);
            gps.message("Map pin: "+Math.round(gps.picked.x())+", "+Math.round(gps.picked.z())+" - type a name, then click Save map pin");
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        renderBackground(g);
        g.drawString(font,"MTS GPS - Mercedes merc230",12,12,0xff66e5d6);
        g.drawString(font,"Destination name",panelX,22,0xffb7c6ce);
        g.pose().pushPose(); g.pose().translate(mapX,mapY,0); g.pose().scale(scale,scale,1);
        g.blit(GpsMapTexture.INSTANCE.location(),0,0,0,0,256,192,256,192);
        g.pose().popPose();
        int infoY=mapY+(int)(192*scale)+8;
        g.drawString(font,"Click map to place a destination pin",mapX,infoY,0xffb7c6ce);
        g.drawString(font,gps.graph.nodes().size()+" nodes / "+gps.graph.edges().size()+" directed edges",mapX,infoY+12,0xffb7c6ce);
        g.drawString(font,gps.speechStatus(),mapX,infoY+24,0xffb7c6ce);
        g.drawString(font,font.plainSubstrByWidth(gps.status,width-24),12,height-16,0xffffffff);
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { gps.flush(); super.onClose(); }
}

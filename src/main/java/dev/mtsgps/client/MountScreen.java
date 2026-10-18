package dev.mtsgps.client;

import dev.mtsgps.GpsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import java.util.Locale;

final class MountScreen extends Screen {
    private final Screen parent;
    private final String[] labels={"X (right is minus)","Y (up)","Z (forward)","Yaw degrees","Pitch degrees","Roll degrees","Width blocks","Height blocks"};
    private final DoubleValue[] values={GpsConfig.X,GpsConfig.Y,GpsConfig.Z,GpsConfig.YAW,GpsConfig.PITCH,GpsConfig.ROLL,GpsConfig.WIDTH,GpsConfig.HEIGHT};
    private int left;
    MountScreen(Screen parent) { super(Component.literal("GPS mount calibration")); this.parent=parent; }
    @Override protected void init() {
        left=width/2-150;
        for(int i=0;i<values.length;i++) {
            final int index=i;
            addRenderableWidget(Button.builder(Component.literal("-"),b->change(index,-1)).bounds(left+212,30+i*21,34,19).build());
            addRenderableWidget(Button.builder(Component.literal("+"),b->change(index,1)).bounds(left+250,30+i*21,34,19).build());
        }
        addRenderableWidget(Button.builder(Component.literal(GpsConfig.TEST.get()?"Live GPS":"Test grid"),b->{GpsConfig.TEST.set(!GpsConfig.TEST.get());GpsConfig.SPEC.save();rebuildWidgets();}).bounds(left,204,94,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset mount"),b->{for(var value:values)value.set(value.getDefault());GpsConfig.SPEC.save();}).bounds(left+98,204,94,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(left+196,204,94,20).build());
    }
    private void change(int index,int direction) {
        double step=index>=3&&index<=5?1:.01;
        double next=values[index].get()+direction*step;
        double min=index==6?.1:index==7?.08:index==4?-90:index>=3?-180:index==0?-3:index==1?-2:-5;
        double max=index==6?1.5:index==7?1:index==4?90:index>=3?180:index==0?3:index==1?4:6;
        values[index].set(Math.max(min,Math.min(max,next))); GpsConfig.SPEC.save();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        // Leave the world visible while adjusting the physical mount.
        g.fill(left-8,5,left+300,229,0xbb101820);
        g.drawString(font,"Physical dashboard mount - changes save immediately",left,12,0xff66e5d6);
        for(int i=0;i<values.length;i++) {
            g.drawString(font,labels[i],left,36+i*21,0xffffffff);
            g.drawString(font,String.format(Locale.ROOT,"%.2f",values[i].get()),left+165,36+i*21,0xff66e5d6);
        }
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

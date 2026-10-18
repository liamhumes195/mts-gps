package dev.mtsgps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mtsgps.GpsConfig;
import dev.mtsgps.MtsGps;
import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Depth-tested geometry in the world; never rendered in a GUI/HUD event. */
@Mod.EventBusSubscriber(modid = MtsGps.ID, value = Dist.CLIENT)
public final class DashboardRenderer {
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        // Forge 47.4.28's AFTER_LEVEL passes the projection stack, not the world-view stack.
        // AFTER_PARTICLES supplies the real world-view pose, including MTS's camera roll.
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !GpsConfig.ENABLED.get()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || InterfaceManager.clientInterface == null) return;
        var world = InterfaceManager.clientInterface.getClientWorld();
        if (world == null) return;
        var camera = e.getCamera().getPosition();
        var active = MtsBridge.car();
        var buffers = mc.renderBuffers().bufferSource();
        for (var entity : world.renderableEntities) {
            if (!(entity instanceof EntityVehicleF_Physics car) || !MtsBridge.isMercedes(car)) continue;
            double dx = car.position.x - camera.x, dy = car.position.y - camera.y, dz = car.position.z - camera.z;
            if (dx*dx + dy*dy + dz*dz > 48*48) continue;
            float t = e.getPartialTick();
            var body = MtsBridge.body(car, t);
            if (body == null || car.ticksExisted == 0) continue;
            PoseStack pose = e.getPoseStack();
            pose.pushPose();
            try {
                pose.translate(car.prevPosition.x + (car.position.x-car.prevPosition.x)*t-camera.x,
                        car.prevPosition.y + (car.position.y-car.prevPosition.y)*t-camera.y,
                        car.prevPosition.z + (car.position.z-car.prevPosition.z)*t-camera.z);
                var rotation = new RotationMatrix();
                car.getInterpolatedOrientation(rotation, t);
                apply(pose, MtsBridge.rotation(rotation));
                pose.scale((float)(car.prevScale.x+(car.scale.x-car.prevScale.x)*t),
                        (float)(car.prevScale.y+(car.scale.y-car.prevScale.y)*t),
                        (float)(car.prevScale.z+(car.scale.z-car.prevScale.z)*t));
                apply(pose, body);
                pose.translate(GpsConfig.X.get(), GpsConfig.Y.get(), GpsConfig.Z.get());
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(GpsConfig.YAW.get().floatValue()));
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(GpsConfig.PITCH.get().floatValue()));
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(GpsConfig.ROLL.get().floatValue()));
                draw(pose, buffers, car == active || GpsConfig.TEST.get() ? GpsMapTexture.INSTANCE.location() : GpsMapTexture.INSTANCE.standby());
            } finally {
                pose.popPose();
            }
        }
        // Flush only our types; the vanilla entity buffer has already been flushed.
        buffers.endBatch(RenderType.entityCutoutNoCull(GpsMapTexture.INSTANCE.white()));
        buffers.endBatch(RenderType.entityTranslucentEmissive(GpsMapTexture.INSTANCE.location()));
        buffers.endBatch(RenderType.entityTranslucentEmissive(GpsMapTexture.INSTANCE.standby()));
    }

    private static void apply(PoseStack pose, org.joml.Matrix4f matrix) {
        pose.mulPoseMatrix(matrix);
        pose.last().normal().mul(new org.joml.Matrix3f(matrix).invert().transpose());
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture) {
        float w = GpsConfig.WIDTH.get().floatValue()/2, h = GpsConfig.HEIGHT.get().floatValue()/2;
        VertexConsumer casing = buffers.getBuffer(RenderType.entityCutoutNoCull(GpsMapTexture.INSTANCE.white()));
        box(pose, casing, -w-.012f, -h-.012f, -.001f, w+.012f, h+.012f, .035f, 0xff202630);
        box(pose, casing, -.035f, -h-.07f, .005f, .035f, -h-.012f, .03f, 0xff202630);
        var v = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        face(pose, v, new float[][]{{w,h,-.003f},{w,-h,-.003f},{-w,-h,-.003f},{-w,h,-.003f}},
                new float[][]{{0,0},{0,1},{1,1},{1,0}}, 0xffffffff, 0,0,-1);
    }

    private static final float[][] UV = {{0,0},{0,1},{1,1},{1,0}};
    private static void box(PoseStack p, VertexConsumer v, float a, float b, float c, float d, float f, float g, int color) {
        face(p,v,new float[][]{{a,b,c},{a,f,c},{d,f,c},{d,b,c}},UV,color,0,0,-1);
        face(p,v,new float[][]{{a,b,g},{d,b,g},{d,f,g},{a,f,g}},UV,color,0,0,1);
        face(p,v,new float[][]{{a,b,c},{a,b,g},{a,f,g},{a,f,c}},UV,color,-1,0,0);
        face(p,v,new float[][]{{d,b,c},{d,f,c},{d,f,g},{d,b,g}},UV,color,1,0,0);
        face(p,v,new float[][]{{a,f,c},{a,f,g},{d,f,g},{d,f,c}},UV,color,0,1,0);
        face(p,v,new float[][]{{a,b,c},{d,b,c},{d,b,g},{a,b,g}},UV,color,0,-1,0);
    }

    private static void face(PoseStack p, VertexConsumer v, float[][] points, float[][] uv, int color, float nx,float ny,float nz) {
        for (int i=0;i<4;i++) v.vertex(p.last().pose(),points[i][0],points[i][1],points[i][2])
                .color((color>>16)&255,(color>>8)&255,color&255,(color>>>24)&255)
                .uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(p.last().normal(),nx,ny,nz).endVertex();
    }
}

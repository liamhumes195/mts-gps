package dev.mtsgps.client;

import minecrafttransportsimulator.baseclasses.AnimationSwitchbox;
import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.baseclasses.TransformationMatrix;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.entities.instances.PartSeat;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import org.joml.Matrix4f;

/** Only MTS entry points verified with javap against the supplied 24.0.0 binary. */
public final class MtsBridge {
    public static boolean isMercedes(EntityVehicleF_Physics car) {
        return car != null && car.isValid && "mtsofficialpack".equals(car.definition.packID)
                && "merc230".equals(car.definition.systemName);
    }

    public static PartSeat seat() {
        if (InterfaceManager.clientInterface == null) return null;
        var player = InterfaceManager.clientInterface.getClientPlayer();
        if (player != null && player.getEntityRiding() instanceof PartSeat seat
                && isMercedes(seat.vehicleOn)) return seat;
        return null;
    }

    public static EntityVehicleF_Physics car() {
        var seat = seat();
        return seat == null ? null : seat.vehicleOn;
    }

    public static boolean driver() {
        var seat = seat();
        return seat != null && seat.placementDefinition.isController;
    }

    public static Matrix4f rotation(RotationMatrix m) {
        // MTS uses row/column mathematical names. JOML names are column/row.
        return new Matrix4f().set((float)m.m00, (float)m.m10, (float)m.m20, 0,
                (float)m.m01, (float)m.m11, (float)m.m21, 0,
                (float)m.m02, (float)m.m12, (float)m.m22, 0, 0, 0, 0, 1);
    }

    public static Matrix4f transform(TransformationMatrix m) {
        return rotation(m).m30((float)m.m03).m31((float)m.m13).m32((float)m.m23);
    }

    public static Matrix4f body(EntityVehicleF_Physics car, float partialTick) {
        AnimationSwitchbox body = car.animatedObjectSwitchboxes.get("body");
        if (body == null || !body.runSwitchbox(partialTick, false)) return null;
        return transform(body.netMatrix);
    }
    private MtsBridge() {}
}

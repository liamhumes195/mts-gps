package dev.mtsgps;

import net.minecraftforge.common.ForgeConfigSpec;

public final class GpsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue X, Y, Z, YAW, PITCH, ROLL, WIDTH, HEIGHT;
    public static final ForgeConfigSpec.DoubleValue SAMPLE, SNAP, ROAD_ATTACH, OFF_ROUTE, MAP_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ENABLED, TEST, SPEECH, TERRAIN;
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("dashboard");
        ENABLED = b.define("enabled", true);
        TEST = b.comment("Show calibration grid on the actual world surface.").define("testScreen", false);
        X = b.comment("Vehicle-local blocks. Driver seat is at x=+0.5; smaller x is to the driver's right.").defineInRange("x", .12, -3d, 3d);
        Y = b.defineInRange("y", .94, -2d, 4d);
        Z = b.comment("Vehicle forward is +Z. Dashboard instruments are near z=2.34.").defineInRange("z", 2.27, -5d, 6d);
        YAW = b.defineInRange("yawDegrees", -20d, -180d, 180d);
        PITCH = b.defineInRange("pitchDegrees", 12d, -90d, 90d);
        ROLL = b.defineInRange("rollDegrees", 0d, -180d, 180d);
        WIDTH = b.defineInRange("width", .38, .1, 1.5);
        HEIGHT = b.defineInRange("height", .25, .08, 1d);
        b.pop().push("navigation");
        SAMPLE = b.defineInRange("recordSpacingBlocks", 3d, 1d, 8d);
        SNAP = b.comment("Automatic junction snapping; height difference is separately limited to 1.25 blocks.").defineInRange("junctionSnapBlocks", 2d, .25, 4d);
        ROAD_ATTACH = b.comment("Maximum distance to a recorded road. No off-road route is invented.").defineInRange("roadAttachBlocks", 24d, 2d, 64d);
        OFF_ROUTE = b.defineInRange("recalculateDistanceBlocks", 10d, 3d, 32d);
        MAP_RADIUS = b.defineInRange("mapRadiusBlocks", 100d, 24d, 256d);
        SPEECH = b.define("spokenDirections", true);
        TERRAIN = b.comment("Samples only already loaded client chunks, within a fixed tick budget.").define("terrainMap", true);
        b.pop();
        SPEC = b.build();
    }
    private GpsConfig() {}
}

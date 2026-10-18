package dev.mtsgps.traffic;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TrafficConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_CARS;
    public static final ForgeConfigSpec.DoubleValue SPEED;
    static {
        var builder=new ForgeConfigSpec.Builder();
        builder.push("traffic");
        ENABLED=builder.comment("Allow players with permission level 2, and the single-player owner, to start traffic.").define("enabled",true);
        MAX_CARS=builder.comment("Total addon-controlled traffic cars per dimension. Traffic is opt-in and session-only.").defineInRange("maxCars",12,1,24);
        SPEED=builder.comment("Cruise speed in blocks per second; corners and obstacles reduce this.").defineInRange("cruiseSpeed",4.5,1d,8d);
        builder.pop(); SPEC=builder.build();
    }
    private TrafficConfig() {}
}

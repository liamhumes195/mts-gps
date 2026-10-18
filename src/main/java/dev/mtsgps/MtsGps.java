package dev.mtsgps;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import dev.mtsgps.traffic.TrafficConfig;
import dev.mtsgps.traffic.TrafficNetwork;

@Mod(MtsGps.ID)
public final class MtsGps {
    public static final String ID = "mtsgps";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MtsGps(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, GpsConfig.SPEC);
        context.registerConfig(ModConfig.Type.COMMON, TrafficConfig.SPEC);
        TrafficNetwork.init();
    }
}

package com.gang.lightpollution;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Client-only controls in a separately named CLIENT config, like the existing text config. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BlackHoleVisualConfig {
    public enum Mode { AUTO, HIGH, LOW, PARTICLES }
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<Mode> MODE = B.comment("AUTO follows existing quality preset. LOW = radial warp + particle ring; PARTICLES disables this family's GLSL.").defineEnum("bhMode", Mode.AUTO);
    private static final ForgeConfigSpec.IntValue DEBUG = B.comment("0 final, 1 normals, 2 steps/deflection, 3 depth/support, 4 R2 calibration grid / R6 2D path.").defineInRange("bhDebugMode", 0, 0, 4);
    private static final ForgeConfigSpec.DoubleValue EXPOSURE = B.defineInRange("bhExposure", .85D, .1D, 3D);
    private static final ForgeConfigSpec.DoubleValue DOPPLER = B.comment("R1 only: visual Doppler multiplier; never affects gravity.").defineInRange("bhDopplerStrength", 1D, 0D, 2D);
    private static final ForgeConfigSpec.IntValue VISIBLE = B.defineInRange("bhMaxVisible", 3, 1, 6);
    public static final ForgeConfigSpec SPEC = ConfigComments.build(B);
    public static volatile Mode mode = Mode.AUTO;
    public static volatile int debugMode = 0, maxVisible = 3;
    public static volatile float exposure = .85F, dopplerStrength = 1F;
    private BlackHoleVisualConfig() {}
    @SubscribeEvent public static void load(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) return;
        mode = MODE.get(); debugMode = DEBUG.get(); exposure = EXPOSURE.get().floatValue();
        dopplerStrength = DOPPLER.get().floatValue(); maxVisible = VISIBLE.get();
    }
}

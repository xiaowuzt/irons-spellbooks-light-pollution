package com.gang.lightpollution;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client-side quality controls for the shared screen-space spell light pass. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpellLightConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable screen-space illumination from migrated spell anchors")
            .define("enabled", true);
    private static final ForgeConfigSpec.IntValue MAX_LIGHTS = BUILDER
            .comment("Legacy light-count hint. The renderer now uploads every active light; this value is retained for config compatibility.")
            .defineInRange("maxLights", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    private static final ForgeConfigSpec.IntValue SHADOW_STEPS = BUILDER
            .comment("Depth-buffer shadow samples per light. Higher values improve contact shadows at a GPU cost.")
            .defineInRange("shadowSteps", 32, 8, 256);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static volatile boolean enabled = true;
    public static volatile int maxLights = Integer.MAX_VALUE;
    public static volatile int shadowSteps = 32;

    private SpellLightConfig() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        enabled = ENABLED.get();
        maxLights = MAX_LIGHTS.get();
        shadowSteps = SHADOW_STEPS.get();
    }
}

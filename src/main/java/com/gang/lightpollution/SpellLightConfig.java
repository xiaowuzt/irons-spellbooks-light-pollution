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

    private static final ForgeConfigSpec.EnumValue<TooltipStyle> TOOLTIP_STYLE = BUILDER
            .comment("How this mod's own tooltips are drawn.",
                    "  VANILLA - the normal box, with only the border tinted per spell.",
                    "  PANEL   - a chamfered panel behind the vanilla layout.",
                    "  ARCANE  - takes rendering over: glow rays, a title separator, own text pass.",
                    "Also switchable in game with /lightpollution frame <vanilla|panel|arcane>.")
            .defineEnum("tooltipStyle", TooltipStyle.VANILLA);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static volatile boolean enabled = true;
    public static volatile int maxLights = Integer.MAX_VALUE;
    public static volatile int shadowSteps = 32;
    /**
     * Which tooltip treatment to draw.
     *
     * <p>Writable at runtime by the command, which is why it is not read straight from the config
     * value on every frame: the command changes this field, and reloading the config overwrites it
     * from disk again.</p>
     */
    public static volatile TooltipStyle tooltipStyle = TooltipStyle.VANILLA;

    /** The three tooltip treatments. */
    public enum TooltipStyle {
        /** The vanilla box, border tinted to the spell's accent. */
        VANILLA,
        /** A chamfered panel drawn behind the vanilla layout, which still does the text. */
        PANEL,
        /**
         * Rendering taken over entirely: glow rays, a separator under the title, own text pass.
         *
         * <p>Safe to take over because the two hard parts are already done by the time the event
         * fires — the components arrive wrapped, and the position has already been corrected away
         * from the screen edges. What is left is drawing.</p>
         */
        ARCANE
    }

    private SpellLightConfig() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        enabled = ENABLED.get();
        tooltipStyle = TOOLTIP_STYLE.get();
        maxLights = MAX_LIGHTS.get();
        shadowSteps = SHADOW_STEPS.get();
    }
}

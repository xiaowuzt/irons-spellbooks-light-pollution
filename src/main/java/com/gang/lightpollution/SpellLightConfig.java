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

    private static final ForgeConfigSpec.BooleanValue ENABLED = ConfigComments
            .bilingual(BUILDER, "enabled",
                    "Enable screen-space illumination from migrated spell anchors.")
            .define("enabled", true);
    private static final ForgeConfigSpec.IntValue MAX_LIGHTS = ConfigComments
            .bilingual(BUILDER, "maxLights",
                    "Hard cap for active spell light sources uploaded each frame. Sources are ranked by intensity and camera influence, with a preference for recent selections.")
            .defineInRange("maxLights", 64, 1, 512);
    private static final ForgeConfigSpec.IntValue SHADOW_STEPS = ConfigComments
            .bilingual(BUILDER, "shadowSteps",
                    "Depth-buffer shadow samples per light. Higher values improve contact shadows at a GPU cost.")
            .defineInRange("shadowSteps", 32, 8, 256);
    private static final ForgeConfigSpec.EnumValue<QualityPreset> QUALITY_PRESET = ConfigComments
            .bilingual(BUILDER, "qualityPreset",
                    "Quality ceiling applied to light count and shadow samples.")
            .defineEnum("qualityPreset", QualityPreset.HIGH);

    private static final ForgeConfigSpec.EnumValue<com.gang.lightpollution.client.perf.AdaptiveQualityController.Mode> ADAPTIVE_MODE = ConfigComments
            .bilingual(BUILDER, "adaptiveMode",
                    "FIXED, AUTO_BALANCED or AUTO_FIDELITY. Only visual budgets are adjusted; gameplay is unchanged.")
            .defineEnum("adaptiveMode", com.gang.lightpollution.client.perf.AdaptiveQualityController.Mode.AUTO_BALANCED);
    private static final ForgeConfigSpec.IntValue TARGET_FPS = ConfigComments
            .bilingual(BUILDER, "adaptiveTargetFps",
                    "Adaptive target, capped by your frame limit and known VSync refresh rate.")
            .defineInRange("adaptiveTargetFps", 60, 20, 240);
    private static final ForgeConfigSpec.EnumValue<QualityPreset> MINIMUM_QUALITY = ConfigComments
            .bilingual(BUILDER, "adaptiveMinimumQuality",
                    "Automatic reduction stops at this floor; qualityPreset remains the ceiling.")
            .defineEnum("adaptiveMinimumQuality", QualityPreset.LOW);

    private static final ForgeConfigSpec.EnumValue<TooltipStyle> TOOLTIP_STYLE = ConfigComments
            .bilingual(BUILDER, "tooltipStyle",
                    "How this mod's own tooltips are drawn. VANILLA, PANEL, ARCANE, ORBIT, ASTRAL, RING, SIGIL or PINWHEEL. Also switchable in game with /lightpollution frame <style>.")
            .defineEnum("tooltipStyle", TooltipStyle.VANILLA);

    private static final ForgeConfigSpec.BooleanValue FLOATING_DAMAGE = ConfigComments
            .bilingual(BUILDER, "floatingDamage",
                    "Show damage from this mod's spells as floating numbers above the target. Off by default because periodic spell damage can create frequent numbers. Also switchable in game with /lightpollution damage <on|off>.")
            .define("floatingDamage", false);

    private static final ForgeConfigSpec.DoubleValue CINEMATIC_FLASH = ConfigComments
            .bilingual(BUILDER, "cinematicFlashStrength",
                    "Strength of the new local impact glows (not a fullscreen flash). Zero disables accents.")
            .defineInRange("cinematicFlashStrength", 0.65D, 0.0D, 1.5D);
    private static final ForgeConfigSpec.DoubleValue LOCAL_DISTORTION = ConfigComments
            .bilingual(BUILDER, "localDistortionStrength",
                    "Local heat haze and shock refraction. Zero disables distortion without hiding spells.")
            .defineInRange("localDistortionStrength", 0.75D, 0.0D, 1.5D);

    public static volatile float cinematicFlashStrength = 0.65F;
    public static volatile float localDistortionStrength = 0.75F;

    private static final ForgeConfigSpec.DoubleValue EVENT_HORIZON_VISUAL_SCALE;
    private static final ForgeConfigSpec.DoubleValue EVENT_HORIZON_ROTATION_SPEED;
    private static final ForgeConfigSpec.DoubleValue EVENT_HORIZON_DISK_BRIGHTNESS;
    static {
        ConfigComments.bilingual(BUILDER, "eventHorizon",
                "Event Horizon / Wu Guang Shi Jie: client-only appearance. Gameplay is unchanged. Damage, cooldown and mana are in the world's serverconfig file under spells.eventHorizon.")
                .push("eventHorizon");
        EVENT_HORIZON_VISUAL_SCALE = ConfigComments
                .bilingual(BUILDER, "eventHorizon.visualScale",
                        "Visual size multiplier. 1 = original size. Does not change pull/damage radius.")
                .defineInRange("visualScale", 1.5D, 0.5D, 3.0D);
        EVENT_HORIZON_ROTATION_SPEED = ConfigComments
                .bilingual(BUILDER, "eventHorizon.rotationSpeed",
                        "Accretion disk rotation multiplier. 1 = base speed; 0 freezes disk flow.")
                .defineInRange("rotationSpeed", 3.0D, 0.0D, 10.0D);
        EVENT_HORIZON_DISK_BRIGHTNESS = ConfigComments
                .bilingual(BUILDER, "eventHorizon.diskBrightness",
                        "Disk, photon-ring and bloom brightness. 0 hides emission, not the black core.")
                .defineInRange("diskBrightness", 1.1D, 0.0D, 3.0D);
        BUILDER.pop();
    }
    public static volatile float eventHorizonVisualScale = 1.5F;
    public static volatile float eventHorizonRotationSpeed = 3.0F;
    public static volatile float eventHorizonDiskBrightness = 1.1F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_VISUAL_SCALE;
    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_ROTATION_SPEED;
    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_DISK_BRIGHTNESS;
    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_TEMPERATURE;
    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_DOPPLER_STRENGTH;
    static {
        ConfigComments.bilingual(BUILDER, "redshiftAbyss",
                "Redshift Abyss / Chi Yi Tian Yuan: client-only appearance. Gameplay is unchanged. Damage, cooldown and mana are in the world's serverconfig file under spells.redshiftAbyss.")
                .push("redshiftAbyss");
        REDSHIFT_ABYSS_VISUAL_SCALE = ConfigComments
                .bilingual(BUILDER, "redshiftAbyss.visualScale",
                        "Visual size multiplier. 1 = original size. Does not change pull/damage radius.")
                .defineInRange("visualScale", 1.25D, 0.5D, 3.0D);
        REDSHIFT_ABYSS_ROTATION_SPEED = ConfigComments
                .bilingual(BUILDER, "redshiftAbyss.rotationSpeed",
                        "Original gas drift and inner-cloud rotation multiplier. 1 = source speed; 0 freezes flow.")
                .defineInRange("rotationSpeed", 1.0D, 0.0D, 10.0D);
        REDSHIFT_ABYSS_DISK_BRIGHTNESS = ConfigComments
                .bilingual(BUILDER, "redshiftAbyss.diskBrightness",
                        "Disk, photon-ring and bloom brightness. 0 hides emission, not the black core.")
                .defineInRange("diskBrightness", 1.0D, 0.0D, 3.0D);
        REDSHIFT_ABYSS_TEMPERATURE = ConfigComments
                .bilingual(BUILDER, "redshiftAbyss.temperature",
                        "Disk temperature multiplier; shifts amber gas toward hot white/blue.")
                .defineInRange("temperature", 1.0D, 0.4D, 2.5D);
        REDSHIFT_ABYSS_DOPPLER_STRENGTH = ConfigComments
                .bilingual(BUILDER, "redshiftAbyss.dopplerStrength",
                        "Strength of the approaching/receding disk colour and brightness asymmetry.")
                .defineInRange("dopplerStrength", 1.0D, 0.0D, 2.0D);
        BUILDER.pop();
    }
    public static volatile float redshiftAbyssVisualScale = 1.25F;
    public static volatile float redshiftAbyssRotationSpeed = 1.0F;
    public static volatile float redshiftAbyssDiskBrightness = 1.0F;
    public static volatile float redshiftAbyssTemperature = 1.0F;
    public static volatile float redshiftAbyssDopplerStrength = 1.0F;

    // Renderer contract: client appearance only; gameplay sizes and timing remain server-owned.
    private static final ForgeConfigSpec.DoubleValue SCHWARZSCHILD_LENS_DISK_THICKNESS = ConfigComments
            .bilingual(BUILDER, "schwarzschildLens.diskThickness",
                    "Accretion gas half-thickness in Schwarzschild radii (Rs); changes the local volume integration, not gameplay collision.",
                    "吸积气体的半厚度，以史瓦西半径 Rs 为单位；改变局部体积积分，不改变玩法碰撞判定。")
            .defineInRange("schwarzschildLens.diskThickness", 0.35D, 0.03D, 1.5D);
    public static volatile float schwarzschildLensDiskThickness = 0.35F;

    private static final ForgeConfigSpec.DoubleValue SCHWARZSCHILD_LENS_NOISE_CONTRAST = ConfigComments
            .bilingual(BUILDER, "schwarzschildLens.noiseContrast",
                    "Contrast of the accretion gas density noise; larger values separate dense filaments from transparent gaps.",
                    "吸积气体密度噪声的对比度；越大越能区分高密度丝状气体与透明空隙。")
            .defineInRange("schwarzschildLens.noiseContrast", 1.4D, 0.1D, 4.0D);
    public static volatile float schwarzschildLensNoiseContrast = 1.4F;

    private static final ForgeConfigSpec.DoubleValue SCHWARZSCHILD_LENS_FLOW_SPEED = ConfigComments
            .bilingual(BUILDER, "schwarzschildLens.flowSpeed",
                    "Visual gas flow speed multiplier; zero freezes the gas pattern without stopping the spell timer.",
                    "气体视觉流动速度倍率；设为 0 冻结气体纹理，不暂停法术计时。")
            .defineInRange("schwarzschildLens.flowSpeed", 1.0D, 0.0D, 5.0D);
    public static volatile float schwarzschildLensFlowSpeed = 1.0F;

    private static final ForgeConfigSpec.DoubleValue SCHWARZSCHILD_LENS_DISK_BRIGHTNESS = ConfigComments
            .bilingual(BUILDER, "schwarzschildLens.diskBrightness",
                    "Accretion gas emission multiplier; zero suppresses disk emission, not the spell entity.",
                    "吸积气体自发光倍率；设为 0 关闭盘面发光，不移除法术实体。")
            .defineInRange("schwarzschildLens.diskBrightness", 1.0D, 0.0D, 4.0D);
    public static volatile float schwarzschildLensDiskBrightness = 1.0F;

    private static final ForgeConfigSpec.DoubleValue RADIANT_COLLAPSE_TIME_SCALE = ConfigComments
            .bilingual(BUILDER, "radiantCollapse.timeScale",
                    "Time multiplier in the original two-dimensional Radiant Collapse formula; zero freezes shader animation only.",
                    "辉环崩解原始二维公式的时间倍率；设为 0 只冻结着色器动画。")
            .defineInRange("radiantCollapse.timeScale", 1.0D, 0.0D, 5.0D);
    public static volatile float radiantCollapseTimeScale = 1.0F;

    private static final ForgeConfigSpec.DoubleValue RADIANT_COLLAPSE_VISUAL_SCALE = ConfigComments
            .bilingual(BUILDER, "radiantCollapse.visualScale",
                    "World-anchored, camera-facing formula size multiplier. Does NOT change the spherical gameplay hit test or damage radius.",
                    "固定于世界位置并朝向相机的公式画面尺寸倍率；不改变球形玩法命中判定或伤害半径。")
            .defineInRange("radiantCollapse.visualScale", 1.0D, 0.25D, 3.0D);
    public static volatile float radiantCollapseVisualScale = 1.0F;

    private static final ForgeConfigSpec.DoubleValue RADIANT_COLLAPSE_INTENSITY = ConfigComments
            .bilingual(BUILDER, "radiantCollapse.intensity",
                    "Emission intensity multiplier of the original Radiant Collapse formula; zero hides this emission without disabling damage.",
                    "辉环崩解原始公式的发光强度倍率；设为 0 隐藏该发光，但不关闭伤害。")
            .defineInRange("radiantCollapse.intensity", 1.0D, 0.0D, 4.0D);
    public static volatile float radiantCollapseIntensity = 1.0F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_SOURCE_TIME_RATE = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.sourceTimeRate",
                    "Original BufferA simulation seconds per game second, multiplied by rotationSpeed; zero freezes gas evolution only.",
                    "原始 BufferA 每游戏秒推进的模拟秒数，再乘 rotationSpeed；设为 0 只冻结气体演化。")
            .defineInRange("redshiftAbyss.sourceTimeRate", 30.0D, 0.0D, 120.0D);
    public static volatile float redshiftAbyssSourceTimeRate = 30.0F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_MASS_SOLAR = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.massSolar",
                    "Black-hole mass in solar masses used to derive the Schwarzschild radius, temperature and gas orbital rate; not entity size or gravity strength.",
                    "黑洞质量，单位为太阳质量；用于推导史瓦西半径、温度和气体轨道速率，不是实体大小或玩法引力强度。")
            .defineInRange("redshiftAbyss.massSolar", 14900000.0D, 1000000.0D, 1000000000.0D);
    public static volatile float redshiftAbyssMassSolar = 14900000.0F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_ACCRETION_RATE = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.accretionRate",
                    "Mass accretion rate as a fraction of the Eddington rate; affects the original thermal emission calculation.",
                    "相对于爱丁顿吸积率的质量吸积率；影响原始热辐射计算。")
            .defineInRange("redshiftAbyss.accretionRate", 2e-06D, 1e-08D, 0.001D);
    public static volatile float redshiftAbyssAccretionRate = 2e-06F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_INNER_RADIUS_RS = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.innerRadiusRs",
                    "Accretion disk inner edge in Schwarzschild radii (source: 0.7 times ISCO = 2.1 Rs); does not resize gameplay bounds.",
                    "吸积盘内缘，以史瓦西半径 Rs 为单位（原始值为 0.7 倍最内稳定圆轨，即 2.1 Rs）；不改变玩法范围。")
            .defineInRange("redshiftAbyss.innerRadiusRs", 2.1D, 1.6D, 5.0D);
    public static volatile float redshiftAbyssInnerRadiusRs = 2.1F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_OUTER_RADIUS_RS = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.outerRadiusRs",
                    "Accretion disk outer edge in Schwarzschild radii; stays beyond every allowed inner edge.",
                    "吸积盘外缘，以史瓦西半径 Rs 为单位；允许范围保证始终大于内缘。")
            .defineInRange("redshiftAbyss.outerRadiusRs", 12.0D, 6.0D, 24.0D);
    public static volatile float redshiftAbyssOuterRadiusRs = 12.0F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_DISK_THICKNESS_RS = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.diskThicknessRs",
                    "Accretion gas half-thickness in Schwarzschild radii (the original Thin parameter).",
                    "吸积气体半厚度，以史瓦西半径 Rs 为单位，对应原始 Thin 参数。")
            .defineInRange("redshiftAbyss.diskThicknessRs", 0.5D, 0.05D, 2.0D);
    public static volatile float redshiftAbyssDiskThicknessRs = 0.5F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_NOISE_CONTRAST = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.noiseContrast",
                    "Contrast exponent passed to the original multilevel accretion gas noise; higher values sharpen gas clumps.",
                    "传入原始多层吸积气体噪声的对比度指数；越大气体团块的密度反差越明显。")
            .defineInRange("redshiftAbyss.noiseContrast", 80.0D, 10.0D, 120.0D);
    public static volatile float redshiftAbyssNoiseContrast = 80.0F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_SHIFT_MAX = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.shiftMax",
                    "Upper brightness gain from relativistic blueshift and Doppler shift; does not modify gameplay damage.",
                    "相对论蓝移与多普勒偏移的亮度增益上限；不影响玩法伤害。")
            .defineInRange("redshiftAbyss.shiftMax", 1.25D, 1.0D, 3.0D);
    public static volatile float redshiftAbyssShiftMax = 1.25F;

    private static final ForgeConfigSpec.DoubleValue REDSHIFT_ABYSS_BLOOM_STRENGTH = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.bloomStrength",
                    "Weight of the reconstructed multiscale HDR bloom in the original Image pass; zero disables bloom, not gas emission.",
                    "原始 Image 阶段中重建的多尺度 HDR 泛光权重；设为 0 关闭泛光，但保留气体自发光。")
            .defineInRange("redshiftAbyss.bloomStrength", 0.08D, 0.0D, 0.3D);
    public static volatile float redshiftAbyssBloomStrength = 0.08F;

    private static final ForgeConfigSpec.BooleanValue REDSHIFT_ABYSS_TEMPORAL_ACCUMULATION = ConfigComments
            .bilingual(BUILDER, "redshiftAbyss.temporalAccumulation",
                    "Accumulate stable prior frames to reduce stochastic gas sampling noise; history is reset when camera/effect state is incompatible.",
                    "累积兼容的历史帧以减少气体随机采样噪声；相机或效果状态不兼容时重置历史。")
            .define("redshiftAbyss.temporalAccumulation", true);
    public static volatile boolean redshiftAbyssTemporalAccumulation = true;

    public static final ForgeConfigSpec SPEC = ConfigComments.build(BUILDER);

    public static volatile boolean enabled = true;
    public static volatile int maxLights = 64;
    public static volatile int shadowSteps = 32;
    public static volatile QualityPreset qualityPreset = QualityPreset.HIGH;
    public static volatile com.gang.lightpollution.client.perf.AdaptiveQualityController.Mode adaptiveMode =
            com.gang.lightpollution.client.perf.AdaptiveQualityController.Mode.AUTO_BALANCED;
    public static volatile int adaptiveTargetFps = 60;
    public static volatile QualityPreset adaptiveMinimumQuality = QualityPreset.LOW;
    /**
     * Which tooltip treatment to draw.
     *
     * <p>Writable at runtime by the command, which is why it is not read straight from the config
     * value on every frame: the command changes this field, and reloading the config overwrites it
     * from disk again.</p>
     */
    public static volatile TooltipStyle tooltipStyle = TooltipStyle.VANILLA;
    /**
     * Whether floating damage numbers are drawn.
     *
     * <p>Off by default. These spells tick damage rather than landing single hits, so even after
     * being merged per target the numbers appear several times a second during a cast, which is a
     * lot of motion to opt somebody into.</p>
     */
    public static volatile boolean floatingDamage;

    /**
     * The tooltip treatments.
     *
     * <p>Each one after VANILLA is a recipe over the shared elements ported from ArcaneVortex's nine
     * separate tooltip renderers. Those nine mostly differed in which pieces they used and in
     * palette; here the palette is always the spell's own accent, so a tooltip looks like the spell
     * it describes rather than like a weapon from another mod.</p>
     */
    public enum TooltipStyle {
        /** The vanilla box, border tinted to the spell's accent. */
        VANILLA,
        /** A rounded panel drawn behind the vanilla layout, which still does the text. */
        PANEL,
        /**
         * Rendering taken over: glow rays behind the box, a centred title, a rule under it.
         *
         * <p>Safe to take over because the two hard parts are already done by the time the event
         * fires — the components arrive wrapped, and the position has already been resolved. What is
         * left is drawing.</p>
         */
        ARCANE,
        /** The spell's own icon drifting behind the panel, with ghost copies trailing it. */
        ORBIT,
        /** A drifting particle field, turning corner marks, and a band of light under the title. */
        ASTRAL,
        /**
         * A tilted ring threaded through the tooltip.
         *
         * <p>The far half is drawn, then the panel, then the near half, so the ring appears to pass
         * behind the box and out the other side.</p>
         */
        RING,
        /** Two counter-rotating triangular bands behind the panel. */
        SIGIL,
        /** No panel: the lines orbit as spokes of a slowly turning pinwheel. */
        PINWHEEL
    }

    private SpellLightConfig() {
    }

    public enum QualityPreset {
        LOW(16, 12),
        MEDIUM(32, 24),
        HIGH(64, 32),
        ULTRA(128, 64);

        private final int lightCap;
        private final int shadowCap;

        QualityPreset(int lightCap, int shadowCap) {
            this.lightCap = lightCap;
            this.shadowCap = shadowCap;
        }
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        enabled = ENABLED.get();
        schwarzschildLensDiskThickness = SCHWARZSCHILD_LENS_DISK_THICKNESS.get().floatValue();
        schwarzschildLensNoiseContrast = SCHWARZSCHILD_LENS_NOISE_CONTRAST.get().floatValue();
        schwarzschildLensFlowSpeed = SCHWARZSCHILD_LENS_FLOW_SPEED.get().floatValue();
        schwarzschildLensDiskBrightness = SCHWARZSCHILD_LENS_DISK_BRIGHTNESS.get().floatValue();
        radiantCollapseTimeScale = RADIANT_COLLAPSE_TIME_SCALE.get().floatValue();
        radiantCollapseVisualScale = RADIANT_COLLAPSE_VISUAL_SCALE.get().floatValue();
        radiantCollapseIntensity = RADIANT_COLLAPSE_INTENSITY.get().floatValue();
        redshiftAbyssSourceTimeRate = REDSHIFT_ABYSS_SOURCE_TIME_RATE.get().floatValue();
        redshiftAbyssMassSolar = REDSHIFT_ABYSS_MASS_SOLAR.get().floatValue();
        redshiftAbyssAccretionRate = REDSHIFT_ABYSS_ACCRETION_RATE.get().floatValue();
        redshiftAbyssInnerRadiusRs = REDSHIFT_ABYSS_INNER_RADIUS_RS.get().floatValue();
        redshiftAbyssOuterRadiusRs = REDSHIFT_ABYSS_OUTER_RADIUS_RS.get().floatValue();
        redshiftAbyssDiskThicknessRs = REDSHIFT_ABYSS_DISK_THICKNESS_RS.get().floatValue();
        redshiftAbyssNoiseContrast = REDSHIFT_ABYSS_NOISE_CONTRAST.get().floatValue();
        redshiftAbyssShiftMax = REDSHIFT_ABYSS_SHIFT_MAX.get().floatValue();
        redshiftAbyssBloomStrength = REDSHIFT_ABYSS_BLOOM_STRENGTH.get().floatValue();
        redshiftAbyssTemporalAccumulation = REDSHIFT_ABYSS_TEMPORAL_ACCUMULATION.get();
        eventHorizonVisualScale = EVENT_HORIZON_VISUAL_SCALE.get().floatValue();
        eventHorizonRotationSpeed = EVENT_HORIZON_ROTATION_SPEED.get().floatValue();
        eventHorizonDiskBrightness = EVENT_HORIZON_DISK_BRIGHTNESS.get().floatValue();
        redshiftAbyssVisualScale = REDSHIFT_ABYSS_VISUAL_SCALE.get().floatValue();
        redshiftAbyssRotationSpeed = REDSHIFT_ABYSS_ROTATION_SPEED.get().floatValue();
        redshiftAbyssDiskBrightness = REDSHIFT_ABYSS_DISK_BRIGHTNESS.get().floatValue();
        redshiftAbyssTemperature = REDSHIFT_ABYSS_TEMPERATURE.get().floatValue();
        redshiftAbyssDopplerStrength = REDSHIFT_ABYSS_DOPPLER_STRENGTH.get().floatValue();
        cinematicFlashStrength = CINEMATIC_FLASH.get().floatValue();
        localDistortionStrength = LOCAL_DISTORTION.get().floatValue();
        tooltipStyle = TOOLTIP_STYLE.get();
        floatingDamage = FLOATING_DAMAGE.get();
        qualityPreset = QUALITY_PRESET.get();
        adaptiveMode = ADAPTIVE_MODE.get();
        adaptiveTargetFps = TARGET_FPS.get();
        adaptiveMinimumQuality = MINIMUM_QUALITY.get();
        maxLights = Math.max(1, Math.min(MAX_LIGHTS.get(), qualityPreset.lightCap));
        shadowSteps = Math.max(8, Math.min(SHADOW_STEPS.get(), qualityPreset.shadowCap));
    }
}

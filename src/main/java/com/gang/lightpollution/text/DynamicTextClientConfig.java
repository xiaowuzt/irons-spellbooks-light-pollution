package com.gang.lightpollution.text;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** 客户端效果与性能配置。读取方法在配置尚未加载时会回退到安全默认值。 */
public final class DynamicTextClientConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue RAINBOW_ENABLED;
    private static final ForgeConfigSpec.DoubleValue RAINBOW_SPEED;
    private static final ForgeConfigSpec.BooleanValue GLITCH_ENABLED;
    private static final ForgeConfigSpec.IntValue GLITCH_FREQUENCY;
    private static final ForgeConfigSpec.BooleanValue CYBER_ENABLED;
    private static final ForgeConfigSpec.BooleanValue MAGIC_ENABLED;
    private static final ForgeConfigSpec.BooleanValue HOLOGRAPHIC_ENABLED;
    private static final ForgeConfigSpec.BooleanValue ENERGY_BAR_ENABLED;
    private static final ForgeConfigSpec.BooleanValue LAVA_ENABLED;
    private static final ForgeConfigSpec.BooleanValue PARCHMENT_ENABLED;
    private static final ForgeConfigSpec.BooleanValue RED_SPEED_NEON_ENABLED;
    private static final ForgeConfigSpec.BooleanValue SYNTHWAVE_NEON_ENABLED;
    private static final ForgeConfigSpec.IntValue REFRESH_INTERVAL;
    private static final ForgeConfigSpec.IntValue MAX_TEXT_LENGTH;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMPATIBLE_SCREENS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("effects");
        ENABLED = builder
                .comment("总开关。关闭后仍会移除动态控制码，但不绘制视觉效果。")
                .define("enabled", true);
        RAINBOW_ENABLED = builder
                .comment("是否启用 &p 快速流动彩虹渐变。")
                .define("rainbowEnabled", true);
        RAINBOW_SPEED = builder
                .comment("&p 以及渐变类效果每秒流动的色相循环数。")
                .defineInRange("rainbowSpeed", 1.35D, 0.05D, 8.0D);
        GLITCH_ENABLED = builder
                .comment("是否启用 &g 闪烁与破碎残影；主文字不会抖动。")
                .define("glitchEnabled", true);
        GLITCH_FREQUENCY = builder
                .comment("&g、&h、&u 与 &m 每秒尝试改变故障状态的次数。")
                .defineInRange("glitchFrequency", 16, 1, 60);
        CYBER_ENABLED = builder
                .comment("是否启用 &h 赛博故障、红青色差与像素残影。")
                .define("cyberEnabled", true);
        MAGIC_ENABLED = builder
                .comment("是否启用 &q 暗黑魔法紫青霓虹与光晕。")
                .define("magicEnabled", true);
        HOLOGRAPHIC_ENABLED = builder
                .comment("是否启用 &y 全息镭射、幻彩金属高光。")
                .define("holographicEnabled", true);
        ENERGY_BAR_ENABLED = builder
                .comment("是否启用 &s 半透明青色高亮能量条。")
                .define("energyBarEnabled", true);
        LAVA_ENABLED = builder
                .comment("是否启用 &t 熔岩等离子发光文字。")
                .define("lavaEnabled", true);
        PARCHMENT_ENABLED = builder
                .comment("是否启用 &v 羊皮纸法术书渐变与局部青色高亮。")
                .define("parchmentEnabled", true);
        RED_SPEED_NEON_ENABLED = builder
                .comment("是否启用 &u 强红霓虹外发光、左右速度线、红黑条纹与动态故障。")
                .define("redSpeedNeonEnabled", true);
        SYNTHWAVE_NEON_ENABLED = builder
                .comment("是否启用 &m 青蓝至品红紫 3D 霓虹、像素边缘、柔光与 VHS 故障。")
                .define("synthwaveNeonEnabled", true);
        builder.pop();

        builder.push("performance");
        REFRESH_INTERVAL = builder
                .comment("动画刷新间隔（毫秒）。数值越小越流畅，性能开销越高。")
                .defineInRange("refreshIntervalMs", 40, 16, 1000);
        MAX_TEXT_LENGTH = builder
                .comment("每段文本最多播放动态效果的可见字符数；超出部分保持静态但不会被截断。")
                .defineInRange("maxTextLength", 512, 16, 8192);
        COMPATIBLE_SCREENS = builder
                .comment("允许动态效果的界面类名片段。* 代表全部，hud 代表无界面的 HUD 文本。")
                .defineListAllowEmpty("compatibleScreens", List.of("*"), value -> value instanceof String);
        builder.pop();

        SPEC = com.gang.lightpollution.ConfigComments.build(builder);
    }

    private DynamicTextClientConfig() {
    }

    public static boolean enabled() {
        return safe(ENABLED, true);
    }

    public static boolean rainbowEnabled() {
        return safe(RAINBOW_ENABLED, true);
    }

    public static double rainbowSpeed() {
        return safe(RAINBOW_SPEED, 1.35D);
    }

    public static boolean glitchEnabled() {
        return safe(GLITCH_ENABLED, true);
    }

    public static int glitchFrequency() {
        return safe(GLITCH_FREQUENCY, 16);
    }

    public static boolean cyberEnabled() {
        return safe(CYBER_ENABLED, true);
    }

    public static boolean magicEnabled() {
        return safe(MAGIC_ENABLED, true);
    }

    public static boolean holographicEnabled() {
        return safe(HOLOGRAPHIC_ENABLED, true);
    }

    public static boolean energyBarEnabled() {
        return safe(ENERGY_BAR_ENABLED, true);
    }

    public static boolean lavaEnabled() {
        return safe(LAVA_ENABLED, true);
    }

    public static boolean parchmentEnabled() {
        return safe(PARCHMENT_ENABLED, true);
    }

    public static boolean redSpeedNeonEnabled() {
        return safe(RED_SPEED_NEON_ENABLED, true);
    }

    public static boolean synthwaveNeonEnabled() {
        return safe(SYNTHWAVE_NEON_ENABLED, true);
    }

    public static int refreshIntervalMs() {
        return safe(REFRESH_INTERVAL, 40);
    }

    public static int maxTextLength() {
        return safe(MAX_TEXT_LENGTH, 512);
    }

    public static List<? extends String> compatibleScreens() {
        return safe(COMPATIBLE_SCREENS, List.of("*"));
    }

    private static <T> T safe(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T result = value.get();
            return result == null ? fallback : result;
        } catch (IllegalStateException | NullPointerException ignored) {
            return fallback;
        }
    }
}

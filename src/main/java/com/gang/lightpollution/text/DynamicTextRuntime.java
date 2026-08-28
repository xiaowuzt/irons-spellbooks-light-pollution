package com.gang.lightpollution.text;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.DynamicTextClientConfig;
import com.gang.lightpollution.text.EffectStyle;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 仅在客户端渲染线程使用的动画时钟、界面过滤、颜色工具和临时效果开关。 */
public final class DynamicTextRuntime {
    private static final Map<Integer, Boolean> OVERRIDES = new ConcurrentHashMap<>();
    private static volatile Boolean masterOverride;
    private static long checkedFrame = Long.MIN_VALUE;
    private static boolean screenAllowed = true;

    private DynamicTextRuntime() {
    }

    public static int effectiveMask(int requestedMask) {
        if (!masterEnabled() || !isCurrentScreenAllowed()) {
            return 0;
        }

        int result = 0;
        for (int effect : new int[]{
                EffectStyle.RAINBOW,
                EffectStyle.GLITCH,
                EffectStyle.CYBER,
                EffectStyle.MAGIC,
                EffectStyle.HOLOGRAPHIC,
                EffectStyle.ENERGY_BAR,
                EffectStyle.LAVA,
                EffectStyle.PARCHMENT,
                EffectStyle.RED_SPEED_NEON,
                EffectStyle.SYNTHWAVE_NEON
        }) {
            if ((requestedMask & effect) != 0 && effectEnabled(effect)) {
                result |= effect;
            }
        }
        return result;
    }

    public static void setMasterOverride(Boolean enabled) {
        masterOverride = enabled;
        invalidateScreenCache();
    }

    public static void setEffectOverride(int effect, Boolean enabled) {
        if (enabled == null) {
            OVERRIDES.remove(effect);
        } else {
            OVERRIDES.put(effect, enabled);
        }
    }

    public static void clearOverrides() {
        masterOverride = null;
        OVERRIDES.clear();
        invalidateScreenCache();
    }

    public static long animationFrame() {
        return System.currentTimeMillis() / refreshIntervalMs();
    }

    public static double animationSeconds() {
        return animationFrame() * refreshIntervalMs() / 1000.0D;
    }

    public static int refreshIntervalMs() {
        return DynamicTextClientConfig.refreshIntervalMs();
    }

    public static double rainbowSpeed() {
        return DynamicTextClientConfig.rainbowSpeed();
    }

    public static int glitchFrequency() {
        return DynamicTextClientConfig.glitchFrequency();
    }

    public static boolean glitchActive(long hash) {
        double attemptsPerFrame = glitchFrequency() * refreshIntervalMs() / 1000.0D;
        double probability = Math.min(0.88D, Math.max(0.08D, attemptsPerFrame * 0.85D));
        return unit(hash) < probability;
    }

    public static long hash(long frame, int index, int codePoint, int salt) {
        long value = frame * 0x9E3779B97F4A7C15L;
        value ^= (long) index * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) codePoint * 0x165667B19E3779F9L;
        value ^= (long) salt * 0x85EBCA77C2B2AE63L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static float unit(long hash) {
        return (float) ((hash >>> 11) * 0x1.0p-53);
    }

    public static float signed(long hash) {
        return unit(hash) * 2.0F - 1.0F;
    }

    public static int rainbowColor(int index) {
        double hue = animationSeconds() * rainbowSpeed() + index * 0.075D;
        return hsvToRgb((float) (hue - Math.floor(hue)), 0.92F, 1.0F);
    }

    public static int paletteColor(int index, double speed, int... palette) {
        if (palette == null || palette.length == 0) {
            return 0xFFFFFF;
        }
        if (palette.length == 1) {
            return palette[0] & 0xFFFFFF;
        }

        double cycle = animationSeconds() * speed + index * 0.135D;
        double wrapped = cycle - Math.floor(cycle);
        double scaled = wrapped * palette.length;
        int first = Math.floorMod((int) Math.floor(scaled), palette.length);
        int second = (first + 1) % palette.length;
        return mixColor(palette[first], palette[second], (float) (scaled - Math.floor(scaled)));
    }

    public static int mixColor(int first, int second, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(((first >> 16) & 255) * (1.0F - clamped) + ((second >> 16) & 255) * clamped);
        int green = Math.round(((first >> 8) & 255) * (1.0F - clamped) + ((second >> 8) & 255) * clamped);
        int blue = Math.round((first & 255) * (1.0F - clamped) + (second & 255) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    public static float pulse(int index, double speed) {
        double phase = animationSeconds() * speed * Math.PI * 2.0D + index * 0.52D;
        return (float) (0.5D + Math.sin(phase) * 0.5D);
    }

    public static void invalidateScreenCache() {
        checkedFrame = Long.MIN_VALUE;
    }

    private static boolean masterEnabled() {
        return masterOverride != null ? masterOverride : DynamicTextClientConfig.enabled();
    }

    private static boolean effectEnabled(int effect) {
        Boolean override = OVERRIDES.get(effect);
        if (override != null) {
            return override;
        }
        return switch (effect) {
            case EffectStyle.RAINBOW -> DynamicTextClientConfig.rainbowEnabled();
            case EffectStyle.GLITCH -> DynamicTextClientConfig.glitchEnabled();
            case EffectStyle.CYBER -> DynamicTextClientConfig.cyberEnabled();
            case EffectStyle.MAGIC -> DynamicTextClientConfig.magicEnabled();
            case EffectStyle.HOLOGRAPHIC -> DynamicTextClientConfig.holographicEnabled();
            case EffectStyle.ENERGY_BAR -> DynamicTextClientConfig.energyBarEnabled();
            case EffectStyle.LAVA -> DynamicTextClientConfig.lavaEnabled();
            case EffectStyle.PARCHMENT -> DynamicTextClientConfig.parchmentEnabled();
            case EffectStyle.RED_SPEED_NEON -> DynamicTextClientConfig.redSpeedNeonEnabled();
            case EffectStyle.SYNTHWAVE_NEON -> DynamicTextClientConfig.synthwaveNeonEnabled();
            default -> false;
        };
    }

    private static boolean isCurrentScreenAllowed() {
        long frame = animationFrame();
        if (checkedFrame == frame) {
            return screenAllowed;
        }

        checkedFrame = frame;
        List<? extends String> filters = DynamicTextClientConfig.compatibleScreens();
        if (filters.isEmpty()) {
            screenAllowed = false;
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String screenName = minecraft.screen == null
                ? "hud"
                : minecraft.screen.getClass().getName().toLowerCase(Locale.ROOT);

        screenAllowed = filters.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> "*".equals(value)
                        || screenName.equals(value)
                        || screenName.startsWith(value)
                        || screenName.contains(value));
        return screenAllowed;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float scaled = hue * 6.0F;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - fraction * saturation);
        float t = value * (1.0F - (1.0F - fraction) * saturation);

        float red;
        float green;
        float blue;
        switch (Math.floorMod(sector, 6)) {
            case 0 -> { red = value; green = t; blue = p; }
            case 1 -> { red = q; green = value; blue = p; }
            case 2 -> { red = p; green = value; blue = t; }
            case 3 -> { red = p; green = q; blue = value; }
            case 4 -> { red = t; green = p; blue = value; }
            default -> { red = value; green = p; blue = q; }
        }

        return ((int) (red * 255.0F) << 16)
                | ((int) (green * 255.0F) << 8)
                | (int) (blue * 255.0F);
    }
}

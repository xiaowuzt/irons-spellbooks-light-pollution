package com.gang.lightpollution.text;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.DynamicTextParser;
import com.gang.lightpollution.text.EffectStyle;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java 与脚本通用入口。
 * KubeJS 可通过 Java.loadClass("com.gang.lightpollution.text.DynamicTextApi") 调用这些静态方法。
 */
public final class DynamicTextApi {
    private static final AtomicLong REFRESH_REVISION = new AtomicLong();

    private DynamicTextApi() {
    }

    public static Component parse(String text) {
        return DynamicTextParser.parse(text);
    }

    public static Component parse(Component text) {
        return DynamicTextParser.parse(text);
    }

    public static Component rainbow(String text) {
        return parse("&p" + nullToEmpty(text));
    }

    public static Component glitch(String text) {
        return parse("&g" + nullToEmpty(text));
    }

    public static Component cyber(String text) {
        return parse("&h" + nullToEmpty(text));
    }

    public static Component magicalNeon(String text) {
        return parse("&q" + nullToEmpty(text));
    }

    public static Component holographic(String text) {
        return parse("&y" + nullToEmpty(text));
    }

    public static Component energyBar(String text) {
        return parse("&s" + nullToEmpty(text));
    }

    public static Component lava(String text) {
        return parse("&t" + nullToEmpty(text));
    }

    public static Component parchment(String text) {
        return parse("&v" + nullToEmpty(text));
    }

    public static Component redSpeedNeon(String text) {
        return parse("&u" + nullToEmpty(text));
    }

    public static Component synthwaveNeon(String text) {
        return parse("&m" + nullToEmpty(text));
    }

    /**
     * 1.x 兼容方法。&o 的动态渲染已经移除，因此这里只返回无动态效果的文字。
     */
    @Deprecated(forRemoval = false)
    public static Component jitter(String text) {
        return parse(nullToEmpty(text));
    }

    /** 组合当前全部十种效果；实际视觉层可继续通过客户端配置独立关闭。 */
    public static Component combined(String text) {
        return parse("&p&g&h&q&y&s&t&v&u&m" + nullToEmpty(text));
    }

    public static String stripEffectCodes(String text) {
        return DynamicTextParser.stripEffectCodes(text);
    }

    /** 设置客户端总开关的临时覆盖；重新启动游戏后仍以配置文件为准。 */
    public static void setClientEffectsEnabled(boolean enabled) {
        onClient(() -> DynamicTextRuntime.setMasterOverride(enabled));
    }

    /** effect 接受 p/g/h/q/y/s/t/v/u/m、对应完整英文名或带 & 的形式。 */
    public static boolean setEffectEnabled(String effect, boolean enabled) {
        int mask = effectMask(effect);
        if (mask == 0 || !isClient()) {
            return false;
        }
        onClient(() -> DynamicTextRuntime.setEffectOverride(mask, enabled));
        return true;
    }

    public static void clearRuntimeOverrides() {
        onClient(DynamicTextRuntime::clearOverrides);
    }

    /**
     * Clears the screen-kind cache and bumps the revision.
     *
     * <p>The FTB Quests cache rebuild the original had here is gone with the rest of that
     * compat layer — this mod has no quest text to invalidate.</p>
     */
    public static long refresh() {
        onClient(DynamicTextRuntime::invalidateScreenCache);
        return REFRESH_REVISION.incrementAndGet();
    }

    public static long refreshRevision() {
        return REFRESH_REVISION.get();
    }

    private static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    /**
     * Run something that touches {@link DynamicTextRuntime}, but only on a client.
     *
     * <p>That class reaches for {@code net.minecraft.client.Minecraft}, which a dedicated server does
     * not have, so it must not be resolved there. This used to be a reflective lookup by class name —
     * which kept the server safe but named the package this code lived in <em>before</em> it was
     * flattened into this mod, so every call here had been silently doing nothing. Going through
     * DistExecutor keeps the same isolation without a string that can rot.</p>
     */
    private static void onClient(Runnable action) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> action::run);
    }

    private static int effectMask(String effect) {
        if (effect == null) {
            return 0;
        }
        return switch (effect.trim().toLowerCase(Locale.ROOT)) {
            case "p", "&p", "rainbow" -> EffectStyle.RAINBOW;
            case "g", "&g", "glitch" -> EffectStyle.GLITCH;
            case "h", "&h", "cyber", "cyber_glitch" -> EffectStyle.CYBER;
            case "q", "&q", "magic", "magical_neon" -> EffectStyle.MAGIC;
            case "y", "&y", "holographic", "iridescent" -> EffectStyle.HOLOGRAPHIC;
            case "s", "&s", "energy", "energy_bar" -> EffectStyle.ENERGY_BAR;
            case "t", "&t", "lava", "plasma" -> EffectStyle.LAVA;
            case "v", "&v", "parchment", "spellbook" -> EffectStyle.PARCHMENT;
            case "u", "&u", "red_speed_neon", "red_neon", "speed_glitch" -> EffectStyle.RED_SPEED_NEON;
            case "m", "&m", "synthwave_neon", "vhs_neon", "neon_3d" -> EffectStyle.SYNTHWAVE_NEON;
            default -> 0;
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

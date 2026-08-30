package com.gang.lightpollution.text;

import com.gang.lightpollution.ExampleMod;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.Optional;

/**
 * 将效果位编码进 Style.font，既能穿过换行、视觉重排、物品 tooltip 和 ModernUI 布局缓存，
 * 又不会占用显示字符。实际查找字体资源前由兼容注入还原原字体 ID。
 */
public final class EffectStyle {
    public static final int RAINBOW = 1;
    public static final int GLITCH = 1 << 1;
    public static final int CYBER = 1 << 2;
    public static final int MAGIC = 1 << 3;
    public static final int HOLOGRAPHIC = 1 << 4;
    public static final int ENERGY_BAR = 1 << 5;
    public static final int LAVA = 1 << 6;
    public static final int PARCHMENT = 1 << 7;
    public static final int RED_SPEED_NEON = 1 << 8;
    public static final int SYNTHWAVE_NEON = 1 << 9;

    public static final int ALL = RAINBOW
            | GLITCH
            | CYBER
            | MAGIC
            | HOLOGRAPHIC
            | ENERGY_BAR
            | LAVA
            | PARCHMENT
            | RED_SPEED_NEON
            | SYNTHWAVE_NEON;

    private static final String PREFIX = "fx/";

    private EffectStyle() {
    }

    public static Style withMask(Style style, int mask) {
        return encode(style, mask, 0);
    }

    /**
     * Carry both the ten-code bit mask and an animation spec id.
     *
     * <p>The two are packed as {@code <mask>.<animId>}. A dot is legal in a resource path and cannot
     * occur in a decimal integer, so a path written by an older version — which had no dot — still
     * decodes. That matters because two mods ship this encoding and a player may have either
     * version.</p>
     */
    public static Style encode(Style style, int mask, int animId) {
        Style source = style == null ? Style.EMPTY : style;
        ResourceLocation base = baseFont(source.getFont());
        int activeMask = mask & ALL;
        if (activeMask == 0 && animId == 0) {
            return source.withFont(base);
        }
        String slot = animId == 0 ? String.valueOf(activeMask) : activeMask + "." + animId;
        String path = PREFIX + slot + "/" + base.getNamespace() + "/" + base.getPath();
        return source.withFont(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
    }

    /** The animation spec id carried by this style, or 0. */
    public static int animId(Style style) {
        return style == null ? 0 : animId(style.getFont());
    }

    public static int animId(ResourceLocation font) {
        String slot = slot(font);
        if (slot == null) {
            return 0;
        }
        int dot = slot.indexOf('.');
        if (dot < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(slot.substring(dot + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** The {@code <mask>} or {@code <mask>.<animId>} segment, or null when not encoded. */
    private static String slot(ResourceLocation font) {
        if (!isEncoded(font)) {
            return null;
        }
        String[] parts = font.getPath().split("/", 4);
        return parts.length < 4 ? null : parts[1];
    }

    public static int mask(Style style) {
        return style == null ? 0 : mask(style.getFont());
    }

    public static int mask(ResourceLocation font) {
        String slot = slot(font);
        if (slot == null) {
            return 0;
        }
        int dot = slot.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? slot : slot.substring(0, dot)) & ALL;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static boolean isEncoded(Style style) {
        return style != null && isEncoded(style.getFont());
    }

    public static boolean isEncoded(ResourceLocation font) {
        return font != null
                && ExampleMod.MODID.equals(font.getNamespace())
                && font.getPath().startsWith(PREFIX);
    }

    /** 移除本模组效果元数据，同时保留原 insertion、事件与所有原版格式。 */
    public static Style clean(Style style) {
        if (style == null) {
            return Style.EMPTY;
        }
        return isEncoded(style.getFont()) ? style.withFont(baseFont(style.getFont())) : style;
    }

    public static ResourceLocation baseFont(ResourceLocation font) {
        if (!isEncoded(font)) {
            return font == null ? Style.DEFAULT_FONT : font;
        }

        String[] parts = font.getPath().split("/", 4);
        if (parts.length < 4) {
            return Style.DEFAULT_FONT;
        }

        ResourceLocation decoded = ResourceLocation.tryBuild(parts[2], parts[3]);
        return decoded == null ? Style.DEFAULT_FONT : decoded;
    }

    public static boolean hasEffects(FormattedText text) {
        if (text == null) {
            return false;
        }

        boolean[] found = {false};
        text.visit((style, segment) -> {
            if (mask(style) != 0 || animId(style) != 0) {
                found[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    public static boolean hasEffects(FormattedCharSequence sequence) {
        if (sequence == null) {
            return false;
        }

        boolean[] found = {false};
        sequence.accept((index, style, codePoint) -> {
            if (mask(style) != 0 || animId(style) != 0) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }
}

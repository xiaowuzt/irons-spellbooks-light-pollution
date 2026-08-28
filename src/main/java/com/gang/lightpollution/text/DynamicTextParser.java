package com.gang.lightpollution.text;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.DynamicTextClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 解析十种动态控制码，并保留原 Component 的颜色、粗体、点击、悬浮和字体样式。
 * 效果是累加式状态；&y 是全息效果，使用 &r 或原版 §r 清除动态与原版格式。
 */
public final class DynamicTextParser {
    public static final char MARK_RAINBOW = '\uE0F1';
    public static final char MARK_GLITCH = '\uE0F2';
    public static final char MARK_CYBER = '\uE0F3';
    public static final char MARK_MAGIC = '\uE0F4';
    public static final char MARK_HOLOGRAPHIC = '\uE0F5';
    public static final char MARK_ENERGY_BAR = '\uE0F6';
    public static final char MARK_LAVA = '\uE0F7';
    public static final char MARK_PARCHMENT = '\uE0F8';
    public static final char MARK_RESET = '\uE0F9';
    public static final char MARK_LITERAL_AMPERSAND = '\uE0FA';
    public static final char MARK_RED_SPEED_NEON = '\uE0FB';
    public static final char MARK_SYNTHWAVE_NEON = '\uE0FC';

    private DynamicTextParser() {
    }

    public static Component parse(String text) {
        return parse(text, Style.EMPTY);
    }

    public static Component parse(String text, Style baseStyle) {
        if (text == null || text.isEmpty()) {
            return Component.empty().setStyle(baseStyle == null ? Style.EMPTY : baseStyle);
        }

        MutableComponent result = Component.empty();
        ParseState state = new ParseState(result);
        Style initialStyle = baseStyle == null ? Style.EMPTY : baseStyle;
        state.beginStyle(initialStyle);
        state.consume(text, EffectStyle.isEncoded(initialStyle));
        state.finish();
        return result;
    }

    public static Component parse(Component source) {
        if (source == null) {
            return Component.empty();
        }
        if (!needsParsing(source)) {
            return source;
        }

        MutableComponent result = Component.empty();
        ParseState state = new ParseState(result);
        source.visit((style, text) -> {
            boolean alreadyProcessed = EffectStyle.isEncoded(style);
            state.beginStyle(style);
            state.consume(text, alreadyProcessed);
            return Optional.empty();
        }, Style.EMPTY);
        state.finish();
        return result;
    }

    public static Component parse(FormattedText source) {
        if (source instanceof Component component) {
            return parse(component);
        }
        if (source == null) {
            return Component.empty();
        }
        if (!needsParsing(source)) {
            MutableComponent unchanged = Component.empty();
            source.visit((style, text) -> {
                unchanged.append(Component.literal(text).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            return unchanged;
        }

        MutableComponent result = Component.empty();
        ParseState state = new ParseState(result);
        source.visit((style, text) -> {
            boolean alreadyProcessed = EffectStyle.isEncoded(style);
            state.beginStyle(style);
            state.consume(text, alreadyProcessed);
            return Optional.empty();
        }, Style.EMPTY);
        state.finish();
        return result;
    }

    public static Component parseIfNeeded(Component source) {
        return needsParsing(source) ? parse(source) : source;
    }

    /** 判断文本里是否仍有处于未编码样式中的动态控制码。 */
    public static boolean needsParsing(FormattedText source) {
        if (source == null || !containsCodes(source.getString())) {
            return false;
        }
        boolean[] needed = {false};
        source.visit((style, text) -> {
            if (!EffectStyle.isEncoded(style) && containsCodes(text)) {
                needed[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return needed[0];
    }

    public static boolean needsParsing(Component source) {
        return needsParsing((FormattedText) source);
    }

    public static boolean containsCodes(String text) {
        if (text == null || text.isEmpty() || DynamicTextPort.standDown()) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (isMarker(current)) {
                return true;
            }
            if (current == '&' && index + 1 < text.length() && isControlCode(text.charAt(index + 1))) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsMarkers(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            if (isMarker(text.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在 FTB Library 解析原版颜色代码前，将动态代码换成普通字符标记。
     * 这样 FTB 的替换变量、JSON Component、链接和图片语法仍由原模组处理。
     */
    public static String prepareForFtb(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return rawText;
        }

        StringBuilder result = new StringBuilder(rawText.length() + 8);
        boolean changed = false;
        for (int index = 0; index < rawText.length(); index++) {
            char current = rawText.charAt(index);

            if (current == '\\' && index + 2 < rawText.length()
                    && rawText.charAt(index + 1) == '&'
                    && isControlCode(rawText.charAt(index + 2))) {
                result.append(MARK_LITERAL_AMPERSAND).append(rawText.charAt(index + 2));
                index += 2;
                changed = true;
                continue;
            }

            if (current == '&' && index + 1 < rawText.length()) {
                char code = Character.toLowerCase(rawText.charAt(index + 1));
                int mask = maskFor(code);
                if (mask != 0) {
                    result.append(markerFor(mask));
                    index++;
                    changed = true;
                    continue;
                }
                if (code == 'r') {
                    result.append(MARK_RESET);
                    index++;
                    changed = true;
                    continue;
                }
                if (isRemovedCode(code)) {
                    index++;
                    changed = true;
                    continue;
                }
            }

            result.append(current);
        }
        return changed ? result.toString() : rawText;
    }

    /** 只去掉本模组的动态控制码，原版颜色代码保持不变。 */
    public static String stripEffectCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\\' && index + 2 < text.length()
                    && text.charAt(index + 1) == '&'
                    && isControlCode(text.charAt(index + 2))) {
                result.append('&').append(text.charAt(index + 2));
                index += 2;
                continue;
            }
            if (current == MARK_LITERAL_AMPERSAND) {
                result.append('&');
                continue;
            }
            if (isMarker(current)) {
                continue;
            }
            if (current == '&' && index + 1 < text.length()) {
                char code = text.charAt(index + 1);
                if (maskFor(code) != 0 || isRemovedCode(code)) {
                    index++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private static boolean isMarker(char value) {
        return value == MARK_RAINBOW
                || value == MARK_GLITCH
                || value == MARK_CYBER
                || value == MARK_MAGIC
                || value == MARK_HOLOGRAPHIC
                || value == MARK_ENERGY_BAR
                || value == MARK_LAVA
                || value == MARK_PARCHMENT
                || value == MARK_RED_SPEED_NEON
                || value == MARK_SYNTHWAVE_NEON
                || value == MARK_RESET
                || value == MARK_LITERAL_AMPERSAND;
    }

    private static boolean isControlCode(char value) {
        char code = Character.toLowerCase(value);
        return maskFor(code) != 0 || isRemovedCode(code) || code == 'r';
    }

    private static boolean isRemovedCode(char code) {
        return Character.toLowerCase(code) == 'o';
    }

    private static int maskFor(char code) {
        return switch (Character.toLowerCase(code)) {
            case 'p' -> EffectStyle.RAINBOW;
            case 'g' -> EffectStyle.GLITCH;
            case 'h' -> EffectStyle.CYBER;
            case 'q' -> EffectStyle.MAGIC;
            case 'y' -> EffectStyle.HOLOGRAPHIC;
            case 's' -> EffectStyle.ENERGY_BAR;
            case 't' -> EffectStyle.LAVA;
            case 'v' -> EffectStyle.PARCHMENT;
            case 'u' -> EffectStyle.RED_SPEED_NEON;
            case 'm' -> EffectStyle.SYNTHWAVE_NEON;
            default -> 0;
        };
    }

    private static char markerFor(int mask) {
        return switch (mask) {
            case EffectStyle.RAINBOW -> MARK_RAINBOW;
            case EffectStyle.GLITCH -> MARK_GLITCH;
            case EffectStyle.CYBER -> MARK_CYBER;
            case EffectStyle.MAGIC -> MARK_MAGIC;
            case EffectStyle.HOLOGRAPHIC -> MARK_HOLOGRAPHIC;
            case EffectStyle.ENERGY_BAR -> MARK_ENERGY_BAR;
            case EffectStyle.LAVA -> MARK_LAVA;
            case EffectStyle.PARCHMENT -> MARK_PARCHMENT;
            case EffectStyle.RED_SPEED_NEON -> MARK_RED_SPEED_NEON;
            case EffectStyle.SYNTHWAVE_NEON -> MARK_SYNTHWAVE_NEON;
            default -> MARK_RESET;
        };
    }

    private static int maskForMarker(char marker) {
        return switch (marker) {
            case MARK_RAINBOW -> EffectStyle.RAINBOW;
            case MARK_GLITCH -> EffectStyle.GLITCH;
            case MARK_CYBER -> EffectStyle.CYBER;
            case MARK_MAGIC -> EffectStyle.MAGIC;
            case MARK_HOLOGRAPHIC -> EffectStyle.HOLOGRAPHIC;
            case MARK_ENERGY_BAR -> EffectStyle.ENERGY_BAR;
            case MARK_LAVA -> EffectStyle.LAVA;
            case MARK_PARCHMENT -> EffectStyle.PARCHMENT;
            case MARK_RED_SPEED_NEON -> EffectStyle.RED_SPEED_NEON;
            case MARK_SYNTHWAVE_NEON -> EffectStyle.SYNTHWAVE_NEON;
            default -> 0;
        };
    }

    private static Style cleanEffectMetadata(Style style) {
        return EffectStyle.clean(style);
    }

    private static Style resetFormatting(Style previous) {
        Style cleanPrevious = EffectStyle.clean(previous);
        Style reset = Style.EMPTY
                .withClickEvent(cleanPrevious.getClickEvent())
                .withHoverEvent(cleanPrevious.getHoverEvent())
                .withInsertion(cleanPrevious.getInsertion());
        ResourceLocation font = cleanPrevious.getFont();
        return Style.DEFAULT_FONT.equals(font) ? reset : reset.withFont(font);
    }

    private static final class ParseState {
        private final MutableComponent target;
        private final StringBuilder buffer = new StringBuilder();
        private Style style = Style.EMPTY;
        private Style bufferStyle = Style.EMPTY;
        private int effectMask;
        private int bufferMask;
        private int visibleCharacters;

        private ParseState(MutableComponent target) {
            this.target = target;
        }

        private void beginStyle(Style nextStyle) {
            flush();
            boolean encoded = EffectStyle.isEncoded(nextStyle);
            int encodedMask = EffectStyle.mask(nextStyle);
            style = cleanEffectMetadata(nextStyle);
            if (encoded) {
                effectMask = encodedMask;
            }
        }

        private void consume(String text, boolean alreadyProcessed) {
            if (alreadyProcessed) {
                for (int index = 0; index < text.length(); index++) {
                    appendVisible(text.charAt(index));
                }
                return;
            }

            for (int index = 0; index < text.length(); index++) {
                char current = text.charAt(index);

                if (current == MARK_LITERAL_AMPERSAND) {
                    appendVisible('&');
                    continue;
                }
                if (current == MARK_RESET) {
                    flush();
                    style = resetFormatting(style);
                    effectMask = 0;
                    continue;
                }
                int markerMask = maskForMarker(current);
                if (markerMask != 0) {
                    flush();
                    effectMask |= markerMask;
                    continue;
                }

                if (current == '\\' && index + 2 < text.length()
                        && text.charAt(index + 1) == '&'
                        && isControlCode(text.charAt(index + 2))) {
                    appendVisible('&');
                    appendVisible(text.charAt(index + 2));
                    index += 2;
                    continue;
                }

                if ((current == '&' || current == ChatFormatting.PREFIX_CODE) && index + 1 < text.length()) {
                    char rawCode = text.charAt(index + 1);
                    if (current == '&') {
                        int dynamicMask = maskFor(rawCode);
                        if (dynamicMask != 0) {
                            flush();
                            effectMask |= dynamicMask;
                            index++;
                            continue;
                        }
                        if (isRemovedCode(rawCode)) {
                            index++;
                            continue;
                        }
                    }

                    ChatFormatting formatting = ChatFormatting.getByCode(rawCode);
                    if (formatting != null) {
                        flush();
                        if (formatting == ChatFormatting.RESET) {
                            style = resetFormatting(style);
                            effectMask = 0;
                        } else {
                            style = cleanEffectMetadata(style.applyLegacyFormat(formatting));
                        }
                        index++;
                        continue;
                    }
                }

                appendVisible(current);
            }
        }

        private void appendVisible(char value) {
            int activeMask = visibleCharacters < DynamicTextClientConfig.maxTextLength() ? effectMask : 0;
            if (!buffer.isEmpty() && (bufferMask != activeMask || !bufferStyle.equals(style))) {
                flush();
            }
            if (buffer.isEmpty()) {
                bufferMask = activeMask;
                bufferStyle = style;
            }
            buffer.append(value);
            if (!Character.isLowSurrogate(value)) {
                visibleCharacters++;
            }
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            Style renderedStyle = EffectStyle.withMask(bufferStyle, bufferMask);
            target.append(Component.literal(buffer.toString()).setStyle(renderedStyle));
            buffer.setLength(0);
        }

        private void finish() {
            flush();
        }
    }
}

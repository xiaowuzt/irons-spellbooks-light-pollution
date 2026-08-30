package com.gang.lightpollution.api;

import com.gang.lightpollution.text.DynamicTextApi;
import com.gang.lightpollution.text.world.WorldTextConfig;
import com.gang.lightpollution.text.world.WorldTextManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Ask this mod to draw text for you — animated in a GUI, or floating in the world.
 *
 * <h2>Two different things</h2>
 *
 * <p>{@link #styled} returns a {@link Component} that animates wherever Minecraft draws it: a
 * tooltip, a chat line, a screen title, a book. You hand the result to whatever wanted a Component
 * and the animation follows it. Nothing is spawned and there is nothing to remove.</p>
 *
 * <p>{@link #inWorld} puts text at a position in the world, with an entrance, a dwell and an exit.
 * That one is spawned, and it ages out on its own.</p>
 *
 * <h2>Client only</h2>
 *
 * <p>{@link #inWorld} draws on this client and syncs nothing, same as the effects in
 * {@link LightPollutionFx} — send your own packet to show text to other players.</p>
 *
 * <p>{@link #styled} is safe to call anywhere. It only marks the Component; the styling happens
 * wherever it is eventually drawn, which is always a client.</p>
 */
public final class LightPollutionText {
    private LightPollutionText() {
    }

    /** The animated styles. Pass one to {@link #styled}. */
    public enum Style {
        /** Colours cycling along the text. */
        RAINBOW("p"),
        /** Characters intermittently replaced with noise. */
        GLITCH("g"),
        /** Glitch with a colder, scanline-ish palette. */
        CYBER("h"),
        /** A soft neon glow, warm. */
        MAGICAL_NEON("q"),
        /** Iridescent, shifting with viewing order. */
        HOLOGRAPHIC("y"),
        /** Reads as a charge or energy meter. */
        ENERGY_BAR("s"),
        /** Molten, with heat moving through it. */
        LAVA("t"),
        /** Aged paper, for spellbook-ish text. */
        PARCHMENT("v"),
        /** Fast red neon with a speed-glitch feel. */
        RED_SPEED_NEON("u"),
        /** Synthwave neon with a VHS wobble. */
        SYNTHWAVE_NEON("m");

        private final String code;

        Style(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    /**
     * Text that animates wherever it is drawn.
     *
     * <p>The player can turn any of these off in this mod's client config, in which case the text
     * still renders — just without that effect. Do not depend on an effect being visible.</p>
     *
     * @param text  the text to style
     * @param style which animation
     * @return a Component to hand to whatever wanted one
     */
    public static Component styled(String text, Style style) {
        return DynamicTextApi.parse("&" + style.code() + (text == null ? "" : text));
    }

    /**
     * Text that animates, with several effects at once.
     *
     * <p>They compose rather than override, but not all pairs read well together — a glitch inside a
     * hologram mostly reads as noise. Two is usually the limit worth using.</p>
     */
    public static Component styled(String text, Style... styles) {
        StringBuilder codes = new StringBuilder();
        for (Style style : styles) {
            if (style != null) {
                codes.append('&').append(style.code());
            }
        }
        return DynamicTextApi.parse(codes + (text == null ? "" : text));
    }

    /**
     * Float text at a position in the world.
     *
     * <p>Ages out on its own once the config's enter, stay and exit durations have run, so there is
     * no handle and nothing to remove. Combine with {@link #styled} to have it animate as well.</p>
     *
     * @param text   what to show; may be a Component from {@link #styled}
     * @param at     where, in world coordinates
     * @param config how it enters, dwells and leaves; see {@link WorldTextConfig#createDefault}
     */
    public static void inWorld(Component text, Vec3 at, WorldTextConfig config) {
        WorldTextManager.spawn(text, at, config);
    }

    /** With a fade in, a two-second dwell and a fade out. */
    public static void inWorld(Component text, Vec3 at) {
        WorldTextManager.spawn(text, at);
    }
}

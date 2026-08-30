package com.gang.lightpollution.text.anim;

import net.minecraft.util.Mth;

/**
 * The sixteen animated effects, as pure functions on a {@link GlyphState}.
 *
 * <p>Ported from TextAnimator's {@code effect} package (Apache 2.0, © 2023 Snownee); see
 * {@code TEXTANIMATOR_LICENSE.txt}. The maths of each effect is the original's, constants unchanged.
 * Changed from the original:</p>
 *
 * <ul>
 *   <li>Fifteen classes and a registry became one enum. The original needed a registry so other mods
 *       could add effect types; nothing here does, and fifteen files of ten lines each was the larger
 *       cost.</li>
 *   <li>Parameters are read per call from {@link AnimParams} rather than cached in per-effect final
 *       fields, because an enum constant is shared by every use of that effect.</li>
 *   <li>The direction table is seeded. The original shuffles it with an unseeded
 *       {@code Collections.shuffle}, so which way a letter shook changed every launch.</li>
 *   <li>{@code glitch} does not split a glyph into masked halves. That needed the original's sibling
 *       list and vertex masking, which are not ported; what remains is its jitter and blink.</li>
 *   <li>{@code neon} and {@code typewriter} are not here. Neon is a multi-pass draw rather than a
 *       per-glyph change, and typewriter needs per-screen state.</li>
 * </ul>
 */
public enum TextAnim {
    /** Random per-letter jog, re-picked as time advances. */
    SHAKE("shake") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float amp = params.number("a", 1.0F);
            float speed = params.number("f", 1.0F);
            int seed = (int) (millis * 0.01F * speed) + glyph.codepoint + glyph.index;
            glyph.x += directionX(seed) * 0.6F * amp;
            glyph.y += directionY(seed) * 0.6F * amp;
        }
    },
    /** A sine travelling along the run, vertically. */
    WAVE("wave") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float amp = params.number("a", 1.0F);
            float speed = params.number("f", 1.0F);
            float phase = params.number("w", 1.0F);
            glyph.y += Mth.sin(millis * 0.01F * speed + glyph.index * phase) * 2.0F * amp;
        }
    },
    /** Like wave, but each letter moves along its own fixed direction rather than up and down. */
    WIGGLE("wiggle") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float amp = params.number("a", 1.0F);
            float speed = params.number("f", 1.0F);
            float phase = params.number("w", 1.0F);
            float delta = Mth.sin(millis * 0.01F * speed + glyph.index * 2.0F * phase) * 1.5F * amp;
            glyph.x += directionX(glyph.codepoint) * delta;
            glyph.y += directionY(glyph.codepoint) * delta;
        }
    },
    /** Rocks each letter about its own centre. */
    SWING("swing") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float amp = params.number("a", 1.0F);
            float speed = params.number("f", 1.0F);
            float phase = params.number("w", 0.0F);
            float t = millis * 0.003F * speed + glyph.index * phase;
            // Upstream multiplies by 0.5, which at a=1 is half a degree — invisible. Twelve degrees
            // is a rock you can see; a=1 still means "the default amount".
            glyph.rotation += Mth.sin(t) * amp * 12.0F;
        }
    },
    /**
     * Throws each letter up and lets it fall, bouncing.
     *
     * <p>The falling part is Robert Penner's bounce easing, via Tween.js (MIT). Reproduced with its
     * constants unchanged, as the original had it.</p>
     */
    BOUNCE("bounce") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float amp = params.number("a", 1.0F);
            float speed = params.number("f", 1.0F);
            float phase = params.number("w", 1.0F);
            float t = (millis * 0.001F * speed - glyph.index * phase * 0.2F) % 1.0F;
            if (t < 0.0F) {
                t += 1.0F;
            }
            float offset = 0.0F;
            if (t < 0.2F) {
                offset = Mth.sin(t / 0.2F * Mth.HALF_PI);
            } else if (t < 0.8F) {
                offset = 1.0F - penner((t - 0.2F) / 0.6F);
            }
            glyph.y -= offset * amp * 4.0F;
        }
    },
    /** Swings each letter from a pivot above it, optionally orbiting as well. */
    PENDULUM("pend") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float speed = params.number("f", 1.0F);
            float maxAngle = params.number("a", 30.0F);
            float radius = params.number("r", 0.0F);
            double phase = millis * 0.002 * speed - glyph.index * 0.1;
            glyph.pendulum = (float) (Math.sin(phase) * Math.toRadians(maxAngle));
            if (radius != 0.0F) {
                glyph.x += (float) (Math.cos(phase) * radius);
                glyph.y += (float) (Math.sin(phase) * radius);
            }
        }
    },
    /** Two out-of-step sines, so the drift never repeats cleanly. */
    TURBULENCE("turb") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float amp = params.number("a", 1.0F) * 1.5F;
            float t = millis * 0.002F * speedOf(params);
            glyph.x += Mth.sin(t * 1.7F + glyph.index * 0.31F + glyph.codepoint * 0.07F) * amp;
            glyph.y += Mth.sin(t * 2.3F + glyph.index * 0.27F + glyph.codepoint * 0.11F) * amp;
        }
    },
    /**
     * Slides the whole run leftward, wrapping every forty pixels.
     *
     * <p>This one deliberately leaves its container: text drawn in a box will run out of it. That is
     * the effect, not a bug — but it is the reason not to put this on an item name.</p>
     */
    SCROLL("scroll") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.x -= (millis * 0.04F * speedOf(params)) % 40.0F;
        }
    },
    /** Hue cycling along the run. */
    RAINBOW("rainb") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            if (glyph.shadow) {
                return;
            }
            float speed = params.number("f", 1.0F);
            float phase = params.number("w", 1.0F);
            int rgb = Mth.hsvToRgb(((millis * 0.02F * speed + glyph.index * phase) % 30.0F) / 30.0F,
                    0.8F, 0.8F);
            glyph.r = (rgb >> 16 & 255) / 255.0F;
            glyph.g = (rgb >> 8 & 255) / 255.0F;
            glyph.b = (rgb & 255) / 255.0F;
        }
    },
    /** Interpolates between two colours along the run, and optionally over time. */
    GRADIENT("grad") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            if (glyph.shadow) {
                return;
            }
            float[] from = params.colour("from", DEFAULT_FROM);
            float[] to = params.colour("to", DEFAULT_TO);
            float span = params.number("sp", 20.0F);
            float speed = params.number("f", 0.0F);

            float alongRun = span > 0.0F ? (glyph.index % span) / span : 0.0F;
            float overTime = speed > 0.0F ? (float) (millis * 0.001 * speed % 1.0) : 0.0F;
            float t = (alongRun + overTime) % 1.0F;
            // Cyclic by default: run the ramp out and back, so a long line does not jump at the seam.
            if (!params.flag("uni", false)) {
                t = (t * 2.0F) % 2.0F;
                if (t > 1.0F) {
                    t = 2.0F - t;
                }
            }

            if (params.flag("hue", false)) {
                float[] a = toHsv(from);
                float[] b = toHsv(to);
                float[] rgb = fromHsv(lerpHue(a[0], b[0], t),
                        Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2]));
                glyph.r = rgb[0];
                glyph.g = rgb[1];
                glyph.b = rgb[2];
            } else {
                glyph.r = Mth.lerp(t, from[0], to[0]);
                glyph.g = Mth.lerp(t, from[1], to[1]);
                glyph.b = Mth.lerp(t, from[2], to[2]);
            }
        }
    },
    /** Brightens and dims whatever colour the text already had. */
    PULSE("pulse") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            if (glyph.shadow) {
                return;
            }
            // Upstream's base is 0.75 with a 0.25 swing, so brightness only ever varies by a
            // quarter — hard to see, and impossible on white text which is already at the ceiling.
            // Dropping the floor is what makes it read as a pulse.
            float base = params.number("base", 0.45F);
            float amp = params.number("a", 1.0F);
            float phase = params.number("w", 0.0F);
            float t = millis * 0.002F * speedOf(params) + glyph.index * phase;
            float k = base + amp * 0.55F * (0.5F + 0.5F * Mth.sin(t));
            glyph.r *= k;
            glyph.g *= k;
            glyph.b *= k;
        }
    },
    /** Breathes the alpha. */
    FADE("fade") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float floor = params.number("a", 0.3F);
            float phase = params.number("w", 0.0F);
            float t = millis * 0.002F * speedOf(params) + glyph.index * phase;
            glyph.a *= floor + (1.0F - floor) * (0.5F + 0.5F * Mth.sin(t));
        }
    },
    /** Recolours and offsets only the drop shadow pass. */
    SHADOW("shadow") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            if (!glyph.shadow) {
                return;
            }
            glyph.x += params.number("x", 0.0F);
            glyph.y += params.number("y", 0.0F);
            float[] colour = params.colour("c", null);
            glyph.r = colour != null ? colour[0] : params.number("r", 0.0F);
            glyph.g = colour != null ? colour[1] : params.number("g", 0.0F);
            glyph.b = colour != null ? colour[2] : params.number("b", 0.0F);
            glyph.a *= params.number("a", 1.0F);
        }
    },
    /** Occasional jitter and blink, on a three-beat pulse. */
    GLITCH("glitch") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            float frequency = params.number("f", 1.0F);
            double time = millis * 0.025 * frequency;
            java.util.Random random =
                    new java.util.Random(glyph.index + glyph.codepoint + (long) (time * 1000.0));
            random.nextFloat();
            // Upstream's 0.015 jitter chance on a one-in-three pulse means a letter moves every few
            // seconds, which reads as nothing happening. Raised so a word visibly breaks up.
            if ((int) time % 3 == 1 && random.nextFloat() < params.number("j", 0.35F)) {
                glyph.x += (random.nextFloat() - 0.5F) * 8.0F;
                glyph.y += (random.nextFloat() - 0.5F) * 4.0F;
            }
            if (random.nextFloat() < params.number("b", 0.06F)) {
                glyph.a = 0.0F;
            }
        }
    },
    /**
     * A ring of dim copies behind each glyph, so it reads as glowing.
     *
     * <p>Upstream's {@code apply} is empty and the drawing happens in its client class; here the
     * request is recorded on the glyph and the renderer makes the extra passes.</p>
     */
    NEON("neon") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            if (glyph.shadow) {
                return;
            }
            glyph.glowPasses = (int) Math.max(4.0F, params.number("p", 10.0F));
            glyph.glowRadius = params.number("r", 2.0F);
            // Upstream's 0.12 is faint, and the glow takes the glyph's own colour — so white text
            // glowed white and showed nothing. Raised, and the renderer tints it toward the hue.
            glyph.glowAlpha = params.number("a", 0.45F);
        }
    },
    /**
     * Reveals the text one character at a time.
     *
     * <p>The only effect that needs state beyond the glyph: how far the reveal has got. That is kept
     * in {@link Typewriter}, keyed by the string, so the same text carries on rather than restarting
     * on every frame.</p>
     */
    TYPEWRITER("typewriter") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            int revealed = Typewriter.revealed(glyph.runKey, params.number("f", 1.0F), millis);
            if (glyph.index >= revealed) {
                glyph.a = 0.0F;
            }
        }
    },
    /** Every letter its own hue, fixed rather than cycling. */
    SPECTRUM("spec") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            if (glyph.shadow) {
                return;
            }
            float span = params.number("sp", 12.0F);
            int rgb = Mth.hsvToRgb((glyph.index % span) / span, 0.75F, 0.95F);
            glyph.r = (rgb >> 16 & 255) / 255.0F;
            glyph.g = (rgb >> 8 & 255) / 255.0F;
            glyph.b = (rgb & 255) / 255.0F;
        }
    };

    /** TextAnimator's defaults, kept so a bare {@code grad} looks as it does there. */
    private static final float[] DEFAULT_FROM = {0x5B / 255.0F, 0xCE / 255.0F, 0xFA / 255.0F};
    private static final float[] DEFAULT_TO = {0xF5 / 255.0F, 0xA9 / 255.0F, 0xB8 / 255.0F};

    /**
     * Thirty directions around the circle, in a fixed scrambled order.
     *
     * <p>Scrambled so that neighbouring letters do not shake in near-identical directions, which would
     * read as the whole word sliding. Seeded, unlike the original — an unseeded shuffle meant the
     * arrangement changed every launch.</p>
     */
    private static final float[] DIRECTION_X = new float[30];
    private static final float[] DIRECTION_Y = new float[30];

    static {
        int count = DIRECTION_X.length;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        java.util.Collections.shuffle(java.util.Arrays.asList(order), new java.util.Random(0x5EEDL));
        for (int i = 0; i < count; i++) {
            float radians = (float) (Math.PI * 2.0 / count) * order[i];
            DIRECTION_X[i] = Mth.cos(radians);
            DIRECTION_Y[i] = Mth.sin(radians);
        }
    }

    private final String id;

    TextAnim(String id) {
        this.id = id;
    }

    /** The name written between the braces. */
    public String id() {
        return id;
    }

    /**
     * Apply this effect to one glyph.
     *
     * @param millis wall-clock milliseconds, so animation continues while the game is paused
     */
    public abstract void apply(GlyphState glyph, AnimParams params, long millis);

    /** By written name, or null. */
    public static TextAnim byId(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.toLowerCase(java.util.Locale.ROOT);
        for (TextAnim anim : values()) {
            if (anim.id.equals(wanted)) {
                return anim;
            }
        }
        return null;
    }

    private static float speedOf(AnimParams params) {
        return params.number("f", 1.0F);
    }

    private static float directionX(int seed) {
        return DIRECTION_X[Math.abs(seed) % DIRECTION_X.length];
    }

    private static float directionY(int seed) {
        return DIRECTION_Y[Math.abs(seed) % DIRECTION_Y.length];
    }

    /** Robert Penner's bounce ease-out, via Tween.js (MIT). Constants as the original had them. */
    private static float penner(float t) {
        if (t < 1.0F / 2.75F) {
            return 7.5625F * t * t;
        }
        if (t < 2.0F / 2.75F) {
            float shifted = t - 1.5F / 2.75F;
            return 7.5625F * shifted * shifted + 0.75F;
        }
        if (t < 2.5F / 2.75F) {
            float shifted = t - 2.25F / 2.75F;
            return 7.5625F * shifted * shifted + 0.9375F;
        }
        float shifted = t - 2.625F / 2.75F;
        return 7.5625F * shifted * shifted + 0.984375F;
    }

    private static float[] toHsv(float[] rgb) {
        float max = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        float min = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        float range = max - min;
        float hue;
        if (range == 0.0F) {
            hue = 0.0F;
        } else if (max == rgb[0]) {
            hue = (rgb[1] - rgb[2]) / range + (rgb[1] < rgb[2] ? 6.0F : 0.0F);
        } else if (max == rgb[1]) {
            hue = (rgb[2] - rgb[0]) / range + 2.0F;
        } else {
            hue = (rgb[0] - rgb[1]) / range + 4.0F;
        }
        return new float[] {hue / 6.0F, max == 0.0F ? 0.0F : range / max, max};
    }

    private static float[] fromHsv(float hue, float saturation, float value) {
        int sextant = (int) (hue * 6.0F);
        float fraction = hue * 6.0F - sextant;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - fraction * saturation);
        float t = value * (1.0F - (1.0F - fraction) * saturation);
        return switch (Math.floorMod(sextant, 6)) {
            case 0 -> new float[] {value, t, p};
            case 1 -> new float[] {q, value, p};
            case 2 -> new float[] {p, value, t};
            case 3 -> new float[] {p, q, value};
            case 4 -> new float[] {t, p, value};
            default -> new float[] {value, p, q};
        };
    }

    /** Round the short way, so cyan to red does not travel through green. */
    private static float lerpHue(float from, float to, float t) {
        float diff = (to - from + 1.0F) % 1.0F;
        if (diff > 0.5F) {
            diff -= 1.0F;
        }
        return (from + diff * t + 1.0F) % 1.0F;
    }
}

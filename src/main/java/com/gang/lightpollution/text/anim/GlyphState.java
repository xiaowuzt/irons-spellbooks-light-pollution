package com.gang.lightpollution.text.anim;

/**
 * What an animated effect may change about one glyph.
 *
 * <p>Ported from TextAnimator's {@code EffectSettings} (Apache 2.0, © 2023 Snownee); see
 * {@code TEXTANIMATOR_LICENSE.txt}. Changed from the original: the typewriter fields and the sibling
 * list are gone, because neither feature is ported; the mask fields are gone with them.</p>
 *
 * <p>Mutable and reused per glyph rather than allocated, because this is touched once per glyph per
 * frame and text is mostly short strings drawn very often.</p>
 */
public final class GlyphState {
    /** The character being drawn. Part of several effects' hash, so they vary per letter. */
    public int codepoint;
    /** Position of this glyph in its run, from 0. Drives every travelling wave. */
    public int index;
    /** True while drawing the drop shadow pass rather than the glyph itself. */
    public boolean shadow;
    /**
     * Identifies the run this glyph belongs to, for effects that need progress across frames.
     *
     * <p>Only the typewriter reads it. Null for a caller that has no stable identity to offer, which
     * that effect treats as fully revealed rather than as a reveal that restarts every frame.</p>
     */
    public String runKey;

    /** Offset from where the glyph would otherwise sit, in pixels. */
    public float x;
    public float y;
    /** Colour, 0 to 1. Seeded from the style's own colour before the effects run. */
    public float r;
    public float g;
    public float b;
    public float a;
    /** Rotation about the glyph's own centre, in degrees. */
    public float rotation;
    /** Swing about a pivot at the glyph's top, in radians. A pendulum, not a spin. */
    public float pendulum;
    /**
     * How many dim copies to ring the glyph with, or 0 for none.
     *
     * <p>A request rather than something applied here: the copies are extra draws, and this seam
     * describes one glyph.</p>
     */
    public int glowPasses;
    /** Radius of that ring, in pixels. */
    public float glowRadius;
    /** Alpha of each copy, as a fraction of the glyph's own. */
    public float glowAlpha;

    /**
     * Which fragment-stage effect to run, or 0 for none.
     *
     * <p>A request, like {@link #glowPasses}: the work happens in a shader, and this seam describes one
     * glyph on the CPU. The emitter reads it to decide whether the glyph goes through the effect render
     * type or the plain one.</p>
     */
    public int fragmentEffect;
    /** That effect's own colour, packed ARGB. */
    public int fragmentColour;
    /** Its four parameters. Reused rather than allocated, and cleared on reset. */
    public final float[] fragmentParams = new float[4];

    /** Ready this for one glyph, keeping nothing from the last one. */
    public void reset(int codepoint, int index, boolean shadow,
                      float r, float g, float b, float a) {
        reset(codepoint, index, shadow, r, g, b, a, null);
    }

    /** As above, naming the run so cross-frame effects can find their progress. */
    public void reset(int codepoint, int index, boolean shadow,
                      float r, float g, float b, float a, String runKey) {
        this.codepoint = codepoint;
        this.index = index;
        this.shadow = shadow;
        this.runKey = runKey;
        this.x = 0.0F;
        this.y = 0.0F;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        this.rotation = 0.0F;
        this.pendulum = 0.0F;
        this.glowPasses = 0;
        this.glowRadius = 0.0F;
        this.glowAlpha = 0.0F;
        this.fragmentEffect = 0;
        this.fragmentColour = 0;
        this.fragmentParams[0] = 0.0F;
        this.fragmentParams[1] = 0.0F;
        this.fragmentParams[2] = 0.0F;
        this.fragmentParams[3] = 0.0F;
    }

    /** True when nothing moved the glyph, so the caller can skip the transform entirely. */
    public boolean stationary() {
        return x == 0.0F && y == 0.0F && rotation == 0.0F && pendulum == 0.0F;
    }

    /** The colour packed the way {@code drawString} wants it. */
    public int packedColour() {
        return (channel(a) << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float value) {
        int scaled = (int) (value * 255.0F + 0.5F);
        return Math.max(0, Math.min(255, scaled));
    }
}

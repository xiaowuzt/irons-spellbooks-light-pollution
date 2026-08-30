package com.gang.lightpollution.api;

/**
 * What a collapsing singularity looks like.
 *
 * <p>A core that opens, draws in and contracts while lightning lashes from it, then collapses into a
 * blast with three decelerating shockwave fronts behind it.</p>
 *
 * @param seed          arranges the lightning; the same seed always lashes the same way
 * @param scale         multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks how long it is drawn for, or 0 to draw until removed by hand
 */
public record SingularityParams(int seed, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 220;

    public static SingularityParams of(int seed) {
        return new SingularityParams(seed, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public SingularityParams scale(float value) {
        return new SingularityParams(seed, value, lifetimeTicks);
    }

    public SingularityParams lifetime(int ticks) {
        return new SingularityParams(seed, scale, ticks);
    }
}

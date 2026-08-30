package com.gang.lightpollution.api;

/**
 * What a leviathan's approach, rear and bite look like.
 *
 * <p>A long body swimming in from behind the caster with an anguilliform undulation, rearing its front
 * third, then plunging its jaws onto the aimed point and unravelling.</p>
 *
 * @param bearingRadians which way the body swims
 * @param seed           sets the undulation's phase, so two of them are not in lockstep
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record LeviathanParams(float bearingRadians, int seed, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 190;

    public static LeviathanParams of(float bearingRadians, int seed) {
        return new LeviathanParams(bearingRadians, seed, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public LeviathanParams scale(float value) {
        return new LeviathanParams(bearingRadians, seed, value, lifetimeTicks);
    }

    public LeviathanParams lifetime(int ticks) {
        return new LeviathanParams(bearingRadians, seed, scale, ticks);
    }
}

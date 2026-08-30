package com.gang.lightpollution.api;

/**
 * What a constellation's orbiting star looks like.
 *
 * <p>A star that falls in from high and far, eases into a ring orbit, and completes one turn over its
 * life. One star, not a pattern — the name is the spell's, and drawing several would be a different
 * effect.</p>
 *
 * @param spinOffsetRadians where in the ring the star starts
 * @param scale             multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks     how long it is drawn for, or 0 to draw until removed by hand
 */
public record ConstellationParams(float spinOffsetRadians, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 240;

    public static ConstellationParams of(float spinOffsetRadians) {
        return new ConstellationParams(spinOffsetRadians, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public ConstellationParams scale(float value) {
        return new ConstellationParams(spinOffsetRadians, value, lifetimeTicks);
    }

    public ConstellationParams lifetime(int ticks) {
        return new ConstellationParams(spinOffsetRadians, scale, ticks);
    }
}

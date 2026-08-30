package com.gang.lightpollution.api;

/**
 * What a second sun rising and going nova looks like.
 *
 * <p>A disc that climbs from the horizon, swells and cools as it goes, then detonates and leaves a
 * collapsing remnant. Drawn as sky rather than as an object: it is placed along a bearing at whatever
 * distance the renderer wants, so it does not have a position in the world the way the other effects
 * do.</p>
 *
 * @param bearingRadians which compass direction it rises in
 * @param scale          multiplies its apparent size; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record SecondSunParams(float bearingRadians, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 320;

    public static SecondSunParams of(float bearingRadians) {
        return new SecondSunParams(bearingRadians, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public SecondSunParams scale(float value) {
        return new SecondSunParams(bearingRadians, value, lifetimeTicks);
    }

    public SecondSunParams lifetime(int ticks) {
        return new SecondSunParams(bearingRadians, scale, ticks);
    }
}

package com.gang.lightpollution.api;

/**
 * What a planetary nebula's shell of cometary knots looks like.
 *
 * <p>An expanding shell of globules, each being boiled off from the side facing the star, arranged
 * as two nested rings seen at an inclination — which is what makes NGC 7293 read as an eye.</p>
 *
 * @param azimuthDegrees which way the rings' pair of axes points
 * @param seed           places the knots; the same seed always gives the same arrangement
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record HelixNebulaParams(float azimuthDegrees, int seed, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 340;

    public static HelixNebulaParams of(float azimuthDegrees, int seed) {
        return new HelixNebulaParams(azimuthDegrees, seed, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public HelixNebulaParams scale(float value) {
        return new HelixNebulaParams(azimuthDegrees, seed, value, lifetimeTicks);
    }

    public HelixNebulaParams lifetime(int ticks) {
        return new HelixNebulaParams(azimuthDegrees, seed, scale, ticks);
    }
}

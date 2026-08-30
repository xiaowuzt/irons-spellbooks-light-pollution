package com.gang.lightpollution.api;

/**
 * What a pinwheel galaxy's spiral arms look like.
 *
 * <p>Two logarithmic arms in a tilted plane, turning as a whole. Tilted rather than face-on because
 * a spiral seen flat reads as a flat decal.</p>
 *
 * @param azimuthDegrees which way the galactic plane is turned
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record PinwheelParams(float azimuthDegrees, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 300;

    public static PinwheelParams of(float azimuthDegrees) {
        return new PinwheelParams(azimuthDegrees, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public PinwheelParams scale(float value) {
        return new PinwheelParams(azimuthDegrees, value, lifetimeTicks);
    }

    public PinwheelParams lifetime(int ticks) {
        return new PinwheelParams(azimuthDegrees, scale, ticks);
    }
}

package com.gang.lightpollution.api;

/**
 * What a microquasar's precessing twin jets look like.
 *
 * <p>A stellar-mass black hole firing two opposed jets that sweep out a cone as the disc precesses,
 * so the blobs already launched trace a helix rather than a straight line — each one keeps the
 * direction the jet had when it left.</p>
 *
 * @param azimuthDegrees which way the jet axis is turned
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record MicroquasarParams(float azimuthDegrees, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 320;

    public static MicroquasarParams of(float azimuthDegrees) {
        return new MicroquasarParams(azimuthDegrees, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public MicroquasarParams scale(float value) {
        return new MicroquasarParams(azimuthDegrees, value, lifetimeTicks);
    }

    public MicroquasarParams lifetime(int ticks) {
        return new MicroquasarParams(azimuthDegrees, scale, ticks);
    }
}

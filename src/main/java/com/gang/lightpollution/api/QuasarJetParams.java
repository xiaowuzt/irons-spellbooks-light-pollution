package com.gang.lightpollution.api;

/**
 * What a relativistic jet looks like.
 *
 * <p>A narrow beam from an active galactic nucleus with bright knots travelling along it and a lobe
 * where it terminates. The knots are spaced by the <em>apparent</em> superluminal speed rather than
 * the real one, which is what a viewer near the jet's axis would actually see.</p>
 *
 * @param azimuthDegrees which way the jet points
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record QuasarJetParams(float azimuthDegrees, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 340;

    public static QuasarJetParams of(float azimuthDegrees) {
        return new QuasarJetParams(azimuthDegrees, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public QuasarJetParams scale(float value) {
        return new QuasarJetParams(azimuthDegrees, value, lifetimeTicks);
    }

    public QuasarJetParams lifetime(int ticks) {
        return new QuasarJetParams(azimuthDegrees, scale, ticks);
    }
}

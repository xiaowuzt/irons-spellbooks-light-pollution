package com.gang.lightpollution.api;

/**
 * What a magnetar's twisting dipole field looks like.
 *
 * <p>A neutron star with a field strong enough to reshape its own crust, drawn as closed dipole
 * loops leaving one magnetic pole and arriving at the other. The loops wind as the field is stressed,
 * and let go in a flare.</p>
 *
 * @param azimuthDegrees which way the dipole axis is turned
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 */
public record MagnetarParams(float azimuthDegrees, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 300;

    public static MagnetarParams of(float azimuthDegrees) {
        return new MagnetarParams(azimuthDegrees, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public MagnetarParams scale(float value) {
        return new MagnetarParams(azimuthDegrees, value, lifetimeTicks);
    }

    public MagnetarParams lifetime(int ticks) {
        return new MagnetarParams(azimuthDegrees, scale, ticks);
    }
}

package com.gang.lightpollution.api;

/**
 * What a supernova remnant's cage of filaments looks like.
 *
 * <p>A shell of ionised gas around a pulsar, threaded by a tangle of closed filament loops, with the
 * pulsar's wind nebula glowing inside it. Unlike a planetary nebula this barely expands — the real
 * remnant has taken a thousand years to reach its size — so it reads as something that hangs there
 * rather than something that sweeps past.</p>
 *
 * <p>No azimuth, unlike the other two. Every filament gets its own tilt and spin hashed out of the
 * seed, so the cage has no single axis to turn and there is nothing for an azimuth to mean. The
 * spell's anchor entity does carry one, but nothing that draws or damages reads it.</p>
 *
 * @param seed          arranges the filaments; the same seed always gives the same tangle
 * @param scale         multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks how long it is drawn for, or 0 to draw until removed by hand
 */
public record CrabNebulaParams(int seed, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 320;

    public static CrabNebulaParams of(int seed) {
        return new CrabNebulaParams(seed, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public CrabNebulaParams scale(float value) {
        return new CrabNebulaParams(seed, value, lifetimeTicks);
    }

    public CrabNebulaParams lifetime(int ticks) {
        return new CrabNebulaParams(seed, scale, ticks);
    }
}

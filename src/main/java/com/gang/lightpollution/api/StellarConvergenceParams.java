package com.gang.lightpollution.api;

/**
 * What a stellar convergence looks like.
 *
 * <p>Nine stars lighting one after another across the upper half of a shell, drifting slowly, then
 * contracting into a net and pouring themselves into a column of light.</p>
 *
 * @param seed          nudges the stars off the lattice; the same seed always places them the same way
 * @param scale         multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks how long it is drawn for, or 0 to draw until removed by hand
 */
public record StellarConvergenceParams(int seed, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 260;

    public static StellarConvergenceParams of(int seed) {
        return new StellarConvergenceParams(seed, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public StellarConvergenceParams scale(float value) {
        return new StellarConvergenceParams(seed, value, lifetimeTicks);
    }

    public StellarConvergenceParams lifetime(int ticks) {
        return new StellarConvergenceParams(seed, scale, ticks);
    }
}

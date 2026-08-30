package com.gang.lightpollution.api;

/**
 * What an eclipse severance's sweep looks like.
 *
 * <p>A wide arc swept through the air with speed lines, particles and lightning behind it, then a
 * ring and a burst where it lands.</p>
 *
 * <h2>Its own clock</h2>
 *
 * <p>Unlike the other effects here, this one is driven by a monotonic wall clock rather than by ticks,
 * and its lifetime is fixed at roughly 1.45 seconds. That is not an oversight: the timing, particle
 * integration and random call order were ported from another mod and are kept byte-for-byte, so the
 * animation cannot be stretched without changing what it is. There is no {@code lifetimeTicks}
 * here.</p>
 *
 * @param facingYawDegrees which way the sweep faces
 * @param seed             places the particles and lightning; the same seed always sweeps the same way
 */
public record EclipseSeveranceParams(float facingYawDegrees, int seed) {
    /** How long the sweep runs, in seconds. Fixed by the ported timeline. */
    public static final float DURATION_SECONDS = 1.45F;

    public static EclipseSeveranceParams of(float facingYawDegrees, int seed) {
        return new EclipseSeveranceParams(facingYawDegrees, seed);
    }
}

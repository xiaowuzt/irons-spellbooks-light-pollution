package com.gang.lightpollution.api;

/**
 * What a funeral nova's collapse looks like.
 *
 * <p>A ten-stage sequence: a magic circle, a tower, a black hole, an accretion disc, a collapse, a
 * void, a flash, a hypernova, an afterglow, a fade. Just over seventeen seconds end to end.</p>
 *
 * <h2>Its own clock</h2>
 *
 * <p>Like {@link EclipseSeveranceParams}, this one runs on seconds rather than ticks and its stage
 * boundaries are fixed. The sequence was ported from another mod and the timing is what the visuals
 * are, so there is no lifetime to set.</p>
 *
 * @param seed places the debris; the same seed always collapses the same way
 */
public record FuneralNovaParams(int seed) {
    /** How long the sequence runs, in seconds. Fixed by the ported timeline. */
    public static final float DURATION_SECONDS = 17.2F;
    /** How long the sequence runs, in ticks. */
    public static final int DURATION_TICKS = 344;

    public static FuneralNovaParams of(int seed) {
        return new FuneralNovaParams(seed);
    }
}

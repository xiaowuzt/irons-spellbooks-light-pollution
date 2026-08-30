package com.gang.lightpollution.api;

/**
 * What a meteor shower looks like.
 *
 * <p>Meteors entering at a slant one after another, each brightening as it falls, with one larger
 * arrival at the end.</p>
 *
 * <h2>Ground level</h2>
 *
 * <p>Same as {@link SkyCollapseParams}: the spell drops each meteor onto the terrain surface using the
 * world's heightmap, so the landing point comes from the source rather than from these parameters. An
 * effect requested through the API lands them all at {@code groundY}.</p>
 *
 * @param seed          arranges the meteors; the same seed always places them the same way
 * @param groundY       the height meteors land at, in blocks
 * @param spreadRadius  how far from the centre meteors can land, in blocks
 * @param scale         multiplies fall height; 1 is what the spell uses
 * @param lifetimeTicks how long it is drawn for, or 0 to draw until removed by hand
 */
public record StarfallParams(int seed, double groundY, double spreadRadius, float scale,
                             int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 310;
    /** How far the spell scatters its meteors, in blocks. */
    public static final double SPELL_SPREAD_RADIUS = 14.0D;

    public static StarfallParams of(int seed, double groundY) {
        return new StarfallParams(seed, groundY, SPELL_SPREAD_RADIUS, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public StarfallParams scale(float value) {
        return new StarfallParams(seed, groundY, spreadRadius, value, lifetimeTicks);
    }

    public StarfallParams lifetime(int ticks) {
        return new StarfallParams(seed, groundY, spreadRadius, scale, ticks);
    }

    public StarfallParams spread(double radius) {
        return new StarfallParams(seed, groundY, radius, scale, lifetimeTicks);
    }
}

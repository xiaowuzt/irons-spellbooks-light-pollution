package com.gang.lightpollution.api;

/**
 * What a sky collapse looks like.
 *
 * <p>The firmament fractures and sheds slabs one after another, each tumbling down onto the terrain,
 * with a keystone last.</p>
 *
 * <h2>Ground level</h2>
 *
 * <p>Where each slab lands is not decided here. The spell drops them onto the terrain surface, which
 * needs the world's heightmap, so the renderer asks its source for the landing point rather than
 * computing one. An effect requested through the API uses {@code groundY} for all of them — pass the
 * height you want them to land at.</p>
 *
 * @param seed          arranges the slabs; the same seed always sheds them the same way
 * @param groundY       the height slabs land at, in blocks
 * @param spreadRadius  how far from the centre slabs can land, in blocks
 * @param scale         multiplies slab size and fall height; 1 is what the spell uses
 * @param lifetimeTicks how long it is drawn for, or 0 to draw until removed by hand
 */
public record SkyCollapseParams(int seed, double groundY, double spreadRadius, float scale,
                                int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 220;
    /** How far the spell scatters its slabs, in blocks. */
    public static final double SPELL_SPREAD_RADIUS = 20.0D;

    public static SkyCollapseParams of(int seed, double groundY) {
        return new SkyCollapseParams(seed, groundY, SPELL_SPREAD_RADIUS, 1.0F,
                SPELL_LIFETIME_TICKS);
    }

    public SkyCollapseParams scale(float value) {
        return new SkyCollapseParams(seed, groundY, spreadRadius, value, lifetimeTicks);
    }

    public SkyCollapseParams lifetime(int ticks) {
        return new SkyCollapseParams(seed, groundY, spreadRadius, scale, ticks);
    }

    public SkyCollapseParams spread(double radius) {
        return new SkyCollapseParams(seed, groundY, radius, scale, lifetimeTicks);
    }
}

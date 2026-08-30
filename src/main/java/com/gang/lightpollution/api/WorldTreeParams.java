package com.gang.lightpollution.api;

/**
 * What a world tree's growth looks like.
 *
 * <p>A trunk rising from a seed point, roots gripping the ground around it, then branches unfolding
 * order by order with a leaf crown opening as the twigs that carry it arrive.</p>
 *
 * <h2>Ground level</h2>
 *
 * <p>The spell drops each root onto the terrain surface using the world's heightmap. An effect
 * requested through the API lays them on the flat height in {@code groundY} instead — see
 * {@link SkyCollapseParams} for the same tradeoff.</p>
 *
 * @param seed          arranges the branches and leaves; the same seed always grows the same tree
 * @param groundY       the height the roots lie at, in blocks
 * @param scale         multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks how long it is drawn for, or 0 to draw until removed by hand
 */
public record WorldTreeParams(int seed, double groundY, float scale, int lifetimeTicks) {
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 300;

    public static WorldTreeParams of(int seed, double groundY) {
        return new WorldTreeParams(seed, groundY, 1.0F, SPELL_LIFETIME_TICKS);
    }

    public WorldTreeParams scale(float value) {
        return new WorldTreeParams(seed, groundY, value, lifetimeTicks);
    }

    public WorldTreeParams lifetime(int ticks) {
        return new WorldTreeParams(seed, groundY, scale, ticks);
    }
}

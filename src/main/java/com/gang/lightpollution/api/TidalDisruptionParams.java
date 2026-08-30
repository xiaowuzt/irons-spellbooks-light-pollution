package com.gang.lightpollution.api;

/**
 * What a tidal disruption looks like, independent of the spell that usually causes one.
 *
 * <p>A star pulled into a long thin stream that wraps back around a hole, and the flare when the
 * bound debris returns.</p>
 *
 * <p>These are the values the shape genuinely needs, not a copy of the entity's fields. The geometry
 * was already nearly independent of the entity — it read exactly one synced value, the azimuth — so
 * this is that value plus the knobs a caller would actually reach for.</p>
 *
 * @param azimuthDegrees which way the stream wraps, about the vertical axis
 * @param scale          multiplies every distance; 1 is the size the spell uses
 * @param lifetimeTicks  how long it is drawn for, or 0 to draw until removed by hand
 * @param flareStartTick when the fallback flare begins, in ticks from the start. Part of the
 *                       parameters rather than a constant because the spell's own flare has to line
 *                       up with when its damage lands, so the caller has to own the number.
 */
public record TidalDisruptionParams(float azimuthDegrees, float scale, int lifetimeTicks,
                                    int flareStartTick) {
    /** When the spell's own flare begins. */
    public static final int SPELL_FLARE_TICK = 250;
    /** How long the spell's own effect lasts. */
    public static final int SPELL_LIFETIME_TICKS = 360;

    /** The proportions and timing the spell itself uses, wrapping the way you ask. */
    public static TidalDisruptionParams of(float azimuthDegrees) {
        return new TidalDisruptionParams(azimuthDegrees, 1.0F, SPELL_LIFETIME_TICKS,
                SPELL_FLARE_TICK);
    }

    public TidalDisruptionParams scale(float value) {
        return new TidalDisruptionParams(azimuthDegrees, value, lifetimeTicks, flareStartTick);
    }

    public TidalDisruptionParams lifetime(int ticks) {
        return new TidalDisruptionParams(azimuthDegrees, scale, ticks, flareStartTick);
    }

    public TidalDisruptionParams flareAt(int tick) {
        return new TidalDisruptionParams(azimuthDegrees, scale, lifetimeTicks, tick);
    }
}

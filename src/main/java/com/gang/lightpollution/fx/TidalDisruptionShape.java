package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.TidalDisruptionParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a tidal disruption, as pure functions.
 *
 * <p>Moved off {@code TidalDisruptionEntity} so the same maths can serve both the spell and a caller
 * who only wants the visual. Nothing here touches an entity, a level or a tick counter: everything
 * arrives as an argument, which is what makes the effect reusable at all.</p>
 *
 * <p>The one thing that had to change to get here is the azimuth. It used to be read off the entity's
 * synced data from inside the geometry, so the geometry could not be evaluated without an entity.
 * Now it comes in with the rest of the parameters.</p>
 *
 * <p>The form constants live here and the entity references them, so there is one definition of each
 * rather than two that can drift. The timeline is the other way round: when the flare starts is a
 * gameplay decision the entity owns, so it arrives as an argument instead.</p>
 */
public final class TidalDisruptionShape {
    /** How long the stream takes to draw itself out fully, in ticks. */
    public static final int STRETCH_END_TICK = 46;
    /** Length of the stream at scale 1, in blocks. */
    public static final double STREAM_LENGTH = 62.0D;
    /** Half-width of the stream at its thickest, in blocks. */
    public static final double STREAM_HALF_WIDTH = 1.3D;
    /** Turns the stream wraps through. */
    public static final double WRAP_TURNS = 1.65D;
    /** Radius the stream reaches at its trailing end, in blocks. */
    public static final double WRAP_RADIUS = 21.0D;

    private TidalDisruptionShape() {
    }

    /**
     * A point along the stream.
     *
     * @param fraction 0 at the leading tip, nearest the hole, to 1 at the trailing end
     */
    public static Vec3 streamPoint(TidalDisruptionParams params, Vec3 centre, float ageTicks,
                                  double fraction) {
        // How much of the stream exists yet. The floor is what stops it being a point at tick zero.
        double drawn = Mth.clamp(ageTicks / (double) STRETCH_END_TICK, 0.12D, 1.0D);
        double along = fraction * drawn;

        double azimuth = Math.toRadians(params.azimuthDegrees());
        Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = new Vec3(Math.cos(azimuth), 0.0D, Math.sin(azimuth));
        Vec3 other = axis.cross(side).normalize();

        // Wrapping: the bound debris goes round, tighter as it falls in. The leading tip is
        // closest to the hole, so radius grows along the stream.
        double phase = along * WRAP_TURNS * Math.PI * 2.0D;
        // Starts at the hole, not some blocks away from it. A floor here left the stream visibly
        // detached from the flare it is supposed to be falling into.
        double radius = WRAP_RADIUS * along * params.scale();
        // And it climbs out of the orbital plane a little, because the orbit is inclined.
        double rise = STREAM_LENGTH * 0.10D * Math.sin(along * Math.PI * 1.1D) * params.scale();

        return centre.add(side.scale(Math.cos(phase) * radius))
                .add(other.scale(Math.sin(phase) * radius))
                .add(axis.scale(rise));
    }

    /** Half-width of the stream at a fraction along it, in blocks. */
    public static double streamWidth(TidalDisruptionParams params, double fraction) {
        // Thinnest at the leading tip, where the tidal stretching has had longest to work.
        return STREAM_HALF_WIDTH * (0.35D + 0.65D * fraction) * params.scale();
    }

    /**
     * The fallback flare, 0 to 1.
     *
     * <p>Rises over a few ticks and then decays as t^(-5/3), which is the law that identifies these
     * events: the rate at which bound debris returns to the hole follows that power, so the light
     * curve is computed rather than eased by hand.</p>
     *
     * @param flareStartTick when the flare begins. An argument rather than a constant here because
     *                       it is a timeline decision, and the entity's timeline is also its damage
     *                       schedule — the two have to agree, so only one of them may own the value.
     */
    public static float flare(float ageTicks, int flareStartTick) {
        float since = ageTicks - flareStartTick;
        if (since < 0.0F) {
            return 0.0F;
        }
        float rise = Mth.clamp(since / 8.0F, 0.0F, 1.0F);
        float t = 1.0F + since / 22.0F;
        return rise * (float) Math.pow(t, -5.0D / 3.0D);
    }
}

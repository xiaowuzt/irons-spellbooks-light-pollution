package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.QuasarJetParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The shape and timeline of a relativistic jet, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class QuasarJetShape {
    /** Ticks by which the jet has launched. */
    public static final int LAUNCH_END_TICK = 34;
    /** Ticks by which the jet stops being fed. */
    public static final int STREAM_END_TICK = 280;

    /** Bulk Lorentz factor of the flow. */
    public static final double GAMMA = 10.0D;
    /** Flow speed as a fraction of light, from {@link #GAMMA}. */
    public static final double BETA = Math.sqrt(1.0D - 1.0D / (GAMMA * GAMMA));
    /** Angle between the jet and the line of sight, in degrees. */
    public static final double VIEW_ANGLE = 17.0D;
    /** How far the jet reaches, in blocks. */
    public static final double JET_LENGTH = 72.0D;
    /** Half-width of the jet, in blocks. */
    public static final double JET_HALF_WIDTH = 0.7D;
    /** Bright knots travelling along the jet. */
    public static final int KNOT_COUNT = 5;
    /** Radius of the lobe where the jet terminates, in blocks. */
    public static final double LOBE_RADIUS = 10.0D;
    /**
     * Tilt of the jet from horizontal, in degrees.
     *
     * <p>Tipped up so the jet clears the ground along its length. Deliberately not tied to
     * {@link #VIEW_ANGLE} despite the similar value: that one is the angle between the jet and the
     * line of sight, which depends on where the player is standing, while this is the jet's tilt from
     * horizontal. Forcing them equal would look tidy and conflate two different quantities.</p>
     */
    public static final double ELEVATION = 16.0D;

    private QuasarJetShape() {
    }

    /** How far the jet has launched, 0 to 1. */
    public static float launched(float ageTicks) {
        return smoothstep(ageTicks / LAUNCH_END_TICK);
    }

    /**
     * The jet's apparent transverse speed, as a multiple of light.
     *
     * <p>Greater than one for a jet pointed near the viewer, which is not a violation of anything —
     * it is what the light travel time does to the appearance of a flow that is merely fast.</p>
     */
    public static double apparentSpeed() {
        double theta = Math.toRadians(VIEW_ANGLE);
        return BETA * Math.sin(theta) / (1.0D - BETA * Math.cos(theta));
    }

    /** How fast a knot appears to travel, in blocks per tick. */
    public static double blocksPerTick() {
        return JET_LENGTH / 44.0D * (apparentSpeed() / 6.0D);
    }

    /** The jet direction for these parameters. */
    public static Vec3 direction(QuasarJetParams params) {
        double azimuth = Math.toRadians(params.azimuthDegrees());
        double elevation = Math.toRadians(ELEVATION);
        return new Vec3(Math.cos(elevation) * Math.cos(azimuth),
                Math.sin(elevation),
                Math.cos(elevation) * Math.sin(azimuth)).normalize();
    }

    /**
     * How far along the jet a knot has travelled, as a fraction of its length.
     *
     * <p>Knots are launched at even intervals and cross the jet in the time the apparent speed
     * implies, so the spacing a viewer sees is the spacing the formula gives. Negative means that
     * knot has not launched yet.</p>
     */
    public static double knotProgress(int knot, float ageTicks) {
        double crossingTicks = JET_LENGTH / Math.max(blocksPerTick(), 0.001D);
        double launched = ageTicks - knot * (crossingTicks / KNOT_COUNT);
        if (launched <= 0.0D) {
            return -1.0D;
        }
        return (launched % crossingTicks) / crossingTicks;
    }

    /** Where a knot sits, or null if it has not launched yet. */
    @Nullable
    public static Vec3 knotPosition(QuasarJetParams params, Vec3 centre, int knot, float ageTicks) {
        double progress = knotProgress(knot, ageTicks);
        if (progress < 0.0D) {
            return null;
        }
        return centre.add(direction(params).scale(JET_LENGTH * params.scale() * progress));
    }

    /** Where the terminal lobe sits. */
    public static Vec3 lobeCentre(QuasarJetParams params, Vec3 centre) {
        return centre.add(direction(params).scale(JET_LENGTH * params.scale()));
    }

    /** Smoothstep, so the ramps ease in and out rather than starting abruptly. */
    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

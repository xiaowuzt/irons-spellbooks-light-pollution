package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.PinwheelParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a pinwheel galaxy's spiral arms, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class PinwheelShape {
    /** Ticks by which the galaxy has spun up. */
    public static final int SPIN_UP_END_TICK = 34;
    /** Ticks by which it stops turning. */
    public static final int SPIN_END_TICK = 250;

    /** Arms drawn. */
    public static final int ARMS = 2;
    /** How far the arms reach, in blocks. */
    public static final double SPIRAL_REACH = 26.0D;
    /** Turns each arm winds through over its length. */
    public static final double SPIRAL_TURNS = 2.2D;
    /** Turns the whole galaxy rotates through over its lit span. */
    public static final double ROTATION_TURNS = 1.4D;
    /** Half-width of an arm, in blocks. */
    public static final double ARM_HALF_WIDTH = 1.1D;
    /** Tilt of the galactic plane from horizontal, in degrees. */
    public static final double PLANE_TILT = 18.0D;

    private PinwheelShape() {
    }

    /** The normal of the galactic plane for these parameters. */
    public static Vec3 planeNormal(PinwheelParams params) {
        double azimuth = Math.toRadians(params.azimuthDegrees());
        double tilt = Math.toRadians(PLANE_TILT);
        return new Vec3(Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt),
                Math.sin(tilt) * Math.sin(azimuth)).normalize();
    }

    /** How far the galaxy has spun up, 0 to 1. */
    public static float spunUp(float ageTicks) {
        return smoothstep(ageTicks / SPIN_UP_END_TICK);
    }

    /** How far the galaxy has turned at a given age, in radians. */
    public static double rotation(float ageTicks) {
        double lit = Math.max(1.0D, SPIN_END_TICK - SPIN_UP_END_TICK);
        double progress = (ageTicks - SPIN_UP_END_TICK) / lit;
        return progress * ROTATION_TURNS * Math.PI * 2.0D;
    }

    /**
     * A point along one spiral arm.
     *
     * @param fraction 0 at the core, 1 at the arm's tip
     * @param rotation from {@link #rotation}
     */
    public static Vec3 armPoint(PinwheelParams params, Vec3 centre, int arm, double fraction,
                                double rotation) {
        Vec3 normal = planeNormal(params);
        Vec3 axisU = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (axisU.lengthSqr() < 1.0e-6D) {
            axisU = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        axisU = axisU.normalize();
        Vec3 axisV = normal.cross(axisU).normalize();

        double angle = fraction * SPIRAL_TURNS * Math.PI * 2.0D
                + arm * (Math.PI * 2.0D / ARMS)
                + rotation;
        double radius = SPIRAL_REACH * params.scale() * fraction;

        return centre.add(axisU.scale(Math.cos(angle) * radius))
                .add(axisV.scale(Math.sin(angle) * radius));
    }

    /** Half-width of an arm at a fraction along it, in blocks. */
    public static double armWidth(PinwheelParams params, double fraction) {
        // Dust spreads as it travels, so the arm thickens outward.
        return ARM_HALF_WIDTH * params.scale() * (0.45D + 0.75D * fraction);
    }

    /** Smoothstep, so the ramps ease in and out rather than starting abruptly. */
    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.MagnetarParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a magnetar's twisting dipole field, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class MagnetarShape {
    /** Ticks by which the field lines have threaded out. */
    public static final int THREAD_END_TICK = 36;
    /** Ticks by which the winding stops and the field lets go. */
    public static final int WIND_END_TICK = 230;
    /** Ticks at which the flare lights. */
    public static final int FLARE_TICK = WIND_END_TICK;
    /** How long the flare lasts, in ticks. */
    public static final float FLARE_DURATION = 26.0F;

    /** Field lines drawn. */
    public static final int FIELD_LINES = 14;
    /** How far the outermost loop reaches, in blocks. */
    public static final double FIELD_REACH = 19.0D;
    /** Radius of the neutron star itself, in blocks. */
    public static final double STAR_RADIUS = 2.1D;
    /** Half-width of a field loop, in blocks. */
    public static final double LOOP_HALF_WIDTH = 0.42D;
    /** Turns the field is twisted through before it lets go. */
    public static final double TWIST_TURNS = 0.85D;
    /**
     * Tilt of the dipole axis from vertical, in degrees.
     *
     * <p>Leaned over rather than upright, because a dipole standing straight up hides its loops
     * behind each other from a viewer on the ground.</p>
     */
    public static final double AXIS_TILT = 28.0D;

    private MagnetarShape() {
    }

    /** The dipole axis for these parameters. */
    public static Vec3 axis(MagnetarParams params) {
        double azimuth = Math.toRadians(params.azimuthDegrees());
        double tilt = Math.toRadians(AXIS_TILT);
        return new Vec3(Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt),
                Math.sin(tilt) * Math.sin(azimuth)).normalize();
    }

    /** How far the field has wound up, 0 to 1. */
    public static float wound(float ageTicks) {
        if (ageTicks <= THREAD_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((ageTicks - THREAD_END_TICK)
                / (float) (WIND_END_TICK - THREAD_END_TICK), 0.0F, 1.0F);
    }

    /** The flare, 0 to 1. A hard onset then a linear decay. */
    public static float flare(float ageTicks, int flareTick) {
        float since = ageTicks - flareTick;
        if (since < 0.0F || since > FLARE_DURATION) {
            return 0.0F;
        }
        return 1.0F - since / FLARE_DURATION;
    }

    /**
     * A point along one dipole field line.
     *
     * @param along 0 at one pole, 1 at the other
     */
    public static Vec3 fieldPoint(MagnetarParams params, Vec3 centre, int line, double along,
                                  float woundFraction) {
        Vec3 axis = axis(params);
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0e-6D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 other = axis.cross(side).normalize();

        // theta from 0 (one pole) to pi (the other).
        double theta = along * Math.PI;
        double sin = Math.sin(theta);
        double cos = Math.cos(theta);
        // Alternating loop sizes so the shell has structure instead of one nested set.
        double scale = 0.45D + 0.55D * ((line % 3) / 2.0D);
        double shell = FIELD_REACH * scale * params.scale();

        // The dipole field line in cylindrical form. From r = L sin^2(theta):
        //     rho = r sin(theta) = L sin^3(theta)
        //     z   = r cos(theta) = L sin^2(theta) cos(theta)
        //
        // Written out this way rather than as r divided by sin, which is what it was before.
        // That version needed a clamp to survive the poles, and the clamp flattened the loops
        // so hard that both ends collapsed onto the star's centre — every line ran out from
        // the middle and back into it, which is why they read as open arcs with loose ends
        // instead of as closed loops.
        double rho = shell * sin * sin * sin;
        double z = shell * sin * sin * cos;

        // Anchor the ends on the star's surface rather than at a point. A mathematical dipole
        // is a point and its lines all return to it; a real one has a body, and the lines
        // terminate at two separated magnetic poles. That separation is what makes each loop
        // visibly leave somewhere and arrive somewhere else.
        z += STAR_RADIUS * params.scale() * cos;

        // The twist has to vanish at both ends, or the two ends of one line sit at different
        // azimuths and the loop cannot close. sin^2 goes to zero at both poles.
        double azimuth = 2.0D * Math.PI * line / FIELD_LINES
                + woundFraction * TWIST_TURNS * Math.PI * 2.0D * sin * sin;

        Vec3 radial = side.scale(Math.cos(azimuth)).add(other.scale(Math.sin(azimuth)));
        return centre.add(axis.scale(z)).add(radial.scale(rho));
    }
}

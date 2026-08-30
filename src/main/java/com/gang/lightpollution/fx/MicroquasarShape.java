package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.MicroquasarParams;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a microquasar's precessing twin jets, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class MicroquasarShape {
    /** Ticks by which the disc has spun up and the jets are lit. */
    public static final int SPINUP_END_TICK = 40;
    /** Ticks by which the jets stop being driven. */
    public static final int HOLD_END_TICK = 260;

    /** Jet speed as a fraction of light, used to place blobs along the jet. */
    public static final double JET_BETA = 0.26D;
    /** Half-angle of the precession cone, in degrees. */
    public static final double CONE_HALF_ANGLE = 20.0D;
    /** Tilt of the jet axis from vertical, in degrees. */
    public static final double AXIS_TILT = 38.0D;
    /** How far each jet reaches, in blocks. */
    public static final double JET_LENGTH = 54.0D;
    /** Blobs drawn per jet. */
    public static final int BULLETS_PER_JET = 30;
    /** Turns the disc precesses through over the jets' lit span. */
    public static final double PRECESSION_CYCLES = 1.5D;
    /** Radius of the beam itself, in blocks. */
    public static final double BEAM_RADIUS = 3.4D;

    private MicroquasarShape() {
    }

    /**
     * How fast a blob travels, in blocks per tick.
     *
     * <p>Derived rather than chosen, so the helix has exactly {@link #PRECESSION_CYCLES} turns
     * visible along the jet's length. Picking a speed independently would leave the number of visible
     * turns depending on it.</p>
     */
    public static double blocksPerTick() {
        double litTicks = HOLD_END_TICK - SPINUP_END_TICK;
        double ticksPerTurn = litTicks / PRECESSION_CYCLES;
        return JET_LENGTH / (ticksPerTurn * 0.2D);
    }

    /** Where the disc is in its precession, in radians. */
    public static double precessionPhase(float ageTicks) {
        double lit = Math.max(1.0D, HOLD_END_TICK - SPINUP_END_TICK);
        double progress = (ageTicks - SPINUP_END_TICK) / lit;
        return progress * PRECESSION_CYCLES * Math.PI * 2.0D;
    }

    /** The jet axis for these parameters. */
    public static Vec3 axis(MicroquasarParams params) {
        double azimuth = Math.toRadians(params.azimuthDegrees());
        double tilt = Math.toRadians(AXIS_TILT);
        return new Vec3(Math.sin(tilt) * Math.cos(azimuth),
                Math.cos(tilt),
                Math.sin(tilt) * Math.sin(azimuth)).normalize();
    }

    /** Where blob {@code index} of a jet is. */
    public static Vec3 bulletPosition(MicroquasarParams params, Vec3 centre, float ageTicks,
                                      boolean forward, int index) {
        return helixPoint(params, centre, ageTicks, forward, index / (double) BULLETS_PER_JET);
    }

    /**
     * A point along one jet.
     *
     * <p>Each blob keeps the direction the jet had when it left, which is what makes the pair trace a
     * helix instead of a straight line. So the direction is computed from the phase at that blob's
     * launch time, not the current one.</p>
     *
     * @param fraction 0 at the hole, 1 at the jet's end
     */
    public static Vec3 helixPoint(MicroquasarParams params, Vec3 centre, float ageTicks,
                                  boolean forward, double fraction) {
        double travel = JET_LENGTH * params.scale() * fraction;
        // Ticks since this blob left, from how far it has gone. 20 ticks per second, and
        // JET_BETA is scaled to blocks per tick by the same constant the renderer uses.
        double ticksAgo = travel / (blocksPerTick() * params.scale());
        double launchPhase = precessionPhase((float) (ageTicks - ticksAgo));

        Vec3 axis = axis(params);
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0e-6D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 other = axis.cross(side).normalize();

        double cone = Math.toRadians(CONE_HALF_ANGLE);
        Vec3 direction = axis.scale(Math.cos(cone))
                .add(side.scale(Math.sin(cone) * Math.cos(launchPhase)))
                .add(other.scale(Math.sin(cone) * Math.sin(launchPhase)))
                .normalize();
        if (!forward) {
            direction = direction.reverse();
        }
        return centre.add(direction.scale(travel));
    }
}

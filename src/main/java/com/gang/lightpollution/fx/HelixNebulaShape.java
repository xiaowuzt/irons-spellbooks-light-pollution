package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.HelixNebulaParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a planetary nebula's knotted shell, as pure functions.
 *
 * <p>Extracted from two places rather than one, unlike the tidal disruption. Its shell radius lived
 * on the entity, but the knot placement — the frame, the hash, the scatter through the shell's
 * thickness — was written inline in the renderer, so that renderer could not have drawn anything but
 * the spell's own entity even in principle.</p>
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class HelixNebulaShape {
    /** Ticks before the shell starts expanding. */
    public static final int IGNITION_END_TICK = 30;
    /** Ticks by which the shell has reached full size. */
    public static final int SHELL_END_TICK = 280;
    /** Shell radius before expansion, in blocks. */
    public static final double SHELL_START_RADIUS = 3.0D;
    /** Shell radius once expanded, in blocks. */
    public static final double SHELL_END_RADIUS = 30.0D;
    /** Half-thickness of the shell, in blocks. The knots live in this layer. */
    public static final double SHELL_THICKNESS = 3.2D;
    /**
     * The outer ring's radius as a multiple of the inner one.
     *
     * <p>The two rings are what make it an eye rather than a bubble. They are also different colours
     * in every image of the object, and for a reason worth keeping: the inner ring glows in doubly
     * ionised oxygen, which is blue-green, and the outer in hydrogen and nitrogen, which is red. Not
     * a gradient — two distinct shells.</p>
     */
    public static final double OUTER_RING_SCALE = 1.42D;
    /** How far the ring pair is tilted, in degrees. Face-on it would read as a circle, not an eye. */
    public static final double RING_INCLINATION = 37.0D;
    /**
     * Knots in the shell.
     *
     * <p>Scaled down hard from the roughly 40,000 counted in the real nebula. At this distance the
     * real count would be sub-pixel and cost a fortune; what carries the look is that every knot has
     * an oriented tail, not how many there are.</p>
     */
    public static final int KNOT_COUNT = 560;

    private HelixNebulaShape() {
    }

    /** The stable frame the rings sit in: a normal and two axes across it. */
    public record Frame(Vec3 normal, Vec3 axisU, Vec3 axisV) {
    }

    /** One knot: where it is, which way it faces away from the star, and how bright. */
    public record Knot(Vec3 at, Vec3 outward, float shade, boolean outerRing) {
    }

    /**
     * Radius of the shell at a given age, in blocks.
     *
     * <p>Eased rather than linear: the envelope is being pushed while the star is lighting and then
     * coasts, so it decelerates.</p>
     */
    public static double shellRadius(HelixNebulaParams params, float ageTicks) {
        if (ageTicks <= IGNITION_END_TICK) {
            return SHELL_START_RADIUS * params.scale();
        }
        double span = SHELL_END_TICK - IGNITION_END_TICK;
        double t = Mth.clamp((ageTicks - IGNITION_END_TICK) / span, 0.0D, 1.0D);
        double eased = 1.0D - Math.pow(1.0D - t, 2.2D);
        return (SHELL_START_RADIUS + (SHELL_END_RADIUS - SHELL_START_RADIUS) * eased)
                * params.scale();
    }

    /**
     * The ring frame for these parameters.
     *
     * <p>Computed once per nebula rather than per knot: it depends only on the azimuth, and there are
     * five hundred and sixty knots.</p>
     */
    public static Frame frame(HelixNebulaParams params) {
        double azimuth = Math.toRadians(params.azimuthDegrees());
        double tilt = Math.toRadians(RING_INCLINATION);
        Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt),
                Math.sin(tilt) * Math.sin(azimuth)).normalize();
        Vec3 axisU = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (axisU.lengthSqr() < 1.0e-6D) {
            // Normal is vertical, so world up gives no reference. Any perpendicular will do.
            axisU = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        axisU = axisU.normalize();
        return new Frame(normal, axisU, normal.cross(axisU).normalize());
    }

    /**
     * Where knot {@code index} is.
     *
     * <p>Alternate knots go to the outer ring, so the pair fills evenly without needing two loops.
     * Placement is hashed off the seed rather than random, which is what lets the server and every
     * client agree on the arrangement without syncing five hundred and sixty positions.</p>
     */
    public static Knot knot(HelixNebulaParams params, Vec3 centre, Frame frame, float ageTicks,
                            int index) {
        double inner = shellRadius(params, ageTicks);
        boolean outerRing = (index & 1) == 0;
        double ringRadius = outerRing ? inner * OUTER_RING_SCALE : inner;

        double angle = FxHash.at(params.seed(), index, 1) * Math.PI * 2.0D;
        // Scattered through the shell's thickness rather than pinned to a circle, because the knots
        // occupy a layer, not a wire.
        double thickness = SHELL_THICKNESS * params.scale();
        double radial = ringRadius + (FxHash.at(params.seed(), index, 2) - 0.5D) * thickness * 2.0D;
        double outOfPlane = (FxHash.at(params.seed(), index, 3) - 0.5D) * thickness * 1.4D;

        Vec3 outward = frame.axisU().scale(Math.cos(angle))
                .add(frame.axisV().scale(Math.sin(angle)));
        Vec3 at = centre.add(outward.scale(radial)).add(frame.normal().scale(outOfPlane));
        float shade = 0.55F + 0.45F * (float) FxHash.at(params.seed(), index, 4);
        return new Knot(at, outward, shade, outerRing);
    }
}

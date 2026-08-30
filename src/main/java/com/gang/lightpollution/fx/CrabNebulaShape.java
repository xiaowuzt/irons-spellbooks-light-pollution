package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.CrabNebulaParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a supernova remnant's filament cage, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class CrabNebulaShape {
    /** Ticks by which the remnant has unfolded and the cage is closed. */
    public static final int FORM_END_TICK = 40;
    /** Ticks by which the pulsar stops driving its wind. */
    public static final int WIND_END_TICK = 270;

    /** Radius of the shell once formed, in blocks. */
    public static final double SHELL_RADIUS = 21.0D;
    /**
     * How much the shell grows over its life, as a fraction.
     *
     * <p>Small on purpose. The real remnant expands at around 1500 km/s, which is fast in absolute
     * terms and slow next to its size — it has taken a thousand years to get where it is. A shell that
     * visibly raced outward would be a planetary nebula, which this set already has; the Crab's
     * character is that it hangs there.</p>
     */
    public static final double SHELL_GROWTH = 0.16D;
    /** Filaments in the cage. */
    public static final int FILAMENTS = 22;
    /** Half-width of a filament, in blocks. */
    public static final double FILAMENT_HALF_WIDTH = 0.55D;
    /** Radius of the interior wind nebula, as a fraction of the shell. */
    public static final double WIND_FRACTION = 0.72D;
    /** Ticks between wind pulses. The pulsar's own rhythm, slowed to be readable. */
    public static final int WIND_INTERVAL_TICKS = 10;

    private CrabNebulaShape() {
    }

    /** Radius of the shell at a given age, in blocks. */
    public static double shellRadius(CrabNebulaParams params, float ageTicks) {
        float formed = Mth.clamp(ageTicks / (float) FORM_END_TICK, 0.0F, 1.0F);
        double drift = Mth.clamp((ageTicks - FORM_END_TICK)
                / (double) (WIND_END_TICK - FORM_END_TICK), 0.0D, 1.0D);
        return SHELL_RADIUS * (0.2D + 0.8D * smoothstep(formed))
                * (1.0D + SHELL_GROWTH * drift) * params.scale();
    }

    /** The pulsar's wind, 0 to 1. Sharp rise, slow decay — a pulse, not a sine. */
    public static float windPulse(float ageTicks) {
        float phase = (ageTicks % WIND_INTERVAL_TICKS) / WIND_INTERVAL_TICKS;
        return (float) Math.pow(1.0D - phase, 2.4D);
    }

    /**
     * A point along one filament loop.
     *
     * <p>Closed by construction: every term in the angle is periodic over a full turn, so fraction 0
     * and fraction 1 land on the same point without the caller having to stitch the ends.</p>
     *
     * @param fraction 0 to 1 around the loop
     */
    public static Vec3 filamentPoint(CrabNebulaParams params, Vec3 centre, int filament,
                                     double fraction, float ageTicks) {
        double radius = shellRadius(params, ageTicks);
        int seed = params.seed();

        // Two angles per filament, hashed off the seed, giving each its own plane.
        double lean = FxHash.at(seed, filament, 1) * Math.PI;
        double spin = FxHash.at(seed, filament, 2) * Math.PI * 2.0D;
        double angle = FxHash.at(seed, filament, 4) * Math.PI * 2.0D
                + Math.PI * 2.0D * fraction;

        // An orthonormal frame: u and w span the loop's nominal plane, n is its normal.
        Vec3 u = new Vec3(Math.cos(spin), 0.0D, Math.sin(spin));
        Vec3 w = new Vec3(-Math.sin(spin) * Math.cos(lean), Math.sin(lean),
                Math.cos(spin) * Math.cos(lean));
        Vec3 n = u.cross(w);

        // Out-of-plane weave, which is what keeps the cage a tangle instead of a globe. The
        // per-filament phases are constant along the loop, so they shift the pattern without
        // breaking the periodicity the closure depends on.
        double weave = 0.30D * Math.sin(angle * 2.0D + filament * 1.7D)
                + 0.17D * Math.sin(angle * 3.0D - filament * 2.3D);
        // Filaments are not perfectly on the surface — they ripple, which is part of why the
        // real ones look like a tangle.
        double ripple = 1.0D + 0.09D * Math.sin(angle * 3.0D + filament);

        // Normalised, so the weave tilts the loop across the shell instead of lifting it off.
        Vec3 direction = u.scale(Math.cos(angle))
                .add(w.scale(Math.sin(angle)))
                .add(n.scale(weave))
                .normalize();
        return centre.add(direction.scale(radius * ripple));
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

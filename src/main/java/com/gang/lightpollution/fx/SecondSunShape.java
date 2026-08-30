package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SecondSunParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a second sun rising and going nova, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class SecondSunShape {
    /** Ticks by which the disc has cleared the horizon. */
    public static final int RISE_END_TICK = 110;
    /** Ticks by which it has finished swelling. */
    public static final int SWELL_END_TICK = 210;
    /** Ticks at which it detonates. */
    public static final int NOVA_TICK = 220;
    /** Ticks by which the nova's flare has passed. */
    public static final int NOVA_END_TICK = 270;

    /** Angular diameter of the disc, in degrees, before it swells. */
    public static final float DISC_ANGLE_DEGREES = 13.0F;
    /** How much larger it gets as it swells. */
    public static final float SWELL_FACTOR = 1.9F;

    private SecondSunShape() {
    }

    /**
     * Altitude of the disc above the horizon, 0 to 1, where 1 is the zenith.
     *
     * <p>Eased at both ends: it lifts clear of the horizon slowly, which is what makes it read as
     * something enormous rather than something rising fast.</p>
     */
    public static float altitude(float ageTicks) {
        if (ageTicks >= RISE_END_TICK) {
            return 1.0F;
        }
        return smoothstep(ageTicks / RISE_END_TICK);
    }

    /** Angular diameter of the disc at a given age, in degrees. */
    public static float discAngleDegrees(SecondSunParams params, float ageTicks) {
        float swell = ageTicks <= RISE_END_TICK
                ? 0.0F
                : Mth.clamp((ageTicks - RISE_END_TICK)
                        / (float) (SWELL_END_TICK - RISE_END_TICK), 0.0F, 1.0F);
        float base = DISC_ANGLE_DEGREES * (1.0F + swell * (SWELL_FACTOR - 1.0F)) * params.scale();
        if (ageTicks <= NOVA_TICK) {
            return base;
        }
        // The nova throws the shell outward, then the remnant collapses.
        float since = ageTicks - NOVA_TICK;
        float expand = Mth.clamp(since / 18.0F, 0.0F, 1.0F);
        float collapse = Mth.clamp((since - 18.0F) / 32.0F, 0.0F, 1.0F);
        return base * (1.0F + expand * 1.8F) * (1.0F - collapse * 0.95F);
    }

    /** How hot it looks, 0 to 1. Cools as it swells, white-hot again when it detonates. */
    public static float temperature(float ageTicks) {
        if (ageTicks <= RISE_END_TICK) {
            return 1.0F;
        }
        if (ageTicks <= NOVA_TICK) {
            float swell = (ageTicks - RISE_END_TICK) / (float) (NOVA_TICK - RISE_END_TICK);
            return 1.0F - swell * 0.75F;
        }
        return Math.min(1.0F, 0.25F + (ageTicks - NOVA_TICK) * 0.2F);
    }

    /** The nova's flash, 0 to 1. Peaks a moment after detonation, not instantly. */
    public static float novaFlash(float ageTicks) {
        float since = ageTicks - NOVA_TICK;
        if (since < 0.0F || since > 26.0F) {
            return 0.0F;
        }
        float t = since / 26.0F;
        return Mth.sin(t * Mth.PI) * (1.0F - t * 0.35F);
    }

    /**
     * Overall brightness. Overshoots above 1 during the nova, deliberately, for the bloom.
     *
     * @param lifetimeTicks when the tail fade should reach zero
     */
    public static float brightness(float ageTicks, int lifetimeTicks) {
        if (ageTicks <= RISE_END_TICK) {
            return altitude(ageTicks);
        }
        if (ageTicks <= NOVA_TICK) {
            return 1.0F;
        }
        if (ageTicks <= NOVA_END_TICK) {
            // A hard flare, then a long decline.
            return Math.max(0.0F, 4.5F - (ageTicks - NOVA_TICK) * 0.09F);
        }
        return Math.max(0.0F, 1.0F - (ageTicks - NOVA_END_TICK)
                / (float) (lifetimeTicks - NOVA_END_TICK));
    }

    /**
     * Which way the disc lies, as a unit vector.
     *
     * <p>Sweeps from the horizon to near the zenith, stopping short of straight overhead so the
     * shadows it throws stay long enough to be legible.</p>
     */
    public static Vec3 discDirection(SecondSunParams params, float ageTicks) {
        float bearing = params.bearingRadians();
        float elevation = Mth.lerp(altitude(ageTicks), 0.04F, 0.78F) * Mth.HALF_PI;
        float horizontal = Mth.cos(elevation);
        return new Vec3(Mth.cos(bearing) * horizontal, Mth.sin(elevation),
                Mth.sin(bearing) * horizontal);
    }

    /** Where the disc sits, far enough out to read as sky. */
    public static Vec3 discPosition(SecondSunParams params, Vec3 viewer, float ageTicks,
                                    double distance) {
        return viewer.add(discDirection(params, ageTicks).scale(distance));
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

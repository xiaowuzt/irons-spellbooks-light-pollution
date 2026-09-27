package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.ConstellationParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a constellation's orbiting star, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class ConstellationShape {
    /** Stars drawn. One, by design. */
    public static final int STAR_COUNT = 1;
    /** Ticks by which the star has settled into its orbit. */
    public static final int GATHER_END_TICK = 30;
    /** Ticks at which the tail fade begins. */
    public static final int FADE_START_TICK = 210;
    /** Length of the normal tail fade. */
    private static final int FADE_DURATION_TICKS = 30;

    /** Radius of the ring orbit, in blocks. */
    public static final float RING_RADIUS = 9.0F;
    /** Height of the ring above the anchor, in blocks. */
    public static final float RING_HEIGHT = 7.0F;
    /** Radius of the star's body, in blocks. */
    public static final float STAR_BODY_RADIUS = 1.6F;
    /** Turns the star completes over its life. */
    public static final float ORBIT_TURNS = 1.0F;

    private ConstellationShape() {
    }

    /**
     * Where the star is.
     *
     * <p>Falls in from high and far, easing into the orbit, then stays on the ring.</p>
     */
    public static Vec3 starPosition(ConstellationParams params, Vec3 anchor, float ageTicks,
                                    int lifetimeTicks) {
        float sc = params.scale();
        // A zero lifetime means "until removed by hand" in the public API. Keep
        // that timeline finite and deterministic instead of producing NaN from
        // a zero denominator on the first frame.
        float orbitProgress = lifetimeTicks <= 0
                ? 0.0F
                : Mth.clamp(ageTicks / (float) lifetimeTicks, 0.0F, 1.0F);
        float angle = params.spinOffsetRadians() + Mth.TWO_PI * ORBIT_TURNS * orbitProgress;
        float ringX = Mth.cos(angle) * RING_RADIUS * sc;
        float ringZ = Mth.sin(angle) * RING_RADIUS * sc;
        Vec3 orbitPos = anchor.add(ringX, RING_HEIGHT * sc, ringZ);

        if (ageTicks < GATHER_END_TICK) {
            float t = smoothstep(ageTicks / GATHER_END_TICK);
            Vec3 start = anchor.add(ringX * 2.2D, (RING_HEIGHT + 26.0D) * sc, ringZ * 2.2D);
            return start.lerp(orbitPos, t);
        }
        return orbitPos;
    }

    /** How bright the star is, 0 to 1. */
    public static float starBrightness(float ageTicks, int lifetimeTicks) {
        float gather = ageTicks < GATHER_END_TICK
                ? smoothstep(ageTicks / GATHER_END_TICK) : 1.0F;
        // Keep the public API's unbounded lifetime truly unbounded while still
        // respecting the normal gather-in envelope.
        if (lifetimeTicks <= 0) {
            return gather;
        }
        // Short custom lifetimes still get a valid tail window. The normal
        // timeline is 210..240; when the caller ends it earlier, move the start
        // back while retaining the same 30-tick fade where possible.
        int fadeEnd = Math.max(1, lifetimeTicks);
        int fadeStart = Math.min(FADE_START_TICK,
                Math.max(0, fadeEnd - FADE_DURATION_TICKS));
        float fade = ageTicks < fadeStart ? 0.0F
                : (ageTicks - fadeStart) / (float) Math.max(1, fadeEnd - fadeStart);
        float tail = Math.max(0.0F, 1.0F - smoothstep(fade));
        return Math.min(gather, tail);
    }

    /** Radius of the star's body, in blocks. */
    public static float bodyRadius(ConstellationParams params) {
        return STAR_BODY_RADIUS * params.scale();
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

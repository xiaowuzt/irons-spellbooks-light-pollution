package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SingularityParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a collapsing singularity, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter.</p>
 */
public final class SingularityShape {
    /** Ticks by which the core has opened. */
    public static final int OPEN_END_TICK = 20;
    /** Ticks at which it collapses. */
    public static final int COLLAPSE_TICK = 120;
    /** Ticks by which the afterglow has gone. */
    public static final int AFTERGLOW_END_TICK = 200;

    /** Radius of the core at full size, in blocks. */
    public static final float CORE_RADIUS = 2.4F;
    /** Lightning bolts drawn at once. */
    public static final int MAX_BOLTS = 10;
    /** How far a bolt can reach, in blocks. */
    public static final float BOLT_REACH = 22.0F;
    /** Shockwave fronts behind the collapse. */
    public static final int SHOCKWAVE_COUNT = 3;

    private SingularityShape() {
    }

    /** How far the core has charged, 0 to 1. Quadratic, so it accelerates into the collapse. */
    public static float charge(float ageTicks) {
        if (ageTicks >= COLLAPSE_TICK) {
            return 1.0F;
        }
        if (ageTicks <= OPEN_END_TICK) {
            return 0.0F;
        }
        float t = (ageTicks - OPEN_END_TICK) / (float) (COLLAPSE_TICK - OPEN_END_TICK);
        return t * t;
    }

    /** Radius of the core at a given age, in blocks. */
    public static float coreRadius(SingularityParams params, float ageTicks) {
        float radius = CORE_RADIUS * params.scale();
        if (ageTicks <= OPEN_END_TICK) {
            return radius * smoothstep(ageTicks / OPEN_END_TICK);
        }
        if (ageTicks < COLLAPSE_TICK) {
            // Contracting: whatever it is pulling in has to go somewhere.
            return radius * (1.0F - charge(ageTicks) * 0.45F);
        }
        return 0.0F;
    }

    /** How bright the core is. Above 1 on purpose, for the bloom to pick up. */
    public static float coreBrightness(float ageTicks) {
        if (ageTicks <= OPEN_END_TICK) {
            return smoothstep(ageTicks / OPEN_END_TICK) * 0.6F;
        }
        if (ageTicks < COLLAPSE_TICK) {
            return 0.6F + charge(ageTicks) * 2.6F;
        }
        return 0.0F;
    }

    /** The collapse flash, 0 to 1. Peaks a couple of ticks in rather than instantly. */
    public static float blastFlash(float ageTicks) {
        float since = ageTicks - COLLAPSE_TICK;
        if (since < 0.0F || since > 30.0F) {
            return 0.0F;
        }
        float t = since / 30.0F;
        return Mth.sin(Math.min(t * 3.4F, 1.0F) * Mth.PI * 0.5F) * (1.0F - t * 0.6F);
    }

    /**
     * Radius of one shockwave front, in blocks.
     *
     * <p>Decelerating, like a real front losing energy: linear expansion reads as a growing sphere
     * rather than as a blast.</p>
     */
    public static float shockwaveRadius(SingularityParams params, int wave, float ageTicks) {
        float since = ageTicks - COLLAPSE_TICK;
        if (since < 0.0F) {
            return 0.0F;
        }
        float speed = switch (wave) {
            case 0 -> 1.5F;
            case 1 -> 0.85F;
            default -> 0.45F;
        };
        float radius = speed * since * (1.0F - since / 260.0F) * params.scale();
        return Math.max(0.0F, radius);
    }

    /** How strong one shockwave front is, 0 to 1. */
    public static float shockwaveStrength(int wave, float ageTicks) {
        float since = ageTicks - COLLAPSE_TICK;
        float life = switch (wave) {
            case 0 -> 34.0F;
            case 1 -> 58.0F;
            default -> 76.0F;
        };
        if (since < 0.0F || since > life) {
            return 0.0F;
        }
        float fade = 1.0F - since / life;
        return fade * fade;
    }

    /** Ticks between bolt re-rolls. Shortens as the core charges, so the lashing speeds up. */
    public static int boltInterval(float ageTicks) {
        return Math.max(1, Math.round(15.0F - charge(ageTicks) * 14.0F));
    }

    /**
     * Direction of one bolt.
     *
     * <p>Re-rolled every {@code bucket}, so the whole set lashes to new places rather than sitting
     * still.</p>
     */
    public static Vec3 boltDirection(SingularityParams params, int bolt, int bucket) {
        int seed = params.seed();
        float yaw = FxHash.unit(seed, bolt * 71 + bucket * 17, 0x9E3779B9L) * Mth.TWO_PI;
        float pitch = (FxHash.unit(seed, bolt * 91 + bucket * 31, 0x85EBCA6BL) - 0.5F) * Mth.PI;
        float horizontal = Mth.cos(pitch);
        return new Vec3(Mth.cos(yaw) * horizontal, Mth.sin(pitch), Mth.sin(yaw) * horizontal);
    }

    /** Length of one bolt, in blocks. */
    public static float boltLength(SingularityParams params, int bolt, int bucket) {
        float unit = FxHash.unit(params.seed(), bolt * 53 + bucket * 7, 0xC2B2AE3DL);
        return BOLT_REACH * params.scale() * (0.35F + unit * 0.65F);
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

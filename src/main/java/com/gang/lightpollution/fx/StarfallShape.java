package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.StarfallParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a meteor shower, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter. As with the sky collapse, where a
 * meteor lands is the one thing it cannot answer — see {@link StarfallSource#meteorLanding}.</p>
 */
public final class StarfallShape {
    /** Ticks of omen before the first meteor. */
    public static final int OMEN_END_TICK = 40;
    /** Ticks by which the shower stops. */
    public static final int RAIN_END_TICK = 240;
    /** Ticks at which the finale arrives. */
    public static final int FINALE_TICK = 240;
    /** Ticks at which the tail fade begins. */
    public static final int FADE_START_TICK = 290;
    /** Ticks between one meteor and the next. */
    public static final int SPAWN_INTERVAL_TICKS = 5;

    /** Ticks an ordinary meteor takes to fall. */
    public static final int FALL_TICKS = 16;
    /** Ticks the finale takes to fall. Longer, because it comes from higher. */
    public static final int FINALE_FALL_TICKS = 34;
    /** How far an ordinary meteor falls, in blocks. */
    public static final double FALL_HEIGHT = 42.0D;
    /** How far the finale falls, in blocks. */
    public static final double FINALE_FALL_HEIGHT = 96.0D;
    /** Angle the meteors enter at, from vertical, in degrees. */
    public static final double ENTRY_ANGLE_DEGREES = 30.0D;

    /** Ordinary meteors in the shower. */
    public static final int RAIN_METEORS = (RAIN_END_TICK - OMEN_END_TICK) / SPAWN_INTERVAL_TICKS;
    /** One. Three of them split the attention that should be on one arrival. */
    public static final int FINALE_METEORS = 1;
    /** Meteors in total. */
    public static final int METEOR_COUNT = RAIN_METEORS + FINALE_METEORS;

    /** How far an ordinary meteor's impact reaches, in blocks. */
    public static final double RAIN_BLAST_RADIUS = 2.5D;
    /** How far the finale's impact reaches, in blocks. */
    public static final double FINALE_BLAST_RADIUS = 9.0D;

    /** How often an ordinary meteor shows a shock ring. */
    public static final float SHOCK_RING_CHANCE = 0.3F;

    private StarfallShape() {
    }

    /** Whether this is the finale — the last and largest. */
    public static boolean isFinaleMeteor(int meteor) {
        return meteor >= RAIN_METEORS;
    }

    /** When a meteor appears, in ticks. */
    public static int spawnTick(int meteor) {
        return isFinaleMeteor(meteor)
                ? FINALE_TICK
                : OMEN_END_TICK + meteor * SPAWN_INTERVAL_TICKS;
    }

    /** Ticks a meteor takes to fall. */
    public static int fallTicks(int meteor) {
        return isFinaleMeteor(meteor) ? FINALE_FALL_TICKS : FALL_TICKS;
    }

    /** How far a meteor falls, in blocks. */
    public static double fallHeight(StarfallParams params, int meteor) {
        return (isFinaleMeteor(meteor) ? FINALE_FALL_HEIGHT : FALL_HEIGHT) * params.scale();
    }

    /** When a meteor lands, in ticks. */
    public static int impactTick(int meteor) {
        return spawnTick(meteor) + fallTicks(meteor);
    }

    /** How far a meteor's impact reaches, in blocks. */
    public static double blastRadius(StarfallParams params, int meteor) {
        return (isFinaleMeteor(meteor) ? FINALE_BLAST_RADIUS : RAIN_BLAST_RADIUS) * params.scale();
    }

    /** Which way a meteor is travelling, as a unit vector. */
    public static Vec3 meteorHeading(StarfallParams params, int meteor) {
        double azimuth = FxHash.unit(params.seed(), meteor, 0xC2B2AE3DL) * Mth.TWO_PI;
        double tilt = Math.toRadians(ENTRY_ANGLE_DEGREES);
        double horizontal = Math.sin(tilt);
        return new Vec3(Math.cos(azimuth) * horizontal, -Math.cos(tilt),
                Math.sin(azimuth) * horizontal);
    }

    /**
     * Where a meteor enters, given where it will land.
     *
     * <p>Scaled so the vertical drop is still the full fall height; the slant adds horizontal travel
     * on top rather than trading height away for it.</p>
     */
    public static Vec3 meteorEntry(StarfallParams params, Vec3 landing, int meteor) {
        double along = fallHeight(params, meteor) / Math.cos(Math.toRadians(ENTRY_ANGLE_DEGREES));
        return landing.subtract(meteorHeading(params, meteor).scale(along));
    }

    /**
     * Where a meteor is, given where it will land.
     *
     * <p>Quadratic rather than linear, so it is slow and readable high up and fast at the end.</p>
     */
    public static Vec3 meteorPosition(StarfallParams params, Vec3 landing, int meteor,
                                      float ageTicks) {
        float fall = Mth.clamp((ageTicks - spawnTick(meteor)) / (float) fallTicks(meteor),
                0.0F, 1.0F);
        return meteorEntry(params, landing, meteor).lerp(landing, fall * fall);
    }

    /** How bright a meteor is. Above 1 after impact, deliberately, for the flash. */
    public static float meteorBrightness(int meteor, float ageTicks) {
        int spawn = spawnTick(meteor);
        if (ageTicks < spawn) {
            return 0.0F;
        }
        int impact = impactTick(meteor);
        if (ageTicks < impact) {
            // Brighten as it approaches, so the threat is legible.
            return 0.45F + ((ageTicks - spawn) / (float) fallTicks(meteor)) * 0.55F;
        }
        float since = ageTicks - impact;
        // The finale's flash is bigger and lasts longer; it is the last thing the effect does and
        // should not blink out.
        return isFinaleMeteor(meteor)
                ? Math.max(0.0F, 3.4F - since * 0.14F)
                : Math.max(0.0F, 1.8F - since * 0.22F);
    }

    /**
     * Whether a meteor sheds a visible shock ring.
     *
     * <p>Not every one of them: forty identical rings turned a shower into a pattern.</p>
     */
    public static boolean hasShockRing(StarfallParams params, int meteor) {
        return isFinaleMeteor(meteor)
                || FxHash.unit(params.seed(), meteor, 0x27D4EB2FL) < SHOCK_RING_CHANCE;
    }

    /** Where a meteor lands, for a caller with no terrain to consult. */
    public static Vec3 flatLanding(StarfallParams params, Vec3 centre, int meteor) {
        if (isFinaleMeteor(meteor)) {
            return new Vec3(centre.x, params.groundY(), centre.z);
        }
        int seed = params.seed();
        float unitAngle = FxHash.unit(seed, meteor, 0x9E3779B9L);
        float unitRadius = FxHash.unit(seed, meteor, 0x85EBCA6BL);
        double radius = Math.sqrt(unitRadius) * params.spreadRadius();
        double angle = unitAngle * Mth.TWO_PI;
        return new Vec3(centre.x + Math.cos(angle) * radius, params.groundY(),
                centre.z + Math.sin(angle) * radius);
    }
}

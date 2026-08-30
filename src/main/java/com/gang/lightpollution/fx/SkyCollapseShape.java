package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SkyCollapseParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape and timeline of a sky collapse, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter. The one thing it cannot answer is
 * where a slab lands, because the spell drops them onto the terrain surface and that needs the
 * world — see {@link SkyCollapseSource#shardLanding}.</p>
 */
public final class SkyCollapseShape {
    /** Ticks by which the firmament has finished fracturing. */
    public static final int FRACTURE_END_TICK = 40;
    /** Ticks at which the first slab is shed. */
    public static final int SHED_START_TICK = 44;
    /** Ticks between one slab and the next. */
    public static final int SHED_INTERVAL_TICKS = 16;
    /** Ticks a slab takes to fall. */
    public static final int SHARD_FALL_TICKS = 42;

    /** Ordinary slabs, before the keystone. */
    public static final int PLAIN_SHARDS = 6;
    /** Slabs in total, keystone included. */
    public static final int SHARD_COUNT = PLAIN_SHARDS + 1;

    /** How far a slab falls, in blocks. */
    public static final double SHARD_FALL_HEIGHT = 120.0D;
    /** Half-span of an ordinary slab, in blocks. */
    public static final float SHARD_HALF_SPAN = 7.0F;
    /** Half-span of the keystone, in blocks. */
    public static final float KEYSTONE_HALF_SPAN = 18.0F;

    /** How far an ordinary slab's impact reaches, in blocks. */
    public static final double SHARD_BLAST_RADIUS = 6.0D;
    /** How far the keystone's impact reaches, in blocks. */
    public static final double KEYSTONE_BLAST_RADIUS = 14.0D;

    /** Fewest corners a slab's outline has. */
    public static final int MIN_SHARD_CORNERS = 5;
    /** Most corners a slab's outline has. */
    public static final int MAX_SHARD_CORNERS = 9;

    private SkyCollapseShape() {
    }

    /** When a slab is shed, in ticks. */
    public static int shedTick(int shard) {
        return SHED_START_TICK + shard * SHED_INTERVAL_TICKS;
    }

    /** When a slab lands, in ticks. */
    public static int impactTick(int shard) {
        return shedTick(shard) + SHARD_FALL_TICKS;
    }

    /** Whether this is the keystone — the last and largest. */
    public static boolean isKeystone(int shard) {
        return shard >= PLAIN_SHARDS;
    }

    /** How far a slab's impact reaches, in blocks. */
    public static double blastRadius(SkyCollapseParams params, int shard) {
        return (isKeystone(shard) ? KEYSTONE_BLAST_RADIUS : SHARD_BLAST_RADIUS) * params.scale();
    }

    /** How far the fracture has spread, 0 to 1. */
    public static float fractureProgress(float ageTicks) {
        return Mth.clamp(ageTicks / FRACTURE_END_TICK, 0.0F, 1.0F);
    }

    /** How brightly the fracture glows, 0 to 1. Dims once slabs start coming down. */
    public static float fractureGlow(float ageTicks) {
        int seal = impactTick(SHARD_COUNT - 1);
        if (ageTicks <= FRACTURE_END_TICK) {
            return fractureProgress(ageTicks);
        }
        if (ageTicks >= seal) {
            return 0.0F;
        }
        return 1.0F - Mth.clamp((ageTicks - FRACTURE_END_TICK)
                / (float) (seal - FRACTURE_END_TICK), 0.0F, 1.0F) * 0.55F;
    }

    /** Which way the rift runs, in radians. */
    public static float riftBearing(SkyCollapseParams params) {
        return FxHash.unit(params.seed(), 0, 0x1B873593L) * Mth.TWO_PI;
    }

    /** How many corners a slab's outline has. */
    public static int shardCorners(SkyCollapseParams params, int shard) {
        int span = MAX_SHARD_CORNERS - MIN_SHARD_CORNERS + 1;
        return MIN_SHARD_CORNERS
                + (int) (FxHash.unit(params.seed(), shard, 0x7FEB352DL) * span) % span;
    }

    /** How far out one corner of a slab's outline sits, as a fraction of its span. */
    public static float shardCornerScale(SkyCollapseParams params, int shard, int corner) {
        return 0.42F + FxHash.unit(params.seed(), shard * 31 + corner, 0xCC9E2D51L) * 0.58F;
    }

    /** How far one corner is nudged around the outline, in radians. */
    public static float shardCornerSkew(SkyCollapseParams params, int shard, int corner) {
        float slice = Mth.TWO_PI / shardCorners(params, shard);
        return (FxHash.unit(params.seed(), shard * 61 + corner, 0x85EBCA77L) - 0.5F) * slice * 0.7F;
    }

    /** Half-span of a slab, in blocks. */
    public static float shardHalfSpan(SkyCollapseParams params, int shard) {
        return (isKeystone(shard) ? KEYSTONE_HALF_SPAN : SHARD_HALF_SPAN) * params.scale();
    }

    /**
     * Height of a slab above its landing point, in blocks.
     *
     * <p>Eased rather than linear, but gently: a slab this size reads wrong if it accelerates like a
     * pebble.</p>
     */
    public static double shardHeightAbove(SkyCollapseParams params, int shard, float ageTicks) {
        float fall = fallFraction(shard, ageTicks);
        float eased = fall * fall * (1.7F - 0.7F * fall);
        return SHARD_FALL_HEIGHT * params.scale() * (1.0F - eased);
    }

    /** Where a slab's centre is, given where it will land. */
    public static Vec3 shardPosition(SkyCollapseParams params, Vec3 landing, int shard,
                                     float ageTicks) {
        return new Vec3(landing.x, landing.y + shardHeightAbove(params, shard, ageTicks),
                landing.z);
    }

    /** How far a slab has tipped over, in radians. */
    public static float shardTilt(SkyCollapseParams params, int shard, float ageTicks) {
        float lean = 0.35F + FxHash.unit(params.seed(), shard, 0xB5297A4DL) * 0.5F;
        return fallFraction(shard, ageTicks) * lean * Mth.PI;
    }

    /** How far a slab has turned about its own axis, in radians. */
    public static float shardSpin(SkyCollapseParams params, int shard, float ageTicks) {
        int seed = params.seed();
        float turns = 0.15F + FxHash.unit(seed, shard, 0x68E31DA4L) * 0.35F;
        return FxHash.unit(seed, shard, 0x2545F491L) * Mth.TWO_PI
                + fallFraction(shard, ageTicks) * turns * Mth.TWO_PI;
    }

    /** How bright a slab is. Above 1 after impact, deliberately, for the flash. */
    public static float shardBrightness(int shard, float ageTicks) {
        int shed = shedTick(shard);
        if (ageTicks < shed) {
            return 0.0F;
        }
        int impact = impactTick(shard);
        if (ageTicks < impact) {
            return 0.5F + ((ageTicks - shed) / (float) SHARD_FALL_TICKS) * 0.5F;
        }
        float since = ageTicks - impact;
        return isKeystone(shard)
                ? Math.max(0.0F, 3.2F - since * 0.15F)
                : Math.max(0.0F, 1.7F - since * 0.2F);
    }

    /**
     * Where a slab lands, relative to the centre, for a caller with no terrain to consult.
     *
     * <p>The keystone comes down on the centre itself — it is what the whole thing builds to. The
     * sqrt spreads the others evenly over the disc instead of clustering them in the middle.</p>
     */
    public static Vec3 flatLanding(SkyCollapseParams params, Vec3 centre, int shard) {
        if (isKeystone(shard)) {
            return new Vec3(centre.x, params.groundY(), centre.z);
        }
        int seed = params.seed();
        float unitAngle = FxHash.unit(seed, shard, 0x9E3779B9L);
        float unitRadius = FxHash.unit(seed, shard, 0x85EBCA6BL);
        double radius = Math.sqrt(unitRadius) * params.spreadRadius();
        double angle = unitAngle * Mth.TWO_PI;
        return new Vec3(centre.x + Math.cos(angle) * radius, params.groundY(),
                centre.z + Math.sin(angle) * radius);
    }

    private static float fallFraction(int shard, float ageTicks) {
        return Mth.clamp((ageTicks - shedTick(shard)) / (float) SHARD_FALL_TICKS, 0.0F, 1.0F);
    }
}

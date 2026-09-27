package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.WorldTreeParams;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape and timeline of a world tree's growth, as pure functions.
 *
 * <p>Nothing here touches an entity, a level or a tick counter. The one thing it cannot answer is what
 * height a root lies at, because the spell drops them onto the terrain — see
 * {@link WorldTreeSource#rootPoint}.</p>
 *
 * <p>The skeleton is built rather than sampled: {@link #build} walks the branch recursion once and
 * returns every limb, so a caller holds the result instead of rebuilding it per frame.</p>
 */
public final class WorldTreeShape {
    /** Ticks by which the trunk has risen. */
    public static final int TRUNK_END_TICK = 50;
    /** Ticks at which roots start spreading. */
    public static final int ROOT_START_TICK = 42;
    /** Ticks by which the roots have spread. */
    public static final int ROOT_END_TICK = 95;
    /** Ticks by which the branches have unfolded. */
    public static final int BRANCH_END_TICK = 120;
    /** Ticks by which the crown has opened. */
    public static final int CROWN_END_TICK = 158;
    /** Ticks at which the wood hardens. */
    public static final int HARDEN_TICK = 168;
    /** Ticks at which the tail fade begins. */
    public static final int FADE_START_TICK = 250;

    /** Roots gripping the ground. */
    public static final int ROOT_COUNT = 7;
    /** Segments along a root. */
    public static final int ROOT_SEGMENTS = 10;
    /** How far a root reaches, in blocks. */
    public static final float ROOT_REACH = 22.0F;
    /** How tall the trunk is, in blocks. */
    public static final float TRUNK_HEIGHT = 34.0F;
    /** Radius of the clear trunk, in blocks. */
    public static final float TRUNK_RADIUS = 2.2F;
    /** How much wider the root flare is than the clear trunk. */
    public static final float FLARE_SCALE = 1.72F;
    /** How far up the flare reaches, in blocks. */
    public static final float FLARE_HEIGHT = 5.0F;
    /** Buttress lobes around the base. */
    public static final int BUTTRESS_LOBES = 7;
    /** Sides the trunk is drawn with. */
    public static final int TRUNK_SIDES = 16;

    /** Branch orders. */
    public static final int BRANCH_LEVELS = 6;
    /** Primary branches off the trunk. */
    public static final int BRANCH_COUNT = 7;
    /** Children per branch. */
    public static final int BRANCH_CHILDREN = 3;
    /** How far a primary branch reaches, in blocks. */
    public static final float BRANCH_REACH = 13.0F;
    /** How much shorter each order is than its parent. */
    public static final float LENGTH_RATIO = 0.65F;
    /**
     * The exponent in da Vinci's rule.
     *
     * <p>The children of a branch share its cross-sectional <em>area</em>, which is what makes the
     * taper look structural rather than chosen.</p>
     */
    public static final float DA_VINCI_EXPONENT = 2.0F;
    /** 137.5 degrees, the phyllotactic angle. */
    public static final float GOLDEN_ANGLE = 2.39996323F;
    /** Leaves per twig. */
    public static final int LEAVES_PER_TWIG = 6;

    private WorldTreeShape() {
    }

    /**
     * One branch.
     *
     * @param growStart branch progress at which this limb starts extending
     * @param growEnd   branch progress at which it is fully out
     */
    public record Limb(Vec3 from, Vec3 to, float fromRadius, float toRadius,
                       int level, float growStart, float growEnd) {
        /** How far this limb has extended at the given branch progress, 0 to 1. */
        public float grown(float progress) {
            if (progress <= growStart) {
                return 0.0F;
            }
            if (progress >= growEnd) {
                return 1.0F;
            }
            float raw = (progress - growStart) / (growEnd - growStart);
            return raw * raw * (3.0F - 2.0F * raw);
        }

        /** Direction from base to tip, unit length. */
        public Vec3 direction() {
            Vec3 delta = to.subtract(from);
            double length = delta.length();
            return length < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : delta.scale(1.0D / length);
        }
    }

    /**
     * One leaf, defined against its twig so it can be positioned once the twig's growth is known.
     *
     * @param twig  index into the twig array
     * @param along where along the twig it hangs, 0 to 1
     * @param spin  phyllotactic angle about the twig, in radians
     */
    public record Leaf(int twig, float along, float spin, float hue, float size) {
    }

    /** A built tree: every limb, the subset that carries leaves, and the leaves themselves. */
    public record Skeleton(Limb[] limbs, Limb[] twigs, Leaf[] leaves) {
    }

    /** How far the trunk has risen, 0 to 1. */
    public static float trunkProgress(float ageTicks) {
        return smoothstep(ageTicks / TRUNK_END_TICK);
    }

    /** How far the roots have spread, 0 to 1. */
    public static float rootProgress(float ageTicks) {
        if (ageTicks <= ROOT_START_TICK) {
            return 0.0F;
        }
        return smoothstep((ageTicks - ROOT_START_TICK) / (ROOT_END_TICK - ROOT_START_TICK));
    }

    /** How far the branches have unfolded, 0 to 1. */
    public static float branchProgress(float ageTicks) {
        float start = TRUNK_END_TICK * 0.8F;
        if (ageTicks <= start) {
            return 0.0F;
        }
        return smoothstep((ageTicks - start) / (BRANCH_END_TICK - start));
    }

    /**
     * How far the crown has opened, 0 to 1.
     *
     * <p>Starts once the outer branch orders are on their way, so leaves open as the twigs that carry
     * them arrive rather than all at once afterwards.</p>
     */
    public static float crownProgress(float ageTicks) {
        float start = BRANCH_END_TICK * 0.62F;
        if (ageTicks <= start) {
            return 0.0F;
        }
        return smoothstep((ageTicks - start) / (CROWN_END_TICK - start));
    }

    /** How far the wood has hardened, 0 to 1. */
    public static float hardened(float ageTicks) {
        if (ageTicks <= HARDEN_TICK) {
            return 0.0F;
        }
        return Mth.clamp((ageTicks - HARDEN_TICK) / 40.0F, 0.0F, 1.0F);
    }

    /** Overall visibility, 1 until the tail fade. */
    public static float fade(float ageTicks, int lifetimeTicks) {
        if (ageTicks <= FADE_START_TICK) {
            return 1.0F;
        }
        // Configurable lifetimes may be shorter than the default tree's hardening
        // phase. Keep the denominator valid so a lifetime of exactly the fade
        // start tick cannot produce NaN in the client renderer.
        int fadeEnd = Math.max(FADE_START_TICK + 1, lifetimeTicks);
        return Math.max(0.0F, 1.0F - (ageTicks - FADE_START_TICK)
                / (float) (fadeEnd - FADE_START_TICK));
    }

    /**
     * Trunk radius at a height above the seed, in blocks.
     *
     * <p>Above the flare the profile is close to a paraboloid, not a straight cone: a linear taper is
     * exactly what makes a trunk read as a fencepost.</p>
     */
    public static float trunkRadius(WorldTreeParams params, float height) {
        float sc = params.scale();
        float clamped = Math.max(0.0F, height / sc);
        float above = Mth.clamp(clamped / TRUNK_HEIGHT, 0.0F, 1.0F);
        float clear = TRUNK_RADIUS * (0.34F + 0.66F * (float) Math.sqrt(1.0D - above));
        if (clamped >= FLARE_HEIGHT) {
            return clear * sc;
        }
        // Root flare, reaching zero at FLARE_HEIGHT so it blends into the clear trunk.
        float into = 1.0F - clamped / FLARE_HEIGHT;
        return clear * (1.0F + (FLARE_SCALE - 1.0F) * into * into) * sc;
    }

    /**
     * How deeply the buttress lobes cut in at a height, as a fraction of the radius.
     *
     * <p>Zero above the flare, so it blends into the clear trunk instead of stepping.</p>
     */
    public static float buttressDepth(WorldTreeParams params, float height) {
        float clamped = height / params.scale();
        if (clamped >= FLARE_HEIGHT || clamped < 0.0F) {
            return 0.0F;
        }
        float into = 1.0F - clamped / FLARE_HEIGHT;
        return 0.30F * into * into;
    }

    /**
     * Which way a root runs, in radians.
     *
     * <p>Aligned with the trunk's buttress lobes, because each root is the continuation of one. Only
     * lightly jittered — enough that the spread does not look surveyed, not enough to break the
     * join.</p>
     */
    public static float rootBearing(WorldTreeParams params, int root) {
        float slice = Mth.TWO_PI / ROOT_COUNT;
        return root * slice + (FxHash.unit(params.seed(), root, 0x9E3779B9L) - 0.5F) * 0.18F;
    }

    /** How far out along the ground a point on a root sits, in blocks. */
    public static double rootDistance(WorldTreeParams params, int root, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        int seed = params.seed();
        float reach = ROOT_REACH * (0.6F + FxHash.unit(seed, root, 0xC2B2AE3DL) * 0.4F);
        // Leaves the flare, not the axis, so the root and the buttress are one shape.
        float start = TRUNK_RADIUS * FLARE_SCALE * 0.8F;
        return (start + (reach - start) * clamped) * params.scale();
    }

    /** Which way a point on a root lies, in radians. Roots wander rather than running straight. */
    public static float rootAngle(WorldTreeParams params, int root, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float wander = (FxHash.unit(params.seed(), root * 13 + 1, 0x85EBCA6BL) - 0.5F) * 0.9F;
        return rootBearing(params, root) + wander * clamped;
    }

    /** Radius of a root at a point along it, in blocks. */
    public static float rootRadius(WorldTreeParams params, int root, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float base = 1.05F + FxHash.unit(params.seed(), root, 0x27D4EB2FL) * 0.25F;
        // Power falloff, so a root keeps its mass for a while and then runs out.
        return base * (1.0F - (float) Math.pow(clamped, 1.5D) * 0.88F) * params.scale();
    }

    /**
     * Where a root point sits, given the surface height there.
     *
     * <p>Half-buried: the top of the root breaks the surface and the rest is in the ground, which is
     * what makes it look like it is gripping rather than painted on. Sunk by a fraction of its own
     * thickness.</p>
     */
    public static Vec3 rootPointAt(WorldTreeParams params, Vec3 seed, int root, float t,
                                   double surfaceY) {
        double out = rootDistance(params, root, t);
        float angle = rootAngle(params, root, t);
        return new Vec3(seed.x + Mth.cos(angle) * out,
                surfaceY + 0.12D - rootRadius(params, root, t) * 0.42D,
                seed.z + Mth.sin(angle) * out);
    }

    /**
     * Hue index of a leaf, 0 to 1.
     *
     * <p>Weighted toward the warm and green-teal end of the palette, with the magenta and violet bands
     * kept to roughly a sixth of the crown. An even spread over all six reads as confetti rather than
     * as a canopy.</p>
     */
    public static float leafHue(WorldTreeParams params, int leaf) {
        float raw = FxHash.unit(params.seed(), leaf * 3 + 5, 0x68E31DA4L);
        // Squashes the middle of the palette, where the magenta and violet sit.
        return raw < 0.84F ? raw / 0.84F * 0.42F : 0.42F + (raw - 0.84F) / 0.16F * 0.58F;
    }

    /**
     * Build the whole tree once.
     *
     * <p>Deterministic from the seed, so every client that builds with the same params gets the same
     * tree without any of it being synced.</p>
     */
    public static Skeleton build(WorldTreeParams params) {
        List<Limb> all = new ArrayList<>();
        List<Limb> tips = new ArrayList<>();
        int seed = params.seed();
        float sc = params.scale();

        // Primaries take the whole trunk's cross-section between them, which is da Vinci's rule
        // applied at the first split.
        float primaryRadius = (float) (TRUNK_RADIUS / Math.pow(BRANCH_COUNT,
                1.0F / DA_VINCI_EXPONENT)) * sc;
        for (int branch = 0; branch < BRANCH_COUNT; branch++) {
            float heightFrac = branch / (float) (BRANCH_COUNT - 1);
            float baseY = TRUNK_HEIGHT * sc * (0.42F + heightFrac * 0.50F);
            float bearing = branch * GOLDEN_ANGLE
                    + (FxHash.unit(seed, branch, 0x1B873593L) - 0.5F) * 0.4F;
            // Lower limbs sit near horizontal and upper ones climb, which is apical control and is
            // most of what gives a crown its rounded outline. 75 degrees off vertical at the bottom,
            // 32 at the top.
            float fromVertical = Mth.lerp(heightFrac, (float) Math.toRadians(75.0D),
                    (float) Math.toRadians(32.0D));
            Vec3 direction = new Vec3(Mth.cos(bearing) * Mth.sin(fromVertical),
                    Mth.cos(fromVertical), Mth.sin(bearing) * Mth.sin(fromVertical));
            // Lower limbs are longer, so the crown is widest below its midpoint.
            float length = BRANCH_REACH * sc * (1.05F - 0.32F * heightFrac)
                    * (0.85F + FxHash.unit(seed, branch, 0x7FEB352DL) * 0.3F);
            // Attach a little way out from the trunk's surface, not at its axis.
            Vec3 from = new Vec3(direction.x, 0.0D, direction.z).normalize()
                    .scale(TRUNK_RADIUS * sc * 0.7D).add(0.0D, baseY, 0.0D);
            growLimb(params, all, tips, from, direction, length, primaryRadius, 0, branch);
        }

        Limb[] limbs = all.toArray(new Limb[0]);
        Limb[] twigs = tips.toArray(new Limb[0]);

        Leaf[] leaves = new Leaf[twigs.length * LEAVES_PER_TWIG];
        for (int twig = 0; twig < twigs.length; twig++) {
            for (int leaf = 0; leaf < LEAVES_PER_TWIG; leaf++) {
                int index = twig * LEAVES_PER_TWIG + leaf;
                // Leaves start a quarter of the way out and run to the tip, spaced on the same
                // 137.5 degree spiral the branches use.
                float along = 0.25F + leaf / (float) LEAVES_PER_TWIG * 0.75F;
                float spin = leaf * GOLDEN_ANGLE
                        + FxHash.unit(seed, index, 0x9E3779B9L) * 0.5F;
                leaves[index] = new Leaf(twig, along, spin, leafHue(params, index),
                        0.34F + FxHash.unit(seed, index, 0xB5297A4DL) * 0.26F);
            }
        }
        return new Skeleton(limbs, twigs, leaves);
    }

    /**
     * Adds one limb and recurses into its children.
     *
     * <p>Each split divides the parent's cross-sectional area between the children and shortens them
     * by {@link #LENGTH_RATIO}, and each child is rotated about the parent by the golden angle so
     * successive ones do not stack. Children also get bent toward world up, more strongly the further
     * out they are — that is the light-seeking bias, and without it the outer crown droops into a
     * mop.</p>
     */
    private static void growLimb(WorldTreeParams params, List<Limb> all, List<Limb> tips,
                                 Vec3 from, Vec3 direction, float length, float radius,
                                 int level, int salt) {
        float childRadius = (float) (radius / Math.pow(BRANCH_CHILDREN,
                1.0F / DA_VINCI_EXPONENT));
        Vec3 to = from.add(direction.scale(length));
        // Levels unfold strictly in sequence, and a level's window ends exactly where the next one's
        // begins. Overlapping them is what left branches floating: a child's base sits at its
        // parent's *full* tip, so if the child starts extending while the parent is only two thirds
        // out, the gap between them is simply empty air.
        float span = 1.0F / BRANCH_LEVELS;
        Limb limb = new Limb(from, to, radius, childRadius, level, level * span,
                (level + 1) * span);
        all.add(limb);
        // The outer two orders carry leaves, not just the last one. Foliage that exists only on the
        // extreme tips is a single-cell-thick skin: real crowns carry it a couple of orders deep,
        // which is what gives the shell thickness and lets you see leaves behind leaves.
        if (level >= BRANCH_LEVELS - 2) {
            tips.add(limb);
        }
        if (level >= BRANCH_LEVELS - 1) {
            return;
        }

        int seed = params.seed();
        Vec3 reference = Math.abs(direction.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = reference.cross(direction).normalize();
        Vec3 other = direction.cross(side).normalize();

        for (int child = 0; child < BRANCH_CHILDREN; child++) {
            int branchSalt = salt * 31 + child + level * 7919;
            // Down-angle off the parent: real trees diverge 30 to 60 degrees.
            float divergence = (float) Math.toRadians(
                    30.0D + FxHash.unit(seed, branchSalt, 0xCC9E2D51L) * 30.0D);
            float spin = child * GOLDEN_ANGLE
                    + (FxHash.unit(seed, branchSalt, 0x85EBCA6BL) - 0.5F) * 0.6F;
            Vec3 lateral = side.scale(Mth.cos(spin)).add(other.scale(Mth.sin(spin)));
            Vec3 childDirection = direction.scale(Mth.cos(divergence))
                    .add(lateral.scale(Mth.sin(divergence)));
            // Light-seeking bias, stronger the further out.
            childDirection = childDirection.add(0.0D, 0.14D * (level + 1), 0.0D).normalize();

            growLimb(params, all, tips, to, childDirection,
                    length * LENGTH_RATIO
                            * (0.85F + FxHash.unit(seed, branchSalt, 0x27D4EB2FL) * 0.3F),
                    childRadius, level + 1, branchSalt);
        }
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}

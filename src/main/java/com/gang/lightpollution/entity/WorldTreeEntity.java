package com.gang.lightpollution.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/**
 * Server-owned anchor for World Tree.
 *
 * <p>The one spell in this set that grows out of the ground rather than arriving
 * at it. Roots race outward across the terrain, spear up into a trunk, and the
 * crown unfolds — a tall vertical silhouette, which nothing else here has.</p>
 *
 * <p>Root paths, branch angles and leaf placement are pure functions of the
 * synchronized seed, so the renderer and the server-side root strikes read the
 * same structure without extra syncing. Root tips follow the real terrain height,
 * so on a slope the roots climb it.</p>
 */
public final class WorldTreeEntity extends Entity {
    public static final int LIFETIME_TICKS = 300;
    /**
     * The trunk spears up first.
     *
     * <p>Trunk before roots, which is the order a tree is actually built in and the
     * order that reads: something erupts, and then it takes hold. Roots racing out
     * of bare ground before anything exists to own them looked like an unrelated
     * effect that the trunk then landed in the middle of.</p>
     */
    public static final int TRUNK_END_TICK = 50;
    /** Roots begin creeping out from the flare, just before the trunk tops out. */
    public static final int ROOT_START_TICK = 42;
    /** Roots finish spreading. */
    public static final int ROOT_END_TICK = 95;
    /** Branches extend, all five orders in sequence. */
    public static final int BRANCH_END_TICK = 120;
    /** The crown fills in and holds. */
    public static final int CROWN_END_TICK = 158;
    /** Everything hardens into crystal and begins shedding motes. */
    public static final int HARDEN_TICK = 168;
    /** The crown starts to fade. */
    public static final int FADE_START_TICK = 250;

    /** Roots radiating from the seed point. One per trunk buttress. */
    public static final int ROOT_COUNT = 7;
    /** Segments a root is sampled into. */
    public static final int ROOT_SEGMENTS = 10;
    /** How far a root reaches, in blocks. */
    public static final float ROOT_REACH = 22.0F;
    /** Height of the trunk, in blocks. */
    public static final float TRUNK_HEIGHT = 34.0F;
    /**
     * Radius of the clear trunk, above the root flare, in blocks.
     *
     * <p>Diameter at breast height is measured at 1.3 m precisely because that is
     * above the butt swell. So this is the trunk proper, and {@link #FLARE_SCALE}
     * widens the bottom of it.</p>
     */
    public static final float TRUNK_RADIUS = 2.2F;
    /** How much wider the base is than the clear trunk. Real trees: 1.3 to 2.0. */
    public static final float FLARE_SCALE = 1.72F;
    /** How far up the flare reaches, in blocks. */
    public static final float FLARE_HEIGHT = 5.0F;
    /** Buttress lobes around the flare. Real trees show 5 to 9. */
    public static final int BUTTRESS_LOBES = 7;
    /**
     * Sides on the trunk. Sixteen, not seven: a seven-sided prism reads as a
     * faceted post, and the buttresses are supposed to come from lobe modulation of
     * a round trunk rather than from the polygon count.
     */
    public static final int TRUNK_SIDES = 16;

    /** Levels of branching above the trunk: primaries, then five more orders. */
    public static final int BRANCH_LEVELS = 6;
    /** Primary limbs leaving the trunk. */
    public static final int BRANCH_COUNT = 7;
    /** Children each branch splits into. */
    public static final int BRANCH_CHILDREN = 3;
    /** How far a primary limb reaches, in blocks. */
    public static final float BRANCH_REACH = 13.0F;
    /** Each level's length as a fraction of its parent's. */
    public static final float LENGTH_RATIO = 0.65F;
    /**
     * Da Vinci's rule exponent: a parent's cross-sectional area equals the sum of
     * its children's, so a child's radius is the parent's over sqrt(children).
     * Measured trees span 1.5 to 2.8; 2 is the classic value.
     */
    public static final float DA_VINCI_EXPONENT = 2.0F;
    /** Golden angle, in radians. Successive children rotate by this about the parent. */
    public static final float GOLDEN_ANGLE = 2.39996323F;
    /**
     * Leaves carried by each leaf-bearing branch.
     *
     * <p>Deliberately few per branch. The total foliage is about the same as before,
     * but it is spread over 2268 twigs across the outer two orders instead of 756 —
     * eighteen leaves crowded onto one twig reads as a bottle brush, six leaves on
     * three times as many twigs reads as a canopy. Real twigs bear 5 to 15, so this
     * is also the honest number; the previous one was compensating for not having
     * enough branches to hang them on.</p>
     */
    public static final int LEAVES_PER_TWIG = 6;

    /** Radius the roots cover, in blocks. */
    public static final double EFFECT_RADIUS = ROOT_REACH + 3.0D;
    /** How close to a root a target must be to be struck, in blocks. */
    private static final double ROOT_STRIKE_RADIUS = 3.2D;
    /** Radius of the trunk's eruption, in blocks. */
    private static final double TRUNK_BURST_RADIUS = 7.0D;
    /** Radius of the crown's final pulse, in blocks. */
    private static final double CROWN_RADIUS = 20.0D;

    /** Max-health fraction per root strike. */
    private static final float ROOT_DAMAGE_FRACTION = 0.040F;
    /** Ticks between root strikes while they spread. */
    private static final int ROOT_INTERVAL_TICKS = 6;
    /** Max-health fraction when the trunk spears up. */
    private static final float TRUNK_DAMAGE_FRACTION = 0.25F;
    /** Max-health fraction of the crown's pulse. */
    private static final float CROWN_DAMAGE_FRACTION = 0.39F;
    /** Ticks of rooting applied while the trunk holds. */
    private static final int ROOTED_TICKS = 60;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            WorldTreeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            WorldTreeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            WorldTreeEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean trunkResolved;
    private boolean crownResolved;

    public WorldTreeEntity(EntityType<? extends WorldTreeEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, int seed) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.setPos(center.x, center.y, center.z);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
    }

    public int getTimelineAgeTicks() {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        if (startGameTick < 0L) {
            return Math.min(LIFETIME_TICKS, Math.max(0, this.tickCount));
        }
        long age = this.level().getGameTime() - startGameTick;
        return (int) Math.min(LIFETIME_TICKS, Math.max(0L, age));
    }

    public float getVisualAgeTicks(float partialTick) {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        float age = startGameTick < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - startGameTick) + partialTick;
        return Math.min(LIFETIME_TICKS, Math.max(0.0F, age));
    }

    /** The seed point on the ground. */
    public Vec3 seedPoint(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** How far the roots have raced out, 0 to 1. */
    public float rootProgress(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= ROOT_START_TICK) {
            return 0.0F;
        }
        return smoothstep((age - ROOT_START_TICK)
                / (ROOT_END_TICK - ROOT_START_TICK));
    }

    /** How far the trunk has risen, 0 to 1. */
    public float trunkProgress(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / TRUNK_END_TICK);
    }

    /** How far the branches have extended, 0 to 1. */
    public float branchProgress(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        float start = TRUNK_END_TICK * 0.8F;
        if (age <= start) {
            return 0.0F;
        }
        return smoothstep((age - start) / (BRANCH_END_TICK - start));
    }

    /** How far the crown has filled in, 0 to 1. */
    public float crownProgress(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        // Starts once the outer branch orders are on their way, so leaves open as
        // the twigs that carry them arrive rather than all at once afterwards.
        float start = BRANCH_END_TICK * 0.62F;
        if (age <= start) {
            return 0.0F;
        }
        return smoothstep((age - start) / (CROWN_END_TICK - start));
    }

    /**
     * How far the tree has hardened into crystal, 0 to 1. Drives the colour shift
     * from living gold to pale crystal, which is what makes the final silhouette
     * read as a monument rather than as a plant.
     */
    public float hardened(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= HARDEN_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - HARDEN_TICK) / 40.0F, 0.0F, 1.0F);
    }

    /** Overall fade, 1 while it stands and falling to 0 at the end. */
    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= FADE_START_TICK) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (age - FADE_START_TICK)
                / (float) (LIFETIME_TICKS - FADE_START_TICK));
    }

    /** Bearing a root runs along, in radians. */
    public float rootBearing(int root) {
        // Aligned with the trunk's buttress lobes, because each root is the
        // continuation of one. Only lightly jittered — enough that the spread does
        // not look surveyed, not enough to break the join.
        float slice = Mth.TWO_PI / ROOT_COUNT;
        return root * slice + (hashUnit(root, 0x9E3779B9L) - 0.5F) * 0.18F;
    }

    /**
     * A point along a root. {@code t} is 0 at the seed and 1 at the tip.
     *
     * <p>Sampled onto the real terrain height rather than laid on a plane, so a
     * root crossing a slope climbs it. Both sides read the same heightmap for
     * loaded chunks, so the visual and the strike agree.</p>
     */
    public Vec3 rootPoint(int root, float t, float partialTick) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        Vec3 seed = seedPoint(partialTick);
        float bearing = rootBearing(root);
        // Roots wander rather than running straight out.
        float wander = (hashUnit(root * 13 + 1, 0x85EBCA6BL) - 0.5F) * 0.9F;
        float angle = bearing + wander * clamped;
        float reach = ROOT_REACH * (0.6F + hashUnit(root, 0xC2B2AE3DL) * 0.4F);
        // Leaves the flare, not the axis, so the root and the buttress are one shape.
        float start = TRUNK_RADIUS * FLARE_SCALE * 0.8F;
        float out = start + (reach - start) * clamped;

        double x = seed.x + Mth.cos(angle) * out;
        double z = seed.z + Mth.sin(angle) * out;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        // Half-buried: the top of the root breaks the surface and the rest is in the
        // ground, which is what makes it look like it is gripping rather than
        // painted on. Sunk by a fraction of its own thickness.
        return new Vec3(x, surface + 0.12D - rootRadius(root, clamped) * 0.42D, z);
    }

    /**
     * Radius of a root at {@code t}, tapering to its tip.
     *
     * <p>Starts at the size of the buttress it continues out of, so the join reads
     * as one structure. The old roots began at the seed point at a fraction of the
     * trunk's width, which is why the trunk looked planted next to them rather than
     * growing out of them.</p>
     */
    public float rootRadius(int root, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float base = 1.05F + hashUnit(root, 0x27D4EB2FL) * 0.25F;
        // Power falloff, so a root keeps its mass for a while and then runs out.
        return base * (1.0F - (float) Math.pow(clamped, 1.5D) * 0.88F);
    }

    /**
     * One branch of the skeleton, in coordinates relative to the seed point and at
     * full growth. Growth is applied when it is drawn, so this can be built once.
     *
     * @param level     0 for a primary limb, up to {@code BRANCH_LEVELS - 1} for a twig
     * @param growStart branch progress at which this limb starts extending
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
     * One leaf, defined against its twig so it can be positioned once the twig's
     * growth is known.
     *
     * @param twig  index into {@link #twigs()}
     * @param along where along the twig it hangs, 0 to 1
     * @param spin  phyllotactic angle about the twig, radians
     */
    public record Leaf(int twig, float along, float spin, float hue, float size) {
    }

    private Limb[] skeleton;
    private Limb[] twigs;
    private Leaf[] leaves;

    /** Every branch, all levels, built once from the seed. */
    public Limb[] skeleton() {
        buildIfNeeded();
        return skeleton;
    }

    /** Just the outermost level. These are what carry leaves. */
    public Limb[] twigs() {
        buildIfNeeded();
        return twigs;
    }

    public Leaf[] leaves() {
        buildIfNeeded();
        return leaves;
    }

    private void buildIfNeeded() {
        if (skeleton != null) {
            return;
        }
        java.util.List<Limb> all = new java.util.ArrayList<>();
        java.util.List<Limb> tips = new java.util.ArrayList<>();

        // Primaries take the whole trunk's cross-section between them, which is da
        // Vinci's rule applied at the first split.
        float primaryRadius = (float) (TRUNK_RADIUS
                / Math.pow(BRANCH_COUNT, 1.0F / DA_VINCI_EXPONENT));
        for (int branch = 0; branch < BRANCH_COUNT; branch++) {
            float heightFrac = branch / (float) (BRANCH_COUNT - 1);
            float baseY = TRUNK_HEIGHT * (0.42F + heightFrac * 0.50F);
            float bearing = branch * GOLDEN_ANGLE
                    + (hashUnit(branch, 0x1B873593L) - 0.5F) * 0.4F;
            // Lower limbs sit near horizontal and upper ones climb, which is apical
            // control and is most of what gives a crown its rounded outline. 75
            // degrees off vertical at the bottom, 32 at the top.
            float fromVertical = Mth.lerp(heightFrac,
                    (float) Math.toRadians(75.0D), (float) Math.toRadians(32.0D));
            Vec3 direction = new Vec3(
                    Mth.cos(bearing) * Mth.sin(fromVertical),
                    Mth.cos(fromVertical),
                    Mth.sin(bearing) * Mth.sin(fromVertical));
            // Lower limbs are longer, so the crown is widest below its midpoint.
            float length = BRANCH_REACH * (1.05F - 0.32F * heightFrac)
                    * (0.85F + hashUnit(branch, 0x7FEB352DL) * 0.3F);
            // Attach a little way out from the trunk's surface, not at its axis.
            Vec3 from = new Vec3(direction.x, 0.0D, direction.z)
                    .normalize().scale(TRUNK_RADIUS * 0.7D)
                    .add(0.0D, baseY, 0.0D);
            growLimb(all, tips, from, direction, length, primaryRadius, 0, branch);
        }

        skeleton = all.toArray(new Limb[0]);
        twigs = tips.toArray(new Limb[0]);

        Leaf[] built = new Leaf[twigs.length * LEAVES_PER_TWIG];
        for (int twig = 0; twig < twigs.length; twig++) {
            for (int leaf = 0; leaf < LEAVES_PER_TWIG; leaf++) {
                int index = twig * LEAVES_PER_TWIG + leaf;
                // Leaves start a quarter of the way out and run to the tip, spaced
                // on the same 137.5 degree spiral the branches use.
                float along = 0.25F + leaf / (float) LEAVES_PER_TWIG * 0.75F;
                float spin = leaf * GOLDEN_ANGLE
                        + hashUnit(index, 0x9E3779B9L) * 0.5F;
                built[index] = new Leaf(twig, along, spin,
                        leafHueFor(index),
                        0.34F + hashUnit(index, 0xB5297A4DL) * 0.26F);
            }
        }
        leaves = built;
    }

    /**
     * Adds one limb and recurses into its children.
     *
     * <p>Each split divides the parent's cross-sectional area between the children
     * and shortens them by {@link #LENGTH_RATIO}, and each child is rotated about
     * the parent by the golden angle so successive ones do not stack. Children also
     * get bent toward world up, more strongly the further out they are — that is the
     * light-seeking bias, and without it the outer crown droops into a mop.</p>
     */
    private void growLimb(java.util.List<Limb> all, java.util.List<Limb> tips,
                          Vec3 from, Vec3 direction, float length, float radius,
                          int level, int salt) {
        float childRadius = (float) (radius
                / Math.pow(BRANCH_CHILDREN, 1.0F / DA_VINCI_EXPONENT));
        Vec3 to = from.add(direction.scale(length));
        // Levels unfold strictly in sequence, and a level's window ends exactly where
        // the next one's begins. Overlapping them is what left branches floating: a
        // child's base sits at its parent's *full* tip, so if the child starts
        // extending while the parent is only two thirds out, the gap between them is
        // simply empty air.
        float span = 1.0F / BRANCH_LEVELS;
        Limb limb = new Limb(from, to, radius, childRadius, level,
                level * span, (level + 1) * span);
        all.add(limb);
        // The outer two orders carry leaves, not just the last one. Foliage that
        // exists only on the extreme tips is a single-cell-thick skin: real crowns
        // carry it a couple of orders deep, which is what gives the shell thickness
        // and lets you see leaves behind leaves.
        if (level >= BRANCH_LEVELS - 2) {
            tips.add(limb);
        }
        if (level >= BRANCH_LEVELS - 1) {
            return;
        }

        Vec3 reference = Math.abs(direction.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = reference.cross(direction).normalize();
        Vec3 other = direction.cross(side).normalize();

        for (int child = 0; child < BRANCH_CHILDREN; child++) {
            int seed = salt * 31 + child + level * 7919;
            // Down-angle off the parent: real trees diverge 30 to 60 degrees.
            float divergence = (float) Math.toRadians(
                    30.0D + hashUnit(seed, 0xCC9E2D51L) * 30.0D);
            float spin = child * GOLDEN_ANGLE
                    + (hashUnit(seed, 0x85EBCA6BL) - 0.5F) * 0.6F;
            Vec3 lateral = side.scale(Mth.cos(spin)).add(other.scale(Mth.sin(spin)));
            Vec3 childDirection = direction.scale(Mth.cos(divergence))
                    .add(lateral.scale(Mth.sin(divergence)));
            // Light-seeking bias, stronger the further out.
            double lift = 0.14D * (level + 1);
            childDirection = childDirection.add(0.0D, lift, 0.0D).normalize();

            growLimb(all, tips, to, childDirection,
                    length * LENGTH_RATIO * (0.85F + hashUnit(seed, 0x27D4EB2FL) * 0.3F),
                    childRadius, level + 1, seed);
        }
    }

    /**
     * Hue index of a leaf, 0 to 1.
     *
     * <p>Weighted toward the warm and green-teal end of the palette, with the
     * magenta and violet bands kept to roughly a sixth of the crown. An even spread
     * over all six reads as confetti rather than as a canopy.</p>
     */
    private float leafHueFor(int leaf) {
        float raw = hashUnit(leaf * 3 + 5, 0x68E31DA4L);
        // Squashes the middle of the palette, where the magenta and violet sit.
        return raw < 0.84F ? raw / 0.84F * 0.42F : 0.42F + (raw - 0.84F) / 0.16F * 0.58F;
    }

    /** Trunk radius at {@code height} blocks above the seed. */
    public static float trunkRadius(float height) {
        float clamped = Math.max(0.0F, height);
        // Above the flare the profile is close to a paraboloid, not a straight cone:
        // a linear taper is exactly what makes a trunk read as a fencepost.
        float above = Mth.clamp(clamped / TRUNK_HEIGHT, 0.0F, 1.0F);
        float clear = TRUNK_RADIUS * (0.34F + 0.66F * (float) Math.sqrt(1.0D - above));
        if (clamped >= FLARE_HEIGHT) {
            return clear;
        }
        // Root flare. Reaching zero at FLARE_HEIGHT so it blends into the clear
        // trunk instead of stepping.
        float into = 1.0F - clamped / FLARE_HEIGHT;
        return clear * (1.0F + (FLARE_SCALE - 1.0F) * into * into);
    }

    /**
     * How far out the buttress lobes stand at {@code height}, as a fraction of the
     * trunk radius. Zero above the flare.
     */
    public static float buttressDepth(float height) {
        if (height >= FLARE_HEIGHT || height < 0.0F) {
            return 0.0F;
        }
        float into = 1.0F - height / FLARE_HEIGHT;
        return 0.30F * into * into;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private float hashUnit(int index, long salt) {
        long hash = (getSeed() & 0xFFFFFFFFL) * 0x2545F4914F6CDD1DL
                ^ (index + 1L) * salt;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return (float) ((hash >>> 1) / (double) Long.MAX_VALUE);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (!this.trunkResolved && timelineTick >= TRUNK_END_TICK) {
                this.trunkResolved = true;
                resolveTrunk(serverLevel);
            }
            if (timelineTick >= ROOT_START_TICK && timelineTick <= ROOT_END_TICK
                    && timelineTick % ROOT_INTERVAL_TICKS == 0) {
                resolveRoots(serverLevel);
            }
            if (!this.crownResolved && timelineTick >= CROWN_END_TICK) {
                this.crownResolved = true;
                resolveCrown(serverLevel);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /**
     * Anything a root has reached. Only the leading portion of each root strikes,
     * so standing where a root has already passed is safe and the spread itself is
     * the threat.
     */
    private void resolveRoots(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        float progress = rootProgress(1.0F);
        if (progress <= 0.02F) {
            return;
        }
        DamageSource source = null;
        for (int root = 0; root < ROOT_COUNT; root++) {
            // The advancing tip, plus a short stretch behind it.
            for (int sample = 0; sample < 2; sample++) {
                float t = Math.max(0.0F, progress - sample * 0.12F);
                Vec3 point = rootPoint(root, t, 1.0F);
                List<LivingEntity> touched = level.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(point.x - ROOT_STRIKE_RADIUS, point.y - 2.0D,
                                point.z - ROOT_STRIKE_RADIUS,
                                point.x + ROOT_STRIKE_RADIUS, point.y + 4.0D,
                                point.z + ROOT_STRIKE_RADIUS),
                        target -> canAffect(caster, target)
                                && horizontalWithin(target, point, ROOT_STRIKE_RADIUS));
                if (touched.isEmpty()) {
                    continue;
                }
                if (source == null) {
                    source = WorldTreeDamage.source(level, this, caster);
                }
                for (LivingEntity target : touched) {
                    applyTrueDamage(target, source, ROOT_DAMAGE_FRACTION);
                }
            }
        }
    }

    /** The trunk spearing up: everything at its foot is thrown and rooted. */
    private void resolveTrunk(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 seed = seedPoint(1.0F);
        List<LivingEntity> targets = gather(level, caster, seed, TRUNK_BURST_RADIUS);
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = WorldTreeDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            applyTrueDamage(target, source, TRUNK_DAMAGE_FRACTION);
            // Held in place rather than knocked away: the tree is a wall now, and
            // pinning is what makes the crown's pulse land.
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, ROOTED_TICKS, 3, false, true));
        }
    }

    /** The crown's pulse, once it has fully opened. */
    private void resolveCrown(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 seed = seedPoint(1.0F);
        List<LivingEntity> targets = gather(level, caster, seed, CROWN_RADIUS);
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = WorldTreeDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            applyTrueDamage(target, source, CROWN_DAMAGE_FRACTION);
        }
    }

    /**
     * Horizontal-only distance. Roots run along the ground, so a target's height
     * above them should not exempt it while it is standing on one.
     */
    private static boolean horizontalWithin(LivingEntity target, Vec3 point, double radius) {
        Vec3 centre = target.getBoundingBox().getCenter();
        double dx = centre.x - point.x;
        double dz = centre.z - point.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    private List<LivingEntity> gather(ServerLevel level, LivingEntity caster,
                                      Vec3 centre, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                        centre.x + radius, centre.y + radius, centre.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                <= radius * radius);
    }

    private LivingEntity resolveCaster(ServerLevel level) {
        Entity byId = level.getEntity(this.getCasterId());
        if (byId instanceof LivingEntity living
                && (this.casterUuid == null || living.getUUID().equals(this.casterUuid))) {
            return living;
        }
        if (this.casterUuid == null) {
            return null;
        }
        Entity byUuid = level.getEntity(this.casterUuid);
        if (byUuid instanceof LivingEntity living) {
            this.entityData.set(DATA_CASTER_ID, living.getId());
            return living;
        }
        return null;
    }

    private boolean canAffect(LivingEntity caster, LivingEntity target) {
        if ((this.casterUuid != null && target.getUUID().equals(this.casterUuid))
                || target == caster || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }
        return caster == null
                || (!caster.isAlliedTo(target) && !target.isAlliedTo(caster));
    }

    private static void applyTrueDamage(LivingEntity target, DamageSource source, float fraction) {
        float damage = Math.max(0.0F, target.getMaxHealth() * fraction);
        float desiredHealth = Math.max(0.0F, target.getHealth() - damage);

        target.invulnerableTime = 0;
        target.hurt(source, damage);
        target.invulnerableTime = 0;

        if (target.isDeadOrDying() || target.isRemoved()) {
            return;
        }

        target.setAbsorptionAmount(0.0F);
        float finalHealth = Math.min(target.getHealth(), desiredHealth);
        if (finalHealth <= 0.0F) {
            target.setHealth(0.0F);
            if (!target.isRemoved()) {
                target.die(source);
            }
        } else {
            target.setHealth(finalHealth);
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_CASTER_ID, 0);
        this.entityData.define(DATA_START_GAME_TICK, -1L);
        this.entityData.define(DATA_SEED, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.trunkResolved = tag.getBoolean("TrunkResolved");
        this.crownResolved = tag.getBoolean("CrownResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        long startGameTick = tag.contains("StartGameTick")
                ? tag.getLong("StartGameTick")
                : this.level().getGameTime() - Math.max(0, tag.getInt("TimelineTick"));
        this.entityData.set(DATA_START_GAME_TICK, startGameTick);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putBoolean("TrunkResolved", this.trunkResolved);
        tag.putBoolean("CrownResolved", this.crownResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
        tag.putLong("StartGameTick", this.entityData.get(DATA_START_GAME_TICK));
        tag.putInt("TimelineTick", getTimelineAgeTicks());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid(FluidType fluidType) {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

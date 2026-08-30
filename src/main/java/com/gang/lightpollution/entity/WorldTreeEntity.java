package com.gang.lightpollution.entity;

import com.gang.lightpollution.api.WorldTreeParams;
import com.gang.lightpollution.fx.FxHash;
import com.gang.lightpollution.fx.WorldTreeShape;
import com.gang.lightpollution.fx.WorldTreeSource;
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
public final class WorldTreeEntity extends Entity implements WorldTreeSource {
    // The form lives in WorldTreeShape, which the renderer and the public API both read, so there is
    // one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = WorldTreeParams.SPELL_LIFETIME_TICKS;
    /**
     * The trunk spears up first.
     *
     * <p>Trunk before roots, which is the order a tree is actually built in and the
     * order that reads: something erupts, and then it takes hold. Roots racing out
     * of bare ground before anything exists to own them looked like an unrelated
     * effect that the trunk then landed in the middle of.</p>
     */

    /**
     * Radius of the clear trunk, above the root flare, in blocks.
     *
     * <p>Diameter at breast height is measured at 1.3 m precisely because that is
     * above the butt swell. So this is the trunk proper, and {@link #WorldTreeShape.FLARE_SCALE}
     * widens the bottom of it.</p>
     */

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

    /** Radius the roots cover, in blocks. */
    public static final double EFFECT_RADIUS = WorldTreeShape.ROOT_REACH + 3.0D;
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
        return WorldTreeShape.rootProgress(getVisualAgeTicks(partialTick));
    }

    /** How far the trunk has risen, 0 to 1. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public WorldTreeParams shapeParams() {
        // groundY is unused on this side: the roots follow the terrain instead.
        return WorldTreeParams.of(getSeed(), this.getY());
    }

    public float trunkProgress(float partialTick) {
        return WorldTreeShape.trunkProgress(getVisualAgeTicks(partialTick));
    }

    /** How far the branches have extended, 0 to 1. */
    public float branchProgress(float partialTick) {
        return WorldTreeShape.branchProgress(getVisualAgeTicks(partialTick));
    }

    /** How far the crown has filled in, 0 to 1. */
    public float crownProgress(float partialTick) {
        return WorldTreeShape.crownProgress(getVisualAgeTicks(partialTick));
    }

    /**
     * How far the tree has hardened into crystal, 0 to 1. Drives the colour shift
     * from living gold to pale crystal, which is what makes the final silhouette
     * read as a monument rather than as a plant.
     */
    public float hardened(float partialTick) {
        return WorldTreeShape.hardened(getVisualAgeTicks(partialTick));
    }

    /** Overall fade, 1 while it stands and falling to 0 at the end. */
    public float fade(float partialTick) {
        return WorldTreeShape.fade(getVisualAgeTicks(partialTick), LIFETIME_TICKS);
    }

    /** Bearing a root runs along, in radians. */
    public float rootBearing(int root) {
        return WorldTreeShape.rootBearing(shapeParams(), root);
    }

    /**
     * A point along a root. {@code t} is 0 at the seed and 1 at the tip.
     *
     * <p>Sampled onto the real terrain height rather than laid on a plane, so a
     * root crossing a slope climbs it. Both sides read the same heightmap for
     * loaded chunks, so the visual and the strike agree.</p>
     */
    /** The built tree. Built on first use and held; deterministic from the seed. */
    @Override
    public WorldTreeShape.Skeleton tree() {
        if (tree == null) {
            tree = WorldTreeShape.build(shapeParams());
        }
        return tree;
    }

    /** Every branch, all levels. */
    public WorldTreeShape.Limb[] skeleton() {
        return tree().limbs();
    }

    /** The subset of branches that carry leaves. */
    public WorldTreeShape.Limb[] twigs() {
        return tree().twigs();
    }

    public WorldTreeShape.Leaf[] leaves() {
        return tree().leaves();
    }

    @Override
    public Vec3 rootPoint(int root, float t, float partialTick) {
        WorldTreeParams params = shapeParams();
        Vec3 seed = seedPoint(partialTick);
        double out = WorldTreeShape.rootDistance(params, root, t);
        float angle = WorldTreeShape.rootAngle(params, root, t);
        double x = seed.x + Mth.cos(angle) * out;
        double z = seed.z + Mth.sin(angle) * out;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        return WorldTreeShape.rootPointAt(params, seed, root, t, surface);
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
        return WorldTreeShape.rootRadius(shapeParams(), root, t);
    }

    /** The built tree, held rather than rebuilt per frame. */
    private WorldTreeShape.Skeleton tree;

    /**
     * Hue index of a leaf, 0 to 1.
     *
     * <p>Weighted toward the warm and green-teal end of the palette, with the
     * magenta and violet bands kept to roughly a sixth of the crown. An even spread
     * over all six reads as confetti rather than as a canopy.</p>
     */
    /** Trunk radius at {@code height} blocks above the seed. */
    public float trunkRadius(float height) {
        return WorldTreeShape.trunkRadius(shapeParams(), height);
    }

    /**
     * How far out the buttress lobes stand at {@code height}, as a fraction of the
     * trunk radius. Zero above the flare.
     */
    public float buttressDepth(float height) {
        return WorldTreeShape.buttressDepth(shapeParams(), height);
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private float hashUnit(int index, long salt) {
        return FxHash.unit(getSeed(), index, salt);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (!this.trunkResolved && timelineTick >= WorldTreeShape.TRUNK_END_TICK) {
                this.trunkResolved = true;
                resolveTrunk(serverLevel);
            }
            if (timelineTick >= WorldTreeShape.ROOT_START_TICK && timelineTick <= WorldTreeShape.ROOT_END_TICK
                    && timelineTick % ROOT_INTERVAL_TICKS == 0) {
                resolveRoots(serverLevel);
            }
            if (!this.crownResolved && timelineTick >= WorldTreeShape.CROWN_END_TICK) {
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
        for (int root = 0; root < WorldTreeShape.ROOT_COUNT; root++) {
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
                    SpellDamage.apply(this, target, source, ROOT_DAMAGE_FRACTION);
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
            SpellDamage.apply(this, target, source, TRUNK_DAMAGE_FRACTION);
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
            SpellDamage.apply(this, target, source, CROWN_DAMAGE_FRACTION);
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

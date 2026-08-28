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
 * Server-owned anchor for Sky Collapse.
 *
 * <p>The sky itself is what breaks. A fracture spreads across the whole dome,
 * then slabs of it tear loose and come down — huge, flat, and slow, because
 * something that large cannot look fast. The shards are sub-objects of this one
 * entity and every property of shard <em>i</em> is a pure function of the
 * synchronized seed, so the renderer and the server-side impacts agree without
 * any extra syncing.</p>
 */
public final class SkyCollapseEntity extends Entity {
    public static final int LIFETIME_TICKS = 220;
    /** The fracture spreads across the sky over this window. */
    public static final int FRACTURE_END_TICK = 40;
    /** First shard tears loose here. */
    public static final int SHED_START_TICK = 44;
    /** Ticks between successive shards tearing loose. */
    public static final int SHED_INTERVAL_TICKS = 16;
    /** How long a shard takes to come down. Slow: it is enormous. */
    public static final int SHARD_FALL_TICKS = 42;
    /** Ordinary shards, then the keystone. */
    public static final int PLAIN_SHARDS = 6;
    public static final int SHARD_COUNT = PLAIN_SHARDS + 1;

    /** Radius over which shards come down, in blocks. */
    public static final double EFFECT_RADIUS = 20.0D;
    /** Height a shard starts at, above its landing point. */
    public static final double SHARD_FALL_HEIGHT = 120.0D;
    /** Half-width of an ordinary shard, in blocks. */
    public static final float SHARD_HALF_SPAN = 7.0F;
    /** Half-width of the keystone. */
    public static final float KEYSTONE_HALF_SPAN = 18.0F;

    public static final double SHARD_BLAST_RADIUS = 6.0D;
    public static final double KEYSTONE_BLAST_RADIUS = 14.0D;
    private static final float SHARD_DAMAGE_FRACTION = 0.065F;
    private static final float KEYSTONE_DAMAGE_FRACTION = 0.37F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            SkyCollapseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            SkyCollapseEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            SkyCollapseEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    /** Highest shard already resolved, so a reload cannot double-hit. */
    private int resolvedThrough = -1;

    public SkyCollapseEntity(EntityType<? extends SkyCollapseEntity> entityType, Level level) {
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

    /** How far the fracture has spread across the sky, 0 to 1. */
    public float fractureProgress(float partialTick) {
        return Mth.clamp(getVisualAgeTicks(partialTick) / FRACTURE_END_TICK, 0.0F, 1.0F);
    }

    /**
     * How lit the fracture is. It closes again as the keystone lands, so the sky
     * takes its light back rather than the cracks simply switching off.
     */
    public float fractureGlow(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        int seal = impactTick(SHARD_COUNT - 1);
        if (age <= FRACTURE_END_TICK) {
            return fractureProgress(partialTick);
        }
        if (age >= seal) {
            return 0.0F;
        }
        return 1.0F - Mth.clamp((age - FRACTURE_END_TICK)
                / (float) (seal - FRACTURE_END_TICK), 0.0F, 1.0F) * 0.55F;
    }

    /** Fewest corners on a shard's outline. */
    public static final int MIN_SHARD_CORNERS = 5;
    /** Most corners on a shard's outline. */
    public static final int MAX_SHARD_CORNERS = 9;

    /**
     * Bearing the rift runs along, in radians. Derived from the seed so the sky
     * does not split the same way twice.
     */
    public float riftBearing() {
        return hashUnit(0, 0x1B873593L) * Mth.TWO_PI;
    }

    /** How many corners this shard's outline has. */
    public int shardCorners(int shard) {
        int span = MAX_SHARD_CORNERS - MIN_SHARD_CORNERS + 1;
        return MIN_SHARD_CORNERS
                + (int) (hashUnit(shard, 0x7FEB352DL) * span) % span;
    }

    /**
     * Distance of one of a shard's corners from its centre, as a fraction of its
     * half-span. Slabs of a broken dome are not squares, and a square silhouette
     * was the single clearest tell that these were quads rather than debris.
     */
    public float shardCornerScale(int shard, int corner) {
        return 0.42F + hashUnit(shard * 31 + corner, 0xCC9E2D51L) * 0.58F;
    }

    /**
     * Angular offset of a corner from its evenly spaced position, in radians.
     * Without this the corners sit on a regular polygon however much their radii
     * vary, and every shard still reads as the same shape.
     */
    public float shardCornerSkew(int shard, int corner) {
        int corners = shardCorners(shard);
        float slice = Mth.TWO_PI / corners;
        return (hashUnit(shard * 61 + corner, 0x85EBCA77L) - 0.5F) * slice * 0.7F;
    }

    public static boolean isKeystone(int shard) {
        return shard >= PLAIN_SHARDS;
    }

    /** Tick at which a shard tears loose from the sky. */
    public static int shedTick(int shard) {
        return SHED_START_TICK + shard * SHED_INTERVAL_TICKS;
    }

    /** Tick at which a shard lands. */
    public static int impactTick(int shard) {
        return shedTick(shard) + SHARD_FALL_TICKS;
    }

    public static double blastRadius(int shard) {
        return isKeystone(shard) ? KEYSTONE_BLAST_RADIUS : SHARD_BLAST_RADIUS;
    }

    public static float halfSpan(int shard) {
        return isKeystone(shard) ? KEYSTONE_HALF_SPAN : SHARD_HALF_SPAN;
    }

    /**
     * Where a shard lands, dropped onto the terrain surface. The keystone comes
     * down on the aimed point itself — it is what the whole spell builds to.
     */
    public Vec3 shardLanding(int shard) {
        if (isKeystone(shard)) {
            int centre = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    Mth.floor(this.getX()), Mth.floor(this.getZ()));
            return new Vec3(this.getX(), centre, this.getZ());
        }
        float unitAngle = hashUnit(shard, 0x9E3779B9L);
        float unitRadius = hashUnit(shard, 0x85EBCA6BL);
        // sqrt spreads them evenly over the disc instead of clustering the
        // centre.
        double radius = Math.sqrt(unitRadius) * EFFECT_RADIUS;
        double angle = unitAngle * Mth.TWO_PI;
        double x = this.getX() + Math.cos(angle) * radius;
        double z = this.getZ() + Math.sin(angle) * radius;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        return new Vec3(x, surface, z);
    }

    /** Position of a shard's centre, or its landing point once it has hit. */
    public Vec3 shardPosition(int shard, float partialTick) {
        Vec3 landing = shardLanding(shard);
        float age = getVisualAgeTicks(partialTick);
        float fall = Mth.clamp((age - shedTick(shard))
                / (float) SHARD_FALL_TICKS, 0.0F, 1.0F);
        // Eased rather than linear, but gently: a slab this size reads wrong if
        // it accelerates like a pebble.
        float eased = fall * fall * (1.7F - 0.7F * fall);
        return new Vec3(landing.x,
                landing.y + SHARD_FALL_HEIGHT * (1.0F - eased),
                landing.z);
    }

    /**
     * How far a shard has tipped out of the sky plane, in radians. It peels away
     * rather than dropping flat, which is what makes it read as a piece of
     * something that used to be whole.
     */
    public float shardTilt(int shard, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        float fall = Mth.clamp((age - shedTick(shard))
                / (float) SHARD_FALL_TICKS, 0.0F, 1.0F);
        float lean = 0.35F + hashUnit(shard, 0xB5297A4DL) * 0.5F;
        return fall * lean * Mth.PI;
    }

    /** Spin of a shard about its own normal, in radians. */
    public float shardSpin(int shard, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        float fall = Mth.clamp((age - shedTick(shard))
                / (float) SHARD_FALL_TICKS, 0.0F, 1.0F);
        float turns = 0.15F + hashUnit(shard, 0x68E31DA4L) * 0.35F;
        return hashUnit(shard, 0x2545F491L) * Mth.TWO_PI
                + fall * turns * Mth.TWO_PI;
    }

    /** Brightness of a shard, 0 before it tears loose. */
    public float shardBrightness(int shard, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        int shed = shedTick(shard);
        if (age < shed) {
            return 0.0F;
        }
        int impact = impactTick(shard);
        if (age < impact) {
            float fall = (age - shed) / (float) SHARD_FALL_TICKS;
            return 0.5F + fall * 0.5F;
        }
        float since = age - impact;
        return isKeystone(shard)
                ? Math.max(0.0F, 3.2F - since * 0.15F)
                : Math.max(0.0F, 1.7F - since * 0.2F);
    }

    private float hashUnit(int shard, long salt) {
        long hash = (getSeed() & 0xFFFFFFFFL) * 0x2545F4914F6CDD1DL
                ^ (shard + 1L) * salt;
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
            for (int shard = this.resolvedThrough + 1; shard < SHARD_COUNT; shard++) {
                if (timelineTick < impactTick(shard)) {
                    break;
                }
                this.resolvedThrough = shard;
                resolveShardImpact(serverLevel, shard);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    private void resolveShardImpact(ServerLevel level, int shard) {
        LivingEntity caster = resolveCaster(level);
        Vec3 impact = shardLanding(shard);
        double radius = blastRadius(shard);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(impact.x - radius, impact.y - radius, impact.z - radius,
                        impact.x + radius, impact.y + radius, impact.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(impact)
                                <= radius * radius);
        if (targets.isEmpty()) {
            return;
        }

        DamageSource source = SkyCollapseDamage.source(level, this, caster);
        float fraction = isKeystone(shard)
                ? KEYSTONE_DAMAGE_FRACTION : SHARD_DAMAGE_FRACTION;
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, fraction);
        }
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
        this.resolvedThrough = tag.contains("ResolvedThrough")
                ? tag.getInt("ResolvedThrough") : -1;
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
        tag.putInt("ResolvedThrough", this.resolvedThrough);
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

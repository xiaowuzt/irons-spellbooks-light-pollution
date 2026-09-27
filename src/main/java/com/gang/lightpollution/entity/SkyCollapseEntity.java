package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.api.SkyCollapseParams;
import com.gang.lightpollution.fx.FxHash;
import com.gang.lightpollution.fx.SkyCollapseShape;
import com.gang.lightpollution.fx.SkyCollapseSource;
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
public final class SkyCollapseEntity extends Entity implements SkyCollapseSource {
    private static final String CONFIG_ID = "skyCollapse";

    private static int configuredLifetime() {
        return SpellConfig.lifetimeTicks(CONFIG_ID);
    }

    private static double configuredRadius() {
        return SpellConfig.effectRadius(CONFIG_ID);
    }

    private static float configuredPrimaryDamage() {
        return (float) SpellConfig.damageFraction(CONFIG_ID);
    }

    private static float configuredSecondaryDamage() {
        return (float) SpellConfig.secondaryDamageFraction(CONFIG_ID);
    }
    // The form lives in SkyCollapseShape, which the renderer and the public API both read, so there
    // is one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = SkyCollapseParams.SPELL_LIFETIME_TICKS;

    /** Radius over which shards come down, in blocks. */
    public static final double EFFECT_RADIUS = 20.0D;

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
            return Math.min(configuredLifetime(), Math.max(0, this.tickCount));
        }
        long age = this.level().getGameTime() - startGameTick;
        return (int) Math.min(configuredLifetime(), Math.max(0L, age));
    }

    public float getVisualAgeTicks(float partialTick) {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        float age = startGameTick < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - startGameTick) + partialTick;
        return Math.min(configuredLifetime(), Math.max(0.0F, age));
    }

    /** How far the fracture has spread across the sky, 0 to 1. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public SkyCollapseParams shapeParams() {
        // groundY is unused on this side: the landing points come from the terrain instead.
        return SkyCollapseParams.of(getSeed(), this.getY());
    }

    public float fractureProgress(float partialTick) {
        return SkyCollapseShape.fractureProgress(getVisualAgeTicks(partialTick));
    }

    /**
     * How lit the fracture is. It closes again as the keystone lands, so the sky
     * takes its light back rather than the cracks simply switching off.
     */
    public float fractureGlow(float partialTick) {
        return SkyCollapseShape.fractureGlow(getVisualAgeTicks(partialTick));
    }


    /**
     * Bearing the rift runs along, in radians. Derived from the seed so the sky
     * does not split the same way twice.
     */
    public float riftBearing() {
        return SkyCollapseShape.riftBearing(shapeParams());
    }

    /** How many corners this shard's outline has. */
    public int shardCorners(int shard) {
        return SkyCollapseShape.shardCorners(shapeParams(), shard);
    }

    /**
     * Distance of one of a shard's corners from its centre, as a fraction of its
     * half-span. Slabs of a broken dome are not squares, and a square silhouette
     * was the single clearest tell that these were quads rather than debris.
     */
    public float shardCornerScale(int shard, int corner) {
        return SkyCollapseShape.shardCornerScale(shapeParams(), shard, corner);
    }

    /**
     * Angular offset of a corner from its evenly spaced position, in radians.
     * Without this the corners sit on a regular polygon however much their radii
     * vary, and every shard still reads as the same shape.
     */
    public float shardCornerSkew(int shard, int corner) {
        return SkyCollapseShape.shardCornerSkew(shapeParams(), shard, corner);
    }

    public static boolean isKeystone(int shard) {
        return SkyCollapseShape.isKeystone(shard);
    }

    /** Tick at which a shard tears loose from the sky. */
    public static int shedTick(int shard) {
        return SkyCollapseShape.shedTick(shard);
    }

    /** Tick at which a shard lands. */
    public static int impactTick(int shard) {
        return SkyCollapseShape.impactTick(shard);
    }

    public static double blastRadius(int shard) {
        return isKeystone(shard) ? KEYSTONE_BLAST_RADIUS : SHARD_BLAST_RADIUS;
    }

    public static float halfSpan(int shard) {
        return isKeystone(shard) ? SkyCollapseShape.KEYSTONE_HALF_SPAN : SkyCollapseShape.SHARD_HALF_SPAN;
    }

    /**
     * Where a shard lands, dropped onto the terrain surface. The keystone comes
     * down on the aimed point itself — it is what the whole spell builds to.
     */
    @Override
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
        double radius = Math.sqrt(unitRadius) * configuredRadius();
        double angle = unitAngle * Mth.TWO_PI;
        double x = this.getX() + Math.cos(angle) * radius;
        double z = this.getZ() + Math.sin(angle) * radius;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        return new Vec3(x, surface, z);
    }

    /** Position of a shard's centre, or its landing point once it has hit. */
    public Vec3 shardPosition(int shard, float partialTick) {
        return SkyCollapseShape.shardPosition(shapeParams(), shardLanding(shard), shard,
                getVisualAgeTicks(partialTick));
    }

    /**
     * How far a shard has tipped out of the sky plane, in radians. It peels away
     * rather than dropping flat, which is what makes it read as a piece of
     * something that used to be whole.
     */
    public float shardTilt(int shard, float partialTick) {
        return SkyCollapseShape.shardTilt(shapeParams(), shard, getVisualAgeTicks(partialTick));
    }

    /** Spin of a shard about its own normal, in radians. */
    public float shardSpin(int shard, float partialTick) {
        return SkyCollapseShape.shardSpin(shapeParams(), shard, getVisualAgeTicks(partialTick));
    }

    /** Brightness of a shard, 0 before it tears loose. */
    public float shardBrightness(int shard, float partialTick) {
        return SkyCollapseShape.shardBrightness(shard, getVisualAgeTicks(partialTick));
    }

    private float hashUnit(int shard, long salt) {
        return FxHash.unit(getSeed(), shard, salt);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            for (int shard = this.resolvedThrough + 1; shard < SkyCollapseShape.SHARD_COUNT; shard++) {
                if (timelineTick < impactTick(shard)) {
                    break;
                }
                this.resolvedThrough = shard;
                resolveShardImpact(serverLevel, shard);
            }
        }

        if (timelineTick >= configuredLifetime()) {
            this.discard();
        }
    }

    private void resolveShardImpact(ServerLevel level, int shard) {
        LivingEntity caster = resolveCaster(level);
        Vec3 impact = shardLanding(shard);
        double radius = blastRadius(shard);
        List<LivingEntity> targets = SpellConfig.limitTargets("skyCollapse", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(impact.x - radius, impact.y - radius, impact.z - radius,
                        impact.x + radius, impact.y + radius, impact.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(impact)
                                <= radius * radius));
        if (targets.isEmpty()) {
            return;
        }

        DamageSource source = SkyCollapseDamage.source(level, this, caster);
        float fraction = isKeystone(shard)
                ? configuredSecondaryDamage() : configuredPrimaryDamage();
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

package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.api.StellarConvergenceParams;
import com.gang.lightpollution.fx.FxHash;
import com.gang.lightpollution.fx.StellarConvergenceShape;
import com.gang.lightpollution.fx.StellarConvergenceSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/**
 * Server-owned anchor for Stellar Convergence.
 *
 * <p>Nine coloured stars light one after another, hanging at different heights
 * and bearings. Each one is a separate light, so the ground under them carries
 * nine overlapping coloured shadows that drift as they move — the one thing the
 * shading pass can do that no spell in the set had used. Then filaments link them
 * into a net, the net contracts, and all nine fire into the centre at once.</p>
 *
 * <p>Star positions are pure functions of the timeline and the synchronized seed,
 * so the renderer, the light emitter and the server damage all agree.</p>
 */
public final class StellarConvergenceEntity extends Entity
        implements StellarConvergenceSource {
    private static final String CONFIG_ID = "stellarConvergence";

    private static int configuredLifetime() {
        return SpellConfig.lifetimeTicks(CONFIG_ID);
    }

    private static double configuredRadius() {
        return SpellConfig.effectRadius(CONFIG_ID);
    }

    private static int configuredInterval() {
        return SpellConfig.damageIntervalTicks(CONFIG_ID);
    }

    private static float configuredPrimaryDamage() {
        return (float) SpellConfig.damageFraction(CONFIG_ID);
    }

    private static float configuredSecondaryDamage() {
        return (float) SpellConfig.secondaryDamageFraction(CONFIG_ID);
    }

    private static int configuredPhaseOne() {
        return SpellConfig.phaseTick(CONFIG_ID, 1);
    }

    private static int configuredPhaseTwo() {
        return SpellConfig.phaseTick(CONFIG_ID, 2);
    }

    // The form lives in StellarConvergenceShape, which the renderer and the public API both read,
    // so there is one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = StellarConvergenceParams.SPELL_LIFETIME_TICKS;

    /** Bound used to gather candidates. */
    public static final double EFFECT_RADIUS = StellarConvergenceShape.SHELL_RADIUS + StellarConvergenceShape.COLUMN_RADIUS + 2.0D;

    /** Max-health fraction per beam tick while the column holds. */
    private static final float BEAM_DAMAGE_FRACTION = 0.033F;
    /** Ticks between beam damage applications. */
    private static final int BEAM_INTERVAL_TICKS = 10;
    /** Max-health fraction of the final burst. */
    private static final float BURST_DAMAGE_FRACTION = 0.44F;
    /** Radius of the final burst, in blocks. */
    private static final double BURST_RADIUS = 12.0D;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            StellarConvergenceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            StellarConvergenceEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            StellarConvergenceEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean burstResolved;

    public StellarConvergenceEntity(EntityType<? extends StellarConvergenceEntity> entityType,
                                    Level level) {
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

    /** Centre of the constellation, and the point the beams converge on. */
    public Vec3 shellCentre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + StellarConvergenceShape.SHELL_HEIGHT * shapeParams().scale(),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Where the column meets the ground. */
    public Vec3 groundCentre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public StellarConvergenceParams shapeParams() {
        return StellarConvergenceParams.of(getSeed()).scale((float) (configuredRadius() / 18.0D)).lifetime(configuredLifetime());
    }

    public static int litTick(int star) {
        return StellarConvergenceShape.litTick(star);
    }

    /**
     * World position of a star. They sit on a sphere around the centre, spread by
     * a Fibonacci lattice so the nine of them look deliberately placed rather
     * than randomly scattered, and drift slowly so their shadows sweep.
     */
    public Vec3 starPosition(int star, float partialTick) {
        return StellarConvergenceShape.starPosition(shapeParams(), shellCentre(partialTick), star,
                getVisualAgeTicks(partialTick));
    }

    /** Colour of a star, spread around the spectrum by index. */
    public float[] starColour(int star) {
        return StellarConvergenceShape.starColour(star);
    }

    /** Brightness of a star, 0 before it lights. */
    public float starBrightness(int star, float partialTick) {
        return StellarConvergenceShape.starBrightness(star, getVisualAgeTicks(partialTick));
    }

    /** How far the linking filaments have grown, 0 to 1. */
    public float weaveProgress(float partialTick) {
        return StellarConvergenceShape.weaveProgress(getVisualAgeTicks(partialTick));
    }

    /** Strength of the converged column, 0 before it fires. */
    public float columnStrength(float partialTick) {
        return StellarConvergenceShape.columnStrength(getVisualAgeTicks(partialTick));
    }

    /** Burst flash, 0 outside the moment the column detonates. */
    public float burstFlash(float partialTick) {
        return StellarConvergenceShape.burstFlash(getVisualAgeTicks(partialTick));
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private float hashUnit(int star, long salt) {
        return FxHash.unit(getSeed(), star, salt);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick >= configuredPhaseOne() && timelineTick < configuredPhaseTwo()
                    && timelineTick % configuredInterval() == 0) {
                resolveColumn(serverLevel);
            }
            if (!this.burstResolved && timelineTick >= configuredPhaseTwo()) {
                this.burstResolved = true;
                resolveBurst(serverLevel);
            }
        }

        if (timelineTick >= configuredLifetime()) {
            this.discard();
        }
    }

    /** Anything standing in the column while it holds. */
    private void resolveColumn(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 ground = groundCentre(1.0F);
        // A column, not a sphere: it runs from the ground up to the constellation,
        // so height should not exempt anyone inside it.
        AABB bounds = new AABB(
                ground.x - (StellarConvergenceShape.COLUMN_RADIUS * shapeParams().scale()), ground.y - 2.0D, ground.z - (StellarConvergenceShape.COLUMN_RADIUS * shapeParams().scale()),
                ground.x + (StellarConvergenceShape.COLUMN_RADIUS * shapeParams().scale()), ground.y + (StellarConvergenceShape.SHELL_HEIGHT * shapeParams().scale()) * shapeParams().scale(), ground.z + (StellarConvergenceShape.COLUMN_RADIUS * shapeParams().scale()));
        List<LivingEntity> targets = SpellConfig.limitTargets("stellarConvergence", level.getEntitiesOfClass(
                LivingEntity.class, bounds,
                target -> canAffect(caster, target) && withinColumn(target, ground)));
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = StellarConvergenceDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, configuredPrimaryDamage());
        }
    }

    private boolean withinColumn(LivingEntity target, Vec3 ground) {
        Vec3 centre = target.getBoundingBox().getCenter();
        double dx = centre.x - ground.x;
        double dz = centre.z - ground.z;
        return dx * dx + dz * dz <= (StellarConvergenceShape.COLUMN_RADIUS * shapeParams().scale()) * (StellarConvergenceShape.COLUMN_RADIUS * shapeParams().scale());
    }

    private void resolveBurst(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 ground = groundCentre(1.0F);
        List<LivingEntity> targets = SpellConfig.limitTargets("stellarConvergence", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(ground.x - (BURST_RADIUS * shapeParams().scale()), ground.y - (BURST_RADIUS * shapeParams().scale()),
                        ground.z - (BURST_RADIUS * shapeParams().scale()), ground.x + (BURST_RADIUS * shapeParams().scale()),
                        ground.y + (BURST_RADIUS * shapeParams().scale()), ground.z + (BURST_RADIUS * shapeParams().scale())),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(ground)
                                <= (BURST_RADIUS * shapeParams().scale()) * (BURST_RADIUS * shapeParams().scale())));
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = StellarConvergenceDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, configuredSecondaryDamage());
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
        this.burstResolved = tag.getBoolean("BurstResolved");
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
        tag.putBoolean("BurstResolved", this.burstResolved);
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

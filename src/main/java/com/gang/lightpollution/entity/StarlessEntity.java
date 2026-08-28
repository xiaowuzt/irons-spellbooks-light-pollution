package com.gang.lightpollution.entity;

import com.gang.lightpollution.ExampleMod;
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
 * Server-owned anchor for the 13 second Starless timeline.
 *
 * <p>Starless inverts the rest of the set: instead of emitting light it takes
 * light away. While the void grows it drains every other spell light in range,
 * then releases everything it swallowed as one white detonation. How much it
 * swallowed is therefore both the visual payoff and the damage multiplier, so
 * the count is resolved once on the server and synchronized — the renderer and
 * the damage pulse read the same number rather than each estimating it.</p>
 */
public final class StarlessEntity extends Entity {
    public static final int LIFETIME_TICKS = 260;
    /** The seed is a point of darkness before the void starts to open. */
    public static final int SEED_END_TICK = 20;
    /** Ingest runs from the seed to the nadir; the void opens across it. */
    public static final int NADIR_START_TICK = 110;
    public static final int COLLAPSE_START_TICK = 150;
    public static final int RELEASE_TICK = 160;
    public static final int AFTERGLOW_START_TICK = 175;

    public static final int STARVATION_DAMAGE_TICK = NADIR_START_TICK;
    public static final int RELEASE_DAMAGE_TICK = RELEASE_TICK;

    public static final double EFFECT_RADIUS = 12.0D;
    /** Void radius at the nadir, in blocks. */
    public static final float MAX_VOID_RADIUS = 5.5F;
    /**
     * Ceiling on the drain. A light the sphere has swallowed goes out completely:
     * leaving a residue behind reads as the spell failing to do the one thing it
     * is for.
     */
    public static final float MAX_DRAIN = 1.0F;
    /** Ticks over which the release flash decays. */
    private static final float FLASH_DECAY_TICKS = 26.0F;

    private static final float STARVATION_DAMAGE_FRACTION = 0.16F;
    private static final float RELEASE_DAMAGE_FRACTION = 0.26F;
    /** Extra maximum-health fraction per swallowed light, and its cap. */
    private static final float SWALLOWED_DAMAGE_STEP = 0.05F;
    private static final int SWALLOWED_DAMAGE_CAP = 4;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            StarlessEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            StarlessEntity.class, EntityDataSerializers.LONG);
    /**
     * Number of this mod's other spell effects seen inside the void. These are
     * exactly the entities that produce spell lights, so the count is a faithful
     * server-side stand-in for "how much light was swallowed".
     */
    private static final EntityDataAccessor<Integer> DATA_SWALLOWED = SynchedEntityData.defineId(
            StarlessEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean starvationDamageResolved;
    private boolean releaseDamageResolved;

    public StarlessEntity(EntityType<? extends StarlessEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.setPos(center.x, center.y, center.z);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getSwallowedCount() {
        return this.entityData.get(DATA_SWALLOWED);
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

    /** Centre of the void, a little above the anchor so it reads as a sphere in air. */
    public Vec3 voidCenter(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + 1.5D,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * Radius of the dark sphere. It opens smoothly, holds at the nadir, then is
     * crushed quadratically so the collapse reads as an implosion rather than a
     * shrink.
     */
    public float getVoidRadius(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < SEED_END_TICK) {
            return 0.6F * (age / SEED_END_TICK);
        }
        if (age < NADIR_START_TICK) {
            float t = (age - SEED_END_TICK)
                    / (float) (NADIR_START_TICK - SEED_END_TICK);
            return 0.6F + smoothstep(t) * (MAX_VOID_RADIUS - 0.6F);
        }
        if (age < COLLAPSE_START_TICK) {
            return MAX_VOID_RADIUS;
        }
        if (age < RELEASE_TICK) {
            float t = (age - COLLAPSE_START_TICK)
                    / (float) (RELEASE_TICK - COLLAPSE_START_TICK);
            return MAX_VOID_RADIUS * (1.0F - t * t);
        }
        return 0.0F;
    }

    /**
     * How strongly other spell lights are dimmed, 0 to 1. It tightens through the
     * collapse — the void takes the last of the light just before it lets go —
     * and snaps to zero at the release so the world lights up again.
     */
    public float getDrainStrength(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < SEED_END_TICK) {
            return 0.0F;
        }
        if (age < NADIR_START_TICK) {
            float t = (age - SEED_END_TICK)
                    / (float) (NADIR_START_TICK - SEED_END_TICK);
            return smoothstep(t) * MAX_DRAIN;
        }
        if (age < COLLAPSE_START_TICK) {
            return MAX_DRAIN;
        }
        if (age < RELEASE_TICK) {
            float t = (age - COLLAPSE_START_TICK)
                    / (float) (RELEASE_TICK - COLLAPSE_START_TICK);
            return MAX_DRAIN + (1.0F - MAX_DRAIN) * t;
        }
        return 0.0F;
    }

    /**
     * How far through swallowing the void is, 0 to 1, holding at 1 through the
     * nadir and collapse. Drives the intensity of the lensing and the ring, which
     * should keep building rather than tracking the drain's plateau.
     */
    public float getIngestProgress(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < SEED_END_TICK) {
            return 0.0F;
        }
        if (age >= RELEASE_TICK) {
            return 0.0F;
        }
        if (age >= NADIR_START_TICK) {
            return 1.0F;
        }
        return smoothstep((age - SEED_END_TICK)
                / (float) (NADIR_START_TICK - SEED_END_TICK));
    }

    /** Release flash, 0 to 1: instant attack at the release, exponential decay. */    public float getReleaseFlash(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < RELEASE_TICK) {
            return 0.0F;
        }
        float t = (age - RELEASE_TICK) / FLASH_DECAY_TICKS;
        return (float) Math.exp(-t * t * 3.0D);
    }

    /**
     * Multiplier the swallowed lights add to the release, 1 upward. Shared by the
     * damage pulse and the flash so a bigger detonation always means a bigger hit.
     */
    public float getReleaseScale() {
        return 1.0F + Math.min(getSwallowedCount(), SWALLOWED_DAMAGE_CAP) * 0.35F;
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick >= SEED_END_TICK && timelineTick <= RELEASE_TICK) {
                countSwallowedLights(serverLevel);
            }
            if (!this.starvationDamageResolved && timelineTick >= STARVATION_DAMAGE_TICK) {
                this.starvationDamageResolved = true;
                resolveDamagePulse(serverLevel, STARVATION_DAMAGE_FRACTION,
                        MAX_VOID_RADIUS, false);
            }
            if (!this.releaseDamageResolved && timelineTick >= RELEASE_DAMAGE_TICK) {
                this.releaseDamageResolved = true;
                float bonus = Math.min(getSwallowedCount(), SWALLOWED_DAMAGE_CAP)
                        * SWALLOWED_DAMAGE_STEP;
                resolveDamagePulse(serverLevel,
                        RELEASE_DAMAGE_FRACTION + bonus, EFFECT_RADIUS, true);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /**
     * Counts this mod's other spell effects inside the void and keeps the highest
     * figure seen. A monotonic maximum is deliberate: a Funeral Nova that expires
     * mid-ingest still had its light swallowed, so the release should not shrink
     * because the source has since gone.
     */
    private void countSwallowedLights(ServerLevel level) {
        int seen = 0;
        for (Entity nearby : level.getEntities(this, effectBounds(EFFECT_RADIUS))) {
            if (nearby == this || !isSpellLightSource(nearby)) {
                continue;
            }
            if (nearby.position().distanceToSqr(this.position())
                    <= EFFECT_RADIUS * EFFECT_RADIUS) {
                seen++;
            }
        }
        if (seen > getSwallowedCount()) {
            this.entityData.set(DATA_SWALLOWED, seen);
        }
    }

    /** The mod's own spell anchors are the entities that emit spell light. */
    private static boolean isSpellLightSource(Entity entity) {
        return entity instanceof CelestialJudgmentEntity
                || entity instanceof ChromaticAccretionEntity
                || entity instanceof EclipseSeveranceEntity
                || entity instanceof FuneralNovaEntity
                || entity instanceof StargraveSingularityEntity
                || entity instanceof StarlessEntity;
    }

    private void resolveDamagePulse(ServerLevel level, float fraction, double radius,
                                    boolean knockback) {
        LivingEntity caster = resolveCaster(level);
        Vec3 core = this.position().add(0.0D, 1.5D, 0.0D);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                effectBounds(radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(core)
                                <= radius * radius);
        DamageSource source = StarlessDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            applyTrueDamage(target, source, fraction);
            if (knockback && target.isAlive()) {
                knockTargetOutward(target, core);
            }
        }

        ExampleMod.LOGGER.debug(
                "Starless resolved {} target(s) at {}% maximum-health damage, {} light(s) swallowed",
                targets.size(), Math.round(fraction * 100.0F), getSwallowedCount());
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

    private static void knockTargetOutward(LivingEntity target, Vec3 core) {
        Vec3 outward = target.getBoundingBox().getCenter().subtract(core);
        if (outward.lengthSqr() <= 1.0E-6D) {
            outward = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            outward = outward.normalize();
        }
        target.setDeltaMovement(target.getDeltaMovement()
                .scale(0.35D)
                .add(outward.scale(1.15D))
                .add(0.0D, 0.30D, 0.0D));
        target.hasImpulse = true;
    }

    private AABB effectBounds(double radius) {
        return new AABB(
                this.getX() - radius, this.getY() - radius, this.getZ() - radius,
                this.getX() + radius, this.getY() + radius, this.getZ() + radius);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_CASTER_ID, 0);
        this.entityData.define(DATA_START_GAME_TICK, -1L);
        this.entityData.define(DATA_SWALLOWED, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.starvationDamageResolved = tag.getBoolean("StarvationDamageResolved");
        this.releaseDamageResolved = tag.getBoolean("ReleaseDamageResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SWALLOWED, tag.getInt("Swallowed"));
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
        tag.putBoolean("StarvationDamageResolved", this.starvationDamageResolved);
        tag.putBoolean("ReleaseDamageResolved", this.releaseDamageResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Swallowed", this.getSwallowedCount());
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

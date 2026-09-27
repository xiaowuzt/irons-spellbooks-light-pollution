package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * Server-owned anchor for the 17.2 second Funeral Nova timeline.
 *
 * <p>The visual is the attack: accretion, collapse, and hypernova each resolve
 * one synchronized damage pulse. No death-event hook is involved.</p>
 */
public final class FuneralNovaEntity extends Entity {
    private static int blackHoleStart() {
        return SpellConfig.funeralNovaBlackHoleStartTick;
    }

    private static int accretionStart() {
        return SpellConfig.funeralNovaAccretionStartTick;
    }

    private static int collapseStart() {
        return SpellConfig.funeralNovaCollapseStartTick;
    }

    private static int voidStart() {
        return SpellConfig.funeralNovaVoidStartTick;
    }

    private static int flashStart() {
        return SpellConfig.funeralNovaFlashStartTick;
    }

    private static int hypernovaStart() {
        return SpellConfig.funeralNovaHypernovaStartTick;
    }

    private static int afterglowStart() {
        return SpellConfig.funeralNovaAfterglowStartTick;
    }

    private static int fadeOutStart() {
        return SpellConfig.funeralNovaFadeOutStartTick;
    }

    public static final int LIFETIME_TICKS = 344;
    public static final int BLACK_HOLE_START_TICK = 48;
    public static final int ACCRETION_START_TICK = 68;
    public static final int COLLAPSE_START_TICK = 100;
    public static final int VOID_START_TICK = 120;
    public static final int FLASH_START_TICK = 150;
    public static final int HYPERNOVA_START_TICK = 164;
    public static final int AFTERGLOW_START_TICK = 250;
    public static final int FADE_OUT_START_TICK = 300;

    public static final int ACCRETION_DAMAGE_TICK = 68;
    public static final int COLLAPSE_DAMAGE_TICK = 110;
    public static final int HYPERNOVA_DAMAGE_TICK = 164;

    public static final double EFFECT_RADIUS = 12.0D;

    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            FuneralNovaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(
            FuneralNovaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            FuneralNovaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            FuneralNovaEntity.class, EntityDataSerializers.LONG);

    private UUID casterUuid;
    private boolean accretionDamageResolved;
    private boolean collapseDamageResolved;
    private boolean hypernovaDamageResolved;

    public FuneralNovaEntity(EntityType<? extends FuneralNovaEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, int seed) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_STAGE, 1);
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.setPos(center.x, center.y, center.z);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public int getStage() {
        return this.entityData.get(DATA_STAGE);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getTimelineAgeTicks() {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        if (startGameTick < 0L) {
            return Math.min(SpellConfig.funeralNovaLifetimeTicks, Math.max(0, this.tickCount));
        }
        long age = this.level().getGameTime() - startGameTick;
        return (int) Math.min(SpellConfig.funeralNovaLifetimeTicks, Math.max(0L, age));
    }

    public float getVisualAgeTicks(float partialTick) {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        float age = startGameTick < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - startGameTick) + partialTick;
        return Math.min(SpellConfig.funeralNovaLifetimeTicks, Math.max(0.0F, age));
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.entityData.set(DATA_STAGE, stageForTick(timelineTick));

            if (timelineTick >= blackHoleStart() && timelineTick < voidStart()) {
                pullNearby(serverLevel, timelineTick);
            }

            if (!this.accretionDamageResolved
                    && timelineTick >= SpellConfig.funeralNovaAccretionDamageTick) {
                this.accretionDamageResolved = true;
                resolveDamagePulse(serverLevel, (float) SpellConfig.funeralNovaAccretionDamageFraction, 7.0D, false);
            }
            if (!this.collapseDamageResolved
                    && timelineTick >= SpellConfig.funeralNovaCollapseDamageTick) {
                this.collapseDamageResolved = true;
                resolveDamagePulse(serverLevel, (float) SpellConfig.funeralNovaCollapseDamageFraction, 9.5D, false);
            }
            if (!this.hypernovaDamageResolved && timelineTick >= hypernovaStart()) {
                this.hypernovaDamageResolved = true;
                resolveDamagePulse(serverLevel, (float) SpellConfig.funeralNovaHypernovaDamageFraction, SpellConfig.funeralNovaEffectRadius, true);
            }
        }

        if (timelineTick >= SpellConfig.funeralNovaLifetimeTicks) {
            this.discard();
        }
    }

    private void pullNearby(ServerLevel level, int timelineTick) {
        LivingEntity caster = resolveCaster(level);
        Vec3 core = this.position().add(0.0D, 1.5D, 0.0D);
        float stageProgress = Math.min(1.0F,
                (timelineTick - blackHoleStart())
                        / (float) Math.max(1, voidStart() - blackHoleStart()));
        for (LivingEntity target : SpellConfig.limitTargets("funeralNova", level.getEntitiesOfClass(
                LivingEntity.class, effectBounds(SpellConfig.funeralNovaEffectRadius), entity -> canAffect(caster, entity)))) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 toCore = core.subtract(targetCenter);
            double distance = toCore.length();
            if (distance <= 0.05D || distance > SpellConfig.funeralNovaEffectRadius) {
                continue;
            }

            Vec3 direction = toCore.scale(1.0D / distance);
            double proximity = 1.0D - distance / SpellConfig.funeralNovaEffectRadius;
            double pull = 0.025D + proximity * (0.10D + stageProgress * 0.10D);
            Vec3 tangent = new Vec3(-direction.z, 0.0D, direction.x)
                    .scale(0.018D + proximity * 0.035D);
            target.setDeltaMovement(target.getDeltaMovement()
                    .scale(0.76D)
                    .add(direction.scale(pull))
                    .add(tangent));
            target.hasImpulse = true;
        }
    }

    private void resolveDamagePulse(ServerLevel level, float fraction, double radius,
                                    boolean knockback) {
        LivingEntity caster = resolveCaster(level);
        List<LivingEntity> targets = SpellConfig.limitTargets("funeralNova", level.getEntitiesOfClass(
                LivingEntity.class,
                effectBounds(radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(
                                this.position().add(0.0D, 1.5D, 0.0D)) <= radius * radius));
        DamageSource source = FuneralNovaDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, fraction);
            if (knockback && target.isAlive()) {
                knockTargetOutward(target);
            }
        }

        ExampleMod.LOGGER.debug(
                "Funeral Nova stage {} resolved {} targets at {}% maximum-health damage",
                this.getStage(), targets.size(), Math.round(fraction * 100.0F));
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

    private void knockTargetOutward(LivingEntity target) {
        Vec3 outward = target.getBoundingBox().getCenter()
                .subtract(this.position().add(0.0D, 1.5D, 0.0D));
        if (outward.lengthSqr() <= 1.0E-6D) {
            outward = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            outward = outward.normalize();
        }
        target.setDeltaMovement(target.getDeltaMovement()
                .scale(0.35D)
                .add(outward.scale(1.25D))
                .add(0.0D, 0.35D, 0.0D));
        target.hasImpulse = true;
    }

    private AABB effectBounds(double radius) {
        return new AABB(
                this.getX() - radius,
                this.getY() - radius,
                this.getZ() - radius,
                this.getX() + radius,
                this.getY() + radius,
                this.getZ() + radius);
    }

    private static int stageForTick(int tick) {
        if (tick < 16) {
            return 1;
        }
        if (tick < blackHoleStart()) {
            return 2;
        }
        if (tick < accretionStart()) {
            return 3;
        }
        if (tick < collapseStart()) {
            return 4;
        }
        if (tick < voidStart()) {
            return 5;
        }
        if (tick < flashStart()) {
            return 6;
        }
        if (tick < hypernovaStart()) {
            return 7;
        }
        if (tick < afterglowStart()) {
            return 8;
        }
        if (tick < fadeOutStart()) {
            return 9;
        }
        return 10;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SEED, 0);
        this.entityData.define(DATA_STAGE, 1);
        this.entityData.define(DATA_CASTER_ID, 0);
        this.entityData.define(DATA_START_GAME_TICK, -1L);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.accretionDamageResolved = tag.getBoolean("AccretionDamageResolved");
        this.collapseDamageResolved = tag.getBoolean("CollapseDamageResolved");
        this.hypernovaDamageResolved = tag.getBoolean("HypernovaDamageResolved");
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_STAGE, tag.getInt("Stage"));
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
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
        tag.putBoolean("AccretionDamageResolved", this.accretionDamageResolved);
        tag.putBoolean("CollapseDamageResolved", this.collapseDamageResolved);
        tag.putBoolean("HypernovaDamageResolved", this.hypernovaDamageResolved);
        tag.putInt("Seed", this.getSeed());
        tag.putInt("Stage", this.getStage());
        tag.putInt("CasterId", this.getCasterId());
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
    public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose) {
        return net.minecraft.world.entity.EntityDimensions.fixed(1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

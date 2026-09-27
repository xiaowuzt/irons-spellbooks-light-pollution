package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.spell.ModSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-owned timing, targeting, and damage controller for Eclipse Severance. */
public final class EclipseSeveranceEntity extends Entity {
    public static final int LIFETIME_TICKS = 30;
    public static final int DAMAGE_TICK = 6;
    public static final double ATTACK_RADIUS = 12.0D;
    public static final double VERTICAL_TOLERANCE = 5.0D;
    public static final float ATTACK_ANGLE_DEGREES = 150.0F;
    public static final int MAX_TARGETS = 32;

    private static final double HALF_ANGLE_COS =
            Math.cos(Math.toRadians(ATTACK_ANGLE_DEGREES * 0.5D));
    private static final EntityDataAccessor<Float> DATA_FACING_YAW = SynchedEntityData.defineId(
            EclipseSeveranceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            EclipseSeveranceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            EclipseSeveranceEntity.class, EntityDataSerializers.INT);

    private final Set<UUID> hitTargets = new HashSet<>();
    private UUID casterUuid;
    private float damage;
    private boolean damageResolved;

    public EclipseSeveranceEntity(
            EntityType<? extends EclipseSeveranceEntity> entityType,
            Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, float facingYaw, int seed, float damage) {
        this.casterUuid = caster.getUUID();
        this.damage = Math.max(0.0F, damage);
        this.entityData.set(DATA_FACING_YAW, facingYaw);
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.setPos(caster.getX(), caster.getY(), caster.getZ());
    }

    public float getFacingYaw() {
        return this.entityData.get(DATA_FACING_YAW);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel serverLevel
                && !this.damageResolved
                && this.tickCount >= SpellConfig.eclipseDamageTick) {
            this.damageResolved = true;
            resolveDamage(serverLevel);
        }

        if (this.tickCount >= SpellConfig.eclipseLifetimeTicks) {
            this.discard();
        }
    }

    private void resolveDamage(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        if (caster == null || !caster.isAlive()) {
            return;
        }

        Vec3 forward = horizontalForward();
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                attackBounds(),
                target -> canTarget(caster, target));
        candidates.sort(Comparator.comparingDouble(this::distanceToSqr));

        SpellDamageSource source = ModSpells.ECLIPSE_SEVERANCE.get().getDamageSource(this, caster);
        int successfulHits = 0;
        for (LivingEntity target : candidates) {
            if (successfulHits >= SpellConfig.eclipseMaxTargets) {
                break;
            }
            if (!isInsideAttackVolume(target, forward) || !hasLineOfSight(level, caster, target)) {
                continue;
            }
            if (this.hitTargets.contains(target.getUUID())) {
                continue;
            }

            if (DamageSources.applyDamage(target, this.damage, source)) {
                this.hitTargets.add(target.getUUID());
                successfulHits++;
                knockTargetAway(target, forward);
            }
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

    private boolean canTarget(LivingEntity caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }
        return !caster.isAlliedTo(target) && !target.isAlliedTo(caster);
    }

    private boolean isInsideAttackVolume(LivingEntity target, Vec3 forward) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double verticalOffset = Math.abs(targetCenter.y - (this.getY() + 1.0D));
        if (verticalOffset > SpellConfig.eclipseVerticalTolerance) {
            return false;
        }

        double offsetX = targetCenter.x - this.getX();
        double offsetZ = targetCenter.z - this.getZ();
        double horizontalDistanceSqr = offsetX * offsetX + offsetZ * offsetZ;
        if (horizontalDistanceSqr > SpellConfig.eclipseAttackRadius * SpellConfig.eclipseAttackRadius) {
            return false;
        }
        if (horizontalDistanceSqr <= 1.0E-6D) {
            return true;
        }

        double inverseDistance = 1.0D / Math.sqrt(horizontalDistanceSqr);
        double facingDot = (offsetX * forward.x + offsetZ * forward.z) * inverseDistance;
        return facingDot >= Math.cos(Math.toRadians(SpellConfig.eclipseAttackAngleDegrees * 0.5D));
    }

    private boolean hasLineOfSight(ServerLevel level, LivingEntity caster, LivingEntity target) {
        double eyeOffset = Math.max(0.6D, Math.min(2.4D, caster.getEyeHeight()));
        Vec3 start = this.position().add(0.0D, eyeOffset, 0.0D);
        Vec3 end = target.getEyePosition();
        HitResult result = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster));
        return result.getType() == HitResult.Type.MISS;
    }

    private Vec3 horizontalForward() {
        double yawRadians = Math.toRadians(this.getFacingYaw());
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private void knockTargetAway(LivingEntity target, Vec3 forward) {
        double knockbackX = this.getX() - target.getX();
        double knockbackZ = this.getZ() - target.getZ();
        if (knockbackX * knockbackX + knockbackZ * knockbackZ <= 1.0E-6D) {
            knockbackX = -forward.x;
            knockbackZ = -forward.z;
        }
        target.knockback(1.25D, knockbackX, knockbackZ);
    }

    private AABB attackBounds() {
        return new AABB(
                this.getX() - SpellConfig.eclipseAttackRadius,
                this.getY() - SpellConfig.eclipseVerticalTolerance,
                this.getZ() - SpellConfig.eclipseAttackRadius,
                this.getX() + SpellConfig.eclipseAttackRadius,
                this.getY() + SpellConfig.eclipseVerticalTolerance,
                this.getZ() + SpellConfig.eclipseAttackRadius);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_FACING_YAW, 0.0F);
        this.entityData.define(DATA_SEED, 0);
        this.entityData.define(DATA_CASTER_ID, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.damage = tag.getFloat("Damage");
        this.damageResolved = tag.getBoolean("DamageResolved");
        this.entityData.set(DATA_FACING_YAW, tag.getFloat("FacingYaw"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putFloat("Damage", this.damage);
        tag.putBoolean("DamageResolved", this.damageResolved);
        tag.putFloat("FacingYaw", this.getFacingYaw());
        tag.putInt("Seed", this.getSeed());
        tag.putInt("CasterId", this.getCasterId());
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

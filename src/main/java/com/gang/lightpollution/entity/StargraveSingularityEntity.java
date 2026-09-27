package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side anchor for the Stargrave Singularity visual and gameplay effect.
 * It pulls all other living entities in a 20-block sphere and resolves one
 * 90%-of-max-health collapse at tick 160.
 */
public class StargraveSingularityEntity extends Entity {
    public static final int LIFETIME_TICKS = 200;
    public static final int DAMAGE_TICK = 160;
    public static final double EFFECT_RADIUS = 20.0D;
    public static final float MAX_VISUAL_RADIUS = 8.5F;

    private static final double PROJECTILE_CORE_RADIUS = 1.75D;
    private static final double PROJECTILE_QUERY_PADDING = 8.0D;
    private static final TagKey<EntityType<?>> ADDITIONAL_PROJECTILES = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "stargrave_singularity_projectiles"));
    private static final TagKey<EntityType<?>> PROJECTILE_IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "stargrave_singularity_projectile_immune"));

    private static final EntityDataAccessor<Float> DATA_VISUAL_RADIUS = SynchedEntityData.defineId(
            StargraveSingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            StargraveSingularityEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean collapsed;
    private int lastProjectileAbsorbSoundTick = Integer.MIN_VALUE;

    public StargraveSingularityEntity(
            EntityType<? extends StargraveSingularityEntity> entityType,
            Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center) {
        this.casterUuid = caster.getUUID();
        this.setPos(center.x, center.y, center.z);
        this.entityData.set(DATA_VISUAL_RADIUS, 0.5F);
        this.entityData.set(DATA_PHASE, 0);
    }

    public float getVisualRadius() {
        return this.entityData.get(DATA_VISUAL_RADIUS);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public float getCollapseProgress() {
        return Math.min(1.0F, this.tickCount / (float) SpellConfig.stargraveDamageTick);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel serverLevel) {
            updateVisualState();
            pullNearby(serverLevel);
            pullNearbyProjectiles(serverLevel);

            if (!this.collapsed && this.tickCount >= SpellConfig.stargraveDamageTick) {
                this.collapsed = true;
                collapse(serverLevel);
            }

            if (this.tickCount % 4 == 0) {
                spawnAmbientParticles(serverLevel);
            }
        }

        if (this.tickCount >= SpellConfig.stargraveLifetimeTicks) {
            this.discard();
        }
    }

    private void updateVisualState() {
        float radius;
        int phase;
        if (this.tickCount < 20) {
            radius = lerp(this.tickCount / 20.0F, 0.5F, MAX_VISUAL_RADIUS);
            phase = 0;
        } else if (this.tickCount < SpellConfig.stargraveDamageTick) {
            radius = MAX_VISUAL_RADIUS;
            phase = 1;
        } else {
            radius = lerp((this.tickCount - SpellConfig.stargraveDamageTick) / 40.0F, MAX_VISUAL_RADIUS, 0.0F);
            phase = 2;
        }
        this.entityData.set(DATA_VISUAL_RADIUS, Math.max(0.0F, radius));
        this.entityData.set(DATA_PHASE, phase);
    }

    private void pullNearby(ServerLevel level) {
        for (LivingEntity target : SpellConfig.limitTargets("stargraveSingularity", level.getEntitiesOfClass(
                LivingEntity.class, effectBounds(), this::canAffect))) {
            Vec3 targetCenter = new Vec3(
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ());
            Vec3 toCenter = this.position().subtract(targetCenter);
            double distance = toCenter.length();
            if (distance <= 0.001D || distance > SpellConfig.stargraveEffectRadius) {
                continue;
            }

            Vec3 direction = toCenter.scale(1.0D / distance);
            double proximity = 1.0D - distance / SpellConfig.stargraveEffectRadius;
            double pullStrength = 0.025D + proximity * 0.16D;
            Vec3 tangent = new Vec3(-direction.z, 0.0D, direction.x)
                    .scale(0.012D + proximity * 0.045D);
            Vec3 velocity = target.getDeltaMovement()
                    .scale(0.78D)
                    .add(direction.scale(pullStrength))
                    .add(tangent);
            target.setDeltaMovement(velocity);
            target.hasImpulse = true;

            // Keep entities from escaping the core due to a large opposing impulse.
            if (distance < 2.0D) {
                target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(0.08D)));
            }
        }
    }

    private void pullNearbyProjectiles(ServerLevel level) {
        List<Entity> projectiles = List.copyOf(level.getEntities(
                this,
                effectBounds().inflate(PROJECTILE_QUERY_PADDING),
                this::canAbsorbProjectile));

        for (Entity projectile : projectiles) {
            Vec3 projectileCenter = projectile.getBoundingBox().getCenter();
            Vec3 toCenter = this.position().subtract(projectileCenter);
            double distance = toCenter.length();
            double captureRadius = projectileCaptureRadius(projectile);
            double captureRadiusSqr = captureRadius * captureRadius;
            Vec3 previousCenter = projectile.getPosition(0.0F)
                    .add(0.0D, projectile.getBbHeight() * 0.5D, 0.0D);
            if (distance <= captureRadius
                    || (projectile.tickCount > 1
                    && distanceToSegmentSqr(
                            this.position(),
                            previousCenter,
                            projectileCenter) <= captureRadiusSqr)) {
                absorbProjectile(level, projectile, projectileCenter);
                continue;
            }
            if (distance > SpellConfig.stargraveEffectRadius) {
                continue;
            }

            Vec3 direction = toCenter.scale(1.0D / Math.max(distance, 0.001D));
            double proximity = 1.0D - distance / SpellConfig.stargraveEffectRadius;
            Vec3 tangent = new Vec3(-direction.z, 0.0D, direction.x);
            if (tangent.lengthSqr() < 1.0E-6D) {
                tangent = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                tangent = tangent.normalize();
            }

            double inwardSpeed = 0.45D + proximity * 1.65D;
            double orbitSpeed = (1.0D - proximity) * 0.24D;
            Vec3 desiredVelocity = direction.scale(inwardSpeed)
                    .add(tangent.scale(orbitSpeed));
            Vec3 velocity = projectile.getDeltaMovement()
                    .scale(0.30D)
                    .add(desiredVelocity.scale(0.70D));

            // Catch fast projectiles whose next movement step crosses the core.
            if (distanceToSegmentSqr(
                    this.position(),
                    projectileCenter,
                    projectileCenter.add(velocity)) <= captureRadiusSqr) {
                absorbProjectile(level, projectile, projectileCenter);
                continue;
            }

            projectile.setDeltaMovement(velocity);
            projectile.hasImpulse = true;
        }
    }

    private boolean canAffect(LivingEntity target) {
        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target.getUUID().equals(this.casterUuid)) {
            return false;
        }
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }

        Vec3 offset = target.position().subtract(this.position());
        return offset.lengthSqr() <= SpellConfig.stargraveEffectRadius * SpellConfig.stargraveEffectRadius;
    }

    private boolean canAbsorbProjectile(Entity entity) {
        if (entity == this || entity.isRemoved() || !entity.isAlive()) {
            return false;
        }
        if (entity.getType().is(PROJECTILE_IMMUNE)) {
            return false;
        }
        if (!(entity instanceof Projectile) && !entity.getType().is(ADDITIONAL_PROJECTILES)) {
            return false;
        }

        return true;
    }

    public boolean tryAbsorbProjectileImpact(
            ServerLevel level,
            Entity projectile,
            Vec3 impactLocation) {
        if (!canAbsorbProjectile(projectile)) {
            return false;
        }

        Vec3 projectileCenter = projectile.getBoundingBox().getCenter();
        double captureRadius = projectileCaptureRadius(projectile);
        if (distanceToSegmentSqr(this.position(), projectileCenter, impactLocation)
                > captureRadius * captureRadius) {
            return false;
        }

        absorbProjectile(level, projectile, projectileCenter);
        return true;
    }

    private static double projectileCaptureRadius(Entity projectile) {
        return PROJECTILE_CORE_RADIUS
                + Math.min(0.75D, projectile.getBbWidth() * 0.5D);
    }

    private void absorbProjectile(ServerLevel level, Entity projectile, Vec3 projectileCenter) {
        projectile.discard();
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                projectileCenter.x, projectileCenter.y, projectileCenter.z,
                7, 0.18D, 0.18D, 0.18D, 0.04D);
        if (this.lastProjectileAbsorbSoundTick != this.tickCount) {
            this.lastProjectileAbsorbSoundTick = this.tickCount;
            level.playSound(
                    null,
                    projectileCenter.x, projectileCenter.y, projectileCenter.z,
                    SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.AMBIENT,
                    0.16F,
                    0.55F + this.random.nextFloat() * 0.15F);
        }
    }

    private void collapse(ServerLevel level) {
        Map<UUID, CollapseTarget> targets = new LinkedHashMap<>();
        for (LivingEntity target : List.copyOf(SpellConfig.limitTargets("stargraveSingularity", level.getEntitiesOfClass(
                LivingEntity.class, effectBounds(), this::canAffect)))) {
            targets.putIfAbsent(target.getUUID(), CollapseTarget.capture(target));
        }

        int applied = 0;
        int skipped = 0;
        DamageSource source = createDamageSource(level);
        for (CollapseTarget snapshot : targets.values()) {
            LivingEntity target = snapshot.target();
            if (!canAffect(target)) {
                skipped++;
                continue;
            }

            applyTrueDamage(snapshot, source);
            applied++;
        }

        ExampleMod.LOGGER.debug(
                "Stargrave Singularity collapse resolved {} candidates: {} applied, {} skipped",
                targets.size(), applied, skipped);

        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                this.getX(), this.getY(), this.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private DamageSource createDamageSource(ServerLevel level) {
        Entity caster = this.casterUuid == null ? null : level.getEntity(this.casterUuid);
        return StargraveSingularityDamage.source(level, this, caster);
    }

    private void applyTrueDamage(CollapseTarget snapshot, DamageSource source) {
        LivingEntity target = snapshot.target();
        target.invulnerableTime = 0;

        // Let vanilla/Forge perform combat attribution, events and death setup.
        target.hurt(source, snapshot.damage());
        target.invulnerableTime = 0;

        if (snapshot.desiredHealth() <= 0.0F) {
            target.setAbsorptionAmount(0.0F);
            target.setHealth(0.0F);
            // This remains safe if hurt() already called die(); LivingEntity.die
            // guards the dead flag and preserves the normal loot/death events.
            target.die(source);
            return;
        }

        // Custom bosses frequently cancel hurt() for shields, phases, or dodge
        // windows. Correct the health value after that hook so this spell stays
        // true damage while still retaining the source when a normal hit works.
        if (!target.isDeadOrDying()) {
            target.setAbsorptionAmount(0.0F);
            target.setHealth(snapshot.desiredHealth());
        }
    }

    private AABB effectBounds() {
        return new AABB(
                this.getX() - SpellConfig.stargraveEffectRadius,
                this.getY() - SpellConfig.stargraveEffectRadius,
                this.getZ() - SpellConfig.stargraveEffectRadius,
                this.getX() + SpellConfig.stargraveEffectRadius,
                this.getY() + SpellConfig.stargraveEffectRadius,
                this.getZ() + SpellConfig.stargraveEffectRadius);
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-8D) {
            return point.distanceToSqr(start);
        }

        double amount = point.subtract(start).dot(segment) / lengthSqr;
        amount = Math.max(0.0D, Math.min(1.0D, amount));
        return point.distanceToSqr(start.add(segment.scale(amount)));
    }

    private record CollapseTarget(
            LivingEntity target,
            float damage,
            float desiredHealth) {
        private static CollapseTarget capture(LivingEntity target) {
            float maxHealth = target.getMaxHealth();
            float healthBefore = target.getHealth();
            float damage = Math.max(0.0F, (float) (maxHealth * SpellConfig.stargraveDamageFraction));
            float desiredHealth = Math.max(0.0F, healthBefore - damage);
            return new CollapseTarget(target, damage, desiredHealth);
        }
    }

    private void spawnAmbientParticles(ServerLevel level) {
        level.sendParticles(
                ParticleTypes.PORTAL,
                this.getX(), this.getY(), this.getZ(),
                8, 1.5D, 1.5D, 1.5D, 0.12D);
        level.sendParticles(
                ParticleTypes.DRAGON_BREATH,
                this.getX(), this.getY(), this.getZ(),
                3, 0.8D, 0.8D, 0.8D, 0.01D);
    }

    private static float lerp(float amount, float from, float to) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        return from + (to - from) * clamped;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VISUAL_RADIUS, 0.5F);
        this.entityData.define(DATA_PHASE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.collapsed = tag.getBoolean("Collapsed");
        this.entityData.set(DATA_VISUAL_RADIUS,
                tag.contains("VisualRadius") ? tag.getFloat("VisualRadius") : 0.5F);
        this.entityData.set(DATA_PHASE, tag.getInt("Phase"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putBoolean("Collapsed", this.collapsed);
        tag.putFloat("VisualRadius", this.getVisualRadius());
        tag.putInt("Phase", this.getPhase());
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
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

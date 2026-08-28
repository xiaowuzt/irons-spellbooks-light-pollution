package com.gang.lightpollution.entity;

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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned timeline for Chromatic Accretion.
 *
 * <p>The effect forms for 24 ticks, orbits enemies through four four-percent
 * maximum-health pulses, then resolves a 34% collapse with stored caster
 * damage and projectile mass.</p>
 */
public final class ChromaticAccretionEntity extends Entity {
    public static final int LIFETIME_TICKS = 170;
    public static final int FORMATION_END_TICK = 24;
    public static final int COLLAPSE_TICK = 136;
    public static final double EFFECT_RADIUS = 12.0D;
    public static final float MAX_VISUAL_RADIUS = 6.75F;

    private static final int[] PULSE_TICKS = {36, 64, 92, 120};
    private static final float PULSE_DAMAGE_FRACTION = 0.044F;
    private static final float COLLAPSE_DAMAGE_FRACTION = 0.31F;
    private static final float STORED_DAMAGE_SHARE = 0.30F;
    private static final float STORED_DAMAGE_CAP_FRACTION = 0.20F;
    private static final float PROJECTILE_DAMAGE_FRACTION = 0.01F;
    private static final int MAX_PROJECTILE_CHARGE = 6;
    private static final double PROJECTILE_CORE_RADIUS = 1.35D;
    private static final double PROJECTILE_QUERY_PADDING = 6.0D;

    private static final TagKey<EntityType<?>> ADDITIONAL_PROJECTILES = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    ExampleMod.MODID, "chromatic_accretion_projectiles"));
    private static final TagKey<EntityType<?>> PROJECTILE_IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    ExampleMod.MODID, "chromatic_accretion_projectile_immune"));

    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PROJECTILE_CHARGE = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VISUAL_RADIUS = SynchedEntityData.defineId(
            ChromaticAccretionEntity.class, EntityDataSerializers.FLOAT);

    private final Map<UUID, Float> storedCasterDamage = new HashMap<>();
    private UUID casterUuid;
    private UUID targetUuid;
    private int resolvedPulseMask;
    private boolean collapsed;
    private int lastProjectileAbsorbSoundTick = Integer.MIN_VALUE;

    public ChromaticAccretionEntity(
            EntityType<? extends ChromaticAccretionEntity> entityType,
            Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, LivingEntity followedTarget, Vec3 center, int seed) {
        this.casterUuid = caster.getUUID();
        this.targetUuid = followedTarget == null ? null : followedTarget.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_TARGET_ID, followedTarget == null ? 0 : followedTarget.getId());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_AGE, 0);
        this.entityData.set(DATA_PHASE, 0);
        this.entityData.set(DATA_PROJECTILE_CHARGE, 0);
        this.entityData.set(DATA_VISUAL_RADIUS, 0.35F);
        this.setPos(center.x, center.y, center.z);
    }

    public int getEffectAge() {
        return this.entityData.get(DATA_AGE);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getTargetId() {
        return this.entityData.get(DATA_TARGET_ID);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public int getProjectileCharge() {
        return this.entityData.get(DATA_PROJECTILE_CHARGE);
    }

    public float getVisualRadius() {
        return this.entityData.get(DATA_VISUAL_RADIUS);
    }

    public float getVisualAgeTicks(float partialTick) {
        return Math.min(LIFETIME_TICKS, Math.max(0.0F, getEffectAge() + partialTick));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel serverLevel) {
            int age = Math.min(LIFETIME_TICKS, this.tickCount);
            this.entityData.set(DATA_AGE, age);
            followTarget(serverLevel);
            updateVisualState(age);

            if (age >= FORMATION_END_TICK && age < COLLAPSE_TICK) {
                orbitNearby(serverLevel);
                pullNearbyProjectiles(serverLevel);
                resolvePendingPulses(serverLevel, age);
            }

            if (!this.collapsed && age >= COLLAPSE_TICK) {
                this.collapsed = true;
                collapse(serverLevel);
            }

            if (age % 5 == 0) {
                spawnAmbientParticles(serverLevel, age);
            }
        }

        if (this.tickCount >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    private void followTarget(ServerLevel level) {
        if (this.targetUuid == null) {
            return;
        }

        Entity target = level.getEntity(this.targetUuid);
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            this.targetUuid = null;
            this.entityData.set(DATA_TARGET_ID, 0);
            return;
        }

        this.entityData.set(DATA_TARGET_ID, living.getId());
        Vec3 center = living.getBoundingBox().getCenter();
        this.setPos(center.x, center.y, center.z);
    }

    private void updateVisualState(int age) {
        float radius;
        int phase;
        if (age < FORMATION_END_TICK) {
            radius = lerp(age / (float) FORMATION_END_TICK, 0.35F, MAX_VISUAL_RADIUS);
            phase = 0;
        } else if (age < COLLAPSE_TICK) {
            radius = MAX_VISUAL_RADIUS;
            phase = 1;
        } else {
            radius = lerp(
                    (age - COLLAPSE_TICK) / (float) (LIFETIME_TICKS - COLLAPSE_TICK),
                    MAX_VISUAL_RADIUS,
                    0.0F);
            phase = 2;
        }
        this.entityData.set(DATA_VISUAL_RADIUS, Math.max(0.0F, radius));
        this.entityData.set(DATA_PHASE, phase);
    }

    private void resolvePendingPulses(ServerLevel level, int age) {
        for (int index = 0; index < PULSE_TICKS.length; index++) {
            int bit = 1 << index;
            if ((this.resolvedPulseMask & bit) == 0 && age >= PULSE_TICKS[index]) {
                this.resolvedPulseMask |= bit;
                resolveDamagePulse(level, PULSE_DAMAGE_FRACTION);
            }
        }
    }

    private void resolveDamagePulse(ServerLevel level, float damageFraction) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = ChromaticAccretionDamage.source(level, this, caster);
        for (LivingEntity target : currentTargets(level, caster)) {
            applyRespectfulTrueDamage(target, source, target.getMaxHealth() * damageFraction);
        }
    }

    private void collapse(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = ChromaticAccretionDamage.source(level, this, caster);
        float projectileFraction = getProjectileCharge() * PROJECTILE_DAMAGE_FRACTION;
        int applied = 0;
        for (LivingEntity target : currentTargets(level, caster)) {
            float storedDamage = this.storedCasterDamage.getOrDefault(target.getUUID(), 0.0F);
            float damage = target.getMaxHealth()
                    * (COLLAPSE_DAMAGE_FRACTION + projectileFraction)
                    + storedDamage;
            if (applyRespectfulTrueDamage(target, source, damage)) {
                applied++;
            }
        }

        ExampleMod.LOGGER.debug(
                "Chromatic Accretion collapsed on {} targets with {} projectile charge",
                applied, getProjectileCharge());
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                this.getX(), this.getY(), this.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(
                null,
                this.blockPosition(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS,
                1.3F,
                0.55F);
    }

    /** Records final post-mitigation damage dealt by this effect's caster. */
    public void recordCasterDamage(LivingEntity target, DamageSource source, float actualDamage) {
        if (!(this.level() instanceof ServerLevel level)
                || getEffectAge() < FORMATION_END_TICK
                || getEffectAge() >= COLLAPSE_TICK
                || actualDamage <= 0.0F
                || source.getDirectEntity() instanceof ChromaticAccretionEntity
                || source.getEntity() == this) {
            return;
        }

        LivingEntity caster = resolveCaster(level);
        if (!canAffect(caster, target)
                || !isInsideEffect(target)
                || !isCasterDamage(source)) {
            return;
        }

        float creditedDamage = Math.min(actualDamage, Math.max(0.0F, target.getHealth()));
        float cap = target.getMaxHealth() * STORED_DAMAGE_CAP_FRACTION;
        float addedDamage = Math.min(cap, creditedDamage * STORED_DAMAGE_SHARE);
        this.storedCasterDamage.merge(
                target.getUUID(),
                addedDamage,
                (current, added) -> Math.min(cap, current + added));
    }

    private boolean isCasterDamage(DamageSource source) {
        if (isCaster(source.getEntity()) || isCaster(source.getDirectEntity())) {
            return true;
        }

        Entity direct = source.getDirectEntity();
        return direct instanceof Projectile projectile && isCaster(projectile.getOwner());
    }

    private boolean isCaster(Entity entity) {
        return entity != null
                && this.casterUuid != null
                && this.casterUuid.equals(entity.getUUID());
    }

    private void orbitNearby(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 core = this.position();
        for (LivingEntity target : currentTargets(level, caster)) {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 horizontal = targetCenter.subtract(core).multiply(1.0D, 0.0D, 1.0D);
            double horizontalDistance = horizontal.length();
            Vec3 radial = horizontalDistance <= 1.0E-4D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : horizontal.scale(1.0D / horizontalDistance);
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);

            double desiredRadius = Math.min(6.5D, 3.5D + target.getBbWidth() * 0.65D);
            double radialCorrection = (desiredRadius - horizontalDistance) * 0.035D;
            double tangentSpeed = 0.10D + 0.08D * (1.0D - Math.min(1.0D,
                    horizontalDistance / EFFECT_RADIUS));
            double verticalCorrection = (core.y - targetCenter.y) * 0.025D;
            double displacementScale = isBossLike(target) ? 0.28D : 1.0D;

            Vec3 acceleration = radial.scale(radialCorrection)
                    .add(tangent.scale(tangentSpeed))
                    .add(0.0D, verticalCorrection, 0.0D)
                    .scale(displacementScale);
            target.setDeltaMovement(target.getDeltaMovement()
                    .scale(isBossLike(target) ? 0.94D : 0.82D)
                    .add(acceleration));
            target.hasImpulse = true;
        }
    }

    private void pullNearbyProjectiles(ServerLevel level) {
        for (Entity projectile : List.copyOf(level.getEntities(
                this,
                effectBounds().inflate(PROJECTILE_QUERY_PADDING),
                this::canAbsorbProjectile))) {
            Vec3 center = projectile.getBoundingBox().getCenter();
            Vec3 toCore = this.position().subtract(center);
            double distance = toCore.length();
            double captureRadius = projectileCaptureRadius(projectile);
            if (distance <= captureRadius) {
                absorbProjectile(level, projectile, center);
                continue;
            }
            if (distance > EFFECT_RADIUS) {
                continue;
            }

            Vec3 direction = toCore.scale(1.0D / Math.max(distance, 0.001D));
            Vec3 tangent = new Vec3(-direction.z, 0.0D, direction.x);
            if (tangent.lengthSqr() > 1.0E-6D) {
                tangent = tangent.normalize();
            }
            double proximity = 1.0D - distance / EFFECT_RADIUS;
            Vec3 desiredVelocity = direction.scale(0.40D + proximity * 1.2D)
                    .add(tangent.scale(0.10D + (1.0D - proximity) * 0.18D));
            Vec3 velocity = projectile.getDeltaMovement().scale(0.28D)
                    .add(desiredVelocity.scale(0.72D));
            if (distanceToSegmentSqr(
                    this.position(), center, center.add(velocity)) <= captureRadius * captureRadius) {
                absorbProjectile(level, projectile, center);
                continue;
            }

            projectile.setDeltaMovement(velocity);
            projectile.hasImpulse = true;
        }
    }

    public boolean tryAbsorbProjectileImpact(
            ServerLevel level,
            Entity projectile,
            Vec3 impactLocation) {
        if (getEffectAge() < FORMATION_END_TICK
                || getEffectAge() >= COLLAPSE_TICK
                || !canAbsorbProjectile(projectile)) {
            return false;
        }

        Vec3 center = projectile.getBoundingBox().getCenter();
        double captureRadius = projectileCaptureRadius(projectile);
        if (distanceToSegmentSqr(this.position(), center, impactLocation)
                > captureRadius * captureRadius) {
            return false;
        }

        absorbProjectile(level, projectile, center);
        return true;
    }

    private boolean canAbsorbProjectile(Entity entity) {
        if (entity == this || entity.isRemoved() || !entity.isAlive()
                || entity.getType().is(PROJECTILE_IMMUNE)
                || (!(entity instanceof Projectile)
                && !entity.getType().is(ADDITIONAL_PROJECTILES))) {
            return false;
        }

        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (isCaster(owner)) {
                return false;
            }
            LivingEntity caster = resolveCasterIfPresent();
            if (owner instanceof LivingEntity livingOwner
                    && caster != null
                    && (caster.isAlliedTo(livingOwner) || livingOwner.isAlliedTo(caster))) {
                return false;
            }
        }
        return true;
    }

    private void absorbProjectile(ServerLevel level, Entity projectile, Vec3 center) {
        projectile.discard();
        if (getProjectileCharge() < MAX_PROJECTILE_CHARGE) {
            this.entityData.set(DATA_PROJECTILE_CHARGE, getProjectileCharge() + 1);
        }
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                center.x, center.y, center.z,
                9, 0.20D, 0.20D, 0.20D, 0.05D);
        if (this.lastProjectileAbsorbSoundTick != this.tickCount) {
            this.lastProjectileAbsorbSoundTick = this.tickCount;
            level.playSound(
                    null,
                    center.x, center.y, center.z,
                    SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.AMBIENT,
                    0.18F,
                    0.65F + this.random.nextFloat() * 0.12F);
        }
    }

    private List<LivingEntity> currentTargets(ServerLevel level, LivingEntity caster) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                effectBounds(),
                target -> canAffect(caster, target) && isInsideEffect(target));
    }

    private boolean canAffect(LivingEntity caster, LivingEntity target) {
        if (!target.isAlive() || target.isRemoved()
                || (this.casterUuid != null && this.casterUuid.equals(target.getUUID()))
                || target == caster
                || target instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        if (target instanceof Player player
                && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        return caster == null
                || (!caster.isAlliedTo(target) && !target.isAlliedTo(caster));
    }

    private boolean isInsideEffect(LivingEntity target) {
        return target.getBoundingBox().getCenter().distanceToSqr(this.position())
                <= EFFECT_RADIUS * EFFECT_RADIUS;
    }

    private LivingEntity resolveCaster(ServerLevel level) {
        Entity byId = level.getEntity(getCasterId());
        if (byId instanceof LivingEntity living && isCaster(living)) {
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

    private LivingEntity resolveCasterIfPresent() {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        return resolveCaster(level);
    }

    private static boolean applyRespectfulTrueDamage(
            LivingEntity target,
            DamageSource source,
            float damage) {
        if (damage <= 0.0F || target.isInvulnerableTo(source)) {
            return false;
        }
        return target.hurt(source, damage);
    }

    private static boolean isBossLike(LivingEntity target) {
        return target instanceof EnderDragon
                || target instanceof WitherBoss
                || target.getMaxHealth() >= 300.0F;
    }

    private AABB effectBounds() {
        return new AABB(
                this.getX() - EFFECT_RADIUS,
                this.getY() - EFFECT_RADIUS,
                this.getZ() - EFFECT_RADIUS,
                this.getX() + EFFECT_RADIUS,
                this.getY() + EFFECT_RADIUS,
                this.getZ() + EFFECT_RADIUS);
    }

    private static double projectileCaptureRadius(Entity projectile) {
        return PROJECTILE_CORE_RADIUS + Math.min(0.65D, projectile.getBbWidth() * 0.5D);
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

    private void spawnAmbientParticles(ServerLevel level, int age) {
        if (age >= COLLAPSE_TICK) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    this.getX(), this.getY(), this.getZ(),
                    5, 0.45D, 0.45D, 0.45D, 0.02D);
            return;
        }
        level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY(), this.getZ(),
                age < FORMATION_END_TICK ? 4 : 8,
                0.8D, 0.8D, 0.8D, 0.05D);
    }

    private static float lerp(float amount, float from, float to) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        return from + (to - from) * clamped;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_AGE, 0);
        this.entityData.define(DATA_PHASE, 0);
        this.entityData.define(DATA_CASTER_ID, 0);
        this.entityData.define(DATA_TARGET_ID, 0);
        this.entityData.define(DATA_SEED, 0);
        this.entityData.define(DATA_PROJECTILE_CHARGE, 0);
        this.entityData.define(DATA_VISUAL_RADIUS, 0.35F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.targetUuid = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        this.resolvedPulseMask = tag.getInt("ResolvedPulseMask");
        this.collapsed = tag.getBoolean("Collapsed");
        this.entityData.set(DATA_AGE, tag.getInt("Age"));
        this.entityData.set(DATA_PHASE, tag.getInt("Phase"));
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_TARGET_ID, tag.getInt("TargetId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_PROJECTILE_CHARGE, tag.getInt("ProjectileCharge"));
        this.entityData.set(DATA_VISUAL_RADIUS,
                tag.contains("VisualRadius") ? tag.getFloat("VisualRadius") : 0.35F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        if (this.targetUuid != null) {
            tag.putUUID("Target", this.targetUuid);
        }
        tag.putInt("ResolvedPulseMask", this.resolvedPulseMask);
        tag.putBoolean("Collapsed", this.collapsed);
        tag.putInt("Age", getEffectAge());
        tag.putInt("Phase", getPhase());
        tag.putInt("CasterId", getCasterId());
        tag.putInt("TargetId", getTargetId());
        tag.putInt("Seed", getSeed());
        tag.putInt("ProjectileCharge", getProjectileCharge());
        tag.putFloat("VisualRadius", getVisualRadius());
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

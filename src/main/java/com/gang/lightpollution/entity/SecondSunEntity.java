package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.api.SecondSunParams;
import com.gang.lightpollution.fx.SecondSunShape;
import com.gang.lightpollution.fx.SecondSunSource;
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
 * Server-owned anchor for Second Sun.
 *
 * <p>A star the size of a sun climbs out of the horizon, crosses to the zenith,
 * swells, and goes supernova. It is deliberately the largest and slowest thing in
 * the set: nothing else in the mod changes where the light in the world is coming
 * from, and that only reads if there is time to notice it.</p>
 *
 * <p>The disc is placed by bearing and altitude rather than by a world position,
 * because at this scale it belongs to the sky rather than to a point on the
 * ground. Damage, though, is anchored to the aimed point: this is still a spell
 * cast at something.</p>
 */
public final class SecondSunEntity extends Entity implements SecondSunSource {
    private static final String CONFIG_ID = "secondSun";

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

    private static int configuredPhaseThree() {
        return SpellConfig.phaseTick(CONFIG_ID, 3);
    }
    // The form lives in SecondSunShape, which the renderer and the public API both read, so there
    // is one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = SecondSunParams.SPELL_LIFETIME_TICKS;


    /** Radius of the scorched area on the ground, in blocks. */
    public static final double EFFECT_RADIUS = 26.0D;
    /** Max-health fraction per scorch tick while it is overhead. */
    private static final float SCORCH_DAMAGE_FRACTION = 0.018F;
    /** Ticks between scorch applications. */
    private static final int SCORCH_INTERVAL_TICKS = 15;
    /** Max-health fraction of the supernova itself. */
    private static final float NOVA_DAMAGE_FRACTION = 0.61F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            SecondSunEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            SecondSunEntity.class, EntityDataSerializers.LONG);
    /** Bearing the disc rises on, in radians. */
    private static final EntityDataAccessor<Float> DATA_BEARING = SynchedEntityData.defineId(
            SecondSunEntity.class, EntityDataSerializers.FLOAT);

    private UUID casterUuid;
    private boolean novaResolved;

    public SecondSunEntity(EntityType<? extends SecondSunEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, float bearing) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_BEARING, bearing);
        this.setPos(center.x, center.y, center.z);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public float getBearing() {
        return this.entityData.get(DATA_BEARING);
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

    /** Aimed point on the ground; the scorch and the nova are centred here. */
    public Vec3 groundCentre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * Altitude of the disc above the horizon, 0 to 1, where 1 is the zenith.
     *
     * <p>Eased at both ends: it lifts clear of the horizon slowly, which is what
     * makes it read as something enormous rather than something rising fast.</p>
     */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public SecondSunParams shapeParams() {
        return SecondSunParams.of(getBearing());
    }

    public float altitude(float partialTick) {
        return SecondSunShape.altitude(getVisualAgeTicks(partialTick));
    }

    /** Angular radius of the disc right now, in degrees. */
    public float discAngleDegrees(float partialTick) {
        return SecondSunShape.discAngleDegrees(shapeParams(), getVisualAgeTicks(partialTick));
    }

    /**
     * Surface temperature, 1 white through 0 deep red. It cools as it swells,
     * which is what makes the nova read as the end of a life cycle.
     */
    public float temperature(float partialTick) {
        return SecondSunShape.temperature(getVisualAgeTicks(partialTick));
    }

    /** Overall brightness, driving both the visual and the emitted light. */
    @Override
    public float brightness(float partialTick) {
        return SecondSunShape.brightness(getVisualAgeTicks(partialTick), configuredLifetime());
    }

    /** Full-screen flash of the nova, 0 outside its window. */
    public float novaFlash(float partialTick) {
        return SecondSunShape.novaFlash(getVisualAgeTicks(partialTick));
    }

    /**
     * Unit direction from the world toward the disc. This is what makes it a
     * light source in the sky rather than a decal.
     */
    public Vec3 discDirection(float partialTick) {
        return SecondSunShape.discDirection(shapeParams(), getVisualAgeTicks(partialTick));
    }

    /** Where the disc sits, far enough out to read as sky. */
    public Vec3 discPosition(Vec3 viewer, float partialTick, double distance) {
        return SecondSunShape.discPosition(
                shapeParams(), viewer, getVisualAgeTicks(partialTick), distance);
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
            // Only once it is properly up: a disc still on the horizon is not
            // burning anything.
            if (timelineTick >= configuredPhaseOne() && timelineTick < configuredPhaseTwo()
                    && timelineTick % configuredInterval() == 0) {
                resolveScorch(serverLevel);
            }
            if (!this.novaResolved && timelineTick >= configuredPhaseTwo()) {
                this.novaResolved = true;
                resolveNova(serverLevel);
            }
        }

        if (timelineTick >= configuredLifetime()) {
            this.discard();
        }
    }

    /** Slow burn on everything under it. */
    private void resolveScorch(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 ground = groundCentre(1.0F);
        for (LivingEntity target : gather(level, caster, ground, configuredRadius())) {
            SpellDamage.apply(this, target,
                    SecondSunDamage.source(level, this, caster),
                    configuredPrimaryDamage());
            target.setSecondsOnFire(4);
        }
    }

    private void resolveNova(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 ground = groundCentre(1.0F);
        List<LivingEntity> targets = gather(level, caster, ground, configuredRadius());
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = SecondSunDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, configuredSecondaryDamage());
            target.setSecondsOnFire(10);
        }
    }

    private List<LivingEntity> gather(ServerLevel level, LivingEntity caster,
                                      Vec3 centre, double radius) {
        return SpellConfig.limitTargets("secondSun", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                        centre.x + radius, centre.y + radius, centre.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                <= radius * radius));
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
        this.entityData.define(DATA_BEARING, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.novaResolved = tag.getBoolean("NovaResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_BEARING, tag.getFloat("Bearing"));
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
        tag.putBoolean("NovaResolved", this.novaResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putFloat("Bearing", this.getBearing());
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

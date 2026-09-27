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
 * Server-owned anchor for Silhouette's channelled negative field.
 *
 * <p>While the caster channels, brightness inside a sphere around them is
 * inverted: lit surfaces go black and surfaces in shadow glow. The inversion
 * itself is a screen-space operation in the light blend; this entity exists to
 * carry the field's position and radius to the client and to resolve damage on
 * the server.</p>
 *
 * <p>Damage deliberately uses distance to the caster's spell lights rather than
 * the GPU shadow test. The two cannot be made to agree per-pixel, and a player
 * needs to be able to predict whether they are safe: "far from the light" is
 * legible, "in a traced shadow volume" is not.</p>
 */
public final class SilhouetteEntity extends Entity {
    private static final String CONFIG_ID = "silhouette";

    private static int configuredLifetime() {
        return SpellConfig.lifetimeTicks(CONFIG_ID);
    }

    private static double configuredRadius() {
        return SpellConfig.effectRadius(CONFIG_ID);
    }

    private static int configuredInterval() {
        return SpellConfig.damageIntervalTicks(CONFIG_ID);
    }

    private static float configuredDamage() {
        return (float) SpellConfig.damageFraction(CONFIG_ID);
    }

    /** Radius of the inverted field, in blocks. */
    public static final float FIELD_RADIUS = 32.0F;
    /** Ticks the field takes to flip in, and to settle back afterwards. */
    public static final int FLIP_TICKS = 12;
    /** Hard ceiling on the channel, so a stuck cast cannot leave it running. */
    public static final int MAX_LIFETIME_TICKS = 240;
    /** Ticks between shadow damage applications. */
    private static final int DAMAGE_INTERVAL_TICKS = 20;
    /** Max-health fraction per damage tick for anything standing in shadow. */
    private static final float SHADOW_DAMAGE_FRACTION = 0.030F;
    /**
     * A target closer than this to any of the caster's spell lights counts as
     * lit, and is therefore safe. Matches the reach of a typical spell light.
     */
    public static final double LIT_RADIUS = 10.0D;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            SilhouetteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            SilhouetteEntity.class, EntityDataSerializers.LONG);
    /** Set false the moment the channel ends, which starts the settle-back. */
    private static final EntityDataAccessor<Boolean> DATA_CHANNELLING =
            SynchedEntityData.defineId(SilhouetteEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private int releasedAtTick = -1;

    public SilhouetteEntity(EntityType<? extends SilhouetteEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_CHANNELLING, true);
        this.setPos(caster.getX(), caster.getY(), caster.getZ());
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public boolean isChannelling() {
        return this.entityData.get(DATA_CHANNELLING);
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
    }

    /** Ends the channel; the field then settles back over {@link #FLIP_TICKS}. */
    public void release() {
        if (this.isChannelling()) {
            this.entityData.set(DATA_CHANNELLING, false);
            this.releasedAtTick = getTimelineAgeTicks();
        }
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

    /** Centre of the field, interpolated so it tracks the caster smoothly. */
    public Vec3 fieldCenter(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + 1.0D,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * Inversion strength, 0 to 1. Ramps in over {@link #FLIP_TICKS}, holds while
     * the channel lasts, then settles back once released.
     */
    public float getInversionStrength(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (isChannelling()) {
            return smoothstep(age / FLIP_TICKS);
        }
        if (this.releasedAtTick < 0) {
            return 0.0F;
        }
        float since = age - this.releasedAtTick;
        return smoothstep(1.0F - since / FLIP_TICKS);
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
            LivingEntity caster = resolveCaster(serverLevel);
            // The field is anchored on the caster, so it follows them while they
            // hold the channel.
            if (caster != null && isChannelling()) {
                this.setPos(caster.getX(), caster.getY(), caster.getZ());
            }
            if (caster == null || !caster.isAlive()) {
                release();
            }
            if (isChannelling() && timelineTick > 0
                    && timelineTick % configuredInterval() == 0) {
                resolveShadowDamage(serverLevel, caster);
            }
        }

        // Linger just long enough for the settle-back to finish.
        boolean finished = !isChannelling()
                && this.releasedAtTick >= 0
                && timelineTick - this.releasedAtTick >= FLIP_TICKS;
        if (finished || timelineTick >= configuredLifetime()) {
            this.discard();
        }
    }

    /**
     * Burns everything in the field that is NOT close to one of this caster's
     * spell lights. In the inverted world the shadows are what shine, so the
     * things standing in shadow are the things being burned.
     */
    private void resolveShadowDamage(ServerLevel level, LivingEntity caster) {
        List<Vec3> lightSources = collectCasterLightPositions(level, caster);
        Vec3 centre = this.position().add(0.0D, 1.0D, 0.0D);
        List<LivingEntity> targets = SpellConfig.limitTargets("silhouette", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - configuredRadius(), centre.y - configuredRadius(),
                        centre.z - configuredRadius(), centre.x + configuredRadius(),
                        centre.y + configuredRadius(), centre.z + configuredRadius()),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                <= configuredRadius() * configuredRadius()));
        if (targets.isEmpty()) {
            return;
        }

        DamageSource source = SilhouetteDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            if (isLit(target, lightSources)) {
                continue;
            }
            SpellDamage.apply(this, target, source, configuredDamage());
        }
    }

    private static boolean isLit(LivingEntity target, List<Vec3> lightSources) {
        Vec3 centre = target.getBoundingBox().getCenter();
        for (Vec3 light : lightSources) {
            if (centre.distanceToSqr(light) <= LIT_RADIUS * LIT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /** Positions of this mod's spell effects, which are what emit spell light. */
    private List<Vec3> collectCasterLightPositions(ServerLevel level, LivingEntity caster) {
        java.util.List<Vec3> positions = new java.util.ArrayList<>();
        // A light further away than this cannot make anything inside the field
        // count as lit, so there is no reason to gather it.
        double search = configuredRadius() + LIT_RADIUS;
        for (Entity nearby : level.getEntities(this,
                new AABB(this.getX() - search, this.getY() - search, this.getZ() - search,
                        this.getX() + search, this.getY() + search, this.getZ() + search))) {
            if (nearby instanceof CelestialJudgmentEntity
                    || nearby instanceof ChromaticAccretionEntity
                    || nearby instanceof EclipseSeveranceEntity
                    || nearby instanceof FuneralNovaEntity
                    || nearby instanceof StargraveSingularityEntity
                    || nearby instanceof ConstellationEntity) {
                positions.add(nearby.position());
            }
        }
        return positions;
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
        this.entityData.define(DATA_CHANNELLING, true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.releasedAtTick = tag.getInt("ReleasedAtTick");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_CHANNELLING, tag.getBoolean("Channelling"));
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
        tag.putInt("ReleasedAtTick", this.releasedAtTick);
        tag.putInt("CasterId", this.getCasterId());
        tag.putBoolean("Channelling", this.isChannelling());
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

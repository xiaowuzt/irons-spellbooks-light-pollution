package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.api.StarfallParams;
import com.gang.lightpollution.fx.FxHash;
import com.gang.lightpollution.fx.StarfallShape;
import com.gang.lightpollution.fx.StarfallSource;
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
 * Server-owned anchor for the 15.5 second Starfall timeline.
 *
 * <p>Meteors are sub-objects of this one entity, not entities of their own: forty
 * of them would be forty spawn packets and forty tick loops for something that is
 * entirely determined by a seed and a timeline. Every property of meteor
 * <em>i</em> — where it lands, when it falls, when it hits — is a pure function of
 * the synchronized seed, so the renderer, the light emitter and the server damage
 * all agree without a single extra byte on the wire.</p>
 *
 * <p>The pressure is meant to be continuous rather than a single detonation:
 * several meteors are always in the air, each one a moving light, so the shadows
 * on the ground never stop sweeping. The exception is the finale, which is one
 * colossal body falling on the aimed point.</p>
 */
public final class StarfallEntity extends Entity implements StarfallSource {
    private static final String CONFIG_ID = "starfall";

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
    // The form lives in StarfallShape, which the renderer and the public API both read, so there is
    // one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = StarfallParams.SPELL_LIFETIME_TICKS;

    /** Meteors in the rain phase, then the single finale. */

    /** Radius of the bombarded area, in blocks. */
    public static final double EFFECT_RADIUS = 14.0D;

    public static final double RAIN_BLAST_RADIUS = 2.5D;
    public static final double FINALE_BLAST_RADIUS = 9.0D;
    private static final float RAIN_DAMAGE_FRACTION = 0.037F;
    /** The finale's damage, consolidated from what used to be three bodies. */
    private static final float FINALE_DAMAGE_FRACTION = 0.28F;
    /** Fraction of rain meteors that shed a visible shock ring. */

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            StarfallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            StarfallEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            StarfallEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    /** Highest meteor index already resolved, so a reload cannot double-hit. */
    private int resolvedThrough = -1;

    public StarfallEntity(EntityType<? extends StarfallEntity> entityType, Level level) {
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

    public static boolean isFinaleMeteor(int meteor) {
        return StarfallShape.isFinaleMeteor(meteor);
    }

    /** Tick at which a meteor begins to fall. */
    public static int spawnTick(int meteor) {
        return StarfallShape.spawnTick(meteor);
    }

    /** Tick at which a meteor lands. */
    public static int impactTick(int meteor) {
        return StarfallShape.impactTick(meteor);
    }

    /** How long this meteor spends falling. */
    public static int fallTicks(int meteor) {
        return StarfallShape.fallTicks(meteor);
    }

    /** How high above its landing point this meteor starts. */
    public static double fallHeight(int meteor) {
        return StarfallShape.isFinaleMeteor(meteor)
                ? StarfallShape.FINALE_FALL_HEIGHT
                : StarfallShape.FALL_HEIGHT;
    }

    public static double blastRadius(int meteor) {
        return isFinaleMeteor(meteor) ? FINALE_BLAST_RADIUS : RAIN_BLAST_RADIUS;
    }

    /** Anchor centre, interpolated for smooth rendering. */
    public Vec3 anchorCenter(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * Where a meteor lands. Derived from the seed alone so every side agrees, then
     * dropped onto the terrain surface — both client and server read the same
     * heightmap for loaded chunks, so a meteor never detonates inside a hillside
     * or hangs in the air above a valley.
     */
    @Override
    public Vec3 meteorLanding(int meteor) {
        // The finale lands on the aimed point itself. It is the arrival the whole
        // spell has been building to, so it should not be off to one side.
        if (isFinaleMeteor(meteor)) {
            int centre = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    Mth.floor(this.getX()), Mth.floor(this.getZ()));
            return new Vec3(this.getX(), centre, this.getZ());
        }
        float unitAngle = hashUnit(meteor, 0x9E3779B9L);
        float unitRadius = hashUnit(meteor, 0x85EBCA6BL);
        // sqrt distributes the points evenly across the disc instead of
        // clustering them at the centre.
        double radius = Math.sqrt(unitRadius) * configuredRadius();
        double angle = unitAngle * Mth.TWO_PI;
        double x = this.getX() + Math.cos(angle) * radius;
        double z = this.getZ() + Math.sin(angle) * radius;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        return new Vec3(x, surface, z);
    }

    /**
     * Whether this meteor sheds a visible shock ring.
     *
     * <p>Not every one of them: forty identical rings turned a shower into a
     * repeating pattern. Derived from the seed so every client picks the same
     * ones. The finale always has one.</p>
     */
    public boolean hasShockRing(int meteor) {
        return StarfallShape.hasShockRing(shapeParams(), meteor);
    }

    /**
     * Direction a meteor travels, pointing down-range. Unit length.
     *
     * <p>Meteors come in on a slant rather than straight down: a vertical drop
     * gives the camera no sense of speed, because the body barely moves across
     * the screen. The azimuth is derived from the meteor's own hash so the shower
     * arrives from assorted bearings instead of all sharing one, which would look
     * like a volley rather than a shower.</p>
     */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public StarfallParams shapeParams() {
        // groundY is unused on this side: the landing points come from the terrain instead.
        return StarfallParams.of(getSeed(), this.getY());
    }

    public Vec3 meteorHeading(int meteor) {
        return StarfallShape.meteorHeading(shapeParams(), meteor);
    }

    /** Where a meteor enters, back up its heading from the landing point. */
    public Vec3 meteorEntry(int meteor) {
        return StarfallShape.meteorEntry(shapeParams(), meteorLanding(meteor), meteor);
    }

    /** Position of a meteor in flight, or its landing point once it has hit. */
    public Vec3 meteorPosition(int meteor, float partialTick) {
        return StarfallShape.meteorPosition(shapeParams(), meteorLanding(meteor), meteor,
                getVisualAgeTicks(partialTick));
    }

    /**
     * Brightness of a meteor, 0 when it does not exist yet. Falls to zero shortly
     * after impact so the flash does not linger as a permanent light.
     */
    public float meteorBrightness(int meteor, float partialTick) {
        return StarfallShape.meteorBrightness(meteor, getVisualAgeTicks(partialTick));
    }

    private float hashUnit(int meteor, long salt) {
        return FxHash.unit(getSeed(), meteor, salt);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            for (int meteor = this.resolvedThrough + 1; meteor < StarfallShape.METEOR_COUNT; meteor++) {
                if (timelineTick < impactTick(meteor)) {
                    break;
                }
                this.resolvedThrough = meteor;
                resolveMeteorImpact(serverLevel, meteor);
            }
        }

        if (timelineTick >= configuredLifetime()) {
            this.discard();
        }
    }

    private void resolveMeteorImpact(ServerLevel level, int meteor) {
        LivingEntity caster = resolveCaster(level);
        Vec3 impact = meteorLanding(meteor);
        double radius = blastRadius(meteor);
        List<LivingEntity> targets = SpellConfig.limitTargets("starfall", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(impact.x - radius, impact.y - radius, impact.z - radius,
                        impact.x + radius, impact.y + radius, impact.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(impact)
                                <= radius * radius));
        if (targets.isEmpty()) {
            return;
        }

        DamageSource source = StarfallDamage.source(level, this, caster);
        float fraction = isFinaleMeteor(meteor)
                ? configuredSecondaryDamage() : configuredPrimaryDamage();
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, fraction);
            target.setSecondsOnFire(isFinaleMeteor(meteor) ? 6 : 3);
        }
        ExampleMod.LOGGER.debug("Starfall meteor {} struck {} target(s)", meteor, targets.size());
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

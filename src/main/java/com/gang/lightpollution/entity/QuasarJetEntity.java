package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.api.QuasarJetParams;
import com.gang.lightpollution.fx.QuasarJetShape;
import com.gang.lightpollution.fx.QuasarJetSource;
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
 * Server-owned anchor for a quasar jet, after M87.
 *
 * <p>This set already has the microquasar's jets, and the overlap is the risk worth naming:
 * both are relativistic jets with knots. Three things separate them, and all three are real
 * differences between the objects rather than dressing.</p>
 *
 * <p>It is <em>one</em> jet. A quasar throws two, but at these speeds relativistic beaming
 * makes the receding one so faint it is effectively invisible — the famous images of M87's
 * jet show a single needle out of the nucleus. The microquasar's pair are only mildly
 * relativistic, so both of its jets show.</p>
 *
 * <p>It is straight. No precession, no corkscrew — a narrow collimated needle that holds
 * its direction, which is what makes the knots' motion along it legible.</p>
 *
 * <p>And the knots appear to move faster than light. For a source at speed beta at a small
 * angle theta to the line of sight, the apparent transverse speed is
 *
 * <pre>   beta_app = beta sin(theta) / (1 - beta cos(theta))</pre>
 *
 * <p>which exceeds one for a wide range of angles. M87's knots have been tracked at an
 * apparent six times light speed. The knots here move at the speed that formula gives for
 * the jet's own geometry, so the illusion is produced rather than asserted. It also ends in
 * something the microquasar has not got: a terminal hotspot where the jet rams the
 * surrounding medium, inside a diffuse lobe.</p>
 *
 * <p>Figures here are established results, not values verified in the session that wrote
 * this file.</p>
 */
public final class QuasarJetEntity extends Entity implements QuasarJetSource {
    private static final String CONFIG_ID = "quasarJet";

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

    // The form lives in QuasarJetShape, which the renderer and the public API both read, so there is
    // one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = QuasarJetParams.SPELL_LIFETIME_TICKS;

    /**
     * Angle between the jet and the line of sight, degrees.
     *
     * <p>Small, because that is the condition for the apparent superluminal motion this
     * effect is built on. 17 degrees rather than something nearer the peak: the apparent
     * speed is maximised at an angle whose cosine equals beta, 5.7 degrees for gamma = 10,
     * and it reaches almost 10c there. 17 degrees brings it back down to about 6c, which is
     * the figure actually tracked for M87's knots, and it puts the jet at a angle a player
     * can see along rather than nearly end-on.</p>
     */
    /** How close to the jet counts as being inside it, in blocks. */
    public static final double JET_TOUCH_RADIUS = 2.6D;

    public static final double HOVER_HEIGHT = 13.0D;
    public static final double EFFECT_RADIUS = QuasarJetShape.JET_LENGTH + QuasarJetShape.LOBE_RADIUS + 4.0D;

    /** A knot passing through, as a fraction of max health. */
    private static final float KNOT_DAMAGE_FRACTION = 0.068F;
    private static final int JET_INTERVAL_TICKS = 6;
    /** The terminal hotspot where the jet rams the medium, as a fraction of max health. */
    private static final float HOTSPOT_DAMAGE_FRACTION = 0.53F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(QuasarJetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(QuasarJetEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(QuasarJetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(QuasarJetEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(QuasarJetEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean hotspotResolved;

    public QuasarJetEntity(EntityType<? extends QuasarJetEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 centre, float azimuthDegrees, int seed) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_AZIMUTH, azimuthDegrees);
        this.moveTo(centre.x, centre.y - HOVER_HEIGHT, centre.z, 0.0F, 0.0F);
    }

    /** Stands one up with no caster, no damage and no expiry, for looking at. */
    public void configureDisplay(Vec3 centre, float azimuthDegrees, int seed) {
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_AZIMUTH, azimuthDegrees);
        this.entityData.set(DATA_DISPLAY, true);
        this.moveTo(centre.x, centre.y - HOVER_HEIGHT, centre.z, 0.0F, 0.0F);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public boolean isDisplay() {
        return this.entityData.get(DATA_DISPLAY);
    }

    public float azimuth() {
        return this.entityData.get(DATA_AZIMUTH);
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null && this.casterUuid != null
                && caster.getUUID().equals(this.casterUuid);
    }

    public int getTimelineAgeTicks() {
        long start = this.entityData.get(DATA_START_GAME_TICK);
        if (start < 0L) {
            return Math.min(configuredLifetime(), Math.max(0, this.tickCount));
        }
        return (int) Math.min(configuredLifetime(),
                Math.max(0L, this.level().getGameTime() - start));
    }

    public float getVisualAgeTicks(float partialTick) {
        long start = this.entityData.get(DATA_START_GAME_TICK);
        float age = start < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - start) + partialTick;
        age = Math.max(0.0F, age);
        if (isDisplay()) {
            // Kept running: the knots streaming outward are the thing to look at, and the
            // jet's shape does not change, so there is nothing that a wrap would disturb.
            float span = configuredPhaseTwo() - configuredPhaseOne();
            return configuredPhaseOne() + (age % span);
        }
        return Math.min(configuredLifetime(), age);
    }

    /** The nucleus, where the jet starts. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Direction the jet points, as a unit vector. Fixed — this one does not precess. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public QuasarJetParams shapeParams() {
        return QuasarJetParams.of(azimuth());
    }

    public Vec3 direction() {
        return QuasarJetShape.direction(shapeParams());
    }

    /**
     * Apparent transverse speed of a knot, in units of c.
     *
     * <p>beta_app = beta sin(theta) / (1 - beta cos(theta)). At gamma = 10 and 17 degrees
     * this is very close to 6, which is the apparent speed actually tracked for M87's
     * knots, and the reason the knots here have to be seen to outrun light rather than
     * merely be described as doing so.</p>
     */
    public static double apparentSpeed() {
        return QuasarJetShape.apparentSpeed();
    }

    /**
     * How far along the jet a knot has travelled, as a fraction of its length.
     *
     * <p>Knots are launched at even intervals and cross the jet in the time the apparent
     * speed implies, so the spacing a viewer sees is the spacing the formula gives.</p>
     */
    public double knotProgress(int knot, float ageTicks) {
        return QuasarJetShape.knotProgress(knot, ageTicks);
    }

    /**
     * Knot speed in blocks per tick.
     *
     * <p>Scaled so the apparent superluminal factor is what sets the pace: a knot crosses
     * the visible jet in a couple of seconds, which is fast enough to read as outrunning
     * everything else in the mod and slow enough to track with the eye.</p>
     */
    public static double blocksPerTick() {
        return QuasarJetShape.blocksPerTick();
    }

    /** Where a knot sits, or null if it has not launched yet. */
    public Vec3 knotPosition(Vec3 centre, int knot, float ageTicks) {
        return QuasarJetShape.knotPosition(shapeParams(), centre, knot, ageTicks);
    }

    /** Centre of the terminal lobe, where the jet rams the surrounding medium. */
    public Vec3 lobeCentre(Vec3 centre) {
        return QuasarJetShape.lobeCentre(shapeParams(), centre);
    }

    /** How far the jet has punched out, 0 to 1. */
    public float launched(float partialTick) {
        return QuasarJetShape.launched(getVisualAgeTicks(partialTick));
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= configuredPhaseTwo()) {
            return 0.0F;
        }
        return Mth.clamp((age - configuredPhaseTwo())
                / (float) (configuredLifetime() - configuredPhaseTwo()), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= configuredPhaseOne()) {
            return launched(partialTick);
        }
        if (age <= configuredPhaseTwo()) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick));
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > configuredPhaseOne() && timelineTick < configuredPhaseTwo()
                    && timelineTick % configuredInterval() == 0) {
                resolveKnots(serverLevel, timelineTick);
            }
            if (!this.hotspotResolved && timelineTick >= configuredPhaseTwo()) {
                this.hotspotResolved = true;
                resolveHotspot(serverLevel);
            }
            if (timelineTick >= configuredLifetime()) {
                this.discard();
            }
        }
    }

    /**
     * Damage whatever a knot is passing through.
     *
     * <p>Only the knots hit, not the whole jet, and that is the mechanic: the jet is visible
     * the whole time but only dangerous when a knot arrives. Standing in it between knots is
     * a gamble on timing rather than a mistake.</p>
     */
    private void resolveKnots(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = QuasarJetDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        List<LivingEntity> targets = SpellConfig.limitTargets("quasarJet", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - configuredRadius(), centre.y - configuredRadius(),
                        centre.z - configuredRadius(), centre.x + configuredRadius(),
                        centre.y + configuredRadius(), centre.z + configuredRadius())));
        if (targets.isEmpty()) {
            return;
        }

        double touchSqr = JET_TOUCH_RADIUS * JET_TOUCH_RADIUS;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            for (int knot = 0; knot < QuasarJetShape.KNOT_COUNT; ++knot) {
                Vec3 position = knotPosition(centre, knot, ageTicks);
                if (position != null && position.distanceToSqr(at) <= touchSqr) {
                    SpellDamage.apply(this, target, source, configuredPrimaryDamage());
                    break;
                }
            }
        }
    }

    /** The terminal hotspot, at the far end where the jet stops. */
    private void resolveHotspot(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = QuasarJetDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        Vec3 lobe = lobeCentre(centre);

        for (LivingEntity target : SpellConfig.limitTargets("quasarJet", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(lobe.x - QuasarJetShape.LOBE_RADIUS, lobe.y - QuasarJetShape.LOBE_RADIUS, lobe.z - QuasarJetShape.LOBE_RADIUS,
                        lobe.x + QuasarJetShape.LOBE_RADIUS, lobe.y + QuasarJetShape.LOBE_RADIUS, lobe.z + QuasarJetShape.LOBE_RADIUS)))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(lobe) <= QuasarJetShape.LOBE_RADIUS) {
                SpellDamage.apply(this, target, source, configuredSecondaryDamage());
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
        this.entityData.define(DATA_AZIMUTH, 0.0F);
        this.entityData.define(DATA_DISPLAY, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.hotspotResolved = tag.getBoolean("HotspotResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_AZIMUTH, tag.getFloat("Azimuth"));
        this.entityData.set(DATA_DISPLAY, tag.getBoolean("Display"));
        long start = tag.contains("StartGameTick")
                ? tag.getLong("StartGameTick")
                : this.level().getGameTime() - Math.max(0, tag.getInt("TimelineTick"));
        this.entityData.set(DATA_START_GAME_TICK, start);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putBoolean("HotspotResolved", this.hotspotResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
        tag.putFloat("Azimuth", this.azimuth());
        tag.putBoolean("Display", this.isDisplay());
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

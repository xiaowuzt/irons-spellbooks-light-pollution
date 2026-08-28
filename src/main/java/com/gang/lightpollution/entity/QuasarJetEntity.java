package com.gang.lightpollution.entity;

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
public final class QuasarJetEntity extends Entity {
    public static final int LIFETIME_TICKS = 340;
    /** The nucleus lights and the jet punches out. */
    public static final int LAUNCH_END_TICK = 34;
    /** Knots stream along it. This is the body of the spell. */
    public static final int STREAM_END_TICK = 280;

    /** Bulk Lorentz factor. Around ten is typical for a powerful quasar jet. */
    public static final double GAMMA = 10.0D;
    /** Speed as a fraction of c, from the Lorentz factor. */
    public static final double BETA = Math.sqrt(1.0D - 1.0D / (GAMMA * GAMMA));
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
    public static final double VIEW_ANGLE = 17.0D;
    /** How far the jet reaches, in blocks. */
    public static final double JET_LENGTH = 72.0D;
    /** Half-width of the jet at the nucleus, in blocks. Collimated, so narrow. */
    public static final double JET_HALF_WIDTH = 0.7D;
    /** How close to the jet counts as being inside it, in blocks. */
    public static final double JET_TOUCH_RADIUS = 2.6D;
    /** Knots in flight along the jet at once. */
    public static final int KNOT_COUNT = 5;
    /** Radius of the terminal lobe, in blocks. */
    public static final double LOBE_RADIUS = 10.0D;

    public static final double HOVER_HEIGHT = 13.0D;
    public static final double EFFECT_RADIUS = JET_LENGTH + LOBE_RADIUS + 4.0D;

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
            return Math.min(LIFETIME_TICKS, Math.max(0, this.tickCount));
        }
        return (int) Math.min(LIFETIME_TICKS,
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
            float span = STREAM_END_TICK - LAUNCH_END_TICK;
            return LAUNCH_END_TICK + (age % span);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** The nucleus, where the jet starts. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Direction the jet points, as a unit vector. Fixed — this one does not precess. */
    public Vec3 direction() {
        double azimuth = Math.toRadians(azimuth());
        // Tipped up so the jet clears the ground along its length. Deliberately not tied to
        // VIEW_ANGLE despite the similar value: that one is the angle between the jet and
        // the line of sight, which depends on where the player is standing, while this is
        // the jet's tilt from horizontal. Forcing them equal would look tidy and conflate
        // two different quantities.
        double elevation = Math.toRadians(16.0D);
        return new Vec3(Math.cos(elevation) * Math.cos(azimuth),
                Math.sin(elevation),
                Math.cos(elevation) * Math.sin(azimuth)).normalize();
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
        double theta = Math.toRadians(VIEW_ANGLE);
        return BETA * Math.sin(theta) / (1.0D - BETA * Math.cos(theta));
    }

    /**
     * How far along the jet a knot has travelled, as a fraction of its length.
     *
     * <p>Knots are launched at even intervals and cross the jet in the time the apparent
     * speed implies, so the spacing a viewer sees is the spacing the formula gives.</p>
     */
    public double knotProgress(int knot, float ageTicks) {
        double crossingTicks = JET_LENGTH / Math.max(blocksPerTick(), 0.001D);
        double launched = ageTicks - knot * (crossingTicks / KNOT_COUNT);
        if (launched <= 0.0D) {
            return -1.0D;
        }
        double progress = (launched % crossingTicks) / crossingTicks;
        return progress;
    }

    /**
     * Knot speed in blocks per tick.
     *
     * <p>Scaled so the apparent superluminal factor is what sets the pace: a knot crosses
     * the visible jet in a couple of seconds, which is fast enough to read as outrunning
     * everything else in the mod and slow enough to track with the eye.</p>
     */
    public static double blocksPerTick() {
        return JET_LENGTH / 44.0D * (apparentSpeed() / 6.0D);
    }

    /** Where a knot sits, or null if it has not launched yet. */
    public Vec3 knotPosition(Vec3 centre, int knot, float ageTicks) {
        double progress = knotProgress(knot, ageTicks);
        if (progress < 0.0D) {
            return null;
        }
        return centre.add(direction().scale(JET_LENGTH * progress));
    }

    /** Centre of the terminal lobe, where the jet rams the surrounding medium. */
    public Vec3 lobeCentre(Vec3 centre) {
        return centre.add(direction().scale(JET_LENGTH));
    }

    /** How far the jet has punched out, 0 to 1. */
    public float launched(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / LAUNCH_END_TICK);
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= STREAM_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - STREAM_END_TICK)
                / (float) (LIFETIME_TICKS - STREAM_END_TICK), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= LAUNCH_END_TICK) {
            return launched(partialTick);
        }
        if (age <= STREAM_END_TICK) {
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
            if (timelineTick > LAUNCH_END_TICK && timelineTick < STREAM_END_TICK
                    && timelineTick % JET_INTERVAL_TICKS == 0) {
                resolveKnots(serverLevel, timelineTick);
            }
            if (!this.hotspotResolved && timelineTick >= STREAM_END_TICK) {
                this.hotspotResolved = true;
                resolveHotspot(serverLevel);
            }
            if (timelineTick >= LIFETIME_TICKS) {
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

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - EFFECT_RADIUS, centre.y - EFFECT_RADIUS,
                        centre.z - EFFECT_RADIUS, centre.x + EFFECT_RADIUS,
                        centre.y + EFFECT_RADIUS, centre.z + EFFECT_RADIUS));
        if (targets.isEmpty()) {
            return;
        }

        double touchSqr = JET_TOUCH_RADIUS * JET_TOUCH_RADIUS;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            for (int knot = 0; knot < KNOT_COUNT; ++knot) {
                Vec3 position = knotPosition(centre, knot, ageTicks);
                if (position != null && position.distanceToSqr(at) <= touchSqr) {
                    SpellDamage.apply(this, target, source, KNOT_DAMAGE_FRACTION);
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

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(lobe.x - LOBE_RADIUS, lobe.y - LOBE_RADIUS, lobe.z - LOBE_RADIUS,
                        lobe.x + LOBE_RADIUS, lobe.y + LOBE_RADIUS, lobe.z + LOBE_RADIUS))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(lobe) <= LOBE_RADIUS) {
                SpellDamage.apply(this, target, source, HOTSPOT_DAMAGE_FRACTION);
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

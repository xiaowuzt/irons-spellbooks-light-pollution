package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.performance.PulseShapeSamples;

import com.gang.lightpollution.api.MagnetarParams;
import com.gang.lightpollution.fx.MagnetarShape;
import com.gang.lightpollution.fx.MagnetarSource;
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
 * Server-owned anchor for a magnetar giant flare, after SGR 1806-20.
 *
 * <p>A neutron star with a field around 10^15 gauss — the strongest magnetism known. In
 * December 2004 this one produced the most powerful giant flare ever observed; the
 * published account attributes it to "a large-scale rearrangement of the magnetosphere",
 * and it ionised Earth's upper atmosphere from fifty thousand light years away.</p>
 *
 * <p>The magnetosphere is the effect. Field lines are drawn from the real dipole relation
 * r = r0 sin^2(theta), so they are the closed loops a dipole actually makes rather than
 * arcs chosen to look magnetic, and they wind tighter as the star spins up to the flare.
 * That makes the hazard a set of curved loops with gaps between them: unlike every sphere
 * in this set, where you stand inside the radius matters.</p>
 */
public final class MagnetarEntity extends Entity implements MagnetarSource {
    private static final String CONFIG_ID = "magnetar";

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

    // The form of the magnetar lives in MagnetarShape, which the renderer and the public API both
    // read, so there is one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = MagnetarParams.SPELL_LIFETIME_TICKS;
    /** The rearrangement. One pulse, and the loops snap open. */
    public static final int FLARE_TICK = MagnetarShape.FLARE_TICK;

    /**
     * Field line count.
     *
     * <p>Drawn in pairs about the axis, so this is even. Enough to read as a magnetosphere
     * with visible gaps between the loops, which is the point — a solid shell of them would
     * just be a sphere again.</p>
     */
    /**
     * Radius of the neutron star itself, in blocks.
     *
     * <p>Load-bearing rather than decorative: the field lines terminate on it, so it is what
     * separates each loop's two ends and lets the loop close visibly.</p>
     */
    /** How close to a loop counts as touching it, in blocks. */
    public static final double LOOP_TOUCH_RADIUS = 2.1D;

    public static final double HOVER_HEIGHT = 9.0D;
    public static final double FLARE_RADIUS = 22.0D;
    public static final double EFFECT_RADIUS = FLARE_RADIUS + 4.0D;

    /** Touching a field line, as a fraction of max health. */
    private static final float FIELD_DAMAGE_FRACTION = 0.024F;
    private static final int FIELD_INTERVAL_TICKS = 8;
    /**
     * The flare, as a fraction of max health.
     *
     * <p>The largest single hit here short of the two dedicated executioners, which is the
     * right shape for the most energetic flare ever recorded — but it is one pulse with a
     * long wind-up that is impossible to miss, so it is avoidable in a way they are not.</p>
     */
    private static final float FLARE_DAMAGE_FRACTION = 0.78F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean flareResolved;

    public MagnetarEntity(EntityType<? extends MagnetarEntity> type, Level level) {
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
            // Cycles the wind-up instead of holding it at the top. Held fully wound the field
            // is always at the white-hot end of its ramp, so the violet it starts from is never
            // visible and the effect looks like it only has one colour. Stopping short of the
            // flare still avoids blanking the view every cycle.
            float span = MagnetarShape.WIND_END_TICK - configuredPhaseOne();
            return configuredPhaseOne() + (age % span);
        }
        return Math.min(configuredLifetime(), age);
    }

    /** Centre of the star. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** The magnetic axis, as a unit vector. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public MagnetarParams shapeParams() {
        return MagnetarParams.of(azimuth());
    }

    public Vec3 axis() {
        return MagnetarShape.axis(shapeParams());
    }

    /** How far the field has threaded out, 0 to 1. */
    public float threaded(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / configuredPhaseOne());
    }

    /** How far the magnetosphere has wound up, 0 to 1. */
    public float wound(float partialTick) {
        return MagnetarShape.wound(getVisualAgeTicks(partialTick));
    }

    /** The flare itself, 1 at the instant it goes and decaying after. */
    public float flare(float partialTick) {
        return MagnetarShape.flare(getVisualAgeTicks(partialTick), configuredPhaseTwo());
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
            return threaded(partialTick);
        }
        if (age <= MagnetarShape.WIND_END_TICK) {
            // Brightens as it winds: the field is being stressed, and that is the tell
            // that the flare is coming.
            return 1.0F + wound(partialTick) * 0.8F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick)) + flare(partialTick) * 2.5F;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * A point on one dipole field line.
     *
     * <p>From the dipole relation r = r0 sin^2(theta), which is the actual shape of a
     * magnetic field line rather than an arc picked to look like one: it leaves a pole
     * along the axis, bulges out at the equator, and closes on the other pole. The twist
     * winds the loop about the axis as the magnetosphere is stressed, which is the
     * rearrangement that eventually lets go.</p>
     *
     * @param line  which loop, 0 to MagnetarShape.FIELD_LINES-1
     * @param along position along the loop, 0 at one pole to 1 at the other
     */
    public Vec3 fieldPoint(Vec3 centre, int line, double along, float woundFraction) {
        return MagnetarShape.fieldPoint(shapeParams(), centre, line, along, woundFraction);
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
                resolveField(serverLevel, timelineTick);
            }
            if (!this.flareResolved && timelineTick >= configuredPhaseTwo()) {
                this.flareResolved = true;
                resolveFlare(serverLevel);
            }
            if (timelineTick >= configuredLifetime()) {
                this.discard();
            }
        }
    }

    /**
     * Damage whatever is touching a field line.
     *
     * <p>Tested against the same sampled loop points the renderer draws, so the gaps a
     * player can see between loops are gaps they can actually stand in. That is the whole
     * mechanic — a radius test would erase it.</p>
     */
    private void resolveField(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = MagnetarDamage.field(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        float woundFraction = wound(0.0F);

        List<LivingEntity> targets = SpellConfig.limitTargets("magnetar", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - configuredRadius(), centre.y - configuredRadius(),
                        centre.z - configuredRadius(), centre.x + configuredRadius(),
                        centre.y + configuredRadius(), centre.z + configuredRadius())));
        if (targets.isEmpty()) {
            return;
        }

        double touchSqr = LOOP_TOUCH_RADIUS * LOOP_TOUCH_RADIUS;
        var samples = PulseShapeSamples.magnetar(centre, woundFraction, touchSqr);
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            MagnetarParams params = shapeParams();
            Vec3 at = target.getBoundingBox().getCenter();
            if (samples.firstHit(params, at) >= 0) {
                SpellDamage.apply(this, target, source, configuredPrimaryDamage());
            }
        }
    }

    /** The rearrangement. Everything within reach, once. */
    private void resolveFlare(ServerLevel level) {
        // Announced before anything else in here, including the early return when
        // nothing is in range: the event happened regardless of whether it hit.
        com.gang.lightpollution.net.ModNetwork.sendCaption(level,
                this.position().add(0.0D, HOVER_HEIGHT, 0.0D),
                "caption.irons_spellbooks_light_pollution.magnetar.flare",
                com.gang.lightpollution.SpellPalette.accentFor(this), 1.7F);
        LivingEntity caster = resolveCaster(level);
        DamageSource source = MagnetarDamage.field(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        for (LivingEntity target : SpellConfig.limitTargets("magnetar", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - FLARE_RADIUS, centre.y - FLARE_RADIUS,
                        centre.z - FLARE_RADIUS, centre.x + FLARE_RADIUS,
                        centre.y + FLARE_RADIUS, centre.z + FLARE_RADIUS)))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= FLARE_RADIUS) {
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
        this.flareResolved = tag.getBoolean("FlareResolved");
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
        tag.putBoolean("FlareResolved", this.flareResolved);
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

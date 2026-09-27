package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;

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
import com.gang.lightpollution.api.TidalDisruptionParams;
import com.gang.lightpollution.fx.TidalDisruptionShape;
import com.gang.lightpollution.fx.TidalDisruptionSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/**
 * Server-owned anchor for a tidal disruption event.
 *
 * <p>A star passes too close to a black hole and is pulled apart. The subject here is the
 * star, not the hole — there are already four holes in this set and a fifth would be a
 * reskin. What makes a disruption its own thing is spaghettification: the tidal field
 * stretches the star along its orbit into one enormously long, thin stream of debris,
 * which then wraps around and comes back.</p>
 *
 * <p>Roughly half the debris ends up bound and returns; the other half is flung away and
 * never comes back. The returning material is what lights the flare, and its fallback rate
 * declines as t^(-5/3) — the classic signature of a disruption, from Rees and Phinney's
 * late-1980s work. That exponent drives the brightness curve here rather than a hand-drawn
 * fade.</p>
 *
 * <p>Numbers in this file are established results rather than figures verified in the
 * session that wrote it; the network research for this batch did not go through.</p>
 */
public final class TidalDisruptionEntity extends Entity implements TidalDisruptionSource {
    private static final String CONFIG_ID = "tidalDisruption";

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
    // Both of these live in TidalDisruptionParams, which is the public contract, so there is one
    // definition rather than a spell value and an API value that can drift apart.
    public static final int LIFETIME_TICKS = TidalDisruptionParams.SPELL_LIFETIME_TICKS;
    /** The stream is fully drawn out and whipping round. */
    public static final int STREAM_END_TICK = 250;
    /** Returning debris lights the flare. */
    public static final int FLARE_TICK = TidalDisruptionParams.SPELL_FLARE_TICK;

    /** How close to the stream counts as being struck, in blocks. */
    public static final double STREAM_TOUCH_RADIUS = 3.0D;

    public static final double HOVER_HEIGHT = 11.0D;
    public static final double FLARE_RADIUS = 20.0D;
    public static final double EFFECT_RADIUS = TidalDisruptionShape.WRAP_RADIUS + STREAM_TOUCH_RADIUS + 6.0D;

    /** Lashed by the stream, as a fraction of max health. */
    private static final float STREAM_DAMAGE_FRACTION = 0.058F;
    private static final int STREAM_INTERVAL_TICKS = 5;
    /** The accretion flare as the bound debris returns, as a fraction of max health. */
    private static final float FLARE_DAMAGE_FRACTION = 0.47F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(TidalDisruptionEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(TidalDisruptionEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(TidalDisruptionEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(TidalDisruptionEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(TidalDisruptionEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean flareResolved;

    public TidalDisruptionEntity(EntityType<? extends TidalDisruptionEntity> type,
                                 Level level) {
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
            return Math.min(age, configuredPhaseTwo() - 1.0F);
        }
        return Math.min(configuredLifetime(), age);
    }

    /** Where the hole is. The star is torn apart around this point. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** How far the star has been drawn out, 0 to 1. */
    public float stretched(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / configuredPhaseOne());
    }

    /**
     * Brightness of the flare, from the fallback rate.
     *
     * <p>Not an eased fade: the returning debris feeds the hole at a rate declining as
     * t^(-5/3), so the light curve rises fast and then has a long shallow tail. That shape
     * is the reason a disruption is identifiable at all, and an exponential or linear fade
     * would throw away the one thing about its timing that is diagnostic.</p>
     */
    public float flare(float partialTick) {
        return TidalDisruptionShape.flare(getVisualAgeTicks(partialTick), configuredPhaseThree());
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
            return stretched(partialTick);
        }
        if (age <= configuredPhaseTwo()) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick)) + flare(partialTick) * 2.2F;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * A point on the debris stream.
     *
     * <p>Two things at once, which is what a disruption looks like: the stream winds around
     * the hole on a bound orbit, and it is being drawn steadily thinner and longer. The
     * fraction runs from the leading tip, closest in, out along the stream to the trailing
     * end that is still arriving.</p>
     *
     * @param fraction 0 at the leading tip, 1 at the trailing end
     */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    public TidalDisruptionParams shapeParams() {
        return TidalDisruptionParams.of(azimuth());
    }

    public Vec3 streamPoint(Vec3 centre, float ageTicks, double fraction) {
        return TidalDisruptionShape.streamPoint(shapeParams(), centre, ageTicks, fraction);
    }

    /** Half-width of the stream at a fraction along it, in blocks. */
    public double streamWidth(double fraction) {
        return TidalDisruptionShape.streamWidth(shapeParams(), fraction);
    }

    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > configuredPhaseOne() && timelineTick < configuredPhaseThree()
                    && timelineTick % configuredInterval() == 0) {
                resolveStream(serverLevel, timelineTick);
            }
            if (!this.flareResolved && timelineTick >= configuredPhaseThree()) {
                this.flareResolved = true;
                resolveFlare(serverLevel);
            }
            if (timelineTick >= configuredLifetime()) {
                this.discard();
            }
        }
    }

    /** Damage whatever the stream is lashing, tested against the drawn curve. */
    private void resolveStream(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = TidalDisruptionDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        List<LivingEntity> targets = SpellConfig.limitTargets("tidalDisruption", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - configuredRadius(), centre.y - configuredRadius(),
                        centre.z - configuredRadius(), centre.x + configuredRadius(),
                        centre.y + configuredRadius(), centre.z + configuredRadius())));
        if (targets.isEmpty()) {
            return;
        }

        int samples = 40;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            boolean struck = false;
            for (int i = 0; i <= samples; ++i) {
                double fraction = i / (double) samples;
                double reach = STREAM_TOUCH_RADIUS + streamWidth(fraction);
                if (streamPoint(centre, ageTicks, fraction).distanceToSqr(at)
                        <= reach * reach) {
                    struck = true;
                    break;
                }
            }
            if (struck) {
                SpellDamage.apply(this, target, source, configuredPrimaryDamage());
            }
        }
    }

    /** The accretion flare when the bound half of the debris comes back. */
    private void resolveFlare(ServerLevel level) {
        // Announced before anything else in here, including the early return when
        // nothing is in range: the event happened regardless of whether it hit.
        com.gang.lightpollution.net.ModNetwork.sendCaption(level,
                this.position().add(0.0D, HOVER_HEIGHT, 0.0D),
                "caption.irons_spellbooks_light_pollution.tidal_disruption.peak",
                com.gang.lightpollution.SpellPalette.accentFor(this), 1.6F);
        LivingEntity caster = resolveCaster(level);
        DamageSource source = TidalDisruptionDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        for (LivingEntity target : SpellConfig.limitTargets("tidalDisruption", level.getEntitiesOfClass(LivingEntity.class,
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

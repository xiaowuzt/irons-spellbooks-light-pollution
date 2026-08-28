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
public final class TidalDisruptionEntity extends Entity {
    public static final int LIFETIME_TICKS = 360;
    /** The star arrives and begins to stretch. */
    public static final int STRETCH_END_TICK = 46;
    /** The stream is fully drawn out and whipping round. */
    public static final int STREAM_END_TICK = 250;
    /** Returning debris lights the flare. */
    public static final int FLARE_TICK = STREAM_END_TICK;

    /** How long the debris stream gets, in blocks. */
    public static final double STREAM_LENGTH = 62.0D;
    /** Half-width of the stream at its thickest, in blocks. */
    public static final double STREAM_HALF_WIDTH = 1.3D;
    /** How close to the stream counts as being struck, in blocks. */
    public static final double STREAM_TOUCH_RADIUS = 3.0D;
    /**
     * Turns the stream wraps through as it falls back.
     *
     * <p>Debris on a bound orbit comes back round, so the stream is not a straight line —
     * it is a wound-up ribbon. Just over a turn and a half is enough to read as an orbit
     * rather than as an arc.</p>
     */
    public static final double WRAP_TURNS = 1.65D;
    /** How far the stream reaches from the axis at its widest, in blocks. */
    public static final double WRAP_RADIUS = 21.0D;

    public static final double HOVER_HEIGHT = 11.0D;
    public static final double FLARE_RADIUS = 20.0D;
    public static final double EFFECT_RADIUS = WRAP_RADIUS + STREAM_TOUCH_RADIUS + 6.0D;

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
            return Math.min(age, STREAM_END_TICK - 1.0F);
        }
        return Math.min(LIFETIME_TICKS, age);
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
        return smoothstep(getVisualAgeTicks(partialTick) / STRETCH_END_TICK);
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
        float age = getVisualAgeTicks(partialTick);
        float since = age - FLARE_TICK;
        if (since < 0.0F) {
            return 0.0F;
        }
        // Peak is a few ticks wide, then the power law takes over.
        float rise = Mth.clamp(since / 8.0F, 0.0F, 1.0F);
        float t = 1.0F + since / 22.0F;
        float decay = (float) Math.pow(t, -5.0D / 3.0D);
        return rise * decay;
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
        if (age <= STRETCH_END_TICK) {
            return stretched(partialTick);
        }
        if (age <= STREAM_END_TICK) {
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
    public Vec3 streamPoint(Vec3 centre, float ageTicks, double fraction) {
        double drawn = Mth.clamp(ageTicks / (double) STRETCH_END_TICK, 0.12D, 1.0D);
        double along = fraction * drawn;

        double azimuth = Math.toRadians(azimuth());
        Vec3 axis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = new Vec3(Math.cos(azimuth), 0.0D, Math.sin(azimuth));
        Vec3 other = axis.cross(side).normalize();

        // Wrapping: the bound debris goes round, tighter as it falls in. The leading tip is
        // closest to the hole, so radius grows along the stream.
        double phase = along * WRAP_TURNS * Math.PI * 2.0D;
        // Starts at the hole, not 3.8 blocks away from it. The 0.18 floor this used to have
        // left the stream visibly detached from the flare it is supposed to be falling into,
        // which is one of the gaps reported as "没有链接在一起".
        double radius = WRAP_RADIUS * along;
        // And it climbs out of the orbital plane a little, because the orbit is inclined.
        double rise = STREAM_LENGTH * 0.10D * Math.sin(along * Math.PI * 1.1D);

        return centre.add(side.scale(Math.cos(phase) * radius))
                .add(other.scale(Math.sin(phase) * radius))
                .add(axis.scale(rise));
    }

    /** Half-width of the stream at a fraction along it, in blocks. */
    public static double streamWidth(double fraction) {
        // Thinnest at the leading tip, where the tidal stretching has had longest to work.
        return STREAM_HALF_WIDTH * (0.35D + 0.65D * fraction);
    }

    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > STRETCH_END_TICK && timelineTick < FLARE_TICK
                    && timelineTick % STREAM_INTERVAL_TICKS == 0) {
                resolveStream(serverLevel, timelineTick);
            }
            if (!this.flareResolved && timelineTick >= FLARE_TICK) {
                this.flareResolved = true;
                resolveFlare(serverLevel);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /** Damage whatever the stream is lashing, tested against the drawn curve. */
    private void resolveStream(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = TidalDisruptionDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - EFFECT_RADIUS, centre.y - EFFECT_RADIUS,
                        centre.z - EFFECT_RADIUS, centre.x + EFFECT_RADIUS,
                        centre.y + EFFECT_RADIUS, centre.z + EFFECT_RADIUS));
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
                applyTrueDamage(target, source, STREAM_DAMAGE_FRACTION);
            }
        }
    }

    /** The accretion flare when the bound half of the debris comes back. */
    private void resolveFlare(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = TidalDisruptionDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - FLARE_RADIUS, centre.y - FLARE_RADIUS,
                        centre.z - FLARE_RADIUS, centre.x + FLARE_RADIUS,
                        centre.y + FLARE_RADIUS, centre.z + FLARE_RADIUS))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= FLARE_RADIUS) {
                applyTrueDamage(target, source, FLARE_DAMAGE_FRACTION);
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

    private static void applyTrueDamage(LivingEntity target, DamageSource source,
                                        float fraction) {
        float damage = Math.max(0.0F, target.getMaxHealth() * fraction);
        float desiredHealth = Math.max(0.0F, target.getHealth() - damage);

        target.invulnerableTime = 0;
        target.hurt(source, damage);
        target.invulnerableTime = 0;

        if (target.isDeadOrDying() || target.isRemoved()) {
            return;
        }

        target.setAbsorptionAmount(0.0F);
        float finalHealth = Math.min(target.getHealth(), desiredHealth);
        if (finalHealth <= 0.0F) {
            target.setHealth(0.0F);
            if (!target.isRemoved()) {
                target.die(source);
            }
        } else {
            target.setHealth(finalHealth);
        }
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

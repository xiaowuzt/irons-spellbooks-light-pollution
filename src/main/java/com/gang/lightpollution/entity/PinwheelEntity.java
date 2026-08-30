package com.gang.lightpollution.entity;

import com.gang.lightpollution.api.PinwheelParams;
import com.gang.lightpollution.fx.PinwheelShape;
import com.gang.lightpollution.fx.PinwheelSource;
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
 * Server-owned anchor for a Wolf-Rayet pinwheel, after WR 104.
 *
 * <p>Two hot stars in a binary, their winds colliding along a surface that trails behind the
 * orbit. Dust condenses in the shocked gas and is carried outward, so the pair leaves a
 * spiral of dust behind it — imaged directly with aperture masking on Keck, and rotating with
 * the orbital period of about 220 days.</p>
 *
 * <p>This set has two accretion disks already, and a spiral could easily be mistaken for a
 * third. Three things keep it distinct, and all three are real differences rather than
 * styling. The spiral is <em>Archimedean</em> — constant pitch, r proportional to the angle —
 * because the dust is carried out at a steady wind speed while the binary turns at a steady
 * rate; a disk is Keplerian, with material at different radii going round at different
 * speeds. The arms have <em>empty space between them</em>, where a disk is a filled annulus.
 * And it is <em>dust</em>, a few hundred kelvin rather than thousands, so it is red-brown and
 * not blue-white.</p>
 *
 * <p>Mechanically it rotates rigidly, which is the whole point: the safe gaps between the
 * arms move, so standing still in one only works until the arm comes round. The magnetar's
 * gaps stay put; these do not.</p>
 *
 * <p>Figures here are established results, not values verified in the session that wrote
 * this file.</p>
 */
public final class PinwheelEntity extends Entity implements PinwheelSource {
    // The form lives in PinwheelShape, which the renderer and the public API both read, so there is
    // one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = PinwheelParams.SPELL_LIFETIME_TICKS;

    /**
     * Turns of spiral within that reach.
     *
     * <p>Sets the pitch, and the pitch is what distinguishes this from a disk: a constant
     * number of turns over a constant radius means constant spacing between successive arm
     * crossings, which is what an Archimedean spiral is.</p>
     */
    /** How close to an arm counts as being in the dust, in blocks. */
    public static final double ARM_TOUCH_RADIUS = 2.2D;

    public static final double HOVER_HEIGHT = 12.0D;
    public static final double EFFECT_RADIUS = PinwheelShape.SPIRAL_REACH + ARM_TOUCH_RADIUS + 4.0D;

    /** Caught in the dust, as a fraction of max health. */
    private static final float ARM_DAMAGE_FRACTION = 0.021F;
    private static final int ARM_INTERVAL_TICKS = 4;
    /** The binary's final flare-up, as a fraction of max health. */
    private static final float FLARE_DAMAGE_FRACTION = 0.36F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(PinwheelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(PinwheelEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(PinwheelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(PinwheelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(PinwheelEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean flareResolved;

    public PinwheelEntity(EntityType<? extends PinwheelEntity> type, Level level) {
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
            // Wrapped on one whole rotation of the pattern, not on the lit window. The
            // window is 1.4 turns, and wrapping there would send the phase from 2.8*pi back
            // to zero, which is a different angle — the pattern would visibly jump. Same
            // trap the microquasar's precession fell into.
            float ticksPerTurn = (PinwheelShape.SPIN_END_TICK - PinwheelShape.SPIN_UP_END_TICK)
                    / (float) PinwheelShape.ROTATION_TURNS;
            return PinwheelShape.SPIN_UP_END_TICK + (age % ticksPerTurn);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** Centre of the binary. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Normal of the spiral plane, as a unit vector. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public PinwheelParams shapeParams() {
        return PinwheelParams.of(azimuth());
    }

    public Vec3 planeNormal() {
        return PinwheelShape.planeNormal(shapeParams());
    }

    /** How much of the pattern has appeared, 0 to 1. */
    public float spunUp(float partialTick) {
        return PinwheelShape.spunUp(getVisualAgeTicks(partialTick));
    }

    /** Rigid rotation of the whole pattern, in radians. */
    public double rotation(float ageTicks) {
        return PinwheelShape.rotation(ageTicks);
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= PinwheelShape.SPIN_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - PinwheelShape.SPIN_END_TICK)
                / (float) (LIFETIME_TICKS - PinwheelShape.SPIN_END_TICK), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= PinwheelShape.SPIN_UP_END_TICK) {
            return spunUp(partialTick);
        }
        if (age <= PinwheelShape.SPIN_END_TICK) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick));
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * A point on one spiral arm.
     *
     * <p>Archimedean: the radius is proportional to the angle turned, because dust leaves at
     * a steady wind speed while the binary turns at a steady rate. A Keplerian spiral would
     * bunch up toward the centre; this one keeps the same spacing all the way out, and that
     * even spacing is what stops it reading as a disk.</p>
     *
     * @param fraction 0 at the centre, 1 at the outer end of the arm
     */
    public Vec3 armPoint(Vec3 centre, int arm, double fraction, double rotation) {
        return PinwheelShape.armPoint(shapeParams(), centre, arm, fraction, rotation);
    }

    /** Half-width of an arm at a fraction along it, in blocks. */
    public double armWidth(double fraction) {
        return PinwheelShape.armWidth(shapeParams(), fraction);
    }

    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > PinwheelShape.SPIN_UP_END_TICK && timelineTick < PinwheelShape.SPIN_END_TICK
                    && timelineTick % ARM_INTERVAL_TICKS == 0) {
                resolveArms(serverLevel, timelineTick);
            }
            if (!this.flareResolved && timelineTick >= PinwheelShape.SPIN_END_TICK) {
                this.flareResolved = true;
                resolveFlare(serverLevel);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /**
     * Damage whatever is in the dust.
     *
     * <p>Tested against the same arm curves the renderer draws, at the same rotation, so a
     * gap that looks safe is safe — and stops being safe when the arm reaches it, which is
     * the point of the whole effect.</p>
     */
    private void resolveArms(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = PinwheelDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        double rotation = rotation(ageTicks);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - EFFECT_RADIUS, centre.y - EFFECT_RADIUS,
                        centre.z - EFFECT_RADIUS, centre.x + EFFECT_RADIUS,
                        centre.y + EFFECT_RADIUS, centre.z + EFFECT_RADIUS));
        if (targets.isEmpty()) {
            return;
        }

        int samples = 46;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            boolean caught = false;
            for (int arm = 0; arm < PinwheelShape.ARMS && !caught; ++arm) {
                for (int i = 1; i <= samples; ++i) {
                    double fraction = i / (double) samples;
                    double reach = ARM_TOUCH_RADIUS + armWidth(fraction);
                    if (armPoint(centre, arm, fraction, rotation).distanceToSqr(at)
                            <= reach * reach) {
                        caught = true;
                        break;
                    }
                }
            }
            if (caught) {
                SpellDamage.apply(this, target, source, ARM_DAMAGE_FRACTION);
            }
        }
    }

    /** The binary's last flare, out to the spiral's full reach. */
    private void resolveFlare(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = PinwheelDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - PinwheelShape.SPIRAL_REACH, centre.y - PinwheelShape.SPIRAL_REACH,
                        centre.z - PinwheelShape.SPIRAL_REACH, centre.x + PinwheelShape.SPIRAL_REACH,
                        centre.y + PinwheelShape.SPIRAL_REACH, centre.z + PinwheelShape.SPIRAL_REACH))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= PinwheelShape.SPIRAL_REACH) {
                SpellDamage.apply(this, target, source, FLARE_DAMAGE_FRACTION);
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

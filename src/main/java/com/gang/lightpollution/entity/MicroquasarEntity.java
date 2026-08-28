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
 * Server-owned anchor for SS 433, the microquasar with precessing jets.
 *
 * <p>Two opposed relativistic jets leave a disk that is itself precessing, so the
 * ejecta do not form a straight beam — each blob keeps the direction it was launched
 * with, and the jet as a whole is drawn out into a corkscrew. That is not a stylisation:
 * the helix has been directly imaged in radio, and the standard kinematic model of the
 * system reproduces it from a precession period of about 160 days, a cone half-angle
 * near 20 degrees, and a jet speed of 0.26c.</p>
 *
 * <p>The helix is why this spell exists. Everything else in this set is dodged by
 * getting out of a radius; the jets sweep, so this one is dodged by reading where the
 * beam will be next. The precession phase is public and the beam is visible, so the
 * information a player needs is on screen.</p>
 */
public final class MicroquasarEntity extends Entity {
    public static final int LIFETIME_TICKS = 320;
    /** The disk assembles and the jets light. */
    public static final int SPINUP_END_TICK = 40;
    /** The jets sweep. This is the whole spell. */
    public static final int HOLD_END_TICK = 260;

    /**
     * Jet speed as a fraction of c.
     *
     * <p>The measured value for SS 433, and it is load-bearing rather than decorative:
     * the relativistic beaming that makes the approaching jet bright and blue and the
     * receding one dim and red is computed from this, so changing it changes the
     * asymmetry that makes the pair recognisable.</p>
     */
    public static final double JET_BETA = 0.26D;
    /** Half-angle of the precession cone, degrees. From the kinematic model. */
    public static final double CONE_HALF_ANGLE = 20.0D;
    /**
     * Tilt of the precession axis away from vertical, degrees.
     *
     * <p>Not from the object — SS 433's axis is inclined about 78 degrees to our line of
     * sight, which is a fact about where Earth happens to be and means nothing here.
     * This is chosen so the swept cone crosses the ground at a readable distance: with
     * the axis 38 degrees off vertical and a 20 degree cone, each jet's ground track is a
     * ring the player can see coming rather than a beam that stays overhead or one that
     * scrapes along the horizon.</p>
     */
    public static final double AXIS_TILT = 38.0D;
    /** How far the jets reach, in blocks. */
    public static final double JET_LENGTH = 54.0D;
    /** Blobs drawn and tested per jet. */
    public static final int BULLETS_PER_JET = 30;
    /**
     * Precession cycles completed while the jets are lit.
     *
     * <p>The real period is 162 days. Compressed to about one and a half turns across the
     * eleven seconds the jets burn, which is slow enough to read the sweep coming and fast
     * enough that the corkscrew is visibly turning rather than apparently frozen.</p>
     */
    public static final double PRECESSION_CYCLES = 1.5D;

    public static final double HOVER_HEIGHT = 16.0D;
    /** How close to the helix is a hit, in blocks. */
    public static final double BEAM_RADIUS = 3.4D;
    public static final double EFFECT_RADIUS = JET_LENGTH + BEAM_RADIUS + 4.0D;

    /** Swept by the jet, as a fraction of max health. */
    private static final float JET_DAMAGE_FRACTION = 0.048F;
    private static final int JET_INTERVAL_TICKS = 5;
    /** Both jets fire at once as the disk lets go. */
    private static final float TERMINAL_DAMAGE_FRACTION = 0.50F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(MicroquasarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(MicroquasarEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(MicroquasarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(MicroquasarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(MicroquasarEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean terminalResolved;

    public MicroquasarEntity(EntityType<? extends MicroquasarEntity> type, Level level) {
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
            // Left running rather than frozen: the sweep is the thing being inspected, so
            // holding it still would hide exactly what needs looking at.
            //
            // Wrapped on exactly one precession turn, not on the whole lit window. The
            // window is 1.5 turns long, and wrapping there sent the phase from 3*pi back
            // to 0 — which is not the same angle, so the whole corkscrew snapped to a new
            // orientation once per cycle and looked like a twitch. Wrapping the time only
            // works when the wrapped quantity is congruent in the quantity that matters,
            // and here that is the phase.
            float ticksPerTurn = (HOLD_END_TICK - SPINUP_END_TICK)
                    / (float) PRECESSION_CYCLES;
            return SPINUP_END_TICK + (age % ticksPerTurn);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** Centre of the disk. It hangs above the aimed point rather than at it. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** How far the jets have lit, 0 to 1. */
    public float ignition(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / SPINUP_END_TICK);
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= HOLD_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - HOLD_END_TICK)
                / (float) (LIFETIME_TICKS - HOLD_END_TICK), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= SPINUP_END_TICK) {
            return ignition(partialTick);
        }
        if (age <= HOLD_END_TICK) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick));
    }

    /**
     * Precession phase in radians at a given age.
     *
     * <p>Measured from the moment the jets light, so the corkscrew starts unwound.</p>
     */
    public double precessionPhase(float ageTicks) {
        double lit = Math.max(1.0D, HOLD_END_TICK - SPINUP_END_TICK);
        double progress = (ageTicks - SPINUP_END_TICK) / lit;
        return progress * PRECESSION_CYCLES * Math.PI * 2.0D;
    }

    /** The precession axis, as a unit vector. Fixed for the life of the effect. */
    public Vec3 axis() {
        double azimuth = Math.toRadians(this.entityData.get(DATA_AZIMUTH));
        double tilt = Math.toRadians(AXIS_TILT);
        return new Vec3(Math.sin(tilt) * Math.cos(azimuth),
                Math.cos(tilt),
                Math.sin(tilt) * Math.sin(azimuth)).normalize();
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
            if (timelineTick > SPINUP_END_TICK && timelineTick < HOLD_END_TICK
                    && timelineTick % JET_INTERVAL_TICKS == 0) {
                resolveSweep(serverLevel, timelineTick, JET_DAMAGE_FRACTION);
            }
            if (!this.terminalResolved && timelineTick >= HOLD_END_TICK) {
                this.terminalResolved = true;
                resolveSweep(serverLevel, HOLD_END_TICK, TERMINAL_DAMAGE_FRACTION);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /**
     * Damage whatever the helix is currently passing through.
     *
     * <p>Tested against the same sampled blob positions the renderer draws, so what looks
     * like it is about to be hit is what gets hit. A single line test against the current
     * jet direction would be cheaper and wrong: the visible hazard is the corkscrew left
     * behind by earlier phases, not the instantaneous axis.</p>
     */
    private void resolveSweep(ServerLevel level, float ageTicks, float fraction) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = MicroquasarDamage.jet(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - EFFECT_RADIUS, centre.y - EFFECT_RADIUS,
                        centre.z - EFFECT_RADIUS, centre.x + EFFECT_RADIUS,
                        centre.y + EFFECT_RADIUS, centre.z + EFFECT_RADIUS));
        if (targets.isEmpty()) {
            return;
        }

        double hitSqr = BEAM_RADIUS * BEAM_RADIUS;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            boolean struck = false;
            for (int side = 0; side < 2 && !struck; ++side) {
                for (int i = 1; i <= BULLETS_PER_JET; ++i) {
                    if (bulletPosition(centre, ageTicks, side == 0, i)
                            .distanceToSqr(at) <= hitSqr) {
                        struck = true;
                        break;
                    }
                }
            }
            if (struck) {
                applyTrueDamage(target, source, fraction);
            }
        }
    }

    /**
     * Where one sampled blob sits, for the damage sweep.
     *
     * <p>A thin wrapper on {@link #helixPoint}: the sweep tests a fixed 30 points because
     * that is dense enough for a 3.4 block hit radius, while the renderer subdivides the
     * same curve far more finely. Both read the one function, so the shape drawn and the
     * shape that hits stay identical.</p>
     */
    public Vec3 bulletPosition(Vec3 centre, float ageTicks, boolean forward, int index) {
        return helixPoint(centre, ageTicks, forward, index / (double) BULLETS_PER_JET);
    }

    /**
     * A point on the corkscrew, at a fraction of the way out along the jet.
     *
     * <p>This is the whole effect in one method, and the subtlety is that the launch phase
     * is recovered from the travel time rather than taken as the current one. A blob keeps
     * the direction it left with; the disk has turned since. Using the current phase for
     * every blob would draw a straight beam that swings around, which is what a rotating
     * searchlight looks like and is not what this object does.</p>
     *
     * <p>Takes a continuous fraction rather than an index so the renderer can subdivide as
     * finely as it needs without changing how far the jet reaches.</p>
     */
    public Vec3 helixPoint(Vec3 centre, float ageTicks, boolean forward, double fraction) {
        double travel = JET_LENGTH * fraction;
        // Ticks since this blob left, from how far it has gone. 20 ticks per second, and
        // JET_BETA is scaled to blocks per tick by the same constant the renderer uses.
        double ticksAgo = travel / blocksPerTick();
        double launchPhase = precessionPhase((float) (ageTicks - ticksAgo));

        Vec3 axis = axis();
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0e-6D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 other = axis.cross(side).normalize();

        double cone = Math.toRadians(CONE_HALF_ANGLE);
        Vec3 direction = axis.scale(Math.cos(cone))
                .add(side.scale(Math.sin(cone) * Math.cos(launchPhase)))
                .add(other.scale(Math.sin(cone) * Math.sin(launchPhase)))
                .normalize();
        if (!forward) {
            direction = direction.reverse();
        }
        return centre.add(direction.scale(travel));
    }

    /**
     * Jet speed in blocks per tick.
     *
     * <p>0.26c is 78000 km/s, which is meaningless at this scale, so the fraction is
     * reinterpreted: the jets cross their full length in the time it takes the disk to
     * precess a fifth of a turn. That ratio is what shapes the corkscrew's pitch, and it
     * is the only thing about the speed the eye can actually read.</p>
     */
    public static double blocksPerTick() {
        double litTicks = HOLD_END_TICK - SPINUP_END_TICK;
        double ticksPerTurn = litTicks / PRECESSION_CYCLES;
        return JET_LENGTH / (ticksPerTurn * 0.2D);
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
        this.terminalResolved = tag.getBoolean("TerminalResolved");
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
        tag.putBoolean("TerminalResolved", this.terminalResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
        tag.putFloat("Azimuth", this.entityData.get(DATA_AZIMUTH));
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

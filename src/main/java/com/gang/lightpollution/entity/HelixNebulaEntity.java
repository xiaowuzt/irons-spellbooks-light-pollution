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
 * Server-owned anchor for the Helix Nebula, NGC 7293 — the Eye of God.
 *
 * <p>A planetary nebula: the envelope a dying star has thrown off, lit from inside by the
 * white dwarf left behind. Two nested rings seen at an inclination give it the look of an
 * eye, and the shell between them is not smooth — it is full of cometary knots, each a
 * dense globule whose sunward face is being boiled off by the central star's ionizing
 * radiation, leaving a bright head and a tail streaming radially outward. About 40,000 of
 * them have been counted, each roughly 300 AU across, and the photoevaporated flow that
 * shapes them has been modelled from an ionizing output near 5e45 photons per second.</p>
 *
 * <p>Planetary nebulae expand, and that is what makes this mechanically unlike anything
 * else here: the hazard is a moving wavefront rather than a radius. Standing outside is
 * only temporary, standing inside means it has already passed, and the moment that matters
 * is when the shell arrives.</p>
 */
public final class HelixNebulaEntity extends Entity {
    public static final int LIFETIME_TICKS = 340;
    /** The white dwarf lights and the shell starts out. */
    public static final int IGNITION_END_TICK = 30;
    /** The shell reaches its full extent and the star lets go. */
    public static final int SHELL_END_TICK = 280;

    /** Where the shell starts, in blocks. */
    public static final double SHELL_START_RADIUS = 3.0D;
    /** Where the shell ends up, in blocks. */
    public static final double SHELL_END_RADIUS = 30.0D;
    /** Half-thickness of the shell, in blocks. The knots live in this layer. */
    public static final double SHELL_THICKNESS = 3.2D;
    /**
     * Ratio of the outer ring to the inner one.
     *
     * <p>The two rings are what make it an eye rather than a bubble. They are also
     * different colours in every image of the object, and for a reason worth keeping: the
     * inner ring glows in doubly ionised oxygen, which is blue-green, and the outer in
     * hydrogen and nitrogen, which is red. Not a gradient — two distinct shells.</p>
     */
    public static final double OUTER_RING_SCALE = 1.42D;
    /** Tilt of the rings from face-on, degrees. Near enough to give the eye its oval. */
    public static final double RING_INCLINATION = 37.0D;
    /**
     * Knots drawn.
     *
     * <p>Scaled down hard from the roughly 40,000 counted in the real nebula. At this
     * distance the real count would be sub-pixel and cost a fortune; what carries the
     * look is that every knot has an oriented tail, not how many there are.</p>
     */
    public static final int KNOT_COUNT = 560;

    public static final double HOVER_HEIGHT = 4.0D;
    public static final double EFFECT_RADIUS =
            SHELL_END_RADIUS * OUTER_RING_SCALE + SHELL_THICKNESS + 4.0D;

    /** Taken by the shell as it sweeps past, as a fraction of max health. */
    private static final float SHELL_DAMAGE_FRACTION = 0.072F;
    private static final int SHELL_INTERVAL_TICKS = 6;
    /** The central star's final collapse, as a fraction of max health. */
    private static final float COLLAPSE_DAMAGE_FRACTION = 0.42F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(HelixNebulaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(HelixNebulaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(HelixNebulaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(HelixNebulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(HelixNebulaEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean collapseResolved;
    /** Shell radius the last sweep was resolved at, so nothing is hit twice. */
    private double lastSweptRadius = -1.0D;

    public HelixNebulaEntity(EntityType<? extends HelixNebulaEntity> type, Level level) {
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
            // Held just short of the collapse, at full extent. Unlike the microquasar
            // there is nothing cyclic to watch here — the shell only grows — so looping it
            // would restart the expansion over and over instead of letting it be looked at.
            return Math.min(age, SHELL_END_TICK - 1.0F);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** Centre of the nebula, where the white dwarf sits. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * Radius of the inner ring at a given age, in blocks.
     *
     * <p>Eased so the shell leaves quickly and then coasts, which is how an ejected
     * envelope behaves once it is no longer being pushed.</p>
     */
    public double shellRadius(float ageTicks) {
        if (ageTicks <= IGNITION_END_TICK) {
            return SHELL_START_RADIUS;
        }
        double span = SHELL_END_TICK - IGNITION_END_TICK;
        double t = Mth.clamp((ageTicks - IGNITION_END_TICK) / span, 0.0D, 1.0D);
        double eased = 1.0D - Math.pow(1.0D - t, 2.2D);
        return SHELL_START_RADIUS + (SHELL_END_RADIUS - SHELL_START_RADIUS) * eased;
    }

    /** How far the white dwarf has lit, 0 to 1. */
    public float ignition(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / IGNITION_END_TICK);
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= SHELL_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - SHELL_END_TICK)
                / (float) (LIFETIME_TICKS - SHELL_END_TICK), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= IGNITION_END_TICK) {
            return ignition(partialTick);
        }
        if (age <= SHELL_END_TICK) {
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
            if (timelineTick > IGNITION_END_TICK && timelineTick < SHELL_END_TICK
                    && timelineTick % SHELL_INTERVAL_TICKS == 0) {
                resolveShell(serverLevel, timelineTick);
            }
            if (!this.collapseResolved && timelineTick >= SHELL_END_TICK) {
                this.collapseResolved = true;
                resolveCollapse(serverLevel);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /**
     * Damage whatever the shell has swept over since the last check.
     *
     * <p>Tested as a band the wavefront has crossed rather than a band it currently
     * occupies. The shell moves several blocks between checks near the start, so testing
     * only the instantaneous layer would let a target the front had passed clean through go
     * untouched — a hole in the hazard that is invisible and would look like the spell
     * randomly missing.</p>
     */
    private void resolveShell(ServerLevel level, float ageTicks) {
        double radius = shellRadius(ageTicks);
        double previous = this.lastSweptRadius < 0.0D
                ? SHELL_START_RADIUS
                : this.lastSweptRadius;
        this.lastSweptRadius = radius;

        double inner = Math.min(previous, radius) - SHELL_THICKNESS;
        double outer = Math.max(previous, radius) + SHELL_THICKNESS;

        LivingEntity caster = resolveCaster(level);
        DamageSource source = HelixNebulaDamage.shell(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - outer, centre.y - outer, centre.z - outer,
                        centre.x + outer, centre.y + outer, centre.z + outer))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            double distance = target.getBoundingBox().getCenter().distanceTo(centre);
            if (distance >= inner && distance <= outer) {
                applyTrueDamage(target, source, SHELL_DAMAGE_FRACTION);
            }
        }
    }

    /** The white dwarf's final collapse, out to the shell it has already blown. */
    private void resolveCollapse(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = HelixNebulaDamage.shell(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        double reach = SHELL_END_RADIUS * OUTER_RING_SCALE;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - reach, centre.y - reach, centre.z - reach,
                        centre.x + reach, centre.y + reach, centre.z + reach))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= reach) {
                applyTrueDamage(target, source, COLLAPSE_DAMAGE_FRACTION);
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
        this.collapseResolved = tag.getBoolean("CollapseResolved");
        this.lastSweptRadius = tag.contains("SweptRadius")
                ? tag.getDouble("SweptRadius") : -1.0D;
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
        tag.putBoolean("CollapseResolved", this.collapseResolved);
        tag.putDouble("SweptRadius", this.lastSweptRadius);
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

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
 * Server-owned anchor for the Crab Nebula, M1.
 *
 * <p>The remnant of the supernova recorded by Chinese and Japanese astronomers in 1054. It
 * is two things at once, and the fact that they are separate is what this effect is about: a
 * cage of filaments on the surface of an expanding shell, with nothing between them, and
 * inside that cage a synchrotron wind nebula fed by the pulsar at the centre — a different
 * colour, a different texture, and moving outward in wisps.</p>
 *
 * <p>This set has the World Tree, and a nebula of filaments could be mistaken for the same
 * fractal branching. It is not: the World Tree is a solid structure branching from a root,
 * while these filaments are a hollow cage over a void, connected to nothing. A player can
 * stand inside a Crab and there is only the wind; nobody can stand inside a tree.</p>
 *
 * <p>Which makes the mechanic a trap rather than a radius. Outside the cage is safe. The
 * filaments themselves sting. Inside is where the pulsar's wind is, and getting out means
 * crossing the cage again.</p>
 *
 * <p>Figures here are established results, not values verified in the session that wrote
 * this file.</p>
 */
public final class CrabNebulaEntity extends Entity {
    public static final int LIFETIME_TICKS = 320;
    /** The remnant unfolds and the cage closes. */
    public static final int FORM_END_TICK = 40;
    /** The pulsar drives its wind. This is the body of the spell. */
    public static final int WIND_END_TICK = 270;

    /** Radius of the shell when it has formed, in blocks. */
    public static final double SHELL_RADIUS = 21.0D;
    /**
     * How much the shell grows over its life, as a fraction.
     *
     * <p>Small on purpose. The real remnant expands at around 1500 km/s, which is fast in
     * absolute terms and slow next to its size — it has taken a thousand years to get where
     * it is. A shell that visibly raced outward would be a planetary nebula, which this set
     * already has; the Crab's character is that it hangs there.</p>
     */
    public static final double SHELL_GROWTH = 0.16D;
    /** Filaments in the cage. */
    public static final int FILAMENTS = 22;
    /** Half-width of a filament, in blocks. */
    public static final double FILAMENT_HALF_WIDTH = 0.55D;
    /** How close to a filament counts as touching it, in blocks. */
    public static final double FILAMENT_TOUCH_RADIUS = 1.9D;
    /** Radius of the interior wind nebula, as a fraction of the shell. */
    public static final double WIND_FRACTION = 0.72D;
    /** Ticks between wind pulses. The pulsar's own rhythm, slowed to be readable. */
    public static final int WIND_INTERVAL_TICKS = 10;

    public static final double HOVER_HEIGHT = 13.0D;
    public static final double EFFECT_RADIUS =
            SHELL_RADIUS * (1.0D + SHELL_GROWTH) + FILAMENT_TOUCH_RADIUS + 4.0D;

    /** Brushing a filament, as a fraction of max health. */
    private static final float FILAMENT_DAMAGE_FRACTION = 0.016F;
    private static final int FILAMENT_INTERVAL_TICKS = 7;
    /** A wind pulse, for anything inside the cage, as a fraction of max health. */
    private static final float WIND_DAMAGE_FRACTION = 0.026F;
    /** The remnant letting go at the end, as a fraction of max health. */
    private static final float COLLAPSE_DAMAGE_FRACTION = 0.30F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(CrabNebulaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(CrabNebulaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(CrabNebulaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(CrabNebulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(CrabNebulaEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean collapseResolved;

    public CrabNebulaEntity(EntityType<? extends CrabNebulaEntity> type, Level level) {
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
            return Math.min(age, WIND_END_TICK - 1.0F);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** Centre of the remnant, where the pulsar is. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Radius of the shell at a given age, in blocks. */
    public double shellRadius(float ageTicks) {
        float formed = Mth.clamp(ageTicks / (float) FORM_END_TICK, 0.0F, 1.0F);
        double drift = Mth.clamp((ageTicks - FORM_END_TICK)
                / (double) (WIND_END_TICK - FORM_END_TICK), 0.0D, 1.0D);
        return SHELL_RADIUS * (0.2D + 0.8D * smoothstep(formed))
                * (1.0D + SHELL_GROWTH * drift);
    }

    /** How far the remnant has unfolded, 0 to 1. */
    public float formed(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / FORM_END_TICK);
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= WIND_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - WIND_END_TICK)
                / (float) (LIFETIME_TICKS - WIND_END_TICK), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= FORM_END_TICK) {
            return formed(partialTick);
        }
        if (age <= WIND_END_TICK) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick));
    }

    /** Pulse of the wind nebula, 0 to 1, on the pulsar's rhythm. */
    public float windPulse(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        float phase = (age % WIND_INTERVAL_TICKS) / WIND_INTERVAL_TICKS;
        // Sharp rise, slow decay — a pulse, not a sine.
        return (float) Math.pow(1.0D - phase, 2.4D);
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * A point on one filament of the cage.
     *
     * <p>Each filament is an arc lying on the shell's surface, tilted and rotated by its
     * index so the set of them wraps the sphere from many directions. Staying on the surface
     * is the whole point: the interior has to be empty, because a hollow cage over a void is
     * what separates this from a branching solid.</p>
     *
     * @param fraction 0 to 1 along the filament
     */
    public Vec3 filamentPoint(Vec3 centre, int filament, double fraction, float ageTicks) {
        double radius = shellRadius(ageTicks);
        int seed = getSeed();

        // Two angles per filament, hashed off the synced seed, giving each its own plane.
        double lean = hash(seed, filament, 1) * Math.PI;
        double spin = hash(seed, filament, 2) * Math.PI * 2.0D;
        // Arcs cover part of a great circle rather than all of it, so the cage has openings.
        double span = Math.PI * (0.55D + 0.7D * hash(seed, filament, 3));
        double start = hash(seed, filament, 4) * Math.PI * 2.0D;
        double angle = start + span * fraction;

        // A great circle in a plane defined by lean and spin.
        Vec3 u = new Vec3(Math.cos(spin), 0.0D, Math.sin(spin));
        Vec3 w = new Vec3(-Math.sin(spin) * Math.cos(lean), Math.sin(lean),
                Math.cos(spin) * Math.cos(lean));
        // Filaments are not perfectly on the surface — they ripple, which is why the real
        // ones look like a tangle rather than a wireframe globe.
        double ripple = 1.0D + 0.09D * Math.sin(angle * 3.0D + filament);

        return centre.add(u.scale(Math.cos(angle) * radius * ripple))
                .add(w.scale(Math.sin(angle) * radius * ripple));
    }

    /** Stable hash in [0,1) from the synced seed, an index and a field selector. */
    public static double hash(int seed, int index, int field) {
        int h = seed * 73_856_093 ^ index * 19_349_663 ^ field * 83_492_791;
        h ^= h >>> 13;
        h *= 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > FORM_END_TICK && timelineTick < WIND_END_TICK) {
                if (timelineTick % FILAMENT_INTERVAL_TICKS == 0) {
                    resolveFilaments(serverLevel, timelineTick);
                }
                if (timelineTick % WIND_INTERVAL_TICKS == 0) {
                    resolveWind(serverLevel, timelineTick);
                }
            }
            if (!this.collapseResolved && timelineTick >= WIND_END_TICK) {
                this.collapseResolved = true;
                resolveCollapse(serverLevel);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /** Damage whatever is touching a filament of the cage. */
    private void resolveFilaments(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CrabNebulaDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - EFFECT_RADIUS, centre.y - EFFECT_RADIUS,
                        centre.z - EFFECT_RADIUS, centre.x + EFFECT_RADIUS,
                        centre.y + EFFECT_RADIUS, centre.z + EFFECT_RADIUS));
        if (targets.isEmpty()) {
            return;
        }

        double touchSqr = (FILAMENT_TOUCH_RADIUS + FILAMENT_HALF_WIDTH)
                * (FILAMENT_TOUCH_RADIUS + FILAMENT_HALF_WIDTH);
        int samples = 16;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            boolean touching = false;
            for (int filament = 0; filament < FILAMENTS && !touching; ++filament) {
                for (int i = 0; i <= samples; ++i) {
                    if (filamentPoint(centre, filament, i / (double) samples, ageTicks)
                            .distanceToSqr(at) <= touchSqr) {
                        touching = true;
                        break;
                    }
                }
            }
            if (touching) {
                applyTrueDamage(target, source, FILAMENT_DAMAGE_FRACTION);
            }
        }
    }

    /** The pulsar's wind, for anything inside the cage. */
    private void resolveWind(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CrabNebulaDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        double reach = shellRadius(ageTicks) * WIND_FRACTION;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - reach, centre.y - reach, centre.z - reach,
                        centre.x + reach, centre.y + reach, centre.z + reach))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= reach) {
                applyTrueDamage(target, source, WIND_DAMAGE_FRACTION);
            }
        }
    }

    /** The remnant letting go, out to the whole shell. */
    private void resolveCollapse(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CrabNebulaDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        double reach = shellRadius(WIND_END_TICK);

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

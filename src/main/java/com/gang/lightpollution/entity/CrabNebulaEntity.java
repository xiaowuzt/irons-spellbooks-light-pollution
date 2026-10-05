package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.performance.PulseShapeSamples;

import com.gang.lightpollution.api.CrabNebulaParams;
import com.gang.lightpollution.fx.CrabNebulaShape;
import com.gang.lightpollution.fx.CrabNebulaSource;
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
public final class CrabNebulaEntity extends Entity implements CrabNebulaSource {
    private static final String CONFIG_ID = "crabNebula";

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

    private static int configuredFormEnd() {
        return SpellConfig.phaseTick(CONFIG_ID, 1);
    }

    private static int configuredWindEnd() {
        return SpellConfig.phaseTick(CONFIG_ID, 2);
    }
    public static final int LIFETIME_TICKS = CrabNebulaParams.SPELL_LIFETIME_TICKS;

    /** How close to a filament counts as touching it, in blocks. */
    public static final double FILAMENT_TOUCH_RADIUS = 1.9D;

    public static final double HOVER_HEIGHT = 13.0D;
    public static final double EFFECT_RADIUS =
            CrabNebulaShape.SHELL_RADIUS * (1.0D + CrabNebulaShape.SHELL_GROWTH) + FILAMENT_TOUCH_RADIUS + 4.0D;

    /** Brushing a filament, as a fraction of max health. */
    private static final float FILAMENT_DAMAGE_FRACTION = 0.016F;
    private static final int FILAMENT_INTERVAL_TICKS = 7;
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
            return Math.min(age, configuredWindEnd() - 1.0F);
        }
        return Math.min(configuredLifetime(), age);
    }

    /** Centre of the remnant, where the pulsar is. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Radius of the shell at a given age, in blocks. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public CrabNebulaParams shapeParams() {
        return CrabNebulaParams.of(getSeed());
    }

    public double shellRadius(float ageTicks) {
        return CrabNebulaShape.shellRadius(shapeParams(), ageTicks);
    }

    /** How far the remnant has unfolded, 0 to 1. */
    public float formed(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / configuredFormEnd());
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= configuredWindEnd()) {
            return 0.0F;
        }
        return Mth.clamp((age - configuredWindEnd())
                / (float) (configuredLifetime() - configuredWindEnd()), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= configuredFormEnd()) {
            return formed(partialTick);
        }
        if (age <= configuredWindEnd()) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick));
    }

    /** Pulse of the wind nebula, 0 to 1, on the pulsar's rhythm. */
    public float windPulse(float partialTick) {
        return CrabNebulaShape.windPulse(getVisualAgeTicks(partialTick));
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * A point on one filament of the cage.
     *
     * <p>Each filament is a closed loop on the shell's surface, tilted and rotated by its index so
     * the set of them wraps the sphere from many directions. Staying on the surface is the whole
     * point: the interior has to be empty, because a hollow cage over a void is what separates this
     * from a branching solid.</p>
     *
     * <p>Closed rather than an arc, which is what it was. Open arcs left every filament with two
     * loose ends hanging in the middle of the shell, and twenty-two of those read as debris rather
     * than as a cage.</p>
     *
     * <p>Closing them cannot mean making them circles, though — twenty-two great circles is a
     * wireframe globe, which is exactly what the real remnant does not look like. So each loop
     * weaves out of its own plane on whole harmonics of the turn. Whole ones specifically: a
     * fractional harmonic would not return to its starting value after a full turn, so the loop
     * would arrive back at its start pointing somewhere else.</p>
     *
     * @param fraction 0 to 1 around the loop; 1 is the same point as 0
     */
    public Vec3 filamentPoint(Vec3 centre, int filament, double fraction, float ageTicks) {
        return CrabNebulaShape.filamentPoint(shapeParams(), centre, filament, fraction, ageTicks);
    }


    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > configuredFormEnd() && timelineTick < configuredWindEnd()) {
                if (timelineTick % configuredInterval() == 0) {
                    resolveFilaments(serverLevel, timelineTick);
                }
                if (timelineTick % CrabNebulaShape.WIND_INTERVAL_TICKS == 0) {
                    resolveWind(serverLevel, timelineTick);
                }
            }
            if (!this.collapseResolved && timelineTick >= configuredWindEnd()) {
                this.collapseResolved = true;
                resolveCollapse(serverLevel);
            }
            if (timelineTick >= configuredLifetime()) {
                this.discard();
            }
        }
    }

    /** Damage whatever is touching a filament of the cage. */
    private void resolveFilaments(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CrabNebulaDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        List<LivingEntity> targets = SpellConfig.limitTargets("crabNebula", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - configuredRadius(), centre.y - configuredRadius(),
                        centre.z - configuredRadius(), centre.x + configuredRadius(),
                        centre.y + configuredRadius(), centre.z + configuredRadius())));
        if (targets.isEmpty()) {
            return;
        }

        double touchSqr = (FILAMENT_TOUCH_RADIUS + CrabNebulaShape.FILAMENT_HALF_WIDTH)
                * (FILAMENT_TOUCH_RADIUS + CrabNebulaShape.FILAMENT_HALF_WIDTH);
        // Enough samples that consecutive ones are closer together than the touch radius.
        // A closed loop at this radius is around 130 blocks long, so 48 puts them under three
        // blocks apart; the 16 that covered the old half-arcs would leave gaps a player could
        // stand in while visibly inside a filament.
        // Pulse-local: the cage is identical for every target. Populate on demand so an
        // early hit (or a list containing only ineligible targets) stays cheap too.
        var samples = PulseShapeSamples.crab(centre, ageTicks, touchSqr);
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            CrabNebulaParams params = shapeParams();
            Vec3 at = target.getBoundingBox().getCenter();
            if (samples.firstHit(params, at) >= 0) {
                SpellDamage.apply(this, target, source, configuredPrimaryDamage());
            }
        }
    }

    /** The pulsar's wind, for anything inside the cage. */
    private void resolveWind(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CrabNebulaDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        double reach = shellRadius(ageTicks) * CrabNebulaShape.WIND_FRACTION;

        for (LivingEntity target : SpellConfig.limitTargets("crabNebula", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - reach, centre.y - reach, centre.z - reach,
                        centre.x + reach, centre.y + reach, centre.z + reach)))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= reach) {
                SpellDamage.apply(this, target, source,
                        (float) SpellConfig.crabNebulaWindDamageFraction);
            }
        }
    }

    /** The remnant letting go, out to the whole shell. */
    private void resolveCollapse(ServerLevel level) {
        // Announced before anything else in here, including the early return when
        // nothing is in range: the event happened regardless of whether it hit.
        com.gang.lightpollution.net.ModNetwork.sendCaption(level,
                this.position().add(0.0D, HOVER_HEIGHT, 0.0D),
                "caption.irons_spellbooks_light_pollution.crab_nebula.letgo",
                com.gang.lightpollution.SpellPalette.accentFor(this), 1.6F);
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CrabNebulaDamage.source(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        double reach = shellRadius(configuredWindEnd());

        for (LivingEntity target : SpellConfig.limitTargets("crabNebula", level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - reach, centre.y - reach, centre.z - reach,
                        centre.x + reach, centre.y + reach, centre.z + reach)))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= reach) {
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

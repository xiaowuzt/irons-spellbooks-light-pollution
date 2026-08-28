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
 * Server-owned anchor for the Cosmic Horseshoe.
 *
 * <p>SDSS J1148+1930, discovered by Belokurov et al. in SDSS DR5 (2007). It is not one
 * object but an alignment of two: a luminous red galaxy at redshift 0.444 whose mass
 * bends the light of a star-forming galaxy at redshift 2.379 behind it into an almost
 * complete ring. The discovery paper measures the ring at ten arcseconds across and
 * the arc at about three hundred degrees — and it is that missing sixty degrees that
 * gives the thing its name.</p>
 *
 * <p>Unlike this set's other lenses it does not swallow. A gravitational lens
 * magnifies: the tangential magnification diverges at the Einstein radius, so light
 * arriving there is focused rather than lost. Everything below follows from that. The
 * ring itself is the dangerous part, the interior is not, and the other spells' light
 * inside it gets brighter instead of being drained.</p>
 *
 * <p>Mass within the Einstein radius is (5.02 +/- 0.09)e12 solar masses within a
 * projected 30 kpc (Dye et al. 2008); the lens galaxy's line-of-sight velocity
 * dispersion exceeds 400 km/s, and in 2025 its centre was measured to hold a 36
 * billion solar mass black hole.</p>
 */
public final class CosmicHorseshoeEntity extends Entity {
    public static final int LIFETIME_TICKS = 360;
    /** The alignment closes and the arc lights up. */
    public static final int ALIGN_END_TICK = 50;
    /** It stands, focusing. Nothing about the geometry changes through this. */
    public static final int HOLD_END_TICK = 300;

    /**
     * The Einstein radius, in blocks. Every other length here is a multiple of it.
     *
     * <p>The real ring is ten arcseconds across, which is an angle rather than a size,
     * so there is nothing to convert. This is chosen instead so that a player standing
     * near the axis sees the ring fill a good part of the view without the arc leaving
     * the screen.</p>
     */
    public static final float EINSTEIN_RADIUS = 11.0F;
    /** Half-thickness of the damaging band, in blocks. */
    public static final float RING_HALF_WIDTH = 1.6F;
    /** How far above the aimed point the lens hangs, in blocks. */
    public static final double HOVER_HEIGHT = 13.0D;
    public static final double EFFECT_RADIUS = EINSTEIN_RADIUS + RING_HALF_WIDTH + 4.0D;

    /**
     * Radius of the synthetic source galaxy, in units of the Einstein radius.
     *
     * <p>Jones et al. (2018) resolve the source into four star-forming regions of
     * 4-8 kpc^2 each. Regions that size are roughly 2-3 kpc across, and a galaxy
     * holding four of them is 10-15 kpc across, so each clump covers about a fifth of
     * the source. That ratio is what the shader needs; the absolute scale is set here.
     * </p>
     */
    public static final float SOURCE_RADIUS = 0.26F;
    /**
     * How far the source sits off the optical axis, in units of the Einstein radius.
     *
     * <p>This single number decides how much of a ring there is. For a circular lens the
     * ring is complete if and only if the source covers the axis, so the arc closes below
     * a ratio of one and breaks above it — but the ellipticity that makes this a horseshoe
     * rather than two matching arcs shortens the arc a great deal on top of that, and it
     * has to be measured rather than predicted. At a ratio of 1.14 the arc came out around
     * 100 degrees, not the 240 the circular formula suggests. 0.69 of the source radius is
     * the correction for that, aiming at the real arc's ~300 degrees with a ~60 degree
     * gap, which is the acceptance test.</p>
     */
    public static final float SOURCE_OFFSET = 0.13F;

    /** Focused light along the ring, as a fraction of max health. */
    private static final float RING_DAMAGE_FRACTION = 0.028F;
    private static final int RING_INTERVAL_TICKS = 15;
    /**
     * The release when the alignment lets go, as a fraction of max health.
     *
     * <p>Along the ring only, not a sphere. The interior of an Einstein ring is the one
     * place the focused light never reaches, and a spell whose whole identity is that
     * the ring is the dangerous part cannot end by detonating everywhere at once.</p>
     */
    private static final float COLLAPSE_DAMAGE_FRACTION = 0.33F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.INT);
    /**
     * World-space direction the gap in the arc faces.
     *
     * <p>A deliberate departure from the real geometry, and worth being explicit about.
     * The source plane is perpendicular to the observer's line of sight, so a source at
     * a fixed point in space presents a different offset to every viewing position — walk
     * far enough around a real lens and the alignment is lost and the ring is simply
     * gone. That is unusable for a spell, so the offset's magnitude is held constant and
     * only its direction is taken from the world: this vector, projected onto the view
     * plane, orients the gap. The ring therefore never breaks up or seals, but circling
     * it does swing the opening around, which is the part a player can read.</p>
     */
    private static final EntityDataAccessor<Float> DATA_GAP_X =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GAP_Y =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GAP_Z =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(CosmicHorseshoeEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean collapseResolved;

    public CosmicHorseshoeEntity(EntityType<? extends CosmicHorseshoeEntity> type,
                                 Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 centre, Vec3 gapDirection, int seed) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.moveTo(centre.x, centre.y - HOVER_HEIGHT, centre.z, 0.0F, 0.0F);
        Vec3 gap = gapDirection.lengthSqr() < 1.0e-6D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : gapDirection.normalize();
        this.entityData.set(DATA_GAP_X, (float) gap.x);
        this.entityData.set(DATA_GAP_Y, (float) gap.y);
        this.entityData.set(DATA_GAP_Z, (float) gap.z);
    }

    /** Stands a lens up with no caster, no damage and no expiry, for looking at. */
    public void configureDisplay(Vec3 centre, Vec3 gapDirection, int seed) {
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_DISPLAY, true);
        this.moveTo(centre.x, centre.y - HOVER_HEIGHT, centre.z, 0.0F, 0.0F);
        Vec3 gap = gapDirection.lengthSqr() < 1.0e-6D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : gapDirection.normalize();
        this.entityData.set(DATA_GAP_X, (float) gap.x);
        this.entityData.set(DATA_GAP_Y, (float) gap.y);
        this.entityData.set(DATA_GAP_Z, (float) gap.z);
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

    public Vec3 gapDirection() {
        return new Vec3(
                this.entityData.get(DATA_GAP_X),
                this.entityData.get(DATA_GAP_Y),
                this.entityData.get(DATA_GAP_Z));
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null && this.casterUuid != null
                && caster.getUUID().equals(this.casterUuid);
    }

    public int getTimelineAgeTicks() {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        if (startGameTick < 0L) {
            return Math.min(LIFETIME_TICKS, Math.max(0, this.tickCount));
        }
        long age = this.level().getGameTime() - startGameTick;
        return (int) Math.min(LIFETIME_TICKS, Math.max(0L, age));
    }

    public float getVisualAgeTicks(float partialTick) {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        float age = startGameTick < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - startGameTick) + partialTick;
        age = Math.max(0.0F, age);
        if (isDisplay()) {
            return Math.min(age, HOLD_END_TICK - 1.0F);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** Centre of the lens galaxy. It hangs above the aimed point rather than at it. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** How far the alignment has closed, 0 to 1. */
    public float aligned(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / ALIGN_END_TICK);
    }

    /** How far into the closing fade it is, 0 to 1. */
    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= HOLD_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - HOLD_END_TICK)
                / (float) (LIFETIME_TICKS - HOLD_END_TICK), 0.0F, 1.0F);
    }

    /** Overall brightness envelope. */
    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= ALIGN_END_TICK) {
            return aligned(partialTick);
        }
        if (age <= HOLD_END_TICK) {
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
            if (timelineTick > ALIGN_END_TICK && timelineTick < HOLD_END_TICK
                    && timelineTick % RING_INTERVAL_TICKS == 0) {
                resolveRing(serverLevel, RING_DAMAGE_FRACTION);
            }
            if (!this.collapseResolved && timelineTick >= HOLD_END_TICK) {
                this.collapseResolved = true;
                resolveRing(serverLevel, COLLAPSE_DAMAGE_FRACTION);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /**
     * Damage everything standing on the ring, and nothing standing inside it.
     *
     * <p>Distance is measured from the vertical axis through the lens, not from its
     * centre, because the ring the shader draws is a circle on the sky about that axis.
     * A target's height above the ground does not change whether it is on the ring.</p>
     */
    private void resolveRing(ServerLevel level, float fraction) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = CosmicHorseshoeDamage.focusedLight(level, caster, this);
        Vec3 axis = this.position();
        float inner = EINSTEIN_RADIUS - RING_HALF_WIDTH;
        float outer = EINSTEIN_RADIUS + RING_HALF_WIDTH;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(axis.x - outer, axis.y - EFFECT_RADIUS, axis.z - outer,
                        axis.x + outer, axis.y + EFFECT_RADIUS, axis.z + outer));
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            double dx = target.getX() - axis.x;
            double dz = target.getZ() - axis.z;
            double planar = Math.sqrt(dx * dx + dz * dz);
            if (planar < inner || planar > outer) {
                continue;
            }
            applyTrueDamage(target, source, fraction);
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
        this.entityData.define(DATA_GAP_X, 1.0F);
        this.entityData.define(DATA_GAP_Y, 0.0F);
        this.entityData.define(DATA_GAP_Z, 0.0F);
        this.entityData.define(DATA_DISPLAY, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.collapseResolved = tag.getBoolean("CollapseResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_DISPLAY, tag.getBoolean("Display"));
        this.entityData.set(DATA_GAP_X, tag.getFloat("GapX"));
        this.entityData.set(DATA_GAP_Y, tag.getFloat("GapY"));
        this.entityData.set(DATA_GAP_Z, tag.getFloat("GapZ"));
        long startGameTick = tag.contains("StartGameTick")
                ? tag.getLong("StartGameTick")
                : this.level().getGameTime() - Math.max(0, tag.getInt("TimelineTick"));
        this.entityData.set(DATA_START_GAME_TICK, startGameTick);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putBoolean("CollapseResolved", this.collapseResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
        tag.putBoolean("Display", this.isDisplay());
        Vec3 gap = gapDirection();
        tag.putFloat("GapX", (float) gap.x);
        tag.putFloat("GapY", (float) gap.y);
        tag.putFloat("GapZ", (float) gap.z);
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

package com.gang.lightpollution.entity;

import com.gang.lightpollution.api.ConstellationParams;
import com.gang.lightpollution.fx.ConstellationShape;
import com.gang.lightpollution.fx.ConstellationSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Server-owned anchor for the 12 second Constellation timeline.
 *
 * <p>A single real star orbits the aimed point, low and wide enough that it
 * throws a long moving shadow from every occluder it passes. It is one bright
 * orbiting light, which is exactly the case the shading pass turns into a
 * sweeping cast shadow.</p>
 *
 * <p>The star's gravity drags living things toward it and its surface burns
 * whatever it pulls in, so the threat is the whole time it is up rather than one
 * moment at the end. It never touches the ground: when the timeline runs out it
 * simply burns out.</p>
 *
 * <p>The star position is a pure function of the timeline, so the world
 * renderer, the light emitter and the server-side pull all read the same point
 * and stay in lockstep without any extra syncing.</p>
 */
public final class ConstellationEntity extends Entity implements ConstellationSource {
    // The form lives in ConstellationShape, which the renderer and the public API both read, so
    // there is one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = ConstellationParams.SPELL_LIFETIME_TICKS;

    /** How far the star's gravity reaches, in blocks. */
    public static final double PULL_RADIUS = 14.0D;
    /** Bound used to gather candidates; the ring plus the pull's full reach. */
    public static final double EFFECT_RADIUS = ConstellationShape.RING_RADIUS + PULL_RADIUS + 1.0D;

    /**
     * Strength of the pull at the star itself, in blocks per tick added to a
     * target's velocity. It falls off to nothing at {@link #PULL_RADIUS}.
     */
    private static final double PULL_ACCELERATION = 0.11D;
    /** Ticks between burn applications, so the damage is a rate and not a spike. */
    private static final int BURN_INTERVAL_TICKS = 10;
    /** Max-health fraction per burn at the very edge of the pull. */
    private static final float BURN_MIN_FRACTION = 0.010F;
    /** Max-health fraction per burn for anything held against the star. */
    private static final float BURN_MAX_FRACTION = 0.052F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            ConstellationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            ConstellationEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_SPIN_OFFSET = SynchedEntityData.defineId(
            ConstellationEntity.class, EntityDataSerializers.FLOAT);

    private UUID casterUuid;
    /** The creature the anchor follows, or null when the spell hit terrain. */
    private UUID targetUuid;

    public ConstellationEntity(EntityType<? extends ConstellationEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, float spinOffset,
                          @org.jetbrains.annotations.Nullable LivingEntity target) {
        this.casterUuid = caster.getUUID();
        this.targetUuid = target == null ? null : target.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SPIN_OFFSET, spinOffset);
        this.setPos(center.x, center.y, center.z);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
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
        return Math.min(LIFETIME_TICKS, Math.max(0.0F, age));
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
    }

    /** Anchor centre, interpolated for smooth rendering. */
    public Vec3 anchorCenter(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * World position of the star. Pure function of the timeline, so the renderer,
     * the light emitter and the server-side pull all read the same point.
     *
     * <p>It descends into orbit and then circles the anchor for the rest of its
     * life, including while it fades. It never reaches the ground.</p>
     */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public ConstellationParams shapeParams() {
        return ConstellationParams.of(this.entityData.get(DATA_SPIN_OFFSET));
    }

    public Vec3 starPosition(int star, float partialTick) {
        return ConstellationShape.starPosition(shapeParams(), anchorCenter(partialTick),
                getVisualAgeTicks(partialTick), LIFETIME_TICKS);
    }

    /** Star brightness envelope, 0 to 1: fades in, holds, then burns out. */
    public float starBrightness(int star, float partialTick) {
        return ConstellationShape.starBrightness(getVisualAgeTicks(partialTick), LIFETIME_TICKS);
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            // The orbit and the pull both read the anchor, so moving it here is
            // enough to make the whole spell track its mark.
            followTarget(serverLevel);
            if (timelineTick >= ConstellationShape.GATHER_END_TICK) {
                // Every tick, so the pull is a continuous force rather than a
                // series of shoves.
                applyPull(serverLevel);
                if (timelineTick % BURN_INTERVAL_TICKS == 0) {
                    resolveBurn(serverLevel);
                }
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /**
     * Keeps the anchor on the tracked creature for as long as it lives. Once it
     * dies the anchor stays where it last was, so the star keeps burning over the
     * spot.
     */
    private void followTarget(ServerLevel level) {
        if (this.targetUuid == null) {
            return;
        }
        if (level.getEntity(this.targetUuid) instanceof LivingEntity target
                && target.isAlive()) {
            this.setPos(target.getX(), target.getY(), target.getZ());
        }
    }

    /**
     * Drags everything in reach toward the star.
     *
     * <p>Applied as an addition to velocity rather than a teleport so vanilla
     * still resolves collisions: a target being pulled through a wall slides
     * along it instead of passing through. The pull is strongest at the star and
     * reaches zero at {@link #PULL_RADIUS}, so the edge of the field is somewhere
     * a target can still walk out of.</p>
     */
    private void applyPull(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        for (int star = 0; star < ConstellationShape.STAR_COUNT; star++) {
            float brightness = starBrightness(star, 1.0F);
            if (brightness <= 0.05F) {
                continue;
            }
            Vec3 centre = starPosition(star, 1.0F);
            for (LivingEntity target : pullCandidates(level, caster, centre)) {
                Vec3 toStar = centre.subtract(target.getBoundingBox().getCenter());
                double distance = toStar.length();
                if (distance < 0.001D) {
                    continue;
                }
                // Linear falloff, scaled by the star's own brightness so the pull
                // dies away with the light as it burns out.
                double strength = PULL_ACCELERATION * brightness
                        * (1.0D - distance / PULL_RADIUS);
                if (strength <= 0.0D) {
                    continue;
                }
                target.setDeltaMovement(target.getDeltaMovement()
                        .add(toStar.scale(strength / distance)));
                // Without this the client keeps predicting its own movement and
                // fights the pull, which reads as stuttering rather than being
                // dragged.
                target.hurtMarked = true;
                // A player's own client owns its movement, and hurtMarked only
                // reaches the players tracking it, never the player being moved.
                // Vanilla has the same problem and solves it the same way.
                if (target instanceof ServerPlayer player) {
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
            }
        }
    }

    /** Burns everything the star is holding, harder the closer it is held. */
    private void resolveBurn(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        for (int star = 0; star < ConstellationShape.STAR_COUNT; star++) {
            if (starBrightness(star, 1.0F) <= 0.05F) {
                continue;
            }
            Vec3 centre = starPosition(star, 1.0F);
            List<LivingEntity> caught = pullCandidates(level, caster, centre);
            if (caught.isEmpty()) {
                continue;
            }
            DamageSource source = ConstellationDamage.source(level, this, caster);
            for (LivingEntity target : caught) {
                double distance = centre.distanceTo(
                        target.getBoundingBox().getCenter());
                // Squared so the damage climbs steeply only near the surface: the
                // outer field is a nuisance, being held against the star is not.
                double closeness = 1.0D - Mth.clamp(distance / PULL_RADIUS, 0.0D, 1.0D);
                float fraction = (float) Mth.lerp(closeness * closeness,
                        BURN_MIN_FRACTION, BURN_MAX_FRACTION);
                SpellDamage.apply(this, target, source, fraction);
                target.setSecondsOnFire(closeness > 0.5D ? 6 : 3);
            }
        }
    }

    private List<LivingEntity> pullCandidates(ServerLevel level, LivingEntity caster,
                                              Vec3 centre) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - PULL_RADIUS, centre.y - PULL_RADIUS,
                        centre.z - PULL_RADIUS, centre.x + PULL_RADIUS,
                        centre.y + PULL_RADIUS, centre.z + PULL_RADIUS),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                <= PULL_RADIUS * PULL_RADIUS);
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
        this.entityData.define(DATA_SPIN_OFFSET, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.targetUuid = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SPIN_OFFSET, tag.getFloat("SpinOffset"));
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
        if (this.targetUuid != null) {
            tag.putUUID("Target", this.targetUuid);
        }
        tag.putInt("CasterId", this.getCasterId());
        tag.putFloat("SpinOffset", this.entityData.get(DATA_SPIN_OFFSET));
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

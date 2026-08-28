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
 * Server-owned anchor for Stellar Convergence.
 *
 * <p>Nine coloured stars light one after another, hanging at different heights
 * and bearings. Each one is a separate light, so the ground under them carries
 * nine overlapping coloured shadows that drift as they move — the one thing the
 * shading pass can do that no spell in the set had used. Then filaments link them
 * into a net, the net contracts, and all nine fire into the centre at once.</p>
 *
 * <p>Star positions are pure functions of the timeline and the synchronized seed,
 * so the renderer, the light emitter and the server damage all agree.</p>
 */
public final class StellarConvergenceEntity extends Entity {
    public static final int STAR_COUNT = 9;
    public static final int LIFETIME_TICKS = 260;
    /** Ticks between successive stars lighting. */
    public static final int STAR_STAGGER_TICKS = 8;
    /** All nine are lit by here. */
    public static final int LIT_END_TICK = STAR_COUNT * STAR_STAGGER_TICKS;
    /** Filaments start linking the constellation. */
    public static final int WEAVE_START_TICK = 90;
    /** The net finishes contracting and the beams fire. */
    public static final int BEAM_START_TICK = 150;
    /** Beams hold, then the column detonates. */
    public static final int BURST_TICK = 190;
    public static final int FADE_START_TICK = 220;

    /** Radius of the constellation, in blocks. */
    public static final float SHELL_RADIUS = 22.0F;
    /** Height of the constellation's centre above the anchor, in blocks. */
    public static final float SHELL_HEIGHT = 26.0F;
    /** Visual radius of one star, in blocks. */
    public static final float STAR_RADIUS = 1.9F;
    /** Radius of the damaging column, in blocks. */
    public static final double COLUMN_RADIUS = 6.0D;
    /** Bound used to gather candidates. */
    public static final double EFFECT_RADIUS = SHELL_RADIUS + COLUMN_RADIUS + 2.0D;

    /** Max-health fraction per beam tick while the column holds. */
    private static final float BEAM_DAMAGE_FRACTION = 0.033F;
    /** Ticks between beam damage applications. */
    private static final int BEAM_INTERVAL_TICKS = 10;
    /** Max-health fraction of the final burst. */
    private static final float BURST_DAMAGE_FRACTION = 0.44F;
    /** Radius of the final burst, in blocks. */
    private static final double BURST_RADIUS = 12.0D;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            StellarConvergenceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            StellarConvergenceEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            StellarConvergenceEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean burstResolved;

    public StellarConvergenceEntity(EntityType<? extends StellarConvergenceEntity> entityType,
                                    Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, int seed) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.setPos(center.x, center.y, center.z);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
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

    /** Centre of the constellation, and the point the beams converge on. */
    public Vec3 shellCentre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + SHELL_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** Where the column meets the ground. */
    public Vec3 groundCentre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    public static int litTick(int star) {
        return star * STAR_STAGGER_TICKS;
    }

    /**
     * World position of a star. They sit on a sphere around the centre, spread by
     * a Fibonacci lattice so the nine of them look deliberately placed rather
     * than randomly scattered, and drift slowly so their shadows sweep.
     */
    public Vec3 starPosition(int star, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        Vec3 centre = shellCentre(partialTick);

        // Golden-angle lattice: even coverage of the sphere for any count.
        float latitude = 1.0F - 2.0F * (star + 0.5F) / STAR_COUNT;
        float ringRadius = Mth.sqrt(Math.max(0.0F, 1.0F - latitude * latitude));
        float angle = star * 2.39996323F
                + hashUnit(star, 0x9E3779B9L) * Mth.TWO_PI * 0.15F
                // The slow drift. This is what makes the coloured shadows move.
                + age * 0.004F;

        float shell = SHELL_RADIUS;
        // Only the upper half: stars below the centre would be underground.
        float y = Math.abs(latitude) * 0.75F;
        Vec3 seat = centre.add(
                Mth.cos(angle) * ringRadius * shell,
                y * shell,
                Mth.sin(angle) * ringRadius * shell);

        int lit = litTick(star);
        if (age < lit) {
            return seat;
        }
        if (age < BEAM_START_TICK) {
            // Arriving: eases in from further out along its own bearing.
            float settle = smoothstep(Math.min((age - lit) / 26.0F, 1.0F));
            Vec3 far = centre.add(seat.subtract(centre).scale(2.1D));
            Vec3 arrival = far.lerp(seat, settle);
            if (age < WEAVE_START_TICK) {
                return arrival;
            }
            // The net contracts, drawing them inward before they fire.
            float pull = smoothstep((age - WEAVE_START_TICK)
                    / (float) (BEAM_START_TICK - WEAVE_START_TICK));
            return arrival.lerp(centre.add(seat.subtract(centre).scale(0.62D)), pull);
        }
        return centre.add(seat.subtract(centre).scale(0.62D));
    }

    /** Colour of a star, spread around the spectrum by index. */
    public float[] starColour(int star) {
        // Evenly spaced hues, so nine distinct coloured shadows overlap rather
        // than nine of the same tint.
        float hue = star / (float) STAR_COUNT;
        return hueToRgb(hue);
    }

    /** Brightness of a star, 0 before it lights. */
    public float starBrightness(int star, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        int lit = litTick(star);
        if (age < lit) {
            return 0.0F;
        }
        if (age < BEAM_START_TICK) {
            return Math.min(1.0F, (age - lit) / 14.0F);
        }
        if (age < BURST_TICK) {
            // Pouring themselves into the column.
            return 1.0F + (age - BEAM_START_TICK) * 0.02F;
        }
        float since = age - BURST_TICK;
        return Math.max(0.0F, 1.6F - since * 0.06F);
    }

    /** How far the linking filaments have grown, 0 to 1. */
    public float weaveProgress(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < WEAVE_START_TICK) {
            return 0.0F;
        }
        if (age >= BURST_TICK) {
            return Math.max(0.0F, 1.0F - (age - BURST_TICK) / 20.0F);
        }
        return Mth.clamp((age - WEAVE_START_TICK)
                / (float) (BEAM_START_TICK - WEAVE_START_TICK), 0.0F, 1.0F);
    }

    /** Strength of the converged column, 0 before it fires. */
    public float columnStrength(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < BEAM_START_TICK) {
            return 0.0F;
        }
        if (age < BURST_TICK) {
            return smoothstep((age - BEAM_START_TICK) / 12.0F);
        }
        float since = age - BURST_TICK;
        // Flares on the burst, then dies with the spell.
        return Math.max(0.0F, 2.4F - since * 0.09F);
    }

    /** Burst flash, 0 outside the moment the column detonates. */
    public float burstFlash(float partialTick) {
        float since = getVisualAgeTicks(partialTick) - BURST_TICK;
        if (since < 0.0F || since > 16.0F) {
            return 0.0F;
        }
        return 1.0F - since / 16.0F;
    }

    private static float[] hueToRgb(float hue) {
        float h = (hue % 1.0F) * 6.0F;
        float x = 1.0F - Math.abs(h % 2.0F - 1.0F);
        return switch ((int) h) {
            case 0 -> new float[] {1.0F, x, 0.0F};
            case 1 -> new float[] {x, 1.0F, 0.0F};
            case 2 -> new float[] {0.0F, 1.0F, x};
            case 3 -> new float[] {0.0F, x, 1.0F};
            case 4 -> new float[] {x, 0.0F, 1.0F};
            default -> new float[] {1.0F, 0.0F, x};
        };
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private float hashUnit(int star, long salt) {
        long hash = (getSeed() & 0xFFFFFFFFL) * 0x2545F4914F6CDD1DL
                ^ (star + 1L) * salt;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return (float) ((hash >>> 1) / (double) Long.MAX_VALUE);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick >= BEAM_START_TICK && timelineTick < BURST_TICK
                    && timelineTick % BEAM_INTERVAL_TICKS == 0) {
                resolveColumn(serverLevel);
            }
            if (!this.burstResolved && timelineTick >= BURST_TICK) {
                this.burstResolved = true;
                resolveBurst(serverLevel);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /** Anything standing in the column while it holds. */
    private void resolveColumn(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 ground = groundCentre(1.0F);
        // A column, not a sphere: it runs from the ground up to the constellation,
        // so height should not exempt anyone inside it.
        AABB bounds = new AABB(
                ground.x - COLUMN_RADIUS, ground.y - 2.0D, ground.z - COLUMN_RADIUS,
                ground.x + COLUMN_RADIUS, ground.y + SHELL_HEIGHT, ground.z + COLUMN_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, bounds,
                target -> canAffect(caster, target) && withinColumn(target, ground));
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = StellarConvergenceDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            applyTrueDamage(target, source, BEAM_DAMAGE_FRACTION);
        }
    }

    private boolean withinColumn(LivingEntity target, Vec3 ground) {
        Vec3 centre = target.getBoundingBox().getCenter();
        double dx = centre.x - ground.x;
        double dz = centre.z - ground.z;
        return dx * dx + dz * dz <= COLUMN_RADIUS * COLUMN_RADIUS;
    }

    private void resolveBurst(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 ground = groundCentre(1.0F);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(ground.x - BURST_RADIUS, ground.y - BURST_RADIUS,
                        ground.z - BURST_RADIUS, ground.x + BURST_RADIUS,
                        ground.y + BURST_RADIUS, ground.z + BURST_RADIUS),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(ground)
                                <= BURST_RADIUS * BURST_RADIUS);
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = StellarConvergenceDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            applyTrueDamage(target, source, BURST_DAMAGE_FRACTION);
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

    private static void applyTrueDamage(LivingEntity target, DamageSource source, float fraction) {
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.burstResolved = tag.getBoolean("BurstResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
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
        tag.putBoolean("BurstResolved", this.burstResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
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

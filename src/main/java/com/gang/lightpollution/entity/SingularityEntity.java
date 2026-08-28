package com.gang.lightpollution.entity;

import com.gang.lightpollution.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Server-owned anchor for Singularity.
 *
 * <p>Structure follows Some of FX's singularity bomb
 * (github.com/YangMao-Minister/some_of_fx, MIT, (c) 2026 Pizuka): a charge that
 * builds for five seconds while it drags everything inward and lashes lightning
 * out at an accelerating rate, then one detonation that leaves several
 * shockwaves expanding behind it, then a long afterglow.</p>
 *
 * <p>What makes the charge work is that all three of its channels accelerate
 * together — the pull, the light, and the interval between bolts. Any one of them
 * alone reads as a countdown; all three read as something about to fail.</p>
 */
public final class SingularityEntity extends Entity {
    public static final int LIFETIME_TICKS = 220;
    /** The core opens over this window. */
    public static final int OPEN_END_TICK = 20;
    /** Detonation. */
    public static final int COLLAPSE_TICK = 120;
    /** Shockwaves and afterglow run to here. */
    public static final int AFTERGLOW_END_TICK = 200;

    /** How far the pull reaches, in blocks. */
    public static final double PULL_RADIUS = 20.0D;
    /** Radius of the detonation, in blocks. */
    public static final double BLAST_RADIUS = 18.0D;
    /** Visual radius of the core at full charge, in blocks. */
    public static final float CORE_RADIUS = 2.4F;
    /** Bound used to gather candidates. */
    public static final double EFFECT_RADIUS = PULL_RADIUS + 2.0D;

    /** Bolts lashing out at once, at most. */
    public static final int MAX_BOLTS = 10;
    /** How far a bolt reaches, in blocks. */
    public static final float BOLT_REACH = 22.0F;
    /** Shockwaves left expanding by the detonation. */
    public static final int SHOCKWAVE_COUNT = 3;
    /** Radius of the charge's standing lens when it opens, in blocks. */
    public static final float CHARGE_LENS_MIN_RADIUS = 3.0F;
    /** Radius of the charge's standing lens at full charge, in blocks. */
    public static final float CHARGE_LENS_MAX_RADIUS = 15.0F;

    /**
     * Strength of the pull at the core, in blocks per tick added to velocity.
     * Scaled by the charge, so early on it is a nuisance and late on it is not.
     */
    private static final double PULL_ACCELERATION = 0.16D;
    /** Ticks between crush applications while the charge builds. */
    private static final int CRUSH_INTERVAL_TICKS = 10;
    /** Max-health fraction per crush at the very edge of the pull. */
    private static final float CRUSH_MIN_FRACTION = 0.006F;
    /** Max-health fraction per crush for anything held at the core. */
    private static final float CRUSH_MAX_FRACTION = 0.035F;
    /** Max-health fraction of the detonation itself. */
    private static final float BLAST_DAMAGE_FRACTION = 0.66F;

    /** Ticks between arc sounds while the charge builds. */
    private static final int ARC_SOUND_INTERVAL_TICKS = 5;
    /** How far the ringing in the ears reaches, in blocks. */
    private static final double TINNITUS_RANGE = 64.0D;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            SingularityEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            SingularityEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private boolean blastResolved;

    public SingularityEntity(EntityType<? extends SingularityEntity> entityType, Level level) {
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

    /** Centre of the core, interpolated for smooth rendering. */
    public Vec3 coreCentre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * How far the charge has built, 0 to 1. Squared rather than linear, which is
     * what makes the last second feel like it is running away.
     */
    public float charge(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age >= COLLAPSE_TICK) {
            return 1.0F;
        }
        if (age <= OPEN_END_TICK) {
            return 0.0F;
        }
        float t = (age - OPEN_END_TICK) / (float) (COLLAPSE_TICK - OPEN_END_TICK);
        return t * t;
    }

    /** Radius of the core right now. It shrinks as it charges, then vanishes. */
    public float coreRadius(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= OPEN_END_TICK) {
            return CORE_RADIUS * smoothstep(age / OPEN_END_TICK);
        }
        if (age < COLLAPSE_TICK) {
            // Contracting: whatever it is pulling in has to go somewhere.
            return CORE_RADIUS * (1.0F - charge(partialTick) * 0.45F);
        }
        return 0.0F;
    }

    /** Brightness of the core and its light, 0 once it has collapsed. */
    public float coreBrightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= OPEN_END_TICK) {
            return smoothstep(age / OPEN_END_TICK) * 0.6F;
        }
        if (age < COLLAPSE_TICK) {
            return 0.6F + charge(partialTick) * 2.6F;
        }
        return 0.0F;
    }

    /** Flash of the detonation, 0 outside its window. */
    public float blastFlash(float partialTick) {
        float since = getVisualAgeTicks(partialTick) - COLLAPSE_TICK;
        if (since < 0.0F || since > 30.0F) {
            return 0.0F;
        }
        // Peaks a couple of ticks in rather than instantly, so the eye reads it as
        // an event rather than a single white frame.
        float t = since / 30.0F;
        return Mth.sin(Math.min(t * 3.4F, 1.0F) * Mth.PI * 0.5F) * (1.0F - t * 0.6F);
    }

    /**
     * Radius of one of the shockwaves, in blocks, or 0 before it exists. Each has
     * its own speed, so they separate as they travel instead of moving as one
     * thick shell.
     */
    public float shockwaveRadius(int wave, float partialTick) {
        float since = getVisualAgeTicks(partialTick) - COLLAPSE_TICK;
        if (since < 0.0F) {
            return 0.0F;
        }
        float speed = switch (wave) {
            case 0 -> 1.5F;
            case 1 -> 0.85F;
            default -> 0.45F;
        };
        // Decelerating, like a real front losing energy: linear expansion reads as
        // a growing sphere rather than as a blast.
        float radius = speed * since * (1.0F - since / 260.0F);
        return Math.max(0.0F, radius);
    }

    /** Strength of one shockwave, 0 once it has faded. */
    public float shockwaveStrength(int wave, float partialTick) {
        float since = getVisualAgeTicks(partialTick) - COLLAPSE_TICK;
        float life = switch (wave) {
            case 0 -> 34.0F;
            case 1 -> 58.0F;
            default -> 76.0F;
        };
        if (since < 0.0F || since > life) {
            return 0.0F;
        }
        float fade = 1.0F - since / life;
        return fade * fade;
    }

    /**
     * Ticks between bolts at the current charge. Some of FX ramps this from 15
     * down to 1, and that acceleration is most of what makes the charge read as
     * losing control.
     */
    public int boltInterval(float partialTick) {
        return Math.max(1, Math.round(15.0F - charge(partialTick) * 14.0F));
    }

    /**
     * Direction of one bolt. Re-rolled every {@code bucket}, so the whole set
     * lashes to new places rather than sitting still.
     */
    public Vec3 boltDirection(int bolt, int bucket) {
        float yaw = hashUnit(bolt * 71 + bucket * 17, 0x9E3779B9L) * Mth.TWO_PI;
        float pitch = (hashUnit(bolt * 91 + bucket * 31, 0x85EBCA6BL) - 0.5F) * Mth.PI;
        float horizontal = Mth.cos(pitch);
        return new Vec3(Mth.cos(yaw) * horizontal, Mth.sin(pitch),
                Mth.sin(yaw) * horizontal);
    }

    /** Length of one bolt, in blocks. */
    public float boltLength(int bolt, int bucket) {
        return BOLT_REACH * (0.35F + hashUnit(bolt * 53 + bucket * 7, 0xC2B2AE3DL) * 0.65F);
    }

    /**
     * One refracting sphere for the screen-space pass.
     *
     * @param radius    sphere radius in blocks
     * @param amplitude how far the scene is displaced, in screen UV
     * @param ripple    tangential wobble in the outer band, in screen UV
     * @param life      0 to 1 across the shell's travel; drives its profile
     * @param edge      how much light the front adds
     */
    public record Lens(float radius, float amplitude, float ripple, float life,
                       float edge) {
    }

    /**
     * The refracting spheres to draw this frame.
     *
     * <p>Some of FX spawns a one-tick smooth shock every tick of the charge, with
     * a preset whose radius grows as the timer rises. The effect of that is a lens
     * around the core that keeps getting bigger — the air visibly bending, harder
     * and wider, right up to the collapse — and it is the single largest reason
     * their bomb reads as a singularity rather than as a light. That is what the
     * first entry below reproduces; the rest are the shells the collapse leaves.</p>
     */
    public java.util.List<Lens> lenses(float partialTick) {
        java.util.List<Lens> lenses = new java.util.ArrayList<>(4);
        float age = getVisualAgeTicks(partialTick);

        if (age > OPEN_END_TICK && age < COLLAPSE_TICK) {
            float charge = charge(partialTick);
            // Life is held mid-profile rather than swept: this one is not
            // travelling anywhere, it is a standing lens that grows.
            lenses.add(new Lens(
                    CHARGE_LENS_MIN_RADIUS
                            + charge * (CHARGE_LENS_MAX_RADIUS - CHARGE_LENS_MIN_RADIUS),
                    0.05F + charge * 0.16F,
                    0.004F + charge * 0.012F,
                    0.45F,
                    0.05F + charge * 0.2F));
        }

        for (int wave = 0; wave < SHOCKWAVE_COUNT; wave++) {
            float radius = shockwaveRadius(wave, partialTick);
            float strength = shockwaveStrength(wave, partialTick);
            if (radius <= 0.2F || strength <= 0.01F) {
                continue;
            }
            lenses.add(new Lens(radius,
                    (0.10F + wave * 0.05F) * strength,
                    0.02F * strength,
                    1.0F - strength,
                    0.18F * strength));
        }
        return lenses;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private float hashUnit(int index, long salt) {
        long hash = (getSeed() & 0xFFFFFFFFL) * 0x2545F4914F6CDD1DL
                ^ (index + 1L) * salt;
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
            if (timelineTick == 1) {
                playCharge(serverLevel);
            }
            if (timelineTick > OPEN_END_TICK && timelineTick < COLLAPSE_TICK) {
                applyPull(serverLevel);
                if (timelineTick % CRUSH_INTERVAL_TICKS == 0) {
                    resolveCrush(serverLevel);
                }
                // Throttled rather than one per bolt. By the end of the charge a
                // dozen bolts a second are being drawn, and a sound on each of
                // them is not a crackle, it is noise.
                if (timelineTick % ARC_SOUND_INTERVAL_TICKS == 0) {
                    playArc(serverLevel, timelineTick);
                }
            }
            if (!this.blastResolved && timelineTick >= COLLAPSE_TICK) {
                this.blastResolved = true;
                resolveBlast(serverLevel);
                playCollapse(serverLevel);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /**
     * Drags everything in reach toward the core. Applied to velocity rather than
     * by teleporting, so vanilla still resolves collisions and a target pulled
     * into a wall slides along it.
     */
    private void applyPull(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = coreCentre(1.0F);
        float charge = charge(1.0F);
        for (LivingEntity target : gather(level, caster, centre, PULL_RADIUS)) {
            Vec3 toCore = centre.subtract(target.getBoundingBox().getCenter());
            double distance = toCore.length();
            if (distance < 0.001D) {
                continue;
            }
            double strength = PULL_ACCELERATION * (0.25D + charge)
                    * (1.0D - distance / PULL_RADIUS);
            if (strength <= 0.0D) {
                continue;
            }
            target.setDeltaMovement(target.getDeltaMovement()
                    .add(toCore.scale(strength / distance)));
            target.hurtMarked = true;
            // hurtMarked only reaches the players tracking this entity, never the
            // player being moved: their own client owns their movement.
            if (target instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }
    }

    /** Crushes whatever the core is holding, harder the closer it is held. */
    private void resolveCrush(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = coreCentre(1.0F);
        List<LivingEntity> caught = gather(level, caster, centre, PULL_RADIUS);
        if (caught.isEmpty()) {
            return;
        }
        DamageSource source = SingularityDamage.source(level, this, caster);
        for (LivingEntity target : caught) {
            double distance = centre.distanceTo(target.getBoundingBox().getCenter());
            double closeness = 1.0D - Mth.clamp(distance / PULL_RADIUS, 0.0D, 1.0D);
            float fraction = (float) Mth.lerp(closeness * closeness,
                    CRUSH_MIN_FRACTION, CRUSH_MAX_FRACTION);
            applyTrueDamage(target, source, fraction);
        }
    }

    private void resolveBlast(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = coreCentre(1.0F);
        List<LivingEntity> targets = gather(level, caster, centre, BLAST_RADIUS);
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = SingularityDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            applyTrueDamage(target, source, BLAST_DAMAGE_FRACTION);
            // Thrown outward by what is left of it.
            Vec3 away = target.getBoundingBox().getCenter().subtract(centre);
            double distance = away.length();
            if (distance > 0.001D) {
                target.setDeltaMovement(target.getDeltaMovement()
                        .add(away.scale(0.9D / distance)).add(0.0D, 0.35D, 0.0D));
                target.hurtMarked = true;
                if (target instanceof ServerPlayer player) {
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
            }
        }
    }

    /**
     * The charge opening. Layered the way Some of FX layers it: three vanilla
     * sounds under the mod's own tone, because one sample alone does not carry
     * the weight of what is about to happen.
     */
    private void playCharge(ServerLevel level) {
        Vec3 centre = coreCentre(1.0F);
        // Some of FX opens on heavy_core.break, which is 1.20.5+. The nearest
        // thing this version has with the same weight is the respawn anchor.
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(), SoundSource.AMBIENT, 4.0F, 0.6F);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 2.0F, 0.7F);
        level.playSound(null, centre.x, centre.y, centre.z,
                ModSounds.SINGULARITY_CHARGE.get(), SoundSource.AMBIENT, 8.0F, 1.0F);
        level.playSound(null, centre.x, centre.y, centre.z,
                ModSounds.SINGULARITY_ACTIVE.get(), SoundSource.AMBIENT, 5.0F, 1.0F);
    }

    /** One arc, pitched up as the charge builds. */
    private void playArc(ServerLevel level, int timelineTick) {
        Vec3 centre = coreCentre(1.0F);
        float charge = charge(1.0F);
        level.playSound(null, centre.x, centre.y, centre.z,
                ModSounds.LIGHTNING_ARC.get(), SoundSource.AMBIENT,
                1.0F + charge * 0.8F, 0.8F + charge * 0.5F);
    }

    /**
     * The collapse, and the ringing after it. The ringing is sent to each nearby
     * player individually rather than played into the world: it is the listener's
     * ears, not a sound at a place, so it must not attenuate or pan.
     */
    private void playCollapse(ServerLevel level) {
        Vec3 centre = coreCentre(1.0F);
        level.playSound(null, centre.x, centre.y, centre.z,
                ModSounds.SINGULARITY_EXPLODE.get(), SoundSource.AMBIENT, 6.0F, 1.0F);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 4.0F, 0.6F);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(centre)
                    > TINNITUS_RANGE * TINNITUS_RANGE) {
                continue;
            }
            player.playNotifySound(ModSounds.TINNITUS.get(),
                    SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    private List<LivingEntity> gather(ServerLevel level, LivingEntity caster,
                                      Vec3 centre, double radius) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                        centre.x + radius, centre.y + radius, centre.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                <= radius * radius);
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
        this.blastResolved = tag.getBoolean("BlastResolved");
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
        tag.putBoolean("BlastResolved", this.blastResolved);
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

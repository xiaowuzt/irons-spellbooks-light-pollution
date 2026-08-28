package com.gang.lightpollution.entity;

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
 * Server-owned anchor for Leviathan.
 *
 * <p>Everything else in this set arrives from above and detonates. This one moves
 * <em>laterally</em>: a body several dozen blocks long swims in through the air
 * on one long S-curve, rears, bites once, and comes apart. The motion language is
 * the point, so the spine is a spline rather than a straight line and the damage
 * follows that same spline instead of a radius around a point.</p>
 *
 * <p>Spine, fins and jaws are all pure functions of the timeline and the
 * synchronized seed, so the renderer and the server-side sweeps read the same
 * curve without any extra syncing.</p>
 */
public final class LeviathanEntity extends Entity {
    public static final int LIFETIME_TICKS = 190;
    /** The spine traces in from off in the distance over this window. */
    public static final int APPROACH_END_TICK = 70;
    /** Ribs and fins extrude, making the silhouette legible. */
    public static final int FLESH_END_TICK = 95;
    /** The head rears up and holds. */
    public static final int REAR_END_TICK = 120;
    /** The bite. */
    public static final int BITE_TICK = 128;
    /** The body tears into ribbons from here.  */
    public static final int UNRAVEL_START_TICK = 140;

    /** Segments the spine is sampled into. */
    public static final int SPINE_SEGMENTS = 48;
    /** Length of the body, in blocks. */
    public static final float BODY_LENGTH = 90.0F;
    /**
     * How far the S-curve swings off its axis, in blocks.
     *
     * <p>Measured peak specific amplitude for a swimming snake is 0.10 to 0.18 of
     * body length. At 90 blocks that is 9 to 16, so the old 26 was well outside
     * anything a real animal does and read as a flapping ribbon.</p>
     */
    public static final float SWING_AMPLITUDE = 14.0F;
    /**
     * How far the body rises and falls as the wave passes, in blocks. Small,
     * because lateral undulation is overwhelmingly horizontal.
     */
    public static final float VERTICAL_AMPLITUDE = 3.0F;
    /**
     * Complete undulation cycles held along the body at once. Anguilliform
     * wavelength is 0.6 to 0.7 body lengths, which is 1.4 to 1.7 waves.
     */
    public static final float UNDULATION_WAVES = 1.6F;
    /** Height the body swims at above the aimed point, in blocks. */
    public static final float SWIM_HEIGHT = 16.0F;
    /**
     * Thickest radius of the body, in blocks.
     *
     * <p>Total length to midbody diameter runs 20:1 to 35:1 for heavy-bodied
     * snakes and 60:1 upward for slender ones. The old 3.4 gave 13:1 over a
     * 90-block body — stubbier than any real snake, which is part of why it read
     * as a pipe. 2.0 gives 22:1, the proportion of a large constrictor.</p>
     */
    public static final float BODY_RADIUS = 2.0F;
    /** Section height as a fraction of its width. Terrestrial snakes: 1.0 to 1.2. */
    public static final float SECTION_HEIGHT_RATIO = 1.05F;
    /** How much narrower the ventral plate is than the back, 0 to 1. */
    public static final float SECTION_VENTRAL_TAPER = 0.30F;
    /** Dorsal scale rows at midbody. Real large snakes carry 19 to 25. */
    public static final int DORSAL_ROWS_MID = 21;
    /** Dorsal scale rows before the vent; row count drops as the body tapers. */
    public static final int DORSAL_ROWS_REAR = 17;
    /** How far the head rears above the swim height, in blocks. */
    public static final float REAR_HEIGHT = 22.0F;
    /** Share of the swing amplitude the head keeps; small, but not zero. */
    private static final float HEAD_AMPLITUDE_SHARE = 0.08F;
    /** How far the body banks into a bend at peak curvature, in radians. */
    private static final float BANK_ANGLE = 0.38F;

    /** Radius of the bite, in blocks. */
    public static final double BITE_RADIUS = 9.0D;
    /** Radius swept by the body as it passes, in blocks. */
    public static final double SWEEP_RADIUS = 5.0D;
    /** Bound used to gather candidates. */
    public static final double EFFECT_RADIUS = BODY_LENGTH * 0.5D + BITE_RADIUS + 4.0D;

    /** Max-health fraction for being caught by the passing body. */
    private static final float SWEEP_DAMAGE_FRACTION = 0.056F;
    /** Ticks between sweep applications. */
    private static final int SWEEP_INTERVAL_TICKS = 6;
    /** Max-health fraction of the bite. */
    private static final float BITE_DAMAGE_FRACTION = 0.58F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            LeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            LeviathanEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            LeviathanEntity.class, EntityDataSerializers.INT);
    /** Bearing the body swims along, in radians. */
    private static final EntityDataAccessor<Float> DATA_BEARING = SynchedEntityData.defineId(
            LeviathanEntity.class, EntityDataSerializers.FLOAT);
    /** The creature it is going for, or 0 for a fixed point. */
    private static final EntityDataAccessor<Integer> DATA_TARGET_ID =
            SynchedEntityData.defineId(LeviathanEntity.class, EntityDataSerializers.INT);

    /** How much of the way to the target the anchor closes each tick. */
    private static final double HOMING_RATE = 0.22D;

    private UUID casterUuid;
    private boolean biteResolved;

    public LeviathanEntity(EntityType<? extends LeviathanEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, float bearing, int seed) {
        configure(caster, center, bearing, seed, null);
    }

    /**
     * @param target the creature the head should end up on, or null to bite a fixed
     *               point. When set, the anchor chases it, so the bite lands on the
     *               creature rather than on wherever it happened to be standing when
     *               the spell was cast.
     */
    public void configure(LivingEntity caster, Vec3 center, float bearing, int seed,
                          LivingEntity target) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_BEARING, bearing);
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_TARGET_ID, target == null ? 0 : target.getId());
        this.setPos(center.x, center.y, center.z);
    }

    public int getTargetId() {
        return this.entityData.get(DATA_TARGET_ID);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    public float getBearing() {
        return this.entityData.get(DATA_BEARING);
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

    /** Aimed point. The body swims past it and the bite lands on it. */
    public Vec3 anchor(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * How much of the body has arrived, 0 to 1. The head leads, so at 0.3 only the
     * front third exists.
     */
    public float extended(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age >= APPROACH_END_TICK) {
            return 1.0F;
        }
        return smoothstep(age / APPROACH_END_TICK);
    }

    /** How far the ribs and fins have grown out, 0 to 1. */
    public float flesh(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= APPROACH_END_TICK * 0.5F) {
            return 0.0F;
        }
        return Mth.clamp((age - APPROACH_END_TICK * 0.5F)
                / (FLESH_END_TICK - APPROACH_END_TICK * 0.5F), 0.0F, 1.0F);
    }

    /** How far the head has reared, 0 to 1. */
    public float rear(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= FLESH_END_TICK) {
            return 0.0F;
        }
        if (age >= REAR_END_TICK) {
            return 1.0F;
        }
        return smoothstep((age - FLESH_END_TICK) / (REAR_END_TICK - FLESH_END_TICK));
    }

    /**
     * The strike, 0 before it starts and 1 once the jaws are on the target.
     *
     * <p>This is what was missing. The body swims {@link #SWIM_HEIGHT} blocks above
     * the aimed point and the head rears another {@link #REAR_HEIGHT} on top of
     * that, so at the moment of the bite the jaws were closing thirty-eight blocks
     * above the creature they were aimed at — well outside {@link #BITE_RADIUS}, so
     * the bite hit nothing and visibly snapped shut in empty sky. The front of the
     * body now lances down onto the point as the jaws close, which is both what a
     * striking snake does and what puts the jaws where the damage is.</p>
     */
    public float strike(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= REAR_END_TICK) {
            return 0.0F;
        }
        if (age >= BITE_TICK) {
            return 1.0F;
        }
        // Accelerating rather than eased: a strike is a snap, not a lean.
        float raw = (age - REAR_END_TICK) / (BITE_TICK - REAR_END_TICK);
        return raw * raw;
    }

    /** How wide the jaws are open, 0 shut to 1 fully open. */
    public float jawOpen(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= FLESH_END_TICK) {
            return 0.0F;
        }
        if (age < REAR_END_TICK) {
            // Opens as it rears.
            return smoothstep((age - FLESH_END_TICK) / (REAR_END_TICK - FLESH_END_TICK));
        }
        if (age < BITE_TICK) {
            // Snaps shut. Fast, because the snap is the beat the whole spell is for.
            return 1.0F - smoothstep((age - REAR_END_TICK) / (BITE_TICK - REAR_END_TICK));
        }
        return 0.0F;
    }

    /** How far the body has come apart, 0 intact to 1 gone. */
    public float unravel(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= UNRAVEL_START_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - UNRAVEL_START_TICK)
                / (float) (LIFETIME_TICKS - UNRAVEL_START_TICK), 0.0F, 1.0F);
    }

    /**
     * Position along the spine. {@code t} is 0 at the snout and 1 at the tail tip.
     *
     * <p>A snake does not hold a fixed curve, it passes a wave <em>down</em> its
     * body: every point traces the same path the point ahead of it traced a moment
     * earlier. That is why the first version did not read as a snake — it was one
     * frozen S that translated bodily instead of undulating. The wave here is a
     * function of {@code t} minus travelled distance, so it moves tail-ward, and
     * there is a smaller vertical wave out of phase with the lateral one, which is
     * what stops it looking like a flag.</p>
     */
    public Vec3 spinePoint(float t, float partialTick) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        Vec3 anchor = anchor(partialTick);
        float bearing = getBearing();
        float dirX = Mth.cos(bearing);
        float dirZ = Mth.sin(bearing);
        float sideX = -dirZ;
        float sideZ = dirX;

        float along = -clamped * BODY_LENGTH;
        // Negative, so the head starts behind the caster and travels forward past
        // the aimed point. With this added instead of subtracted the head began in
        // front of the target and moved backward, which put the tail at the leading
        // end -- the body arrived tail first.
        float approach = (1.0F - extended(partialTick)) * BODY_LENGTH * 1.6F;
        along -= approach;

        // The wave travels tail-ward: subtracting time from the position term is
        // what makes the body slither rather than sway.
        float wave = wavePhase(clamped, partialTick);

        // Lateral swing. Amplitude grows toward the tail as s^1.5, which is the
        // measured envelope for anguilliform swimming, plus a small constant so the
        // head is not pinned dead still. The old clamp(t * 2.2) saturated a third of
        // the way along and was flat after that, so the back two thirds of the body
        // all swung by the same amount -- a flag, not an animal.
        float envelope = HEAD_AMPLITUDE_SHARE
                + (1.0F - HEAD_AMPLITUDE_SHARE) * (float) Math.pow(clamped, 1.5D);
        float swing = Mth.sin(wave) * SWING_AMPLITUDE * envelope;

        // A smaller vertical wave a quarter cycle out of phase. Without this the
        // body is a flat ribbon of motion however round the mesh is.
        float bob = Mth.sin(wave + Mth.HALF_PI) * VERTICAL_AMPLITUDE * envelope;

        float rear = rear(partialTick);
        // Only the front third of the body rears and strikes; the rest holds its
        // swimming height, which is what makes the motion read as a strike rather
        // than the whole animal moving up and down.
        float headBias = Math.max(0.0F, 1.0F - clamped * 3.0F);
        float lift = REAR_HEIGHT * rear * headBias;
        // Brings the jaws all the way down onto the aimed point. See strike().
        float plunge = strike(partialTick)
                * (SWIM_HEIGHT + REAR_HEIGHT * rear) * headBias;

        return anchor.add(
                dirX * along + sideX * swing,
                SWIM_HEIGHT + lift + bob - plunge,
                dirZ * along + sideZ * swing);
    }

    /**
     * Radius of the body at {@code t}.
     *
     * <p>Shaped from measured snake proportions. The part that matters most is the
     * <em>plateau</em>: real snakes are very nearly constant in girth from about
     * 15% to 55% of their length, and that near-cylindrical middle is what makes
     * them read as heavy. The previous profile peaked at a single point 28% back and
     * tapered continuously from there to the tail, which is the definition of a
     * cone — so however round the mesh was, the eye read a tapered pipe.</p>
     *
     * <p>The rest: the skull is wider than the neck behind it, the neck pinches to
     * roughly 70% of midbody girth, and the tail is 15% of total length and falls
     * off as a power curve rather than a straight line, which is what makes it whip
     * to a point instead of ending in a stub.</p>
     */
    public float bodyRadius(float t, float partialTick) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float profile;
        if (clamped < 0.02F) {
            // Rostral tip.
            profile = 0.30F + clamped / 0.02F * 0.25F;
        } else if (clamped < 0.055F) {
            // Skull, broader than the neck behind it.
            profile = 0.55F + Mth.sin((clamped - 0.02F) / 0.035F * Mth.PI) * 0.40F;
        } else if (clamped < 0.10F) {
            // Neck pinch. This is the detail that makes a head read as a head.
            profile = 0.95F - (clamped - 0.055F) / 0.045F * 0.27F;
        } else if (clamped < 0.15F) {
            // Filling out to full girth.
            profile = 0.68F + (clamped - 0.10F) / 0.05F * 0.32F;
        } else if (clamped < 0.55F) {
            // The plateau. A few percent of drift so it is not a machined
            // cylinder, but essentially constant.
            profile = 1.0F - 0.03F * Mth.sin((clamped - 0.15F) / 0.40F * Mth.PI);
        } else if (clamped < 0.85F) {
            // Gentle taper through the posterior body.
            float back = (clamped - 0.55F) / 0.30F;
            profile = 1.0F - back * back * (3.0F - 2.0F * back) * 0.45F;
        } else {
            // The tail, 15% of total length. Power falloff, so it thins slowly at
            // first and whips to a point at the very end.
            float tail = (clamped - 0.85F) / 0.15F;
            profile = 0.55F * (1.0F - (float) Math.pow(tail, 2.2D)) + 0.02F;
        }
        return BODY_RADIUS * profile * (1.0F - unravel(partialTick) * 0.6F);
    }

    /**
     * Roll about the spine at {@code t}, in radians.
     *
     * <p>The section is a rounded trapezoid with a flat ventral plate, so the mesh
     * needs to be told which way is down. Zero would already keep the belly down,
     * because the frame is rebuilt from world up each ring; the small term here is
     * the bank. A snake going through a lateral wave rolls slightly into each bend,
     * so the roll is driven by the same wave's derivative — 90 degrees out of phase
     * with the displacement, which is where the peak curvature is.</p>
     */
    public float roll(float t, float partialTick) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        float envelope = HEAD_AMPLITUDE_SHARE
                + (1.0F - HEAD_AMPLITUDE_SHARE) * (float) Math.pow(clamped, 1.5D);
        return Mth.cos(wavePhase(clamped, partialTick)) * BANK_ANGLE * envelope;
    }

    /** Phase of the travelling undulation at {@code t}. */
    private float wavePhase(float t, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        float phase = hashUnit(0, 0x9E3779B9L) * Mth.TWO_PI;
        return t * Mth.PI * UNDULATION_WAVES * 2.0F - age * 0.16F + phase;
    }

    /** Brightness envelope of the whole body. */
    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age < APPROACH_END_TICK) {
            return 0.35F + extended(partialTick) * 0.65F;
        }
        if (age < BITE_TICK) {
            return 1.0F;
        }
        if (age < UNRAVEL_START_TICK) {
            // Flares on the bite.
            return 1.0F + (BITE_TICK + 12.0F - age) * 0.09F;
        }
        return Math.max(0.0F, 1.0F - unravel(partialTick));
    }

    /** Flash of the bite, 0 outside its window. */
    public float biteFlash(float partialTick) {
        float since = getVisualAgeTicks(partialTick) - BITE_TICK;
        if (since < 0.0F || since > 14.0F) {
            return 0.0F;
        }
        return 1.0F - since / 14.0F;
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
            // The anchor chases the chosen creature, so the head arrives on it
            // rather than on where it stood when the spell was cast. Eased rather
            // than snapped: the whole ninety-block body hangs off this point, and
            // teleporting it would make the animal jerk.
            if (timelineTick < BITE_TICK) {
                chaseTarget(serverLevel);
            }
            // Anything the body passes through while it is swimming.
            if (timelineTick > 4 && timelineTick < BITE_TICK
                    && timelineTick % SWEEP_INTERVAL_TICKS == 0) {
                resolveSweep(serverLevel);
            }
            if (!this.biteResolved && timelineTick >= BITE_TICK) {
                this.biteResolved = true;
                resolveBite(serverLevel);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /** Eases the anchor toward the chosen creature. */
    private void chaseTarget(ServerLevel level) {
        int targetId = getTargetId();
        if (targetId == 0) {
            return;
        }
        if (!(level.getEntity(targetId) instanceof LivingEntity target)
                || !target.isAlive() || target.isRemoved()) {
            return;
        }
        Vec3 wanted = target.getBoundingBox().getCenter();
        Vec3 here = this.position();
        this.setPos(
                here.x + (wanted.x - here.x) * HOMING_RATE,
                here.y + (wanted.y - here.y) * HOMING_RATE,
                here.z + (wanted.z - here.z) * HOMING_RATE);
    }

    /**
     * Damage along the spine rather than in a ball. Sampled at a handful of points
     * instead of every segment: a 48-segment sweep every six ticks would be
     * dozens of AABB queries for a hit volume the player cannot distinguish from
     * this one.
     */
    private void resolveSweep(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        float extended = extended(1.0F);
        DamageSource source = null;
        for (int sample = 0; sample < SWEEP_SAMPLES; sample++) {
            float t = sample / (float) (SWEEP_SAMPLES - 1) * extended;
            Vec3 point = spinePoint(t, 1.0F);
            List<LivingEntity> touched = level.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(point.x - SWEEP_RADIUS, point.y - SWEEP_RADIUS,
                            point.z - SWEEP_RADIUS, point.x + SWEEP_RADIUS,
                            point.y + SWEEP_RADIUS, point.z + SWEEP_RADIUS),
                    target -> canAffect(caster, target)
                            && target.getBoundingBox().getCenter().distanceToSqr(point)
                                    <= SWEEP_RADIUS * SWEEP_RADIUS);
            if (touched.isEmpty()) {
                continue;
            }
            if (source == null) {
                source = LeviathanDamage.source(level, this, caster);
            }
            for (LivingEntity target : touched) {
                SpellDamage.apply(this, target, source, SWEEP_DAMAGE_FRACTION);
                // Thrown aside along the body's own heading, so being clipped by
                // it reads as being hit by something moving.
                Vec3 aside = target.getBoundingBox().getCenter().subtract(point);
                double length = aside.length();
                if (length > 0.001D) {
                    target.setDeltaMovement(target.getDeltaMovement()
                            .add(aside.scale(0.5D / length)).add(0.0D, 0.2D, 0.0D));
                    target.hurtMarked = true;
                    if (target instanceof ServerPlayer player) {
                        player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    }
                }
            }
        }
    }

    /** Points along the spine tested by one sweep. */
    private static final int SWEEP_SAMPLES = 9;

    private void resolveBite(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 jaws = spinePoint(0.0F, 1.0F);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(jaws.x - BITE_RADIUS, jaws.y - BITE_RADIUS, jaws.z - BITE_RADIUS,
                        jaws.x + BITE_RADIUS, jaws.y + BITE_RADIUS, jaws.z + BITE_RADIUS),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(jaws)
                                <= BITE_RADIUS * BITE_RADIUS);
        if (targets.isEmpty()) {
            return;
        }
        DamageSource source = LeviathanDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, BITE_DAMAGE_FRACTION);
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
        this.entityData.define(DATA_BEARING, 0.0F);
        this.entityData.define(DATA_TARGET_ID, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.biteResolved = tag.getBoolean("BiteResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_BEARING, tag.getFloat("Bearing"));
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
        tag.putBoolean("BiteResolved", this.biteResolved);
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
        tag.putFloat("Bearing", this.getBearing());
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

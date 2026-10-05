package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
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
 * Server-owned anchor for Gargantua.
 *
 * <p>A black hole rendered from the real Kerr geometry rather than as a dark ball
 * with a ring. Every radius below is expressed as a multiple of the gravitational
 * radius {@code r_g}, because that is the only way the proportions come out right:
 * the shadow an observer sees is {@code sqrt(27) = 5.196 r_g}, which is 2.6 times
 * the event horizon, so a black hole always looks considerably bigger than it is.
 * Those figures and the ones below come from the imagery Kip Thorne and Double
 * Negative produced for <em>Interstellar</em> and published in Classical and
 * Quantum Gravity 32, 065001 (2015).</p>
 *
 * <p>It arrives at full size and stays there. The pull and the tidal shear ease in
 * over the opening tear rather than by the hole swelling, and when its time is up it
 * simply goes out over five and a half seconds.</p>
 */
public final class GargantuaEntity extends Entity {
    private static final String CONFIG_ID = "gargantua";

    private static int configuredLifetime() {
        return SpellConfig.lifetimeTicks(CONFIG_ID);
    }

    private static double configuredRadius() {
        return SpellConfig.effectRadius(CONFIG_ID);
    }

    public static final int LIFETIME_TICKS = 400;
    /** A point of light is torn open and the disk lights up. */
    public static final int TEAR_END_TICK = 40;
    /** It hangs there. Nothing about the geometry changes through this. */
    public static final int HOLD_END_TICK = 260;
    /** The disk overexposes and the ground starts to shake. */
    public static final int CRITICAL_END_TICK = 290;
    /** The disk is flung outward. */
    public static final int BLAST_TICK = CRITICAL_END_TICK;
    /**
     * From here it simply goes out.
     *
     * <p>Five and a half seconds of fade rather than a snap. What is left after the
     * collapse is a hole that has stopped being fed, and that reads as something
     * dimming rather than something being switched off.</p>
     */
    public static final int FADE_START_TICK = BLAST_TICK + 30;

    /**
     * Gravitational radius, in blocks. Fixed — it does not grow.
     *
     * <p>Every rendered radius is a multiple of this: the horizon is 1.8 of it, the
     * shadow 5.196, the disk runs 3.83 to 13.5. So this one number sets the whole
     * apparent size, and at 4.0 the shadow spans about 21 blocks.</p>
     */
    public static final float GRAVITATIONAL_RADIUS = 4.0F;

    /**
     * Spin, as a fraction of the maximum. The film settled on 0.6: at near-extremal
     * spin the shadow develops a flattened edge and turns into a D, and Nolan read
     * that as a rendering fault rather than as physics.
     */
    public static final float SPIN = 0.6F;
    /** Event horizon, in units of r_g, for {@link #SPIN}. Inside this nothing returns. */
    public static final float HORIZON_RADIUS = 1.8F;
    /** Apparent radius of the shadow, in units of r_g. This is sqrt(27). */
    public static final float SHADOW_RADIUS = 5.196F;
    /** Inner edge of the disk: the innermost stable circular orbit at spin 0.6. */
    public static final float DISK_INNER_RADIUS = 3.83F;
    /** Outer edge of the disk, in units of r_g. */
    public static final float DISK_OUTER_RADIUS = 13.5F;

    /**
     * How far above the aimed point it hangs, in blocks.
     *
     * <p>Ground level rather than sky. The horizon is 7.2 blocks, so at fourteen the
     * hole clears the terrain while a player standing on the ground is still nearly
     * level with the disk — which is what presents it close to edge-on, the composition
     * the whole effect is built around.</p>
     */
    public static final float HOVER_HEIGHT = 14.0F;

    /** Radius over which creatures and spell light are drawn in, in blocks. */
    public static final double PULL_RADIUS = 24.0D;
    /** Radius of the final detonation, in blocks. */
    public static final double BLAST_RADIUS = 20.0D;
    /** Bound used to gather candidates. */
    public static final double EFFECT_RADIUS = PULL_RADIUS + 4.0D;

    /** Pull per tick at full strength, scaled by r_g squared at the call site. */
    /**
     * How hard it draws things in.
     *
     * <p>Raised from 0.012. At that value the inverse-square falloff left the outer half of the
     * 24-block radius doing essentially nothing — 0.008 blocks per tick at the rim against a
     * walking speed of about 0.11, so a player could stroll out of it without noticing there
     * was a pull at all. The point of a black hole is that being anywhere near it is a
     * problem.</p>
     */
    private static final double PULL_ACCELERATION = 0.055D;
    /**
     * Floor under the inverse-square falloff, in blocks.
     *
     * <p>Without this the pull at the rim is a hundredth of what it is at the centre, which is
     * correct for gravity and useless as a mechanic. Clamping the effective distance flattens
     * the curve enough that the whole radius is dangerous while the centre is still seven times
     * worse. Chosen just outside the horizon, which sits at 1.8 r_g = 7.2 blocks and kills
     * outright — so the flattened region is almost entirely inside the lethal radius and the
     * flattening costs nothing anything could have survived anyway.</p>
     */
    private static final double PULL_FALLOFF_FLOOR = 9.0D;
    /**
     * Extra pull applied to anything that is not alive.
     *
     * <p>An arrow crosses the whole radius in under a second, so the acceleration a walking mob
     * feels as a strong drag barely deflects it. Projectiles, dropped items and thrown things
     * need an order more before their path visibly bends into the hole.</p>
     */
    private static final double PROJECTILE_PULL_SCALE = 6.5D;
    /**
     * Capture radius, as a multiple of the horizon.
     *
     * <p>Inside this the pull stops being a force and becomes a direct haul. Acceleration on its
     * own never finished the job — a player can out-walk it and a mob's pathing and gravity
     * fight it — so nothing was ever actually drawn in, which is what made the spell look as
     * though it had no suction at all.</p>
     */
    private static final double CAPTURE_MULTIPLE = 1.9D;
    /** How fast captured things are hauled inward, in blocks per tick. */
    private static final double CAPTURE_SPEED = 0.85D;
    /** Ticks between tidal applications. */
    private static final int TIDAL_INTERVAL_TICKS = 8;
    /** Max-health fraction of a tidal application at the edge of the pull. */
    private static final float TIDAL_MIN_FRACTION = 0.013F;
    /** Max-health fraction of a tidal application against the horizon. */
    private static final float TIDAL_MAX_FRACTION = 0.046F;
    /** Extra max-health fraction per swallowed spell light. */
    private static final float BLAST_BONUS_PER_LIGHT = 0.038F;
    /** Ceiling on that bonus. */
    private static final float BLAST_BONUS_CAP = 0.20F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);
    /**
     * The spin axis, packed as a unit vector.
     *
     * <p>Straight up, so the disk lies flat — parallel to the ground, the way anything
     * orbiting a hole at rest in the world would. Stored per-entity rather than assumed
     * so the shader has one place to read it from.</p>
     */
    private static final EntityDataAccessor<Float> DATA_AXIS_X =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Y =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AXIS_Z =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.FLOAT);
    /** How many spell lights it has swallowed, for the renderer and the blast. */
    private static final EntityDataAccessor<Integer> DATA_SWALLOWED =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.INT);
    /**
     * Display mode: render it, do nothing else.
     *
     * <p>For looking at. It never pulls, never damages, and never advances past the
     * hold phase, so it can be left standing while the disk is being tuned instead of
     * casting the spell and racing a twenty-second timer.</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(GargantuaEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean blastResolved;

    private static int configuredFadeStart() {
        return Math.min(configuredLifetime() - 1, SpellConfig.phaseTick(CONFIG_ID, 3) + 30);
    }

    public GargantuaEntity(EntityType<? extends GargantuaEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, Vec3 spinAxis, int seed) {
        this.casterUuid = caster.getUUID();
        this.entityData.set(DATA_CASTER_ID, caster.getId());
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        Vec3 axis = spinAxis.lengthSqr() < 1.0E-6D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : spinAxis.normalize();
        this.entityData.set(DATA_AXIS_X, (float) axis.x);
        this.entityData.set(DATA_AXIS_Y, (float) axis.y);
        this.entityData.set(DATA_AXIS_Z, (float) axis.z);
        this.setPos(center.x, center.y, center.z);
    }

    public int getCasterId() {
        return this.entityData.get(DATA_CASTER_ID);
    }

    public int getSeed() {
        return this.entityData.get(DATA_SEED);
    }

    /** True for a command-spawned black hole that only exists to be looked at. */
    public boolean isDisplay() {
        return this.entityData.get(DATA_DISPLAY);
    }

    /** Sets it up as an inert display object at {@code centre}. */
    public void configureDisplay(Vec3 centre, int seed) {
        this.entityData.set(DATA_START_GAME_TICK, this.level().getGameTime());
        this.entityData.set(DATA_SEED, seed);
        this.entityData.set(DATA_DISPLAY, true);
        this.entityData.set(DATA_AXIS_X, 0.0F);
        this.entityData.set(DATA_AXIS_Y, 1.0F);
        this.entityData.set(DATA_AXIS_Z, 0.0F);
        this.setPos(centre.x, centre.y, centre.z);
    }

    public int getSwallowedCount() {
        return this.entityData.get(DATA_SWALLOWED);
    }

    /** Unit spin axis. The disk lies in the plane perpendicular to it. */
    public Vec3 spinAxis() {
        return new Vec3(
                this.entityData.get(DATA_AXIS_X),
                this.entityData.get(DATA_AXIS_Y),
                this.entityData.get(DATA_AXIS_Z));
    }

    public boolean isCastBy(LivingEntity caster) {
        return caster != null
                && this.casterUuid != null
                && this.casterUuid.equals(caster.getUUID());
    }

    public int getTimelineAgeTicks() {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        if (startGameTick < 0L) {
            return Math.min(configuredLifetime(), Math.max(0, this.tickCount));
        }
        long age = this.level().getGameTime() - startGameTick;
        return (int) Math.min(configuredLifetime(), Math.max(0L, age));
    }

    public float getVisualAgeTicks(float partialTick) {
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        float age = startGameTick < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - startGameTick) + partialTick;
        age = Math.max(0.0F, age);
        // A display hole opens and then stops. Capping it just short of criticality
        // keeps the disk at its settled brightness rather than the overexposed flare.
        if (isDisplay()) {
            return Math.min(age, SpellConfig.phaseTick(CONFIG_ID, 2) - 1.0F);
        }
        return Math.min(configuredLifetime(), age);
    }

    /** Centre of the hole. It hangs above the aimed point rather than at it. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * The gravitational radius, in blocks. Constant: it does not grow.
     *
     * <p>Every other radius is a multiple of this, so holding it fixed fixes the whole
     * geometry. An earlier version swelled it from 0.8 over eleven seconds, which meant
     * the first half of the spell was spent watching something too small to read.</p>
     */
    public float gravitationalRadius(float partialTick) {
        return GRAVITATIONAL_RADIUS;
    }

    /** How far into the closing fade it is, 0 to 1. */
    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= configuredFadeStart()) {
            return 0.0F;
        }
        return Mth.clamp((age - configuredFadeStart())
                / (float) (configuredLifetime() - configuredFadeStart()), 0.0F, 1.0F);
    }

    /** How far it has opened, 0 to 1. Scales the disk in during the tear. */
    public float opened(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / SpellConfig.phaseTick(CONFIG_ID, 1));
    }

    /** Overexposure of the disk approaching the blast, 0 to 1. */
    public float criticality(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= SpellConfig.phaseTick(CONFIG_ID, 2)) {
            return 0.0F;
        }
        if (age >= SpellConfig.phaseTick(CONFIG_ID, 3)) {
            return 1.0F;
        }
        return smoothstep((age - SpellConfig.phaseTick(CONFIG_ID, 2))
                / (float) (SpellConfig.phaseTick(CONFIG_ID, 3) - SpellConfig.phaseTick(CONFIG_ID, 2)));
    }

    /** Flash of the detonation, 0 outside its window. */
    public float blastFlash(float partialTick) {
        float since = getVisualAgeTicks(partialTick) - SpellConfig.phaseTick(CONFIG_ID, 3);
        if (since < 0.0F || since > 18.0F) {
            return 0.0F;
        }
        return 1.0F - since / 18.0F;
    }

    /** Overall brightness envelope, including the post-blast fade. */
    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= SpellConfig.phaseTick(CONFIG_ID, 1)) {
            return opened(partialTick);
        }
        if (age <= SpellConfig.phaseTick(CONFIG_ID, 2)) {
            return 1.0F;
        }
        if (age <= SpellConfig.phaseTick(CONFIG_ID, 3)) {
            return 1.0F + criticality(partialTick) * 1.6F;
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
            // Pulls, but does not damage and never expires. The pull is the part worth looking
            // at and the display command was the only way anyone had seen this hole, so
            // returning here made it look as though it had no suction at all.
            if (this.level() instanceof ServerLevel displayLevel) {
                applyPull(displayLevel);
            }
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > SpellConfig.phaseTick(CONFIG_ID, 1) / 2 && timelineTick < SpellConfig.phaseTick(CONFIG_ID, 3)) {
                applyPull(serverLevel);
                if (timelineTick % Math.max(1, SpellConfig.damageIntervalTicks(CONFIG_ID)) == 0) {
                    resolveTidal(serverLevel);
                    countSwallowedLights(serverLevel);
                }
            }
            if (!this.blastResolved && timelineTick >= SpellConfig.phaseTick(CONFIG_ID, 3)) {
                this.blastResolved = true;
                resolveBlast(serverLevel);
            }
        }

        if (timelineTick >= configuredLifetime()) {
            this.discard();
        }
    }

    /**
     * Draws everything in range toward the centre.
     *
     * <p>Added to velocity rather than assigned, so vanilla still resolves
     * collisions, and pushed to the client explicitly because {@code hurtMarked}
     * never reaches the player being moved.</p>
     */
    private void applyPull(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = centre(1.0F);
        float radius = gravitationalRadius(1.0F);
        // Eased in over the opening tear rather than by the hole growing, so the first
        // second is a warning rather than an immediate yank.
        double strength = PULL_ACCELERATION * SpellConfig.gargantuaPullStrength
                * radius * radius * opened(1.0F);
        if (strength <= 0.0D) return;
        double horizon = radius * HORIZON_RADIUS;

        for (Entity target : gatherAny(level, caster, centre, configuredRadius())) {
            Vec3 toCentre = centre.subtract(target.getBoundingBox().getCenter());
            double distance = toCentre.length();
            if (distance < 0.001D) {
                continue;
            }
            Vec3 direction = toCentre.scale(1.0D / distance);

            // Inverse square, floored so the rim still pulls. Unfloored this spans a factor
            // of a hundred across the radius and only the last few blocks read as suction.
            double effective = Math.max(distance, PULL_FALLOFF_FLOOR);
            double falloff = 1.0D / (effective * effective);
            double accel = strength * falloff * configuredRadius();

            // Projectiles are light and fast, and adding a small acceleration to something
            // already travelling at speed barely bends its path. They get a much harder pull
            // so an arrow crossing the radius visibly curves into the hole instead of sailing
            // through it.
            if (!(target instanceof LivingEntity)) {
                accel *= PROJECTILE_PULL_SCALE;
            }

            Vec3 velocity = target.getDeltaMovement().add(direction.scale(accel));

            // Inside the capture radius, stop adding force and start hauling. Acceleration
            // alone never actually gets anything in: a player can out-walk it, and a mob's
            // own pathing plus gravity fight it the whole way. Past this point the velocity is
            // overwritten with a direct inward one, which is what "sucked in" means and what
            // acceleration was failing to deliver.
            double capture = Math.max(horizon * CAPTURE_MULTIPLE, PULL_FALLOFF_FLOOR);
            if (distance < capture) {
                // Overwritten, not added to, so gravity and pathing cannot fight it. Safe to do
                // every tick without disabling gravity: this runs each tick while the hole is
                // open, so whatever gravity adds in between is replaced before it accumulates.
                // Turning gravity off instead would need turning back on again, and anything
                // that survived the hole would be left floating.
                velocity = direction.scale(Math.min(distance, CAPTURE_SPEED));
            }

            target.setDeltaMovement(velocity);
            target.hurtMarked = true;
            if (target instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        }
    }

    private void resolveTidal(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = centre(1.0F);
        float radius = gravitationalRadius(1.0F);
        double horizon = radius * HORIZON_RADIUS;
        DamageSource source = null;

        for (LivingEntity target : gather(level, caster, centre, configuredRadius())) {
            double distance = target.getBoundingBox().getCenter().distanceTo(centre);
            if (source == null) {
                source = GargantuaDamage.source(level, this, caster);
            }
            if (distance <= horizon) {
                target.setHealth(0.0F);
                if (!target.isRemoved()) {
                    target.die(source);
                }
                continue;
            }
            float closeness = (float) Mth.clamp(
                    1.0D - (distance - horizon) / (configuredRadius() - horizon), 0.0D, 1.0D);
            float fraction = Mth.lerp(closeness * closeness,
                    (float) SpellConfig.damageFraction(CONFIG_ID),
                    (float) SpellConfig.gargantuaTidalMaxDamageFraction);
            SpellDamage.apply(this, target, source, fraction);
        }
    }

    /** The detonation. Its strength depends on how much light it ate. */
    private void resolveBlast(ServerLevel level) {
        // Announced before anything else in here, including the early return when
        // nothing is in range: the event happened regardless of whether it hit.
        com.gang.lightpollution.net.ModNetwork.sendCaption(level,
                this.position().add(0.0D, HOVER_HEIGHT, 0.0D),
                "caption.irons_spellbooks_light_pollution.gargantua.collapse",
                com.gang.lightpollution.SpellPalette.accentFor(this), 1.9F);
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = centre(1.0F);
        List<LivingEntity> targets = gather(level, caster, centre, SpellConfig.gargantuaBlastRadius);
        if (targets.isEmpty()) {
            return;
        }
        float bonus = Math.min(BLAST_BONUS_CAP,
                getSwallowedCount() * BLAST_BONUS_PER_LIGHT);
        DamageSource source = GargantuaDamage.source(level, this, caster);
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source,
                    (float) SpellConfig.gargantuaBlastDamageFraction + bonus);
            // Thrown outward: the disk is being flung off, and so is everything else.
            Vec3 outward = target.getBoundingBox().getCenter().subtract(centre);
            double distance = outward.length();
            if (distance > 0.001D) {
                target.setDeltaMovement(target.getDeltaMovement()
                        .add(outward.scale(1.4D / distance)).add(0.0D, 0.45D, 0.0D));
                target.hurtMarked = true;
                if (target instanceof ServerPlayer player) {
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
            }
        }
    }

    /** Records a spell light being swallowed, which the blast then cashes in. */
    public void noteSwallowedLight() {
        this.entityData.set(DATA_SWALLOWED, getSwallowedCount() + 1);
    }

    /**
     * Counts this mod's other spell anchors inside the pull.
     *
     * <p>Spell lights only exist on the client, so the server cannot see them
     * directly. These entities are exactly the things that emit one, which makes the
     * count a faithful stand-in — the same approach Starless uses. It only ever rises,
     * so a light that drifts out again still counts as eaten.</p>
     */
    private void countSwallowedLights(ServerLevel level) {
        Vec3 centre = centre(1.0F);
        int seen = 0;
        for (Entity nearby : level.getEntities(this,
                new AABB(centre.x - configuredRadius(), centre.y - configuredRadius(),
                        centre.z - configuredRadius(), centre.x + configuredRadius(),
                        centre.y + configuredRadius(), centre.z + configuredRadius()))) {
            if (nearby == this || !isSpellLightSource(nearby)) {
                continue;
            }
            if (nearby.position().distanceToSqr(centre) <= configuredRadius() * configuredRadius()) {
                seen++;
            }
        }
        if (seen > getSwallowedCount()) {
            this.entityData.set(DATA_SWALLOWED, seen);
        }
    }

    /** The mod's own spell anchors are the entities that emit spell light. */
    private static boolean isSpellLightSource(Entity entity) {
        return entity instanceof CelestialJudgmentEntity
                || entity instanceof ChromaticAccretionEntity
                || entity instanceof ConstellationEntity
                || entity instanceof EclipseSeveranceEntity
                || entity instanceof FuneralNovaEntity
                || entity instanceof SecondSunEntity
                || entity instanceof SingularityEntity
                || entity instanceof SkyCollapseEntity
                || entity instanceof StarfallEntity
                || entity instanceof StargraveSingularityEntity
                || entity instanceof StarlessEntity
                || entity instanceof StellarConvergenceEntity
                || entity instanceof WorldTreeEntity;
    }

    private List<LivingEntity> gather(ServerLevel level, LivingEntity caster,
                                      Vec3 centre, double radius) {
        return SpellConfig.limitTargets("gargantua", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                        centre.x + radius, centre.y + radius, centre.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                <= radius * radius));
    }

    /**
     * Everything the hole pulls, not just what is alive.
     *
     * <p>Projectiles, dropped items and thrown things all fall in too. Restricting the pull to
     * living entities meant arrows flew straight through a black hole, which reads as the effect
     * being decorative.</p>
     *
     * <p>The caster's own arrows are not spared. The hole does not know who fired them, and an
     * exception there would look like a bug rather than a courtesy.</p>
     */
    private List<Entity> gatherAny(ServerLevel level, LivingEntity caster,
                                   Vec3 centre, double radius) {
        return level.getEntities(this,
                new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                        centre.x + radius, centre.y + radius, centre.z + radius),
                target -> {
                    if (target instanceof LivingEntity living) {
                        return canAffect(caster, living);
                    }
                    // Anything else that moves and is not another one of these holes.
                    return !(target instanceof GargantuaEntity)
                            && target.getBoundingBox().getCenter().distanceToSqr(centre)
                                    <= radius * radius;
                });
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
        this.entityData.define(DATA_AXIS_X, 1.0F);
        this.entityData.define(DATA_AXIS_Y, 0.0F);
        this.entityData.define(DATA_AXIS_Z, 0.0F);
        this.entityData.define(DATA_SWALLOWED, 0);
        this.entityData.define(DATA_DISPLAY, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.blastResolved = tag.getBoolean("BlastResolved");
        this.entityData.set(DATA_CASTER_ID, tag.getInt("CasterId"));
        this.entityData.set(DATA_SEED, tag.getInt("Seed"));
        this.entityData.set(DATA_SWALLOWED, tag.getInt("Swallowed"));
        this.entityData.set(DATA_DISPLAY, tag.getBoolean("Display"));
        this.entityData.set(DATA_AXIS_X, tag.getFloat("AxisX"));
        this.entityData.set(DATA_AXIS_Y, tag.getFloat("AxisY"));
        this.entityData.set(DATA_AXIS_Z, tag.getFloat("AxisZ"));
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
        tag.putInt("Swallowed", this.getSwallowedCount());
        tag.putBoolean("Display", this.isDisplay());
        Vec3 axis = spinAxis();
        tag.putFloat("AxisX", (float) axis.x);
        tag.putFloat("AxisY", (float) axis.y);
        tag.putFloat("AxisZ", (float) axis.z);
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

package com.gang.lightpollution.entity;

import com.gang.lightpollution.ExampleMod;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/**
 * Server-owned anchor for the 15.5 second Starfall timeline.
 *
 * <p>Meteors are sub-objects of this one entity, not entities of their own: forty
 * of them would be forty spawn packets and forty tick loops for something that is
 * entirely determined by a seed and a timeline. Every property of meteor
 * <em>i</em> — where it lands, when it falls, when it hits — is a pure function of
 * the synchronized seed, so the renderer, the light emitter and the server damage
 * all agree without a single extra byte on the wire.</p>
 *
 * <p>The pressure is meant to be continuous rather than a single detonation:
 * several meteors are always in the air, each one a moving light, so the shadows
 * on the ground never stop sweeping. The exception is the finale, which is one
 * colossal body falling on the aimed point.</p>
 */
public final class StarfallEntity extends Entity {
    public static final int LIFETIME_TICKS = 310;
    public static final int OMEN_END_TICK = 40;
    public static final int RAIN_END_TICK = 240;
    public static final int FINALE_TICK = 240;
    public static final int FADE_START_TICK = 290;

    /** Ticks between successive meteors during the rain. */
    public static final int SPAWN_INTERVAL_TICKS = 5;
    /**
     * How long a rain meteor takes to fall. Short on purpose: a meteor that takes
     * its time reads as a drifting light rather than something arriving at speed.
     */
    public static final int FALL_TICKS = 16;
    /**
     * How long the finale takes. Longer than a rain meteor despite falling
     * further: a body that size going past at the same speed is over before it
     * registers, and the whole point of it is that you watch it come.
     */
    public static final int FINALE_FALL_TICKS = 34;
    /**
     * How far a meteor's path leans off vertical, in degrees. Straight down gives
     * the camera almost no parallax, so the body appears to hang instead of move.
     */
    public static final double ENTRY_ANGLE_DEGREES = 30.0D;
    /** Meteors in the rain phase, then the single finale. */
    public static final int RAIN_METEORS =
            (RAIN_END_TICK - OMEN_END_TICK) / SPAWN_INTERVAL_TICKS;
    /** One. Three of them split the attention that should be on one arrival. */
    public static final int FINALE_METEORS = 1;
    public static final int METEOR_COUNT = RAIN_METEORS + FINALE_METEORS;

    /** Radius of the bombarded area, in blocks. */
    public static final double EFFECT_RADIUS = 14.0D;
    /** Height a rain meteor falls from, above its landing point. */
    public static final double FALL_HEIGHT = 42.0D;
    /** Height the finale falls from. Higher, so there is time to see it coming. */
    public static final double FINALE_FALL_HEIGHT = 96.0D;

    public static final double RAIN_BLAST_RADIUS = 2.5D;
    public static final double FINALE_BLAST_RADIUS = 9.0D;
    private static final float RAIN_DAMAGE_FRACTION = 0.037F;
    /** The finale's damage, consolidated from what used to be three bodies. */
    private static final float FINALE_DAMAGE_FRACTION = 0.28F;
    /** Fraction of rain meteors that shed a visible shock ring. */
    private static final float SHOCK_RING_CHANCE = 0.3F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            StarfallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            StarfallEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            StarfallEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    /** Highest meteor index already resolved, so a reload cannot double-hit. */
    private int resolvedThrough = -1;

    public StarfallEntity(EntityType<? extends StarfallEntity> entityType, Level level) {
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

    public static boolean isFinaleMeteor(int meteor) {
        return meteor >= RAIN_METEORS;
    }

    /** Tick at which a meteor begins to fall. */
    public static int spawnTick(int meteor) {
        return isFinaleMeteor(meteor)
                ? FINALE_TICK
                : OMEN_END_TICK + meteor * SPAWN_INTERVAL_TICKS;
    }

    /** Tick at which a meteor lands. */
    public static int impactTick(int meteor) {
        return spawnTick(meteor) + fallTicks(meteor);
    }

    /** How long this meteor spends falling. */
    public static int fallTicks(int meteor) {
        return isFinaleMeteor(meteor) ? FINALE_FALL_TICKS : FALL_TICKS;
    }

    /** How high above its landing point this meteor starts. */
    public static double fallHeight(int meteor) {
        return isFinaleMeteor(meteor) ? FINALE_FALL_HEIGHT : FALL_HEIGHT;
    }

    public static double blastRadius(int meteor) {
        return isFinaleMeteor(meteor) ? FINALE_BLAST_RADIUS : RAIN_BLAST_RADIUS;
    }

    /** Anchor centre, interpolated for smooth rendering. */
    public Vec3 anchorCenter(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /**
     * Where a meteor lands. Derived from the seed alone so every side agrees, then
     * dropped onto the terrain surface — both client and server read the same
     * heightmap for loaded chunks, so a meteor never detonates inside a hillside
     * or hangs in the air above a valley.
     */
    public Vec3 meteorLanding(int meteor) {
        // The finale lands on the aimed point itself. It is the arrival the whole
        // spell has been building to, so it should not be off to one side.
        if (isFinaleMeteor(meteor)) {
            int centre = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    Mth.floor(this.getX()), Mth.floor(this.getZ()));
            return new Vec3(this.getX(), centre, this.getZ());
        }
        float unitAngle = hashUnit(meteor, 0x9E3779B9L);
        float unitRadius = hashUnit(meteor, 0x85EBCA6BL);
        // sqrt distributes the points evenly across the disc instead of
        // clustering them at the centre.
        double radius = Math.sqrt(unitRadius) * EFFECT_RADIUS;
        double angle = unitAngle * Mth.TWO_PI;
        double x = this.getX() + Math.cos(angle) * radius;
        double z = this.getZ() + Math.sin(angle) * radius;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        return new Vec3(x, surface, z);
    }

    /**
     * Whether this meteor sheds a visible shock ring.
     *
     * <p>Not every one of them: forty identical rings turned a shower into a
     * repeating pattern. Derived from the seed so every client picks the same
     * ones. The finale always has one.</p>
     */
    public boolean hasShockRing(int meteor) {
        return isFinaleMeteor(meteor)
                || hashUnit(meteor, 0x27D4EB2FL) < SHOCK_RING_CHANCE;
    }

    /**
     * Direction a meteor travels, pointing down-range. Unit length.
     *
     * <p>Meteors come in on a slant rather than straight down: a vertical drop
     * gives the camera no sense of speed, because the body barely moves across
     * the screen. The azimuth is derived from the meteor's own hash so the shower
     * arrives from assorted bearings instead of all sharing one, which would look
     * like a volley rather than a shower.</p>
     */
    public Vec3 meteorHeading(int meteor) {
        double azimuth = hashUnit(meteor, 0xC2B2AE3DL) * Mth.TWO_PI;
        double tilt = Math.toRadians(ENTRY_ANGLE_DEGREES);
        double horizontal = Math.sin(tilt);
        return new Vec3(Math.cos(azimuth) * horizontal, -Math.cos(tilt),
                Math.sin(azimuth) * horizontal);
    }

    /** Where a meteor enters, back up its heading from the landing point. */
    public Vec3 meteorEntry(int meteor) {
        Vec3 landing = meteorLanding(meteor);
        // Scaled so the vertical drop is still the full fall height; the slant
        // adds horizontal travel on top rather than trading height away for it.
        double along = fallHeight(meteor)
                / Math.cos(Math.toRadians(ENTRY_ANGLE_DEGREES));
        return landing.subtract(meteorHeading(meteor).scale(along));
    }

    /** Position of a meteor in flight, or its landing point once it has hit. */
    public Vec3 meteorPosition(int meteor, float partialTick) {
        Vec3 landing = meteorLanding(meteor);
        float age = getVisualAgeTicks(partialTick);
        int spawn = spawnTick(meteor);
        float fall = Mth.clamp((age - spawn) / (float) fallTicks(meteor), 0.0F, 1.0F);
        // Gravity, so the meteor is slow and readable high up and fast at the end.
        float eased = fall * fall;
        return meteorEntry(meteor).lerp(landing, eased);
    }

    /**
     * Brightness of a meteor, 0 when it does not exist yet. Falls to zero shortly
     * after impact so the flash does not linger as a permanent light.
     */
    public float meteorBrightness(int meteor, float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        int spawn = spawnTick(meteor);
        if (age < spawn) {
            return 0.0F;
        }
        int impact = impactTick(meteor);
        if (age < impact) {
            // Brighten as it approaches, so the threat is legible.
            float fall = (age - spawn) / (float) fallTicks(meteor);
            return 0.45F + fall * 0.55F;
        }
        float since = age - impact;
        // The finale's flash is bigger and lasts longer; it is the last thing the
        // spell does and should not blink out.
        return isFinaleMeteor(meteor)
                ? Math.max(0.0F, 3.4F - since * 0.14F)
                : Math.max(0.0F, 1.8F - since * 0.22F);
    }

    private float hashUnit(int meteor, long salt) {
        long hash = (getSeed() & 0xFFFFFFFFL) * 0x2545F4914F6CDD1DL
                ^ (meteor + 1L) * salt;
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
            for (int meteor = this.resolvedThrough + 1; meteor < METEOR_COUNT; meteor++) {
                if (timelineTick < impactTick(meteor)) {
                    break;
                }
                this.resolvedThrough = meteor;
                resolveMeteorImpact(serverLevel, meteor);
            }
        }

        if (timelineTick >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    private void resolveMeteorImpact(ServerLevel level, int meteor) {
        LivingEntity caster = resolveCaster(level);
        Vec3 impact = meteorLanding(meteor);
        double radius = blastRadius(meteor);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(impact.x - radius, impact.y - radius, impact.z - radius,
                        impact.x + radius, impact.y + radius, impact.z + radius),
                target -> canAffect(caster, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(impact)
                                <= radius * radius);
        if (targets.isEmpty()) {
            return;
        }

        DamageSource source = StarfallDamage.source(level, this, caster);
        float fraction = isFinaleMeteor(meteor)
                ? FINALE_DAMAGE_FRACTION : RAIN_DAMAGE_FRACTION;
        for (LivingEntity target : targets) {
            SpellDamage.apply(this, target, source, fraction);
            target.setSecondsOnFire(isFinaleMeteor(meteor) ? 6 : 3);
        }
        ExampleMod.LOGGER.debug("Starfall meteor {} struck {} target(s)", meteor, targets.size());
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.resolvedThrough = tag.contains("ResolvedThrough")
                ? tag.getInt("ResolvedThrough") : -1;
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
        tag.putInt("ResolvedThrough", this.resolvedThrough);
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

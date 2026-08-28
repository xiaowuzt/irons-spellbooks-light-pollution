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
 * Server-owned anchor for a magnetar giant flare, after SGR 1806-20.
 *
 * <p>A neutron star with a field around 10^15 gauss — the strongest magnetism known. In
 * December 2004 this one produced the most powerful giant flare ever observed; the
 * published account attributes it to "a large-scale rearrangement of the magnetosphere",
 * and it ionised Earth's upper atmosphere from fifty thousand light years away.</p>
 *
 * <p>The magnetosphere is the effect. Field lines are drawn from the real dipole relation
 * r = r0 sin^2(theta), so they are the closed loops a dipole actually makes rather than
 * arcs chosen to look magnetic, and they wind tighter as the star spins up to the flare.
 * That makes the hazard a set of curved loops with gaps between them: unlike every sphere
 * in this set, where you stand inside the radius matters.</p>
 */
public final class MagnetarEntity extends Entity {
    public static final int LIFETIME_TICKS = 300;
    /** The star appears and the field lines thread out. */
    public static final int THREAD_END_TICK = 36;
    /** The magnetosphere winds up. Field lines twist and brighten. */
    public static final int WIND_END_TICK = 230;
    /** The rearrangement. One pulse, and the loops snap open. */
    public static final int FLARE_TICK = WIND_END_TICK;

    /**
     * Field line count.
     *
     * <p>Drawn in pairs about the axis, so this is even. Enough to read as a magnetosphere
     * with visible gaps between the loops, which is the point — a solid shell of them would
     * just be a sphere again.</p>
     */
    public static final int FIELD_LINES = 14;
    /** How far the outermost loop reaches from the star, in blocks. */
    public static final double FIELD_REACH = 19.0D;
    /**
     * Radius of the neutron star itself, in blocks.
     *
     * <p>Load-bearing rather than decorative: the field lines terminate on it, so it is what
     * separates each loop's two ends and lets the loop close visibly.</p>
     */
    public static final double STAR_RADIUS = 2.1D;
    /** Half-width of a loop's ribbon, in blocks. */
    public static final double LOOP_HALF_WIDTH = 0.42D;
    /** How close to a loop counts as touching it, in blocks. */
    public static final double LOOP_TOUCH_RADIUS = 2.1D;
    /** Turns the magnetosphere twists through as it winds up. */
    public static final double TWIST_TURNS = 0.85D;

    public static final double HOVER_HEIGHT = 9.0D;
    public static final double FLARE_RADIUS = 22.0D;
    public static final double EFFECT_RADIUS = FLARE_RADIUS + 4.0D;

    /** Touching a field line, as a fraction of max health. */
    private static final float FIELD_DAMAGE_FRACTION = 0.024F;
    private static final int FIELD_INTERVAL_TICKS = 8;
    /**
     * The flare, as a fraction of max health.
     *
     * <p>The largest single hit here short of the two dedicated executioners, which is the
     * right shape for the most energetic flare ever recorded — but it is one pulse with a
     * long wind-up that is impossible to miss, so it is avoidable in a way they are not.</p>
     */
    private static final float FLARE_DAMAGE_FRACTION = 0.78F;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AZIMUTH =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY =
            SynchedEntityData.defineId(MagnetarEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID casterUuid;
    private boolean flareResolved;

    public MagnetarEntity(EntityType<? extends MagnetarEntity> type, Level level) {
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
            // Cycles the wind-up instead of holding it at the top. Held fully wound the field
            // is always at the white-hot end of its ramp, so the violet it starts from is never
            // visible and the effect looks like it only has one colour. Stopping short of the
            // flare still avoids blanking the view every cycle.
            float span = WIND_END_TICK - THREAD_END_TICK;
            return THREAD_END_TICK + (age % span);
        }
        return Math.min(LIFETIME_TICKS, age);
    }

    /** Centre of the star. */
    public Vec3 centre(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()) + HOVER_HEIGHT,
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** The magnetic axis, as a unit vector. */
    public Vec3 axis() {
        double azimuth = Math.toRadians(azimuth());
        // Leaned over rather than upright, because a dipole standing straight up hides its
        // loops behind each other from a viewer on the ground.
        double tilt = Math.toRadians(28.0D);
        return new Vec3(Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt),
                Math.sin(tilt) * Math.sin(azimuth)).normalize();
    }

    /** How far the field has threaded out, 0 to 1. */
    public float threaded(float partialTick) {
        return smoothstep(getVisualAgeTicks(partialTick) / THREAD_END_TICK);
    }

    /** How far the magnetosphere has wound up, 0 to 1. */
    public float wound(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= THREAD_END_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - THREAD_END_TICK)
                / (float) (WIND_END_TICK - THREAD_END_TICK), 0.0F, 1.0F);
    }

    /** The flare itself, 1 at the instant it goes and decaying after. */
    public float flare(float partialTick) {
        float since = getVisualAgeTicks(partialTick) - FLARE_TICK;
        if (since < 0.0F || since > 26.0F) {
            return 0.0F;
        }
        return 1.0F - since / 26.0F;
    }

    public float fade(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= FLARE_TICK) {
            return 0.0F;
        }
        return Mth.clamp((age - FLARE_TICK)
                / (float) (LIFETIME_TICKS - FLARE_TICK), 0.0F, 1.0F);
    }

    public float brightness(float partialTick) {
        float age = getVisualAgeTicks(partialTick);
        if (age <= THREAD_END_TICK) {
            return threaded(partialTick);
        }
        if (age <= WIND_END_TICK) {
            // Brightens as it winds: the field is being stressed, and that is the tell
            // that the flare is coming.
            return 1.0F + wound(partialTick) * 0.8F;
        }
        return Math.max(0.0F, 1.0F - fade(partialTick)) + flare(partialTick) * 2.5F;
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * A point on one dipole field line.
     *
     * <p>From the dipole relation r = r0 sin^2(theta), which is the actual shape of a
     * magnetic field line rather than an arc picked to look like one: it leaves a pole
     * along the axis, bulges out at the equator, and closes on the other pole. The twist
     * winds the loop about the axis as the magnetosphere is stressed, which is the
     * rearrangement that eventually lets go.</p>
     *
     * @param line  which loop, 0 to FIELD_LINES-1
     * @param along position along the loop, 0 at one pole to 1 at the other
     */
    public Vec3 fieldPoint(Vec3 centre, int line, double along, float woundFraction) {
        Vec3 axis = axis();
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0e-6D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 other = axis.cross(side).normalize();

        // theta from 0 (one pole) to pi (the other).
        double theta = along * Math.PI;
        double sin = Math.sin(theta);
        double cos = Math.cos(theta);
        // Alternating loop sizes so the shell has structure instead of one nested set.
        double scale = 0.45D + 0.55D * ((line % 3) / 2.0D);
        double shell = FIELD_REACH * scale;

        // The dipole field line in cylindrical form. From r = L sin^2(theta):
        //     rho = r sin(theta) = L sin^3(theta)
        //     z   = r cos(theta) = L sin^2(theta) cos(theta)
        //
        // Written out this way rather than as r divided by sin, which is what it was before.
        // That version needed a clamp to survive the poles, and the clamp flattened the loops
        // so hard that both ends collapsed onto the star's centre — every line ran out from
        // the middle and back into it, which is why they read as open arcs with loose ends
        // instead of as closed loops.
        double rho = shell * sin * sin * sin;
        double z = shell * sin * sin * cos;

        // Anchor the ends on the star's surface rather than at a point. A mathematical dipole
        // is a point and its lines all return to it; a real one has a body, and the lines
        // terminate at two separated magnetic poles. That separation is what makes each loop
        // visibly leave somewhere and arrive somewhere else.
        z += STAR_RADIUS * cos;

        // The twist has to vanish at both ends, or the two ends of one line sit at different
        // azimuths and the loop cannot close. sin^2 goes to zero at both poles.
        double azimuth = 2.0D * Math.PI * line / FIELD_LINES
                + woundFraction * TWIST_TURNS * Math.PI * 2.0D * sin * sin;

        Vec3 radial = side.scale(Math.cos(azimuth)).add(other.scale(Math.sin(azimuth)));
        return centre.add(axis.scale(z)).add(radial.scale(rho));
    }

    @Override
    public void tick() {
        super.tick();

        if (isDisplay()) {
            return;
        }

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            if (timelineTick > THREAD_END_TICK && timelineTick < FLARE_TICK
                    && timelineTick % FIELD_INTERVAL_TICKS == 0) {
                resolveField(serverLevel, timelineTick);
            }
            if (!this.flareResolved && timelineTick >= FLARE_TICK) {
                this.flareResolved = true;
                resolveFlare(serverLevel);
            }
            if (timelineTick >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    /**
     * Damage whatever is touching a field line.
     *
     * <p>Tested against the same sampled loop points the renderer draws, so the gaps a
     * player can see between loops are gaps they can actually stand in. That is the whole
     * mechanic — a radius test would erase it.</p>
     */
    private void resolveField(ServerLevel level, float ageTicks) {
        LivingEntity caster = resolveCaster(level);
        DamageSource source = MagnetarDamage.field(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);
        float woundFraction = wound(0.0F);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - EFFECT_RADIUS, centre.y - EFFECT_RADIUS,
                        centre.z - EFFECT_RADIUS, centre.x + EFFECT_RADIUS,
                        centre.y + EFFECT_RADIUS, centre.z + EFFECT_RADIUS));
        if (targets.isEmpty()) {
            return;
        }

        double touchSqr = LOOP_TOUCH_RADIUS * LOOP_TOUCH_RADIUS;
        int samples = 18;
        for (LivingEntity target : targets) {
            if (!canAffect(caster, target)) {
                continue;
            }
            Vec3 at = target.getBoundingBox().getCenter();
            boolean touching = false;
            for (int line = 0; line < FIELD_LINES && !touching; ++line) {
                for (int i = 1; i < samples; ++i) {
                    if (fieldPoint(centre, line, i / (double) samples, woundFraction)
                            .distanceToSqr(at) <= touchSqr) {
                        touching = true;
                        break;
                    }
                }
            }
            if (touching) {
                SpellDamage.apply(this, target, source, FIELD_DAMAGE_FRACTION);
            }
        }
    }

    /** The rearrangement. Everything within reach, once. */
    private void resolveFlare(ServerLevel level) {
        // Announced before anything else in here, including the early return when
        // nothing is in range: the event happened regardless of whether it hit.
        com.gang.lightpollution.net.ModNetwork.sendCaption(level,
                this.position().add(0.0D, HOVER_HEIGHT, 0.0D),
                "caption.irons_spellbooks_light_pollution.magnetar.flare",
                com.gang.lightpollution.SpellPalette.accentFor(this), 1.7F);
        LivingEntity caster = resolveCaster(level);
        DamageSource source = MagnetarDamage.field(level, caster, this);
        Vec3 centre = this.position().add(0.0D, HOVER_HEIGHT, 0.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre.x - FLARE_RADIUS, centre.y - FLARE_RADIUS,
                        centre.z - FLARE_RADIUS, centre.x + FLARE_RADIUS,
                        centre.y + FLARE_RADIUS, centre.z + FLARE_RADIUS))) {
            if (!canAffect(caster, target)) {
                continue;
            }
            if (target.getBoundingBox().getCenter().distanceTo(centre) <= FLARE_RADIUS) {
                SpellDamage.apply(this, target, source, FLARE_DAMAGE_FRACTION);
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
        this.flareResolved = tag.getBoolean("FlareResolved");
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
        tag.putBoolean("FlareResolved", this.flareResolved);
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

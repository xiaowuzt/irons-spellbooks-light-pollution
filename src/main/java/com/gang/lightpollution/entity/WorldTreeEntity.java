package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.api.WorldTreeParams;
import com.gang.lightpollution.fx.FxHash;
import com.gang.lightpollution.fx.WorldTreeShape;
import com.gang.lightpollution.fx.WorldTreeSource;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned anchor for World Tree.
 *
 * <p>The one spell in this set that grows out of the ground rather than arriving
 * at it. Roots race outward across the terrain, spear up into a trunk, and the
 * crown unfolds — a tall vertical silhouette, which nothing else here has. While
 * it stands, the tree is a sanctuary: it heals the caster and allied creatures
 * and wards the configured damage categories.</p>
 *
 * <p>Root paths, branch angles and leaf placement are pure functions of the
 * synchronized seed, so the renderer and the server-side sanctuary read the same
 * structure without extra syncing. Root tips follow the real terrain height, so
 * on a slope the roots climb it.</p>
 */
public final class WorldTreeEntity extends Entity implements WorldTreeSource {
    // The form lives in WorldTreeShape, which the renderer and the public API both read, so there is
    // one definition rather than a spell copy and an API copy that can drift.
    public static final int LIFETIME_TICKS = WorldTreeParams.SPELL_LIFETIME_TICKS;
    /**
     * The trunk spears up first.
     *
     * <p>Trunk before roots, which is the order a tree is actually built in and the
     * order that reads: something erupts, and then it takes hold. Roots racing out
     * of bare ground before anything exists to own them looked like an unrelated
     * effect that the trunk then landed in the middle of.</p>
     */

    /**
     * Radius of the clear trunk, above the root flare, in blocks.
     *
     * <p>Diameter at breast height is measured at 1.3 m precisely because that is
     * above the butt swell. So this is the trunk proper, and {@link #WorldTreeShape.FLARE_SCALE}
     * widens the bottom of it.</p>
     */

    /**
     * Leaves carried by each leaf-bearing branch.
     *
     * <p>Deliberately few per branch. The total foliage is about the same as before,
     * but it is spread over 2268 twigs across the outer two orders instead of 756 —
     * eighteen leaves crowded onto one twig reads as a bottle brush, six leaves on
     * three times as many twigs reads as a canopy. Real twigs bear 5 to 15, so this
     * is also the honest number; the previous one was compensating for not having
     * enough branches to hang them on.</p>
     */

    /** Fallback radius retained for callers that need a conservative search bound. */
    public static final double EFFECT_RADIUS = WorldTreeShape.ROOT_REACH + 3.0D;

    private static final EntityDataAccessor<Integer> DATA_CASTER_ID = SynchedEntityData.defineId(
            WorldTreeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_START_GAME_TICK = SynchedEntityData.defineId(
            WorldTreeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(
            WorldTreeEntity.class, EntityDataSerializers.INT);

    /** UUID is kept when the caster dies, logs out, or changes dimension. */
    private UUID casterUuid;
    /** Team at cast time, used while the caster is not present in this level. */
    private String casterTeamName;
    /** Amount of absorption currently supplied by this tree, per target UUID. */
    private final Map<UUID, AbsorptionState> sanctuaryAbsorption = new HashMap<>();
    /** Object references let cleanup reach a target that changed dimension before lookup. */
    private final Map<UUID, LivingEntity> sanctuaryTargets = new HashMap<>();

    /** Own shield and the last total, used to distinguish damage from other grants. */
    private record AbsorptionState(float own, float lastTotal) {
    }

    public WorldTreeEntity(EntityType<? extends WorldTreeEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, Vec3 center, int seed) {
        this.casterUuid = caster.getUUID();
        this.casterTeamName = caster.getTeam() == null ? null : caster.getTeam().getName();
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

    /** Radius shared by healing, protection, target cleanup, and visible roots. */
    public static double sanctuaryRadius() {
        return Math.max(0.1D, SpellConfig.worldTreeEffectRadius);
    }

    /** Radius used by this tree's server-side sanctuary. */
    public double getSanctuaryRadius() {
        return sanctuaryRadius();
    }

    public int getTimelineAgeTicks() {
        int lifetime = Math.max(1, SpellConfig.worldTreeLifetimeTicks);
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        if (startGameTick < 0L) {
            return Math.min(lifetime, Math.max(0, this.tickCount));
        }
        long age = this.level().getGameTime() - startGameTick;
        return (int) Math.min(lifetime, Math.max(0L, age));
    }

    public float getVisualAgeTicks(float partialTick) {
        int lifetime = Math.max(1, SpellConfig.worldTreeLifetimeTicks);
        long startGameTick = this.entityData.get(DATA_START_GAME_TICK);
        float age = startGameTick < 0L
                ? this.tickCount + partialTick
                : (float) (this.level().getGameTime() - startGameTick) + partialTick;
        return Math.min(lifetime, Math.max(0.0F, age));
    }

    /** The seed point on the ground. */
    public Vec3 seedPoint(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xOld, this.getX()),
                Mth.lerp(partialTick, this.yOld, this.getY()),
                Mth.lerp(partialTick, this.zOld, this.getZ()));
    }

    /** How far the roots have raced out, 0 to 1. */
    public float rootProgress(float partialTick) {
        return WorldTreeShape.rootProgress(getVisualAgeTicks(partialTick));
    }

    /** How far the trunk has risen, 0 to 1. */
    /** What this entity's synced state amounts to, for the shared shape maths. */
    @Override
    public WorldTreeParams shapeParams() {
        // groundY is unused on this side: the roots follow the terrain instead.
        return WorldTreeParams.of(getSeed(), this.getY())
                .lifetime(Math.max(1, SpellConfig.worldTreeLifetimeTicks));
    }

    public float trunkProgress(float partialTick) {
        return WorldTreeShape.trunkProgress(getVisualAgeTicks(partialTick));
    }

    /** How far the branches have extended, 0 to 1. */
    public float branchProgress(float partialTick) {
        return WorldTreeShape.branchProgress(getVisualAgeTicks(partialTick));
    }

    /** How far the crown has filled in, 0 to 1. */
    public float crownProgress(float partialTick) {
        return WorldTreeShape.crownProgress(getVisualAgeTicks(partialTick));
    }

    /**
     * How far the tree has hardened into crystal, 0 to 1. Drives the colour shift
     * from living gold to pale crystal, which is what makes the final silhouette
     * read as a monument rather than as a plant.
     */
    public float hardened(float partialTick) {
        return WorldTreeShape.hardened(getVisualAgeTicks(partialTick));
    }

    /** Overall fade, 1 while it stands and falling to 0 at the end. */
    public float fade(float partialTick) {
        return WorldTreeShape.fade(getVisualAgeTicks(partialTick),
                Math.max(1, SpellConfig.worldTreeLifetimeTicks));
    }

    /** Bearing a root runs along, in radians. */
    public float rootBearing(int root) {
        return WorldTreeShape.rootBearing(shapeParams(), root);
    }

    /**
     * A point along a root. {@code t} is 0 at the seed and 1 at the tip.
     *
     * <p>Sampled onto the real terrain height rather than laid on a plane, so a
     * root crossing a slope climbs it. Both sides read the same heightmap for
     * loaded chunks, so the visual and the strike agree.</p>
     */
    /** The built tree. Built on first use and held; deterministic from the seed. */
    @Override
    public WorldTreeShape.Skeleton tree() {
        if (tree == null) {
            tree = WorldTreeShape.build(shapeParams());
        }
        return tree;
    }

    /** Every branch, all levels. */
    public WorldTreeShape.Limb[] skeleton() {
        return tree().limbs();
    }

    /** The subset of branches that carry leaves. */
    public WorldTreeShape.Limb[] twigs() {
        return tree().twigs();
    }

    public WorldTreeShape.Leaf[] leaves() {
        return tree().leaves();
    }

    @Override
    public Vec3 rootPoint(int root, float t, float partialTick) {
        WorldTreeParams params = shapeParams();
        Vec3 seed = seedPoint(partialTick);
        // Keep the visible roots inside the same radius that grants sanctuary.
        // This prevents a player from seeing roots outside the actual protected area.
        double rootScale = sanctuaryRadius() / WorldTreeShape.ROOT_REACH;
        double out = WorldTreeShape.rootDistance(params, root, t) * rootScale;
        float angle = WorldTreeShape.rootAngle(params, root, t);
        double x = seed.x + Mth.cos(angle) * out;
        double z = seed.z + Mth.sin(angle) * out;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        float rootRadius = WorldTreeShape.rootRadius(params, root, t) * (float) rootScale;
        return new Vec3(seed.x + Mth.cos(angle) * out,
                surface + 0.12D - rootRadius * 0.42D,
                seed.z + Mth.sin(angle) * out);
    }

    /**
     * Radius of a root at {@code t}, tapering to its tip.
     *
     * <p>Starts at the size of the buttress it continues out of, so the join reads
     * as one structure. The old roots began at the seed point at a fraction of the
     * trunk's width, which is why the trunk looked planted next to them rather than
     * growing out of them.</p>
     */
    public float rootRadius(int root, float t) {
        return WorldTreeShape.rootRadius(shapeParams(), root, t)
                * (float) (sanctuaryRadius() / WorldTreeShape.ROOT_REACH);
    }

    /** The built tree, held rather than rebuilt per frame. */
    private WorldTreeShape.Skeleton tree;

    /**
     * Hue index of a leaf, 0 to 1.
     *
     * <p>Weighted toward the warm and green-teal end of the palette, with the
     * magenta and violet bands kept to roughly a sixth of the crown. An even spread
     * over all six reads as confetti rather than as a canopy.</p>
     */
    /** Trunk radius at {@code height} blocks above the seed. */
    public float trunkRadius(float height) {
        return WorldTreeShape.trunkRadius(shapeParams(), height);
    }

    /**
     * How far out the buttress lobes stand at {@code height}, as a fraction of the
     * trunk radius. Zero above the flare.
     */
    public float buttressDepth(float height) {
        return WorldTreeShape.buttressDepth(shapeParams(), height);
    }

    private static float smoothstep(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    private float hashUnit(int index, long salt) {
        return FxHash.unit(getSeed(), index, salt);
    }

    @Override
    public void tick() {
        super.tick();

        int timelineTick = getTimelineAgeTicks();
        if (this.level() instanceof ServerLevel serverLevel) {
            maintainSanctuary(serverLevel);
            int interval = Math.max(1, SpellConfig.worldTreeHealingIntervalTicks);
            if (timelineTick % interval == 0) {
                applySanctuaryPulse(serverLevel);
            }
        }

        if (timelineTick >= SpellConfig.worldTreeLifetimeTicks) {
            this.discard();
        }
    }

    /** Heal allied entities in the tree's sanctuary; the tree no longer attacks. */
    private void applySanctuaryPulse(ServerLevel level) {
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = seedPoint(1.0F);
        double radius = sanctuaryRadius();
        List<LivingEntity> allies = SpellConfig.limitTargets("worldTree", level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(centre.x - radius, centre.y - radius, centre.z - radius,
                        centre.x + radius, centre.y + radius, centre.z + radius),
                target -> isAlly(caster, target) && target.getBoundingBox().getCenter()
                        .distanceToSqr(centre) <= radius * radius));
        float healFraction = (float) Math.max(0.0D, SpellConfig.worldTreeHealingFraction);
        float absorption = (float) Math.max(0.0D, SpellConfig.worldTreeAbsorptionHearts * 2.0D);
        for (LivingEntity target : allies) {
            if (healFraction > 0.0F) {
                target.heal(target.getMaxHealth() * healFraction);
            }
            applyTreeAbsorption(target, absorption);
        }
    }

    /** Remove grants from targets that left this sanctuary or are no longer allies. */
    private void maintainSanctuary(ServerLevel level) {
        if (sanctuaryAbsorption.isEmpty()) {
            return;
        }
        LivingEntity caster = resolveCaster(level);
        Vec3 centre = seedPoint(1.0F);
        Iterator<Map.Entry<UUID, AbsorptionState>> iterator = sanctuaryAbsorption.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AbsorptionState> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            LivingEntity target = entity instanceof LivingEntity living
                    ? living : sanctuaryTargets.get(entry.getKey());
            if (target == null || !isAlly(caster, target)
                    || !insideSanctuary(target, centre)) {
                if (target != null) {
                    removeTreeAbsorption(target, remainingTreeAbsorption(
                            entry.getValue(), target.getAbsorptionAmount()));
                }
                sanctuaryTargets.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private boolean insideSanctuary(LivingEntity target, Vec3 centre) {
        return target.getBoundingBox().getCenter().distanceToSqr(centre)
                <= sanctuaryRadius() * sanctuaryRadius();
    }

    /** Grant only the missing portion, keeping other absorption sources intact. */
    private void applyTreeAbsorption(LivingEntity target, float desired) {
        UUID uuid = target.getUUID();
        sanctuaryTargets.put(uuid, target);
        AbsorptionState previous = sanctuaryAbsorption.get(uuid);
        float current = Math.max(0.0F, target.getAbsorptionAmount());
        float remainingOwn = remainingTreeAbsorption(previous, current);
        if (desired <= 0.0F) {
            if (remainingOwn > 0.0F) {
                removeTreeAbsorption(target, remainingOwn);
            }
            sanctuaryAbsorption.remove(uuid);
            sanctuaryTargets.remove(uuid);
            return;
        }
        // Treat any amount above the previous total as an external grant. If the
        // total dropped, consume the tree's own grant first; this keeps unrelated
        // absorption intact and makes cleanup safe after the shield is damaged.
        float missingOwn = Math.max(0.0F, desired - remainingOwn);
        float total = current + missingOwn;
        if (missingOwn > 0.0F) {
            target.setAbsorptionAmount(total);
        }
        if (desired > 0.0F) {
            sanctuaryAbsorption.put(uuid, new AbsorptionState(desired, total));
        } else {
            sanctuaryAbsorption.remove(uuid);
            sanctuaryTargets.remove(uuid);
        }
    }

    /** Remove this tree's contribution without touching unrelated absorption. */
    private float remainingTreeAbsorption(AbsorptionState previous, float current) {
        if (previous == null) {
            return 0.0F;
        }
        if (current >= previous.lastTotal()) {
            return previous.own();
        }
        return Math.max(0.0F, previous.own() - (previous.lastTotal() - current));
    }

    private void removeTreeAbsorption(LivingEntity target, float grant) {
        float current = Math.max(0.0F, target.getAbsorptionAmount());
        target.setAbsorptionAmount(Math.max(0.0F, current - Math.max(0.0F, grant)));
    }

    private void cleanupSanctuary(ServerLevel level) {
        for (Map.Entry<UUID, AbsorptionState> entry : sanctuaryAbsorption.entrySet()) {
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            LivingEntity target = entity instanceof LivingEntity living
                    ? living : sanctuaryTargets.get(entry.getKey());
            if (target != null) {
                removeTreeAbsorption(target, remainingTreeAbsorption(
                        entry.getValue(), target.getAbsorptionAmount()));
            }
        }
        sanctuaryAbsorption.clear();
        sanctuaryTargets.clear();
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

    private boolean isAlly(LivingEntity caster, LivingEntity target) {
        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }
        if (this.casterUuid != null && this.casterUuid.equals(target.getUUID())) {
            return true;
        }
        if (this.casterUuid != null && target instanceof TamableAnimal tamable
                && this.casterUuid.equals(tamable.getOwnerUUID())) {
            return true;
        }
        if (this.casterTeamName != null && target.getTeam() != null
                && this.casterTeamName.equals(target.getTeam().getName())) {
            return true;
        }
        return caster != null && (target == caster
                || caster.isAlliedTo(target) || target.isAlliedTo(caster)
                || (target instanceof TamableAnimal tamable && tamable.isOwnedBy(caster)));
    }

    /** True when this active tree protects the entity from the selected damage kind. */
    public boolean protects(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        LivingEntity caster = level() instanceof ServerLevel server ? resolveCaster(server) : null;
        if (!isAlly(caster, target)) {
            return false;
        }
        Vec3 centre = seedPoint(1.0F);
        if (!insideSanctuary(target, centre)) {
            return false;
        }
        // Minecraft has no generic MAGIC tag in 1.20.1. WITCH_RESISTANT_TO is
        // the vanilla tag used for potion and magic-like damage, so it is the
        // closest stable category for a configurable sanctuary ward.
        return (SpellConfig.worldTreeProtectMagic
                && (source instanceof SpellDamageSource
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)))
                || (SpellConfig.worldTreeProtectProjectile && source.is(DamageTypeTags.IS_PROJECTILE))
                || (SpellConfig.worldTreeProtectFire && source.is(DamageTypeTags.IS_FIRE));
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
        String teamName = tag.getString("CasterTeam");
        this.casterTeamName = teamName.isEmpty() ? null : teamName;
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
        if (this.casterTeamName != null) {
            tag.putString("CasterTeam", this.casterTeamName);
        }
        tag.putInt("CasterId", this.getCasterId());
        tag.putInt("Seed", this.getSeed());
        tag.putLong("StartGameTick", this.entityData.get(DATA_START_GAME_TICK));
        tag.putInt("TimelineTick", getTimelineAgeTicks());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Also clean grants when the entity is removed by /kill or another system. */
    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            cleanupSanctuary(serverLevel);
        }
        super.remove(reason);
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

package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.fx.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.network.NetworkHooks;

/** Server-owned stationary anchor. Attachments can share a root UUID without duplicating gravity.
 * Data accessors are allocated on THIS declaring class; subclasses must not redeclare them. */
public abstract class BlackHoleEntity extends Entity implements BlackHoleInstance {
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OPEN = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOSE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PARENT_ID = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> PARENT_UUID = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TILT = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RATE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DISPLAY = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.BOOLEAN);
    protected int damageInterval = 10;
    protected float damageFraction, secondaryFraction;
    protected boolean absorbItems, breakBlocks;
    private String ownerTeam = "";

    protected BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }
    public abstract String configId();
    public abstract String effectId();
    public final void configure(LivingEntity caster, Vec3 center, Entity root) {
        entityData.set(OWNER, Optional.of(caster.getUUID()));
        ownerTeam = caster.getTeam() == null ? "" : caster.getTeam().getName();
        configureAt(center, caster.getYRot(), caster.getRandom().nextInt(), false);
        if (root instanceof BlackHoleInstance instance && instance.bhIsRoot() && !instance.bhIsDisplay()
                && instance.bhOwnedBy(caster) && root.level() == level() && root != this && root.isAlive()) {
            entityData.set(PARENT_ID, root.getId());
            entityData.set(PARENT_UUID, Optional.of(root.getUUID()));
            setPos(root.position());
        }
    }
    public final void configureDisplay(Vec3 center, float yaw, int seed) { configureAt(center, yaw, seed, true); }
    private void configureAt(Vec3 center, float yaw, int seed, boolean display) {
        entityData.set(START, level().getGameTime());
        entityData.set(LIFE, SpellConfig.lifetimeTicks(configId()));
        entityData.set(OPEN, SpellConfig.phaseTick(configId(), 1));
        entityData.set(CLOSE, SpellConfig.phaseTick(configId(), 2));
        entityData.set(RADIUS, (float) SpellConfig.effectRadius(configId()));
        entityData.set(YAW, yaw); entityData.set(TILT, SpellConfig.bhDiskTiltDegrees());
        entityData.set(RATE, configId().equals("stasisSingularity") ? SpellConfig.bhTimeScale() : 1F);
        entityData.set(SEED, seed); entityData.set(DISPLAY, display);
        damageInterval = SpellConfig.damageIntervalTicks(configId());
        damageFraction = (float) SpellConfig.damageFraction(configId());
        secondaryFraction = (float) SpellConfig.secondaryDamageFraction(configId());
        absorbItems = SpellConfig.bhAbsorbItems(); breakBlocks = SpellConfig.bhBreakBlocks();
        moveTo(center.x, center.y, center.z, 0, 0);
    }
    public final float age(float partial) {
        long start = entityData.get(START);
        return Math.max(0, (start < 0 ? 0 : level().getGameTime() - start) + partial);
    }
    public final int lifetime() { return entityData.get(LIFE); }
    public final int openTick() { return entityData.get(OPEN); }
    public final int closeTick() { return entityData.get(CLOSE); }
    public final float yawDegrees() { return entityData.get(YAW); }
    public final float localTimeScale() { return entityData.get(RATE); }
    public final float envelope(float partial) {
        return bhIsDisplay() ? 1F : BlackHoleMath.envelope(age(partial), lifetime(), openTick(), closeTick());
    }
    public final Entity rootEntity() {
        int id = entityData.get(PARENT_ID);
        Entity root = id >= 0 ? level().getEntity(id) : null;
        return root instanceof BlackHoleInstance instance && instance.bhIsRoot() && root.isAlive()
                && entityData.get(PARENT_UUID).filter(root.getUUID()::equals).isPresent() ? root : null;
    }
    public final boolean bhReady() { return entityData.get(START) >= 0 && (bhIsRoot() || rootEntity() != null); }
    @Override public final BlackHoleParameters bhParameters(float partial) {
        if (rootEntity() instanceof BlackHoleInstance root) {
            BlackHoleParameters p = root.bhParameters(partial);
            return new BlackHoleParameters(p.bhInstanceId(), p.bhCenter(), p.bhHorizonRadius(),
                    p.bhInfluenceRadius(), p.bhDiskNormal(), p.bhStartTick(), p.bhLifetimeTicks(),
                    p.bhAgeTicks(), Math.min(p.bhEnvelope(), envelope(partial)),
                    configId().equals("stasisSingularity") ? localTimeScale() : p.bhTimeScale(), p.bhSeed());
        }
        double tilt = Math.toRadians(entityData.get(TILT)), yaw = Math.toRadians(yawDegrees());
        Vec3 normal = new Vec3(-Math.sin(tilt) * Math.cos(yaw), Math.cos(tilt), -Math.sin(tilt) * Math.sin(yaw));
        float fade = envelope(partial), radius = entityData.get(RADIUS);
        return new BlackHoleParameters(getUUID(), position(), radius / 12F * fade, radius, normal,
                entityData.get(START), lifetime(), age(partial), fade, localTimeScale(), entityData.get(SEED));
    }
    @Override public final boolean bhIsDisplay() { return entityData.get(DISPLAY); }
    @Override public final boolean bhIsRoot() { return entityData.get(PARENT_ID) < 0; }
    @Override public boolean bhHasGravity() { return false; }
    @Override public final boolean bhOwnedBy(LivingEntity caster) { return entityData.get(OWNER).filter(caster.getUUID()::equals).isPresent(); }
    protected final LivingEntity owner(ServerLevel level) {
        return entityData.get(OWNER).map(level::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).orElse(null);
    }
    protected final boolean canAffect(LivingEntity caster, LivingEntity target) {
        if (!target.isAlive() || target.isRemoved() || target.isInvulnerable()
                || entityData.get(OWNER).filter(target.getUUID()::equals).isPresent()) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (target instanceof TamableAnimal pet && entityData.get(OWNER).filter(id -> id.equals(pet.getOwnerUUID())).isPresent()) return false;
        if (caster != null && (caster.isAlliedTo(target) || target.isAlliedTo(caster))) return false;
        if (caster instanceof Player a && target instanceof Player b
                && (level().getServer() == null || !level().getServer().isPvpAllowed() || !a.canHarmPlayer(b))) return false;
        return ownerTeam.isEmpty() || target.getTeam() == null || !ownerTeam.equals(target.getTeam().getName());
    }
    protected final List<LivingEntity> targets(ServerLevel server, double radius) {
        LivingEntity caster = owner(server);
        var values = server.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(radius),
                t -> canAffect(caster, t) && t.getBoundingBox().getCenter().distanceToSqr(position()) <= radius * radius);
        values.sort(Comparator.comparingDouble(t -> t.distanceToSqr(position())));
        return SpellConfig.limitTargets(configId(), values);
    }
    protected final void damage(ServerLevel server, LivingEntity target, float fraction) {
        if (fraction > 0) target.hurt(BlackHoleDamage.source(server, this, owner(server)), Math.max(.1F, target.getMaxHealth() * fraction));
    }
    @Override public final void tick() {
        // Vanilla baseTick probes eye fluid even for noPhysics entities. Check first so an
        // unloaded stationary anchor never force-loads terrain through that implicit query.
        if (level() instanceof ServerLevel server && !server.hasChunkAt(blockPosition())) {
            discard(); return;
        }
        super.tick(); setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel server)) return;
        if (!bhIsRoot()) {
            Entity root = rootEntity();
            if (root == null) { discard(); return; }
            setPos(root.position());
        }
        if (bhIsDisplay()) return;
        // If the caster unloads/logs out, fail closed rather than losing PVP/team attribution.
        if (entityData.get(OWNER).isEmpty() || owner(server) == null || !owner(server).isAlive()) { discard(); return; }
        int age = (int) age(0);
        if (age >= lifetime()) { discard(); return; }
        serverTick(server, age);
    }
    protected abstract void serverTick(ServerLevel server, int age);
    @Override protected void defineSynchedData() {
        entityData.define(START, -1L); entityData.define(LIFE, 240);
        entityData.define(OPEN, 30); entityData.define(CLOSE, 200); entityData.define(SEED, 0);
        entityData.define(PARENT_ID, -1); entityData.define(PARENT_UUID, Optional.empty()); entityData.define(OWNER, Optional.empty());
        entityData.define(RADIUS, 14F); entityData.define(YAW, 0F); entityData.define(TILT, 26F);
        entityData.define(RATE, 1F); entityData.define(DISPLAY, false);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER).ifPresent(id -> tag.putUUID("bhOwner", id));
        entityData.get(PARENT_UUID).ifPresent(id -> tag.putUUID("bhRoot", id));
        tag.putString("bhOwnerTeam", ownerTeam); tag.putLong("bhStartTick", entityData.get(START));
        tag.putInt("bhLife", lifetime()); tag.putInt("bhOpen", openTick()); tag.putInt("bhClose", closeTick());
        tag.putInt("bhSeed", entityData.get(SEED)); tag.putFloat("bhRadius", entityData.get(RADIUS));
        tag.putFloat("bhYaw", yawDegrees()); tag.putFloat("bhTilt", entityData.get(TILT)); tag.putFloat("bhRate", localTimeScale());
        tag.putBoolean("bhDisplay", bhIsDisplay()); tag.putInt("bhInterval", damageInterval);
        tag.putFloat("bhDamage", damageFraction); tag.putFloat("bhSecondary", secondaryFraction);
        tag.putBoolean("bhItems", absorbItems); tag.putBoolean("bhBlocks", breakBlocks);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER, tag.hasUUID("bhOwner") ? Optional.of(tag.getUUID("bhOwner")) : Optional.empty());
        ownerTeam = tag.getString("bhOwnerTeam");
        entityData.set(START, tag.contains("bhStartTick") ? tag.getLong("bhStartTick") : level().getGameTime());
        entityData.set(LIFE, Math.max(1, Math.min(72000, tag.getInt("bhLife"))));
        entityData.set(OPEN, Math.max(0, tag.getInt("bhOpen"))); entityData.set(CLOSE, Math.max(0, tag.getInt("bhClose")));
        entityData.set(SEED, tag.getInt("bhSeed")); entityData.set(RADIUS, BlackHoleMath.finite(tag.getFloat("bhRadius"), .1F, 256F));
        entityData.set(YAW, BlackHoleMath.finite(tag.getFloat("bhYaw"), -360F, 360F));
        entityData.set(TILT, BlackHoleMath.finite(tag.getFloat("bhTilt"), 0, 85));
        entityData.set(RATE, BlackHoleMath.finite(tag.getFloat("bhRate"), .2F, 1)); entityData.set(DISPLAY, tag.getBoolean("bhDisplay"));
        damageInterval = Math.max(1, tag.getInt("bhInterval")); damageFraction = BlackHoleMath.finite(tag.getFloat("bhDamage"), 0, 1);
        secondaryFraction = BlackHoleMath.finite(tag.getFloat("bhSecondary"), 0, 1); absorbItems = tag.getBoolean("bhItems"); breakBlocks = tag.getBoolean("bhBlocks");
        // Anchors deliberately do not persist across save/unload. Never revive an orphan as an independent damaging well.
        if (tag.hasUUID("bhRoot")) { entityData.set(PARENT_UUID, Optional.of(tag.getUUID("bhRoot"))); entityData.set(PARENT_ID, Integer.MAX_VALUE); }
    }
    @Override public final boolean shouldBeSaved() { return false; }
    @Override public final boolean isPickable() { return false; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override public boolean isPushedByFluid(FluidType fluidType) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}

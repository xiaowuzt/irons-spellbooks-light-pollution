package com.gang.lightpollution.entity;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.fx.EventHorizonShape;
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
import java.util.Comparator;
import java.util.UUID;

/** Stationary, server-owned spell; clients receive the exact lifetime/radius/phase snapshot. */
public final class EventHorizonEntity extends Entity {
    public static final String CONFIG_ID = "eventHorizon";
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OPEN = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLOSE = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DISPLAY = SynchedEntityData.defineId(EventHorizonEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID casterUuid;
    private String casterTeam = "";
    private boolean collapsed;
    private int interval = 10;
    private float damage = .015F, collapseDamage = .28F;

    public EventHorizonEntity(EntityType<? extends EventHorizonEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }
    public void configure(LivingEntity caster, Vec3 centre, int seed) {
        casterUuid = caster.getUUID();
        casterTeam = caster.getTeam() == null ? "" : caster.getTeam().getName();
        configureAt(centre, caster.getYRot(), seed, false);
    }
    public void configureDisplay(Vec3 centre, float yaw, int seed) {
        configureAt(centre, yaw, seed, true);
    }
    private void configureAt(Vec3 centre, float yaw, int seed, boolean display) {
        entityData.set(START, level().getGameTime());
        entityData.set(LIFE, SpellConfig.lifetimeTicks(CONFIG_ID));
        entityData.set(OPEN, SpellConfig.phaseTick(CONFIG_ID, 1));
        entityData.set(CLOSE, SpellConfig.phaseTick(CONFIG_ID, 2));
        entityData.set(RADIUS, (float) SpellConfig.effectRadius(CONFIG_ID));
        entityData.set(YAW, yaw);
        entityData.set(SEED, seed);
        entityData.set(DISPLAY, display);
        interval = SpellConfig.damageIntervalTicks(CONFIG_ID);
        damage = (float) SpellConfig.damageFraction(CONFIG_ID);
        collapseDamage = (float) SpellConfig.secondaryDamageFraction(CONFIG_ID);
        moveTo(centre.x, centre.y, centre.z, 0, 0);
    }
    public boolean isDisplay() { return entityData.get(DISPLAY); }
    public boolean isCastBy(LivingEntity caster) { return caster.getUUID().equals(casterUuid); }
    public float age(float partialTick) {
        long start = entityData.get(START);
        return Math.max(0, (start < 0 ? tickCount : level().getGameTime() - start) + partialTick);
    }
    public float envelope(float partialTick) {
        return isDisplay() ? 1 : EventHorizonShape.envelope(age(partialTick), entityData.get(LIFE), entityData.get(OPEN), entityData.get(CLOSE));
    }
    public float unitRadius(float partialTick) {
        return EventHorizonShape.unitRadius(entityData.get(RADIUS), envelope(partialTick));
    }
    public float visualTime(float partialTick) {
        return age(partialTick) / 20.0F + (entityData.get(SEED) & 1023);
    }
    public Vec3 spinAxis() {
        double yaw = Math.toRadians(entityData.get(YAW));
        return new Vec3(-Math.cos(yaw) * Math.sin(.45), Math.cos(.45), -Math.sin(yaw) * Math.sin(.45));
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isDisplay()) return;
        int age = (int) age(0);
        // Expire before damage: a late packet or unloaded chunk must not replay the collapse.
        if (age >= entityData.get(LIFE)) { discard(); return; }
        int open = entityData.get(OPEN), close = entityData.get(CLOSE);
        if (age >= open && age < close && (age % 4 == 0 || age % interval == 0)) {
            affect(server, age % interval == 0 ? damage : 0, true);
        }
        if (!collapsed && age >= close) {
            collapsed = true;
            affect(server, collapseDamage, false);
        }
    }
    private void affect(ServerLevel server, float fraction, boolean pull) {
        LivingEntity caster = casterUuid != null && server.getEntity(casterUuid) instanceof LivingEntity living ? living : null;
        double radius = entityData.get(RADIUS);
        // Filter BEFORE applying the budget so friendlies cannot crowd enemies out of a pulse.
        var targets = server.getEntitiesOfClass(LivingEntity.class,
                new AABB(position(), position()).inflate(radius), t -> canAffect(caster, t)
                        && t.getBoundingBox().getCenter().distanceToSqr(position()) <= radius * radius);
        targets.sort(Comparator.comparingDouble(t -> t.distanceToSqr(position())));
        for (LivingEntity target : SpellConfig.limitTargets(CONFIG_ID, targets)) {
            Vec3 delta = position().subtract(target.getBoundingBox().getCenter());
            double distance = delta.length();
            if (pull && distance > .1 && SpellConfig.eventHorizonPullStrength > 0) {
                double force = SpellConfig.eventHorizonPullStrength * (.10 + .20 * (1 - Math.min(1, distance / radius)));
                Vec3 velocity = target.getDeltaMovement().scale(.85).add(delta.scale(force / distance));
                if (velocity.lengthSqr() > 1.44) velocity = velocity.normalize().scale(1.2);
                target.setDeltaMovement(velocity);
                target.hurtMarked = true;
            }
            if (fraction > 0) SpellDamage.apply(this, target, EventHorizonDamage.source(server, this, caster), fraction);
        }
    }
    private boolean canAffect(LivingEntity caster, LivingEntity target) {
        if (!target.isAlive() || target.isRemoved() || target.getUUID().equals(casterUuid)) return false;
        if (target instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        if (target instanceof TamableAnimal pet && casterUuid != null && casterUuid.equals(pet.getOwnerUUID())) return false;
        if (caster != null) return !caster.isAlliedTo(target) && !target.isAlliedTo(caster);
        return casterTeam.isEmpty() || target.getTeam() == null || !casterTeam.equals(target.getTeam().getName());
    }
    @Override protected void defineSynchedData() {
        entityData.define(START, -1L); entityData.define(LIFE, 280);
        entityData.define(OPEN, 40); entityData.define(CLOSE, 240);
        entityData.define(SEED, 0); entityData.define(RADIUS, 18F);
        entityData.define(YAW, 0F); entityData.define(DISPLAY, false);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (casterUuid != null) tag.putUUID("Caster", casterUuid);
        tag.putString("CasterTeam", casterTeam);
        tag.putLong("Start", entityData.get(START)); tag.putInt("Lifetime", entityData.get(LIFE));
        tag.putInt("Open", entityData.get(OPEN)); tag.putInt("Close", entityData.get(CLOSE));
        tag.putInt("Seed", entityData.get(SEED)); tag.putFloat("Radius", entityData.get(RADIUS));
        tag.putFloat("Yaw", entityData.get(YAW)); tag.putBoolean("Display", isDisplay());
        tag.putBoolean("Collapsed", collapsed); tag.putInt("Interval", interval);
        tag.putFloat("Damage", damage); tag.putFloat("CollapseDamage", collapseDamage);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        casterTeam = tag.getString("CasterTeam");
        entityData.set(START, tag.contains("Start") ? tag.getLong("Start") : level().getGameTime());
        entityData.set(LIFE, Math.max(1, tag.getInt("Lifetime")));
        entityData.set(OPEN, Math.max(0, tag.getInt("Open")));
        entityData.set(CLOSE, Math.max(0, tag.getInt("Close")));
        entityData.set(SEED, tag.getInt("Seed")); entityData.set(RADIUS, finite(tag.getFloat("Radius"), .1F, 256F));
        entityData.set(YAW, finite(tag.getFloat("Yaw"), -360F, 360F)); entityData.set(DISPLAY, tag.getBoolean("Display"));
        collapsed = tag.getBoolean("Collapsed"); interval = Math.max(1, tag.getInt("Interval"));
        damage = finite(tag.getFloat("Damage"), 0, 1); collapseDamage = finite(tag.getFloat("CollapseDamage"), 0, 1);
    }
    private static float finite(float v, float low, float high) { return Float.isFinite(v) ? Math.max(low, Math.min(high, v)) : low; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushedByFluid(FluidType fluidType) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}

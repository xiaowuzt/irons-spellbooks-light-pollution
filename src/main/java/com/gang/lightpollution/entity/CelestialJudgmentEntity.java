package com.gang.lightpollution.entity;

import com.gang.lightpollution.spell.ModSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.fluids.FluidType;

import java.util.UUID;

/** Server-side anchor and impact controller for the Celestial Judgment visuals. */
public class CelestialJudgmentEntity extends Entity {
    public static final int LIFETIME_TICKS = 100;
    public static final int IMPACT_TICK = 52;

    private static final EntityDataAccessor<Integer> DATA_TARGET_ID = SynchedEntityData.defineId(
            CelestialJudgmentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(
            CelestialJudgmentEntity.class, EntityDataSerializers.INT);

    private UUID casterUuid;
    private UUID targetUuid;
    private boolean impacted;
    private int missingTargetTicks;

    public CelestialJudgmentEntity(EntityType<? extends CelestialJudgmentEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(LivingEntity caster, LivingEntity target) {
        this.casterUuid = caster.getUUID();
        this.targetUuid = target.getUUID();
        this.entityData.set(DATA_TARGET_ID, target.getId());
        this.setPos(target.getX(), target.getY(), target.getZ());
    }

    public int getTargetId() {
        return this.entityData.get(DATA_TARGET_ID);
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel serverLevel) {
            LivingEntity target = resolveTarget(serverLevel);
            if (target == null || !target.isAlive()) {
                if (++this.missingTargetTicks > 5) {
                    this.discard();
                    return;
                }
            } else {
                this.missingTargetTicks = 0;
                this.entityData.set(DATA_TARGET_ID, target.getId());
                this.setPos(target.getX(), target.getY(), target.getZ());

                if (!this.impacted && this.tickCount >= IMPACT_TICK) {
                    this.impacted = true;
                    applyImpact(serverLevel, target);
                }
            }

            this.entityData.set(DATA_PHASE, phaseForTick(this.tickCount));
        }

        if (this.tickCount >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    private LivingEntity resolveTarget(ServerLevel serverLevel) {
        if (this.targetUuid == null) {
            return null;
        }
        Entity entity = serverLevel.getEntity(this.targetUuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void applyImpact(ServerLevel serverLevel, LivingEntity target) {
        Entity casterEntity = this.casterUuid == null ? null : serverLevel.getEntity(this.casterUuid);
        if (!(casterEntity instanceof LivingEntity caster) || !caster.isAlive()) {
            return;
        }

        float rawDamage = Math.max(1.0F, target.getMaxHealth() * 0.84F);
        // The spell entity is the direct source and the caster is the owner.
        // Passing the target as the owner makes Iron's Spells treat this as
        // friendly fire against the target itself and cancel the impact.
        SpellDamageSource source = ModSpells.CELESTIAL_JUDGMENT.get().getDamageSource(this, caster);
        if (DamageSources.applyDamage(target, rawDamage, source)) {
            caster.heal(caster.getMaxHealth() * 0.64F);
        }
    }

    private static int phaseForTick(int tick) {
        if (tick < 16) {
            return 0;
        }
        if (tick < 36) {
            return 1;
        }
        if (tick < IMPACT_TICK) {
            return 2;
        }
        return 3;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TARGET_ID, 0);
        this.entityData.define(DATA_PHASE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.targetUuid = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        this.impacted = tag.getBoolean("Impacted");
        this.missingTargetTicks = tag.getInt("MissingTargetTicks");
        this.entityData.set(DATA_TARGET_ID, tag.getInt("TargetId"));
        this.entityData.set(DATA_PHASE, tag.getInt("Phase"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        if (this.targetUuid != null) {
            tag.putUUID("Target", this.targetUuid);
        }
        tag.putBoolean("Impacted", this.impacted);
        tag.putInt("MissingTargetTicks", this.missingTargetTicks);
        tag.putInt("TargetId", this.getTargetId());
        tag.putInt("Phase", this.getPhase());
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
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
    public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose) {
        return net.minecraft.world.entity.EntityDimensions.fixed(1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

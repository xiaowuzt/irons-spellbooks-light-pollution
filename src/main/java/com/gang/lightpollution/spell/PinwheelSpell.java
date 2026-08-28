package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.PinwheelEntity;
import com.gang.lightpollution.registry.ModEntities;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A Wolf-Rayet pinwheel, after WR 104.
 *
 * <p>Two hot stars whose winds collide, condensing dust along the shock and trailing it out
 * behind the orbit as a spiral. Archimedean rather than Keplerian — constant pitch, because
 * dust leaves at a steady speed while the binary turns at a steady rate — with empty space
 * between the arms and dust colours rather than a hot disk's blue-white, all of which keeps
 * it from being a third accretion disk.</p>
 *
 * <p>It rotates rigidly, and that is the mechanic: the safe gaps between the arms travel
 * with the pattern, so a gap only stays safe until the next arm sweeps into it.</p>
 */
public final class PinwheelSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pinwheel");
    public static final int MAX_RANGE = 42;
    public static final int FIXED_COOLDOWN_TICKS = 280 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(280)
            .setAllowCrafting(false)
            .build();

    public PinwheelSpell() {
        this.baseManaCost = 1100;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 55;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return this.defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return ID;
    }

    @Override
    public int getSpellCooldown() {
        return FIXED_COOLDOWN_TICKS;
    }

    @Override
    public boolean checkPreCastConditions(
            Level level,
            int spellLevel,
            LivingEntity caster,
            MagicData magicData) {
        return caster.isAlive()
                && !(caster instanceof Player player && player.isSpectator());
    }

    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData magicData) {
        if (!(level instanceof ServerLevel serverLevel) || !caster.isAlive()) {
            return;
        }

        double duplicateSearchRadius = MAX_RANGE + PinwheelEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                PinwheelEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        Vec3 centre = resolveCentre(serverLevel, caster);
        PinwheelEntity effect =
                new PinwheelEntity(ModEntities.PINWHEEL.get(), serverLevel);
        // The plane leans across the caster's view, so the spiral is seen close to face-on.
        // Edge-on it collapses into a line and the arms cannot be told apart.
        effect.configure(caster, centre, caster.getYRot() + 90.0F,
                caster.getRandom().nextInt());
        serverLevel.addFreshEntity(effect);
    }

    private static Vec3 resolveCentre(ServerLevel level, LivingEntity caster) {
        HitResult hit = Utils.raycastForEntity(level, caster, MAX_RANGE, true);
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().getBoundingBox().getCenter();
        }
        if (hit instanceof BlockHitResult blockHit
                && hit.getType() != HitResult.Type.MISS) {
            return blockHit.getLocation();
        }
        Vec3 end = caster.getEyePosition()
                .add(caster.getViewVector(1.0F).scale(MAX_RANGE));
        double y = Math.max(level.getMinBuildHeight() + 1.0D,
                Math.min(level.getMaxBuildHeight() - 1.0D, end.y));
        return new Vec3(end.x, y, end.z);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
                "spell.irons_spellbooks_light_pollution.pinwheel.info"));
    }
}

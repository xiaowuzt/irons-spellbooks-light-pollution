package com.gang.lightpollution.spell;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.CrabNebulaEntity;
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
 * The Crab Nebula, M1 — the remnant of the supernova recorded in 1054.
 *
 * <p>Two structures that are genuinely separate: a cage of filaments on the surface of the
 * shell, with nothing between them, and inside it a synchrotron wind nebula driven by the
 * pulsar at the centre. Unlike the World Tree, which is a solid branching from a root, this
 * is a hollow cage over a void — a player can stand inside one.</p>
 *
 * <p>So it is a trap rather than a radius. Outside is safe, the filaments sting, and the
 * inside is where the pulsar's wind pulses. Leaving means crossing the cage again.</p>
 */
public final class CrabNebulaSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "crab_nebula");
    public static final int MAX_RANGE = 42;
    public static final int FIXED_COOLDOWN_TICKS = 260 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(260)
            .setAllowCrafting(true)
            .build();

    public CrabNebulaSpell() {
        this.baseManaCost = 1050;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 55;
    }

    @Override
    public int getManaCost(int spellLevel) {
        return SpellConfig.manaCost("crabNebula");
    }

    @Override
    public int getCastTime(int spellLevel) {
        return SpellConfig.castTimeTicks("crabNebula");
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
        return SpellConfig.cooldownSeconds("crabNebula") * 20;
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

        double duplicateSearchRadius = SpellConfig.castRange("crabNebula") + CrabNebulaEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                CrabNebulaEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        Vec3 centre = resolveCentre(serverLevel, caster);
        CrabNebulaEntity effect =
                new CrabNebulaEntity(ModEntities.CRAB_NEBULA.get(), serverLevel);
        // Orientation only sets which way the filament cage is seeded; a sphere of arcs
        // has no preferred viewing angle, unlike the flat and beamed effects here.
        effect.configure(caster, centre, caster.getYRot() + 90.0F,
                caster.getRandom().nextInt());
        serverLevel.addFreshEntity(effect);
    }

    private static Vec3 resolveCentre(ServerLevel level, LivingEntity caster) {
        HitResult hit = Utils.raycastForEntity(level, caster, SpellConfig.castRange("crabNebula"), true);
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().getBoundingBox().getCenter();
        }
        if (hit instanceof BlockHitResult blockHit
                && hit.getType() != HitResult.Type.MISS) {
            return blockHit.getLocation();
        }
        Vec3 end = caster.getEyePosition()
                .add(caster.getViewVector(1.0F).scale(SpellConfig.castRange("crabNebula")));
        double y = Math.max(level.getMinBuildHeight() + 1.0D,
                Math.min(level.getMaxBuildHeight() - 1.0D, end.y));
        return new Vec3(end.x, y, end.z);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
                "spell.irons_spellbooks_light_pollution.crab_nebula.info"));
    }
}

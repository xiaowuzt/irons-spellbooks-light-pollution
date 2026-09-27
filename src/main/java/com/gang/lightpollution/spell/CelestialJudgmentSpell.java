package com.gang.lightpollution.spell;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.CelestialJudgmentEntity;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A long-cast holy spell that marks one living target, then calls down a moving
 * celestial beam. The custom entity owns the visual lifetime and impact timing.
 */
public class CelestialJudgmentSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "celestial_judgment");
    public static final int FIXED_COOLDOWN_TICKS = 600 * 20;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(600)
            .setAllowCrafting(true)
            .build();

    public CelestialJudgmentSpell() {
        this.baseManaCost = 800;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 50;
    }

    @Override
    public int getManaCost(int spellLevel) {
        return SpellConfig.manaCost("celestialJudgment");
    }

    @Override
    public int getCastTime(int spellLevel) {
        return SpellConfig.castTimeTicks("celestialJudgment");
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return ID;
    }

    @Override
    public int getSpellCooldown() {
        // This spell deliberately ignores irons_spellbooks:cooldown_reduction.
        return SpellConfig.cooldownSeconds("celestialJudgment") * 20;
    }

    @Override
    public boolean checkPreCastConditions(
            Level level,
            int spellLevel,
            LivingEntity caster,
            MagicData magicData) {
        return Utils.preCastTargetHelper(level, caster, magicData, this, SpellConfig.celestialTargetRange, 0.5F, false);
    }

    @Override
    public void onCast(
            Level level,
            int spellLevel,
            LivingEntity caster,
            CastSource castSource,
            MagicData magicData) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LivingEntity target = null;
        if (magicData.getAdditionalCastData() instanceof TargetEntityCastData castData) {
            target = castData.getTarget(serverLevel);
        }
        if (target == null || !target.isAlive() || target == caster) {
            return;
        }

        CelestialJudgmentEntity effect = new CelestialJudgmentEntity(
                com.gang.lightpollution.registry.ModEntities.CELESTIAL_JUDGMENT.get(), serverLevel);
        effect.configure(caster, target);
        effect.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0.0F);
        serverLevel.addFreshEntity(effect);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
                "spell.irons_spellbooks_light_pollution.celestial_judgment.info"));
    }
}

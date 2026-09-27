package com.gang.lightpollution.spell;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.SilhouetteEntity;
import com.gang.lightpollution.registry.ModEntities;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Turns the light/shadow relationship inside out for as long as it is held.
 *
 * <p>The mod's first channelled spell. While the channel lasts, surfaces the
 * caster's spell light reaches go black and surfaces in shadow glow, so the
 * shadows this mod is built to cast become the only thing visible — and standing
 * away from the light is what hurts.</p>
 */
public final class SilhouetteSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "silhouette");
    public static final int FIXED_COOLDOWN_TICKS = 240 * 20;
    /** Channel length in ticks; the entity's own ceiling matches this. */
    private static final int CHANNEL_TICKS = 200;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(240)
            .setAllowCrafting(true)
            .build();

    public SilhouetteSpell() {
        this.baseManaCost = 1000;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = CHANNEL_TICKS;
    }

    @Override
    public int getManaCost(int spellLevel) {
        return SpellConfig.manaCost("silhouette");
    }

    @Override
    public int getCastTime(int spellLevel) {
        return SpellConfig.castTimeTicks("silhouette");
    }

    @Override
    public CastType getCastType() {
        return CastType.CONTINUOUS;
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
        return SpellConfig.cooldownSeconds("silhouette") * 20;
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

    /**
     * A continuous spell fires onCast once as the channel opens; the field then
     * follows the caster and ends itself when the channel does.
     */
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
        if (!findActiveField(serverLevel, caster).isEmpty()) {
            return;
        }
        SilhouetteEntity field =
                new SilhouetteEntity(ModEntities.SILHOUETTE.get(), serverLevel);
        field.configure(caster);
        serverLevel.addFreshEntity(field);
    }

    /**
     * Releasing the channel — or running out of mana, dying, or switching item —
     * all route through here, so this is where the field is told to settle back.
     */
    @Override
    public void onServerCastComplete(
            Level level,
            int spellLevel,
            LivingEntity caster,
            MagicData magicData,
            boolean cancelled) {
        if (level instanceof ServerLevel serverLevel) {
            for (SilhouetteEntity field : findActiveField(serverLevel, caster)) {
                field.release();
            }
        }
        super.onServerCastComplete(level, spellLevel, caster, magicData, cancelled);
    }

    private static List<SilhouetteEntity> findActiveField(ServerLevel level, LivingEntity caster) {
        return level.getEntitiesOfClass(
                SilhouetteEntity.class,
                caster.getBoundingBox().inflate(SilhouetteEntity.FIELD_RADIUS * 2.0D),
                field -> field.isCastBy(caster) && field.isChannelling());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
                "spell.irons_spellbooks_light_pollution.silhouette.info"));
    }
}

package com.gang.lightpollution.spell;

import com.gang.lightpollution.SpellConfig;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.ConstellationEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Calls down a single star that orbits the aimed point and pulls its victims in.
 *
 * <p>The star is one bright moving light, so every occluder under the orbit
 * throws a long shadow that sweeps as it turns. The threat is sustained rather
 * than a single hit: its gravity drags living things toward it and its surface
 * burns whatever it holds, hardest for anything pulled all the way in.</p>
 */
public final class ConstellationSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "constellation");
    public static final int MAX_RANGE = 40;
    public static final int FIXED_COOLDOWN_TICKS = 300 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(300)
            .setAllowCrafting(true)
            .build();

    public ConstellationSpell() {
        this.baseManaCost = 1400;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 40;
    }

    @Override
    public int getManaCost(int spellLevel) {
        return SpellConfig.manaCost("constellation");
    }

    @Override
    public int getCastTime(int spellLevel) {
        return SpellConfig.castTimeTicks("constellation");
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
        return SpellConfig.cooldownSeconds("constellation") * 20;
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

        double duplicateSearchRadius = SpellConfig.castRange("constellation") + ConstellationEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                ConstellationEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        HitResult hit = Utils.raycastForEntity(serverLevel, caster, SpellConfig.castRange("constellation"), true);
        LivingEntity target = hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living ? living : null;
        ConstellationEntity effect =
                new ConstellationEntity(ModEntities.CONSTELLATION.get(), serverLevel);
        effect.configure(caster, resolveCenter(serverLevel, caster, hit),
                caster.getRandom().nextFloat() * (float) (Math.PI * 2.0), target);
        serverLevel.addFreshEntity(effect);
    }

    private static Vec3 resolveCenter(ServerLevel level, LivingEntity caster, HitResult hit) {
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().position();
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
            Vec3 surfaceNormal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            return blockHit.getLocation().add(surfaceNormal.scale(0.08D));
        }

        // Nothing was hit, so drop the anchor onto the terrain under the aim
        // point. Leaving it at eye height left the star's plunge ending in open
        // air, which read as the spell flashing out halfway down.
        Vec3 end = caster.getEyePosition()
                .add(caster.getViewVector(1.0F).scale(SpellConfig.castRange("constellation")));
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(end.x), Mth.floor(end.z));
        return new Vec3(end.x, surface, end.z);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
                "spell.irons_spellbooks_light_pollution.constellation.info"));
    }
}

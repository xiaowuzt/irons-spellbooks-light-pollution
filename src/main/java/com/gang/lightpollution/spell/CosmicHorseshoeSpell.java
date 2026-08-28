package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.CosmicHorseshoeEntity;
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
 * A gravitational lens: an arc of a galaxy's light bent around the mass in front of it.
 *
 * <p>The other lenses in this set swallow. This one focuses, which is what a lens
 * actually does — the tangential magnification diverges at the Einstein radius, so the
 * ring is where the light piles up. That makes the ring the dangerous place and the
 * interior the safe one, and it is the only spell here whose middle is somewhere to
 * stand rather than somewhere to avoid.</p>
 */
public final class CosmicHorseshoeSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "cosmic_horseshoe");
    public static final int MAX_RANGE = 38;
    public static final int FIXED_COOLDOWN_TICKS = 320 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(320)
            .setAllowCrafting(false)
            .build();

    public CosmicHorseshoeSpell() {
        this.baseManaCost = 1250;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 50;
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

        double duplicateSearchRadius =
                MAX_RANGE + CosmicHorseshoeEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                CosmicHorseshoeEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        Vec3 centre = resolveCentre(serverLevel, caster);
        CosmicHorseshoeEntity effect =
                new CosmicHorseshoeEntity(ModEntities.COSMIC_HORSESHOE.get(), serverLevel);
        effect.configure(caster, centre, gapDirectionFor(caster, centre),
                caster.getRandom().nextInt());
        serverLevel.addFreshEntity(effect);
    }

    /**
     * Which way the opening in the arc faces.
     *
     * <p>Back toward the caster, horizontally. The gap is the whole reason the thing is
     * called a horseshoe, so the person who cast it should be looking at it rather than
     * at the closed side.</p>
     */
    private static Vec3 gapDirectionFor(LivingEntity caster, Vec3 centre) {
        Vec3 toCaster = caster.getEyePosition().subtract(centre);
        Vec3 flat = new Vec3(toCaster.x, 0.0D, toCaster.z);
        return flat.lengthSqr() < 1.0e-4D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize();
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
                "spell.irons_spellbooks_light_pollution.cosmic_horseshoe.info"));
    }
}

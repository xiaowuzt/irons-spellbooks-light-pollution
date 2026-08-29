package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.GargantuaEntity;
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
 * A black hole that swells for eleven seconds and then goes off.
 *
 * <p>The rest of this set's black holes pull and crush. This one is about being
 * <em>watched</em> growing: the pull, the tidal shear and every rendered radius are
 * all functions of one number that climbs slowly and then steeply, so the threat is
 * legible long before it lands.</p>
 */
public final class GargantuaSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gargantua");
    public static final int MAX_RANGE = 40;
    public static final int FIXED_COOLDOWN_TICKS = 540 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(540)
            .setAllowCrafting(true)
            .build();

    public GargantuaSpell() {
        this.baseManaCost = 2200;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = 60;
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

        double duplicateSearchRadius = MAX_RANGE + GargantuaEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                GargantuaEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        Vec3 centre = resolveCentre(serverLevel, caster);
        GargantuaEntity effect =
                new GargantuaEntity(ModEntities.GARGANTUA.get(), serverLevel);
        effect.configure(caster, centre, spinAxisFor(caster),
                caster.getRandom().nextInt());
        serverLevel.addFreshEntity(effect);
    }

    /**
     * The disk's plane, expressed as the axis it spins about.
     *
     * <p>Straight up, so the disk lies flat — parallel to the ground, the way an
     * accretion disk sits around a hole at rest in the world.</p>
     *
     * <p>An earlier version tilted the axis horizontally to force an edge-on view,
     * on the theory that the famous composition needs the disk seen from its own
     * plane. That was solving a problem that had already gone away: once the hole
     * sits ten blocks off the ground instead of thirty-four up, a player standing on
     * the ground is already almost level with it, so a flat disk is seen nearly
     * edge-on anyway. Tilting it on top of that just made the disk stand on end,
     * which is not how anything orbits.</p>
     */
    private static Vec3 spinAxisFor(LivingEntity caster) {
        return new Vec3(0.0D, 1.0D, 0.0D);
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
                "spell.irons_spellbooks_light_pollution.gargantua.info"));
    }
}

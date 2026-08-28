package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.HelixNebulaEntity;
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
 * NGC 7293, the Helix Nebula: an expanding shell of cometary knots around a white dwarf.
 *
 * <p>The hazard is a wavefront, not a radius. Two nested rings of knots leave the centre
 * and sweep outward, so distance buys time rather than safety, and being inside means the
 * shell has already gone past. It is the only spell here whose dangerous region travels
 * outward through the world.</p>
 */
public final class HelixNebulaSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "helix_nebula");
    public static final int MAX_RANGE = 42;
    public static final int FIXED_COOLDOWN_TICKS = 340 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(340)
            .setAllowCrafting(false)
            .build();

    public HelixNebulaSpell() {
        this.baseManaCost = 1350;
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

        double duplicateSearchRadius =
                MAX_RANGE + HelixNebulaEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                HelixNebulaEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        Vec3 centre = resolveCentre(serverLevel, caster);
        HelixNebulaEntity effect =
                new HelixNebulaEntity(ModEntities.HELIX_NEBULA.get(), serverLevel);
        // The rings lean across the caster's view, so the pair reads as an oval eye rather
        // than edge-on as two lines.
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
                "spell.irons_spellbooks_light_pollution.helix_nebula.info"));
    }
}

package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.LeviathanEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A body ninety blocks long swims in through the air and bites once.
 *
 * <p>The rest of this set arrives from above. This one crosses the sky sideways,
 * which is the only reason it needs to exist: a long lateral silhouette moving
 * across the view reads completely differently from anything falling onto a point.
 * The bite lands on whichever creature the caster picked out and follows it there,
 * but the body sweeps everything it passes on the way in, so where you stand
 * relative to its approach matters as much as where the caster aimed.</p>
 */
public final class LeviathanSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "leviathan");
    public static final int MAX_RANGE = 44;
    /** How far off the aimed point a creature can be and still be locked on. */
    private static final double TARGET_SNAP_RADIUS = 7.0D;
    public static final int FIXED_COOLDOWN_TICKS = 440 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(440)
            .setAllowCrafting(false)
            .build();

    public LeviathanSpell() {
        this.baseManaCost = 1750;
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

        double duplicateSearchRadius = MAX_RANGE + LeviathanEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                LeviathanEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        HitResult hit = Utils.raycastForEntity(serverLevel, caster, MAX_RANGE, true);
        Vec3 aimed = resolveCenter(serverLevel, caster, hit);
        // If the caster picked out a creature, the head goes after that creature and
        // the bite follows it wherever it moves. A raycast alone is unforgiving over
        // forty-four blocks, so a near miss falls back to whatever living thing is
        // closest to where they aimed.
        LivingEntity target = hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                ? living
                : nearestTarget(serverLevel, caster, aimed);
        Vec3 center = target != null ? target.getBoundingBox().getCenter() : aimed;
        // It swims in from behind the caster's shoulder and forward past the aimed
        // point, so the body crosses the caster's own view rather than arriving from
        // somewhere they cannot see.
        Vec3 facing = caster.getViewVector(1.0F);
        float bearing = (float) Mth.atan2(facing.z, facing.x);
        LeviathanEntity effect =
                new LeviathanEntity(ModEntities.LEVIATHAN.get(), serverLevel);
        effect.configure(caster, center, bearing, caster.getRandom().nextInt(), target);
        serverLevel.addFreshEntity(effect);
    }

    /** Closest living thing to the aimed point that is not the caster or an ally. */
    private static LivingEntity nearestTarget(ServerLevel level, LivingEntity caster,
                                              Vec3 aimed) {
        LivingEntity closest = null;
        double best = TARGET_SNAP_RADIUS * TARGET_SNAP_RADIUS;
        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                new net.minecraft.world.phys.AABB(aimed, aimed)
                        .inflate(TARGET_SNAP_RADIUS))) {
            if (candidate == caster || !candidate.isAlive() || candidate.isRemoved()
                    || (candidate instanceof Player player && player.isSpectator())
                    || caster.isAlliedTo(candidate) || candidate.isAlliedTo(caster)) {
                continue;
            }
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(aimed);
            if (distance < best) {
                best = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private static Vec3 resolveCenter(ServerLevel level, LivingEntity caster,
                                      HitResult hit) {
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().getBoundingBox().getCenter();
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
            Vec3 surfaceNormal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            return blockHit.getLocation().add(surfaceNormal.scale(0.08D));
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
                "spell.irons_spellbooks_light_pollution.leviathan.info"));
    }
}

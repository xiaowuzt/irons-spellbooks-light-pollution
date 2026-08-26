package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.WorldTreeEntity;
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
 * A tree of light grows out of the ground and hardens into a monument.
 *
 * <p>Everything else in this set arrives from the sky. This one comes up, and it
 * is the only spell here with a tall vertical silhouette. It reads the real
 * terrain: roots follow the surface, so on a slope they climb it and the shape
 * belongs to the ground it grew from.</p>
 *
 * <p>The threat is staged and positional. The roots only strike where their tips
 * currently are, so the spread is what has to be avoided; the trunk pins whatever
 * is at its foot; and the crown's pulse then lands on everything still held.</p>
 */
public final class WorldTreeSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "world_tree");
    public static final int MAX_RANGE = 36;
    public static final int FIXED_COOLDOWN_TICKS = 460 * 20;
    private static final int DUPLICATE_CAST_WINDOW_TICKS = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(460)
            .setAllowCrafting(false)
            .build();

    public WorldTreeSpell() {
        this.baseManaCost = 1750;
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

        double duplicateSearchRadius = MAX_RANGE + WorldTreeEntity.EFFECT_RADIUS + 2.0D;
        boolean duplicateCast = !serverLevel.getEntitiesOfClass(
                WorldTreeEntity.class,
                caster.getBoundingBox().inflate(duplicateSearchRadius),
                effect -> effect.isCastBy(caster)
                        && effect.getTimelineAgeTicks() <= DUPLICATE_CAST_WINDOW_TICKS)
                .isEmpty();
        if (duplicateCast) {
            return;
        }

        Vec3 center = rayTraceGround(serverLevel, caster);
        WorldTreeEntity effect =
                new WorldTreeEntity(ModEntities.WORLD_TREE.get(), serverLevel);
        effect.configure(caster, center, caster.getRandom().nextInt());
        serverLevel.addFreshEntity(effect);
    }

    /**
     * The seed has to be on the ground, not wherever the crosshair happened to
     * land: this tree grows out of the terrain, and a seed placed in mid-air would
     * leave the roots hanging.
     */
    private static Vec3 rayTraceGround(ServerLevel level, LivingEntity caster) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getViewVector(1.0F).scale(MAX_RANGE));
        HitResult hit = Utils.raycastForEntity(level, caster, MAX_RANGE, true);

        double x;
        double z;
        if (hit instanceof EntityHitResult entityHit) {
            x = entityHit.getEntity().getX();
            z = entityHit.getEntity().getZ();
        } else if (hit instanceof BlockHitResult blockHit
                && hit.getType() != HitResult.Type.MISS) {
            x = blockHit.getLocation().x;
            z = blockHit.getLocation().z;
        } else {
            x = end.x;
            z = end.z;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(x), Mth.floor(z));
        return new Vec3(x, surface, z);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable(
                "spell.irons_spellbooks_light_pollution.world_tree.info"));
    }
}

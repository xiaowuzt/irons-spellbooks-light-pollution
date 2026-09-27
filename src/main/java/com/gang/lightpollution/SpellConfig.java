package com.gang.lightpollution;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side gameplay values for the spells shipped by this mod.
 *
 * <p>The values are deliberately kept separate from {@link SpellLightConfig}:
 * this file is synchronized by Forge as a server config and is therefore the
 * authoritative tuning surface in single-player and multiplayer worlds.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpellConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue CELESTIAL_COOLDOWN = intValue(
            "celestialJudgment.cooldownSeconds", 600, 0, 86_400,
            "Celestial Judgment cooldown in seconds.");
    private static final ForgeConfigSpec.IntValue CELESTIAL_MANA = intValue(
            "celestialJudgment.manaCost", 800, 0, 100_000,
            "Celestial Judgment mana cost.");
    private static final ForgeConfigSpec.IntValue CELESTIAL_CAST_TIME = intValue(
            "celestialJudgment.castTimeTicks", 50, 0, 20 * 60,
            "Celestial Judgment cast time in ticks.");
    private static final ForgeConfigSpec.DoubleValue CELESTIAL_DAMAGE = doubleValue(
            "celestialJudgment.damageFraction", 0.84D, 0.0D, 1.0D,
            "Celestial Judgment target maximum-health damage fraction.");
    private static final ForgeConfigSpec.DoubleValue CELESTIAL_HEAL = doubleValue(
            "celestialJudgment.healFraction", 0.64D, 0.0D, 1.0D,
            "Celestial Judgment caster maximum-health heal fraction.");
    private static final ForgeConfigSpec.IntValue CELESTIAL_RANGE = intValue(
            "celestialJudgment.targetRange", 48, 1, 256,
            "Celestial Judgment target selection range in blocks.");
    private static final ForgeConfigSpec.IntValue CELESTIAL_IMPACT_TICK = intValue(
            "celestialJudgment.impactTick", 52, 0, 20 * 60,
            "Celestial Judgment impact delay in ticks.");
    private static final ForgeConfigSpec.IntValue CELESTIAL_LIFETIME = intValue(
            "celestialJudgment.lifetimeTicks", 100, 1, 20 * 60,
            "Celestial Judgment visual lifetime in ticks.");

    private static final ForgeConfigSpec.IntValue STARGRAVE_COOLDOWN = intValue(
            "stargraveSingularity.cooldownSeconds", 600, 0, 86_400,
            "Stargrave Singularity cooldown in seconds.");
    private static final ForgeConfigSpec.IntValue STARGRAVE_MANA = intValue(
            "stargraveSingularity.manaCost", 1200, 0, 100_000,
            "Stargrave Singularity mana cost.");
    private static final ForgeConfigSpec.IntValue STARGRAVE_CAST_TIME = intValue(
            "stargraveSingularity.castTimeTicks", 60, 0, 20 * 60,
            "Stargrave Singularity cast time in ticks.");
    private static final ForgeConfigSpec.IntValue STARGRAVE_RANGE = intValue(
            "stargraveSingularity.castRange", 48, 1, 256,
            "Stargrave Singularity cast range in blocks.");
    private static final ForgeConfigSpec.DoubleValue STARGRAVE_RADIUS = doubleValue(
            "stargraveSingularity.effectRadius", 20.0D, 0.1D, 256.0D,
            "Stargrave Singularity gameplay radius in blocks.");
    private static final ForgeConfigSpec.DoubleValue STARGRAVE_DAMAGE = doubleValue(
            "stargraveSingularity.damageFraction", 0.93D, 0.0D, 1.0D,
            "Stargrave Singularity collapse maximum-health damage fraction.");
    private static final ForgeConfigSpec.IntValue STARGRAVE_LIFETIME = intValue(
            "stargraveSingularity.lifetimeTicks", 200, 1, 20 * 60 * 10,
            "Stargrave Singularity lifetime in ticks.");
    private static final ForgeConfigSpec.IntValue STARGRAVE_DAMAGE_TICK = intValue(
            "stargraveSingularity.damageTick", 160, 1, 20 * 60 * 10,
            "Stargrave Singularity collapse tick.");

    private static final ForgeConfigSpec.IntValue ECLIPSE_COOLDOWN = intValue(
            "eclipseSeverance.cooldownSeconds", 250, 0, 86_400,
            "Eclipse Severance cooldown in seconds.");
    private static final ForgeConfigSpec.IntValue ECLIPSE_MANA = intValue(
            "eclipseSeverance.manaCost", 1000, 0, 100_000,
            "Eclipse Severance mana cost.");
    private static final ForgeConfigSpec.IntValue ECLIPSE_CAST_TIME = intValue(
            "eclipseSeverance.castTimeTicks", 20, 0, 20 * 60,
            "Eclipse Severance cast time in ticks.");
    private static final ForgeConfigSpec.DoubleValue ECLIPSE_DAMAGE = doubleValue(
            "eclipseSeverance.damage", 500.0D, 0.0D, 1_000_000.0D,
            "Eclipse Severance base damage.");
    private static final ForgeConfigSpec.DoubleValue ECLIPSE_RADIUS = doubleValue(
            "eclipseSeverance.attackRadius", 12.0D, 0.1D, 256.0D,
            "Eclipse Severance horizontal attack radius in blocks.");
    private static final ForgeConfigSpec.DoubleValue ECLIPSE_ANGLE = doubleValue(
            "eclipseSeverance.attackAngleDegrees", 150.0D, 1.0D, 360.0D,
            "Eclipse Severance attack cone angle in degrees.");
    private static final ForgeConfigSpec.DoubleValue ECLIPSE_VERTICAL = doubleValue(
            "eclipseSeverance.verticalTolerance", 5.0D, 0.0D, 256.0D,
            "Eclipse Severance vertical targeting tolerance in blocks.");
    private static final ForgeConfigSpec.IntValue ECLIPSE_MAX_TARGETS = intValue(
            "eclipseSeverance.maxTargets", 32, 1, 10_000,
            "Maximum number of targets hit by one Eclipse Severance cast.");
    private static final ForgeConfigSpec.IntValue ECLIPSE_LIFETIME = intValue(
            "eclipseSeverance.lifetimeTicks", 30, 1, 20 * 60,
            "Eclipse Severance visual lifetime in ticks.");
    private static final ForgeConfigSpec.IntValue ECLIPSE_DAMAGE_TICK = intValue(
            "eclipseSeverance.damageTick", 6, 0, 20 * 60,
            "Eclipse Severance impact tick.");

    private static final ForgeConfigSpec.IntValue NOVA_COOLDOWN = intValue(
            "funeralNova.cooldownSeconds", 360, 0, 86_400,
            "Funeral Nova cooldown in seconds.");
    private static final ForgeConfigSpec.IntValue NOVA_MANA = intValue(
            "funeralNova.manaCost", 1600, 0, 100_000,
            "Funeral Nova mana cost.");
    private static final ForgeConfigSpec.IntValue NOVA_CAST_TIME = intValue(
            "funeralNova.castTimeTicks", 40, 0, 20 * 60,
            "Funeral Nova cast time in ticks.");
    private static final ForgeConfigSpec.IntValue NOVA_RANGE = intValue(
            "funeralNova.castRange", 40, 1, 256,
            "Funeral Nova cast range in blocks.");
    private static final ForgeConfigSpec.DoubleValue NOVA_RADIUS = doubleValue(
            "funeralNova.effectRadius", 12.0D, 0.1D, 256.0D,
            "Funeral Nova gameplay radius in blocks.");
    private static final ForgeConfigSpec.DoubleValue NOVA_ACCRETION = doubleValue(
            "funeralNova.accretionDamageFraction", 0.13D, 0.0D, 1.0D,
            "Funeral Nova accretion maximum-health damage fraction.");
    private static final ForgeConfigSpec.DoubleValue NOVA_COLLAPSE = doubleValue(
            "funeralNova.collapseDamageFraction", 0.19D, 0.0D, 1.0D,
            "Funeral Nova collapse maximum-health damage fraction.");
    private static final ForgeConfigSpec.DoubleValue NOVA_HYPERNOVA = doubleValue(
            "funeralNova.hypernovaDamageFraction", 0.27D, 0.0D, 1.0D,
            "Funeral Nova hypernova maximum-health damage fraction.");
    private static final ForgeConfigSpec.IntValue NOVA_LIFETIME = intValue(
            "funeralNova.lifetimeTicks", 344, 1, 20 * 60 * 10,
            "Funeral Nova timeline lifetime in ticks.");

    private static final ForgeConfigSpec.IntValue ACCRETION_COOLDOWN = intValue(
            "chromaticAccretion.cooldownSeconds", 420, 0, 86_400,
            "Chromatic Accretion cooldown in seconds.");
    private static final ForgeConfigSpec.IntValue ACCRETION_MANA = intValue(
            "chromaticAccretion.manaCost", 1500, 0, 100_000,
            "Chromatic Accretion mana cost.");
    private static final ForgeConfigSpec.IntValue ACCRETION_CAST_TIME = intValue(
            "chromaticAccretion.castTimeTicks", 50, 0, 20 * 60,
            "Chromatic Accretion cast time in ticks.");
    private static final ForgeConfigSpec.IntValue ACCRETION_RANGE = intValue(
            "chromaticAccretion.castRange", 45, 1, 256,
            "Chromatic Accretion cast range in blocks.");
    private static final ForgeConfigSpec.DoubleValue ACCRETION_RADIUS = doubleValue(
            "chromaticAccretion.effectRadius", 12.0D, 0.1D, 256.0D,
            "Chromatic Accretion gameplay radius in blocks.");
    private static final ForgeConfigSpec.DoubleValue ACCRETION_PULSE = doubleValue(
            "chromaticAccretion.pulseDamageFraction", 0.044D, 0.0D, 1.0D,
            "Chromatic Accretion per-pulse maximum-health damage fraction.");
    private static final ForgeConfigSpec.DoubleValue ACCRETION_COLLAPSE = doubleValue(
            "chromaticAccretion.collapseDamageFraction", 0.31D, 0.0D, 1.0D,
            "Chromatic Accretion collapse maximum-health damage fraction.");
    private static final ForgeConfigSpec.DoubleValue ACCRETION_PROJECTILE = doubleValue(
            "chromaticAccretion.projectileDamageFraction", 0.01D, 0.0D, 1.0D,
            "Chromatic Accretion added maximum-health damage per absorbed projectile.");
    private static final ForgeConfigSpec.IntValue ACCRETION_MAX_PROJECTILES = intValue(
            "chromaticAccretion.maxProjectileCharge", 6, 0, 10_000,
            "Chromatic Accretion maximum absorbed projectile charge.");
    private static final ForgeConfigSpec.IntValue ACCRETION_LIFETIME = intValue(
            "chromaticAccretion.lifetimeTicks", 170, 1, 20 * 60 * 10,
            "Chromatic Accretion timeline lifetime in ticks.");
    private static final ForgeConfigSpec.IntValue ACCRETION_FORMATION = intValue(
            "chromaticAccretion.formationEndTick", 24, 1, 20 * 60 * 10,
            "Chromatic Accretion formation phase end tick.");
    private static final ForgeConfigSpec.IntValue ACCRETION_COLLAPSE_TICK = intValue(
            "chromaticAccretion.collapseTick", 136, 1, 20 * 60 * 10,
            "Chromatic Accretion collapse tick.");

    private static final ForgeConfigSpec.IntValue WORLD_TREE_COOLDOWN = intValue(
            "worldTree.cooldownSeconds", 460, 0, 86_400,
            "World Tree cooldown in seconds.");
    private static final ForgeConfigSpec.IntValue WORLD_TREE_MANA = intValue(
            "worldTree.manaCost", 1750, 0, 100_000,
            "World Tree mana cost.");
    private static final ForgeConfigSpec.IntValue WORLD_TREE_CAST_TIME = intValue(
            "worldTree.castTimeTicks", 55, 0, 20 * 60,
            "World Tree cast time in ticks.");
    private static final ForgeConfigSpec.IntValue WORLD_TREE_RANGE = intValue(
            "worldTree.castRange", 36, 1, 256,
            "World Tree target range in blocks.");
    private static final ForgeConfigSpec.DoubleValue WORLD_TREE_RADIUS = doubleValue(
            "worldTree.effectRadius", 20.0D, 0.1D, 256.0D,
            "World Tree sanctuary radius in blocks.");
    private static final ForgeConfigSpec.IntValue WORLD_TREE_LIFETIME = intValue(
            "worldTree.lifetimeTicks", 300, 1, 20 * 60 * 10,
            "World Tree lifetime in ticks.");
    private static final ForgeConfigSpec.DoubleValue WORLD_TREE_HEAL = doubleValue(
            "worldTree.healingFraction", 0.08D, 0.0D, 1.0D,
            "Fraction of maximum health restored to each allied target per pulse.");
    private static final ForgeConfigSpec.IntValue WORLD_TREE_HEAL_INTERVAL = intValue(
            "worldTree.healingIntervalTicks", 20, 1, 20 * 60,
            "Ticks between World Tree healing pulses.");
    private static final ForgeConfigSpec.DoubleValue WORLD_TREE_ABSORPTION = doubleValue(
            "worldTree.absorptionHearts", 4.0D, 0.0D, 1_000.0D,
            "Absorption hearts granted to allies in the World Tree sanctuary.");
    private static final ForgeConfigSpec.BooleanValue WORLD_TREE_PROTECT_MAGIC = BUILDER
            .comment("Prevent magic damage to allies inside the World Tree sanctuary.")
            .define("worldTree.protectMagic", true);
    private static final ForgeConfigSpec.BooleanValue WORLD_TREE_PROTECT_PROJECTILE = BUILDER
            .comment("Prevent projectile damage to allies inside the World Tree sanctuary.")
            .define("worldTree.protectProjectile", true);
    private static final ForgeConfigSpec.BooleanValue WORLD_TREE_PROTECT_FIRE = BUILDER
            .comment("Prevent fire damage to allies inside the World Tree sanctuary.")
            .define("worldTree.protectFire", true);

    private static final Map<String, GenericValues> GENERIC_SPELLS = new LinkedHashMap<>();

    static {
        genericSpell("starless", 300, 1400, 40, 40);
        genericSpell("constellation", 300, 1400, 40, 40);
        genericSpell("silhouette", 240, 1000, 40, 40);
        genericSpell("starfall", 360, 1600, 50, 48);
        genericSpell("skyCollapse", 420, 1700, 50, 48);
        genericSpell("stellarConvergence", 400, 1650, 50, 40);
        genericSpell("secondSun", 600, 2000, 60, 40);
        genericSpell("singularity", 480, 1800, 45, 36);
        genericSpell("leviathan", 440, 1750, 50, 44);
        genericSpell("gargantua", 540, 2200, 60, 40);
        genericSpell("cosmicHorseshoe", 320, 1250, 50, 38);
        genericSpell("microquasar", 380, 1550, 55, 42);
        genericSpell("helixNebula", 340, 1350, 55, 42);
        genericSpell("magnetar", 500, 1900, 55, 42);
        genericSpell("tidalDisruption", 520, 1950, 55, 42);
        genericSpell("quasarJet", 560, 2100, 55, 42);
        genericSpell("pinwheel", 280, 1100, 55, 42);
        genericSpell("crabNebula", 260, 1050, 55, 42);
    }

    private static void genericSpell(String id, int cooldown, int mana, int castTime, int range) {
        // Forge treats each push as one path component. Push the shared section
        // and spell id separately so successive entries do not become
        // spells.spells.spells.* after each pop.
        BUILDER.push("spells");
        BUILDER.push(id);
        ForgeConfigSpec.IntValue cooldownValue = BUILDER.comment("Cooldown in seconds.")
                .defineInRange("cooldownSeconds", cooldown, 0, 86_400);
        ForgeConfigSpec.IntValue manaValue = BUILDER.comment("Mana cost.")
                .defineInRange("manaCost", mana, 0, 100_000);
        ForgeConfigSpec.IntValue castTimeValue = BUILDER.comment("Cast time in ticks.")
                .defineInRange("castTimeTicks", castTime, 0, 20 * 60);
        ForgeConfigSpec.IntValue rangeValue = BUILDER.comment("Targeting range in blocks.")
                .defineInRange("castRange", range, 1, 256);
        BUILDER.pop();
        BUILDER.pop();
        GENERIC_SPELLS.put(id, new GenericValues(cooldownValue, manaValue, castTimeValue, rangeValue));
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static volatile int celestialCooldownSeconds = 600;
    public static volatile int celestialManaCost = 800;
    public static volatile int celestialCastTimeTicks = 50;
    public static volatile double celestialDamageFraction = 0.84D;
    public static volatile double celestialHealFraction = 0.64D;
    public static volatile int celestialTargetRange = 48;
    public static volatile int celestialImpactTick = 52;
    public static volatile int celestialLifetimeTicks = 100;

    public static volatile int stargraveCooldownSeconds = 600;
    public static volatile int stargraveManaCost = 1200;
    public static volatile int stargraveCastTimeTicks = 60;
    public static volatile int stargraveCastRange = 48;
    public static volatile double stargraveEffectRadius = 20.0D;
    public static volatile double stargraveDamageFraction = 0.93D;
    public static volatile int stargraveLifetimeTicks = 200;
    public static volatile int stargraveDamageTick = 160;

    public static volatile int eclipseCooldownSeconds = 250;
    public static volatile int eclipseManaCost = 1000;
    public static volatile int eclipseCastTimeTicks = 20;
    public static volatile double eclipseDamage = 500.0D;
    public static volatile double eclipseAttackRadius = 12.0D;
    public static volatile double eclipseAttackAngleDegrees = 150.0D;
    public static volatile double eclipseVerticalTolerance = 5.0D;
    public static volatile int eclipseMaxTargets = 32;
    public static volatile int eclipseLifetimeTicks = 30;
    public static volatile int eclipseDamageTick = 6;

    public static volatile int funeralNovaCooldownSeconds = 360;
    public static volatile int funeralNovaManaCost = 1600;
    public static volatile int funeralNovaCastTimeTicks = 40;
    public static volatile int funeralNovaCastRange = 40;
    public static volatile double funeralNovaEffectRadius = 12.0D;
    public static volatile double funeralNovaAccretionDamageFraction = 0.13D;
    public static volatile double funeralNovaCollapseDamageFraction = 0.19D;
    public static volatile double funeralNovaHypernovaDamageFraction = 0.27D;
    public static volatile int funeralNovaLifetimeTicks = 344;

    public static volatile int chromaticAccretionCooldownSeconds = 420;
    public static volatile int chromaticAccretionManaCost = 1500;
    public static volatile int chromaticAccretionCastTimeTicks = 50;
    public static volatile int chromaticAccretionCastRange = 45;
    public static volatile double chromaticAccretionEffectRadius = 12.0D;
    public static volatile double chromaticAccretionPulseDamageFraction = 0.044D;
    public static volatile double chromaticAccretionCollapseDamageFraction = 0.31D;
    public static volatile double chromaticAccretionProjectileDamageFraction = 0.01D;
    public static volatile int chromaticAccretionMaxProjectileCharge = 6;
    public static volatile int chromaticAccretionLifetimeTicks = 170;
    public static volatile int chromaticAccretionFormationEndTick = 24;
    public static volatile int chromaticAccretionCollapseTick = 136;

    public static volatile int worldTreeCooldownSeconds = 460;
    public static volatile int worldTreeManaCost = 1750;
    public static volatile int worldTreeCastTimeTicks = 55;
    public static volatile int worldTreeCastRange = 36;
    public static volatile double worldTreeEffectRadius = 20.0D;
    public static volatile int worldTreeLifetimeTicks = 300;
    public static volatile double worldTreeHealingFraction = 0.08D;
    public static volatile int worldTreeHealingIntervalTicks = 20;
    public static volatile double worldTreeAbsorptionHearts = 4.0D;
    public static volatile boolean worldTreeProtectMagic = true;
    public static volatile boolean worldTreeProtectProjectile = true;
    public static volatile boolean worldTreeProtectFire = true;

    public static int cooldownSeconds(String spellId) {
        return switch (spellId) {
            case "celestialJudgment" -> celestialCooldownSeconds;
            case "stargraveSingularity" -> stargraveCooldownSeconds;
            case "eclipseSeverance" -> eclipseCooldownSeconds;
            case "funeralNova" -> funeralNovaCooldownSeconds;
            case "chromaticAccretion" -> chromaticAccretionCooldownSeconds;
            case "worldTree" -> worldTreeCooldownSeconds;
            default -> generic(spellId).cooldown.get();
        };
    }

    public static int manaCost(String spellId) {
        return switch (spellId) {
            case "celestialJudgment" -> celestialManaCost;
            case "stargraveSingularity" -> stargraveManaCost;
            case "eclipseSeverance" -> eclipseManaCost;
            case "funeralNova" -> funeralNovaManaCost;
            case "chromaticAccretion" -> chromaticAccretionManaCost;
            case "worldTree" -> worldTreeManaCost;
            default -> generic(spellId).mana.get();
        };
    }

    public static int castTimeTicks(String spellId) {
        return switch (spellId) {
            case "celestialJudgment" -> celestialCastTimeTicks;
            case "stargraveSingularity" -> stargraveCastTimeTicks;
            case "eclipseSeverance" -> eclipseCastTimeTicks;
            case "funeralNova" -> funeralNovaCastTimeTicks;
            case "chromaticAccretion" -> chromaticAccretionCastTimeTicks;
            case "worldTree" -> worldTreeCastTimeTicks;
            default -> generic(spellId).castTime.get();
        };
    }

    public static int castRange(String spellId) {
        return switch (spellId) {
            case "celestialJudgment" -> celestialTargetRange;
            case "stargraveSingularity" -> stargraveCastRange;
            case "eclipseSeverance" -> 40;
            case "funeralNova" -> funeralNovaCastRange;
            case "chromaticAccretion" -> chromaticAccretionCastRange;
            case "worldTree" -> worldTreeCastRange;
            default -> generic(spellId).range.get();
        };
    }

    private static GenericValues generic(String spellId) {
        GenericValues values = GENERIC_SPELLS.get(spellId);
        if (values == null) {
            throw new IllegalArgumentException("Unknown spell config id: " + spellId);
        }
        return values;
    }

    private SpellConfig() {
    }

    private static ForgeConfigSpec.IntValue intValue(
            String path, int defaultValue, int min, int max, String comment) {
        return BUILDER.comment(comment).defineInRange(path, defaultValue, min, max);
    }

    private static ForgeConfigSpec.DoubleValue doubleValue(
            String path, double defaultValue, double min, double max, String comment) {
        return BUILDER.comment(comment).defineInRange(path, defaultValue, min, max);
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        celestialCooldownSeconds = CELESTIAL_COOLDOWN.get();
        celestialManaCost = CELESTIAL_MANA.get();
        celestialCastTimeTicks = CELESTIAL_CAST_TIME.get();
        celestialDamageFraction = CELESTIAL_DAMAGE.get();
        celestialHealFraction = CELESTIAL_HEAL.get();
        celestialTargetRange = CELESTIAL_RANGE.get();
        celestialImpactTick = CELESTIAL_IMPACT_TICK.get();
        celestialLifetimeTicks = CELESTIAL_LIFETIME.get();

        stargraveCooldownSeconds = STARGRAVE_COOLDOWN.get();
        stargraveManaCost = STARGRAVE_MANA.get();
        stargraveCastTimeTicks = STARGRAVE_CAST_TIME.get();
        stargraveCastRange = STARGRAVE_RANGE.get();
        stargraveEffectRadius = STARGRAVE_RADIUS.get();
        stargraveDamageFraction = STARGRAVE_DAMAGE.get();
        stargraveLifetimeTicks = STARGRAVE_LIFETIME.get();
        stargraveDamageTick = STARGRAVE_DAMAGE_TICK.get();

        eclipseCooldownSeconds = ECLIPSE_COOLDOWN.get();
        eclipseManaCost = ECLIPSE_MANA.get();
        eclipseCastTimeTicks = ECLIPSE_CAST_TIME.get();
        eclipseDamage = ECLIPSE_DAMAGE.get();
        eclipseAttackRadius = ECLIPSE_RADIUS.get();
        eclipseAttackAngleDegrees = ECLIPSE_ANGLE.get();
        eclipseVerticalTolerance = ECLIPSE_VERTICAL.get();
        eclipseMaxTargets = ECLIPSE_MAX_TARGETS.get();
        eclipseLifetimeTicks = ECLIPSE_LIFETIME.get();
        eclipseDamageTick = ECLIPSE_DAMAGE_TICK.get();

        funeralNovaCooldownSeconds = NOVA_COOLDOWN.get();
        funeralNovaManaCost = NOVA_MANA.get();
        funeralNovaCastTimeTicks = NOVA_CAST_TIME.get();
        funeralNovaCastRange = NOVA_RANGE.get();
        funeralNovaEffectRadius = NOVA_RADIUS.get();
        funeralNovaAccretionDamageFraction = NOVA_ACCRETION.get();
        funeralNovaCollapseDamageFraction = NOVA_COLLAPSE.get();
        funeralNovaHypernovaDamageFraction = NOVA_HYPERNOVA.get();
        funeralNovaLifetimeTicks = NOVA_LIFETIME.get();

        chromaticAccretionCooldownSeconds = ACCRETION_COOLDOWN.get();
        chromaticAccretionManaCost = ACCRETION_MANA.get();
        chromaticAccretionCastTimeTicks = ACCRETION_CAST_TIME.get();
        chromaticAccretionCastRange = ACCRETION_RANGE.get();
        chromaticAccretionEffectRadius = ACCRETION_RADIUS.get();
        chromaticAccretionPulseDamageFraction = ACCRETION_PULSE.get();
        chromaticAccretionCollapseDamageFraction = ACCRETION_COLLAPSE.get();
        chromaticAccretionProjectileDamageFraction = ACCRETION_PROJECTILE.get();
        chromaticAccretionMaxProjectileCharge = ACCRETION_MAX_PROJECTILES.get();
        chromaticAccretionLifetimeTicks = ACCRETION_LIFETIME.get();
        chromaticAccretionFormationEndTick = ACCRETION_FORMATION.get();
        chromaticAccretionCollapseTick = ACCRETION_COLLAPSE_TICK.get();

        worldTreeCooldownSeconds = WORLD_TREE_COOLDOWN.get();
        worldTreeManaCost = WORLD_TREE_MANA.get();
        worldTreeCastTimeTicks = WORLD_TREE_CAST_TIME.get();
        worldTreeCastRange = WORLD_TREE_RANGE.get();
        worldTreeEffectRadius = WORLD_TREE_RADIUS.get();
        worldTreeLifetimeTicks = WORLD_TREE_LIFETIME.get();
        worldTreeHealingFraction = WORLD_TREE_HEAL.get();
        worldTreeHealingIntervalTicks = WORLD_TREE_HEAL_INTERVAL.get();
        worldTreeAbsorptionHearts = WORLD_TREE_ABSORPTION.get();
        worldTreeProtectMagic = WORLD_TREE_PROTECT_MAGIC.get();
        worldTreeProtectProjectile = WORLD_TREE_PROTECT_PROJECTILE.get();
        worldTreeProtectFire = WORLD_TREE_PROTECT_FIRE.get();
    }
    private record GenericValues(
            ForgeConfigSpec.IntValue cooldown,
            ForgeConfigSpec.IntValue mana,
            ForgeConfigSpec.IntValue castTime,
            ForgeConfigSpec.IntValue range) {
    }
}

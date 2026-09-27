package com.gang.lightpollution;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
            "stargraveSingularity.lifetimeTicks", 200, 2, 20 * 60 * 10,
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
    private static final ForgeConfigSpec.IntValue ECLIPSE_RANGE = intValue(
            "eclipseSeverance.castRange", 40, 1, 256,
            "Eclipse Severance target range in blocks.");
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
            "funeralNova.lifetimeTicks", 344, 8, 20 * 60 * 10,
            "Funeral Nova timeline lifetime in ticks.");
    private static final ForgeConfigSpec.IntValue NOVA_BLACK_HOLE_TICK = intValue(
            "funeralNova.blackHoleStartTick", 48, 0, 20 * 60 * 10,
            "Funeral Nova black-hole phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_ACCRETION_TICK = intValue(
            "funeralNova.accretionStartTick", 68, 0, 20 * 60 * 10,
            "Funeral Nova accretion phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_COLLAPSE_TICK = intValue(
            "funeralNova.collapseStartTick", 100, 0, 20 * 60 * 10,
            "Funeral Nova collapse phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_VOID_TICK = intValue(
            "funeralNova.voidStartTick", 120, 0, 20 * 60 * 10,
            "Funeral Nova void phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_FLASH_TICK = intValue(
            "funeralNova.flashStartTick", 150, 0, 20 * 60 * 10,
            "Funeral Nova flash phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_HYPERNOVA_TICK = intValue(
            "funeralNova.hypernovaStartTick", 164, 0, 20 * 60 * 10,
            "Funeral Nova hypernova phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_AFTERGLOW_TICK = intValue(
            "funeralNova.afterglowStartTick", 250, 0, 20 * 60 * 10,
            "Funeral Nova afterglow phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_FADE_TICK = intValue(
            "funeralNova.fadeOutStartTick", 300, 0, 20 * 60 * 10,
            "Funeral Nova fade-out phase start tick.");
    private static final ForgeConfigSpec.IntValue NOVA_ACCRETION_DAMAGE_TICK = intValue(
            "funeralNova.accretionDamageTick", 68, 0, 20 * 60 * 10,
            "Funeral Nova accretion damage tick.");
    private static final ForgeConfigSpec.IntValue NOVA_COLLAPSE_DAMAGE_TICK = intValue(
            "funeralNova.collapseDamageTick", 110, 0, 20 * 60 * 10,
            "Funeral Nova collapse damage tick.");

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
            "chromaticAccretion.lifetimeTicks", 170, 8, 20 * 60 * 10,
            "Chromatic Accretion timeline lifetime in ticks.");
    private static final ForgeConfigSpec.IntValue ACCRETION_FORMATION = intValue(
            "chromaticAccretion.formationEndTick", 24, 1, 20 * 60 * 10,
            "Chromatic Accretion formation phase end tick.");
    private static final ForgeConfigSpec.IntValue ACCRETION_COLLAPSE_TICK = intValue(
            "chromaticAccretion.collapseTick", 136, 1, 20 * 60 * 10,
            "Chromatic Accretion collapse tick.");
    private static final ForgeConfigSpec.IntValue ACCRETION_PULSE_ONE = intValue(
            "chromaticAccretion.pulseOneTick", 36, 1, 20 * 60 * 10,
            "Chromatic Accretion first pulse tick.");
    private static final ForgeConfigSpec.IntValue ACCRETION_PULSE_TWO = intValue(
            "chromaticAccretion.pulseTwoTick", 64, 1, 20 * 60 * 10,
            "Chromatic Accretion second pulse tick.");
    private static final ForgeConfigSpec.IntValue ACCRETION_PULSE_THREE = intValue(
            "chromaticAccretion.pulseThreeTick", 92, 1, 20 * 60 * 10,
            "Chromatic Accretion third pulse tick.");
    private static final ForgeConfigSpec.IntValue ACCRETION_PULSE_FOUR = intValue(
            "chromaticAccretion.pulseFourTick", 120, 1, 20 * 60 * 10,
            "Chromatic Accretion fourth pulse tick.");
    private static final ForgeConfigSpec.DoubleValue ACCRETION_STORED_SHARE = doubleValue(
            "chromaticAccretion.storedDamageShare", 0.30D, 0.0D, 1.0D,
            "Fraction of caster damage stored for Chromatic Accretion's collapse.");
    private static final ForgeConfigSpec.DoubleValue ACCRETION_STORED_CAP_FRACTION = doubleValue(
            "chromaticAccretion.storedDamageCapFraction", 0.20D, 0.0D, 1.0D,
            "Maximum stored Chromatic Accretion damage as a fraction of target maximum health.");

    private static final ForgeConfigSpec.DoubleValue STARLESS_SWALLOWED_DAMAGE_STEP = doubleValue(
            "spells.starless.swallowedDamageStep", 0.05D, 0.0D, 1.0D,
            "Additional Starless release damage fraction per swallowed spell light.");
    private static final ForgeConfigSpec.IntValue STARLESS_SWALLOWED_DAMAGE_CAP = intValue(
            "spells.starless.swallowedDamageCap", 4, 0, 10_000,
            "Maximum swallowed-light count contributing bonus Starless release damage.");

    private static final ForgeConfigSpec.DoubleValue GARGANTUA_BLAST_DAMAGE = doubleValue(
            "spells.gargantua.blastDamageFraction", 0.72D, 0.0D, 1.0D,
            "Gargantua detonation damage as a fraction of target maximum health.");
    private static final ForgeConfigSpec.DoubleValue CRAB_WIND_DAMAGE = doubleValue(
            "spells.crabNebula.windDamageFraction", 0.026D, 0.0D, 1.0D,
            "Crab Nebula pulsar-wind damage as a fraction of target maximum health.");

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

    private static final ForgeConfigSpec.IntValue SERVER_MAX_ACTIVE_ENTITIES = BUILDER
            .comment("Maximum number of active spell entities in one server level.",
                    "New spell casts are refused after this limit is reached.")
            .defineInRange("server.maxActiveSpellEntities", 64, 1, 4096);
    private static final ForgeConfigSpec.IntValue SERVER_MAX_ENTITIES_PER_SPELL = BUILDER
            .comment("Maximum active entities of one spell type in one server level.")
            .defineInRange("server.maxEntitiesPerSpell", 16, 1, 1024);
    private static final ForgeConfigSpec.IntValue SERVER_TARGET_SCAN_LIMIT = BUILDER
            .comment("Safety cap for living targets considered by one spell pulse.")
            .defineInRange("server.targetScanLimit", 128, 1, 10_000);

    private static final Map<String, GenericValues> GENERIC_SPELLS = new LinkedHashMap<>();

    /** Defaults for the shared gameplay fields used by the eighteen generic spells. */
    private static GenericDefaults genericDefaults(String id) {
        return switch (id) {
            case "starless" -> new GenericDefaults(260, 12.0D, 0.16D, 0.26D, 20, 110, 150, 160, 4);
            case "constellation" -> new GenericDefaults(240, 15.0D, 0.010D, 0.052D, 10, 50, 120, 180, 32);
            case "silhouette" -> new GenericDefaults(240, 32.0D, 0.030D, 0.0D, 20, 12, 160, 200, 32);
            case "starfall" -> new GenericDefaults(310, 14.0D, 0.037D, 0.28D, 10, 100, 220, 280, 32);
            case "skyCollapse" -> new GenericDefaults(220, 20.0D, 0.065D, 0.37D, 10, 60, 150, 190, 32);
            case "stellarConvergence" -> new GenericDefaults(260, 18.0D, 0.033D, 0.44D, 10, 80, 180, 220, 32);
            case "secondSun" -> new GenericDefaults(320, 26.0D, 0.018D, 0.61D, 15, 80, 240, 280, 32);
            case "singularity" -> new GenericDefaults(220, 22.0D, 0.006D, 0.66D, 10, 80, 160, 190, 32);
            case "leviathan" -> new GenericDefaults(190, 28.0D, 0.056D, 0.58D, 6, 70, 140, 170, 32);
            case "gargantua" -> new GenericDefaults(400, 28.0D, 0.013D, 0.72D, 8, 40, 260, 290, 32);
            case "cosmicHorseshoe" -> new GenericDefaults(360, 17.0D, 0.028D, 0.33D, 15, 50, 300, 330, 32);
            case "microquasar" -> new GenericDefaults(320, 22.0D, 0.048D, 0.50D, 5, 70, 220, 280, 32);
            case "helixNebula" -> new GenericDefaults(340, 22.0D, 0.072D, 0.42D, 6, 100, 240, 300, 32);
            case "magnetar" -> new GenericDefaults(300, 26.0D, 0.024D, 0.78D, 8, 80, 220, 260, 32);
            case "tidalDisruption" -> new GenericDefaults(360, 28.0D, 0.058D, 0.47D, 5, 80, 250, 310, 32);
            case "quasarJet" -> new GenericDefaults(340, 30.0D, 0.068D, 0.53D, 6, 80, 240, 300, 32);
            case "pinwheel" -> new GenericDefaults(300, 24.0D, 0.021D, 0.36D, 4, 70, 220, 260, 32);
            case "crabNebula" -> new GenericDefaults(320, 24.0D, 0.016D, 0.30D, 7, 100, 220, 280, 32);
            default -> throw new IllegalArgumentException("Unknown spell config id: " + id);
        };
    }

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
        GenericDefaults defaults = genericDefaults(id);
        ForgeConfigSpec.IntValue lifetimeValue = BUILDER.comment("Gameplay lifetime in ticks.")
                .defineInRange("lifetimeTicks", defaults.lifetimeTicks(),
                        Math.max(4, defaults.phaseThreeTick() + 1), 20 * 60 * 10);
        ForgeConfigSpec.DoubleValue radiusValue = BUILDER.comment("Gameplay effect radius in blocks.")
                .defineInRange("effectRadius", defaults.effectRadius(), 0.1D, 256.0D);
        ForgeConfigSpec.DoubleValue damageValue = BUILDER.comment("Primary damage fraction per pulse or contact.")
                .defineInRange("damageFraction", defaults.damageFraction(), 0.0D, 1.0D);
        ForgeConfigSpec.DoubleValue secondaryDamageValue = BUILDER.comment("Secondary or final damage fraction.")
                .defineInRange("secondaryDamageFraction", defaults.secondaryDamageFraction(), 0.0D, 1.0D);
        ForgeConfigSpec.IntValue intervalValue = BUILDER.comment("Ticks between repeated damage pulses.")
                .defineInRange("damageIntervalTicks", defaults.damageIntervalTicks(), 1, 20 * 60);
        ForgeConfigSpec.IntValue phaseOneValue = BUILDER.comment("First configurable phase boundary in ticks.")
                .defineInRange("phaseOneTick", defaults.phaseOneTick(), 0, 20 * 60 * 10);
        ForgeConfigSpec.IntValue phaseTwoValue = BUILDER.comment("Second configurable phase boundary in ticks.")
                .defineInRange("phaseTwoTick", defaults.phaseTwoTick(), 0, 20 * 60 * 10);
        ForgeConfigSpec.IntValue phaseThreeValue = BUILDER.comment("Third configurable phase boundary in ticks.")
                .defineInRange("phaseThreeTick", defaults.phaseThreeTick(), 0, 20 * 60 * 10);
        ForgeConfigSpec.IntValue maxTargetsValue = BUILDER.comment("Maximum living targets affected by one pulse.")
                .defineInRange("maxTargets", defaults.maxTargets(), 1, 10_000);
        BUILDER.pop();
        BUILDER.pop();
        GENERIC_SPELLS.put(id, new GenericValues(cooldownValue, manaValue, castTimeValue, rangeValue,
                lifetimeValue, radiusValue, damageValue, secondaryDamageValue, intervalValue,
                phaseOneValue, phaseTwoValue, phaseThreeValue, maxTargetsValue));
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
    public static volatile int eclipseCastRange = 40;
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
    public static volatile int funeralNovaBlackHoleStartTick = 48;
    public static volatile int funeralNovaAccretionStartTick = 68;
    public static volatile int funeralNovaCollapseStartTick = 100;
    public static volatile int funeralNovaVoidStartTick = 120;
    public static volatile int funeralNovaFlashStartTick = 150;
    public static volatile int funeralNovaHypernovaStartTick = 164;
    public static volatile int funeralNovaAfterglowStartTick = 250;
    public static volatile int funeralNovaFadeOutStartTick = 300;
    public static volatile int funeralNovaAccretionDamageTick = 68;
    public static volatile int funeralNovaCollapseDamageTick = 110;

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
    public static volatile int chromaticAccretionPulseOneTick = 36;
    public static volatile int chromaticAccretionPulseTwoTick = 64;
    public static volatile int chromaticAccretionPulseThreeTick = 92;
    public static volatile int chromaticAccretionPulseFourTick = 120;
    public static volatile double chromaticAccretionStoredDamageShare = 0.30D;
    public static volatile double chromaticAccretionStoredDamageCapFraction = 0.20D;

    public static volatile double starlessSwallowedDamageStep = 0.05D;
    public static volatile int starlessSwallowedDamageCap = 4;

    public static volatile double gargantuaBlastDamageFraction = 0.72D;
    public static volatile double crabNebulaWindDamageFraction = 0.026D;

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
    public static volatile int serverMaxActiveEntities = 64;
    public static volatile int serverMaxEntitiesPerSpell = 16;
    public static volatile int serverTargetScanLimit = 128;

    private static boolean legacyPathWarningShown;

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
            case "eclipseSeverance" -> eclipseCastRange;
            case "funeralNova" -> funeralNovaCastRange;
            case "chromaticAccretion" -> chromaticAccretionCastRange;
            case "worldTree" -> worldTreeCastRange;
            default -> generic(spellId).range.get();
        };
    }

    public static int lifetimeTicks(String spellId) {
        return switch (spellId) {
            case "celestialJudgment" -> celestialLifetimeTicks;
            case "stargraveSingularity" -> stargraveLifetimeTicks;
            case "eclipseSeverance" -> eclipseLifetimeTicks;
            case "funeralNova" -> funeralNovaLifetimeTicks;
            case "chromaticAccretion" -> chromaticAccretionLifetimeTicks;
            case "worldTree" -> worldTreeLifetimeTicks;
            default -> Math.max(1, generic(spellId).lifetimeTicks().get());
        };
    }

    public static double effectRadius(String spellId) {
        return switch (spellId) {
            case "stargraveSingularity" -> stargraveEffectRadius;
            case "eclipseSeverance" -> eclipseAttackRadius;
            case "funeralNova" -> funeralNovaEffectRadius;
            case "chromaticAccretion" -> chromaticAccretionEffectRadius;
            case "worldTree" -> worldTreeEffectRadius;
            default -> generic(spellId).effectRadius().get();
        };
    }

    public static double damageFraction(String spellId) {
        return switch (spellId) {
            case "celestialJudgment" -> celestialDamageFraction;
            case "stargraveSingularity" -> stargraveDamageFraction;
            case "eclipseSeverance" -> eclipseDamage / 20.0D;
            case "funeralNova" -> funeralNovaAccretionDamageFraction;
            case "chromaticAccretion" -> chromaticAccretionPulseDamageFraction;
            default -> generic(spellId).damageFraction().get();
        };
    }

    public static double secondaryDamageFraction(String spellId) {
        return switch (spellId) {
            case "funeralNova" -> funeralNovaHypernovaDamageFraction;
            case "chromaticAccretion" -> chromaticAccretionCollapseDamageFraction;
            default -> generic(spellId).secondaryDamageFraction().get();
        };
    }

    public static int damageIntervalTicks(String spellId) {
        return Math.max(1, generic(spellId).damageIntervalTicks().get());
    }

    public static int phaseTick(String spellId, int phase) {
        GenericValues values = generic(spellId);
        int lifetime = lifetimeTicks(spellId);
        int phaseOne = clampTimelineValue(values.phaseOneTick().get(), 0, lifetime - 3);
        int phaseTwo = clampTimelineValue(values.phaseTwoTick().get(), phaseOne + 1, lifetime - 2);
        int phaseThree = clampTimelineValue(values.phaseThreeTick().get(), phaseTwo + 1, lifetime - 1);
        return switch (phase) {
            case 1 -> phaseOne;
            case 2 -> phaseTwo;
            case 3 -> phaseThree;
            default -> throw new IllegalArgumentException("phase must be 1..3");
        };
    }

    public static int chromaticAccretionPulseTick(int index) {
        return switch (index) {
            case 0 -> chromaticAccretionPulseOneTick;
            case 1 -> chromaticAccretionPulseTwoTick;
            case 2 -> chromaticAccretionPulseThreeTick;
            case 3 -> chromaticAccretionPulseFourTick;
            default -> throw new IllegalArgumentException("pulse index must be 0..3");
        };
    }

    public static int maxTargets(String spellId) {
        return Math.max(1, generic(spellId).maxTargets().get());
    }

    /**
     * Applies both the spell-specific target budget and the server safety ceiling to a query result.
     * The returned view keeps the original distance ordering supplied by the caller.
     */
    public static <T> List<T> limitTargets(String spellId, List<T> targets) {
        if (targets == null || targets.isEmpty()) {
            return targets;
        }
        int configuredLimit = switch (spellId) {
            case "celestialJudgment", "stargraveSingularity", "eclipseSeverance",
                    "funeralNova", "chromaticAccretion", "worldTree" -> serverTargetScanLimit;
            default -> maxTargets(spellId);
        };
        int limit = Math.max(1, Math.min(serverTargetScanLimit, configuredLimit));
        return targets.size() <= limit ? targets : targets.subList(0, limit);
    }

    /** Server-wide cap used by the entity admission guard. */
    public static int activeEntityLimit() {
        return Math.max(1, serverMaxActiveEntities);
    }

    /** Per-spell cap used by the entity admission guard. */
    public static int entityLimit(String spellId) {
        return switch (spellId) {
            case "celestialJudgment", "stargraveSingularity", "eclipseSeverance",
                    "funeralNova", "chromaticAccretion", "worldTree" ->
                    Math.max(1, serverMaxEntitiesPerSpell);
            default -> Math.max(1, Math.min(serverMaxEntitiesPerSpell, maxTargets(spellId)));
        };
    }

    private static int clampTimelineValue(int value, int minimum, int maximum) {
        int safeMaximum = Math.max(minimum, maximum);
        return Math.max(minimum, Math.min(value, safeMaximum));
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
        warnLegacyGenericPath(event.getConfig());
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
        eclipseCastRange = ECLIPSE_RANGE.get();
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
        funeralNovaBlackHoleStartTick = NOVA_BLACK_HOLE_TICK.get();
        funeralNovaAccretionStartTick = NOVA_ACCRETION_TICK.get();
        funeralNovaCollapseStartTick = NOVA_COLLAPSE_TICK.get();
        funeralNovaVoidStartTick = NOVA_VOID_TICK.get();
        funeralNovaFlashStartTick = NOVA_FLASH_TICK.get();
        funeralNovaHypernovaStartTick = NOVA_HYPERNOVA_TICK.get();
        funeralNovaAfterglowStartTick = NOVA_AFTERGLOW_TICK.get();
        funeralNovaFadeOutStartTick = NOVA_FADE_TICK.get();
        funeralNovaAccretionDamageTick = NOVA_ACCRETION_DAMAGE_TICK.get();
        funeralNovaCollapseDamageTick = NOVA_COLLAPSE_DAMAGE_TICK.get();

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
        chromaticAccretionPulseOneTick = ACCRETION_PULSE_ONE.get();
        chromaticAccretionPulseTwoTick = ACCRETION_PULSE_TWO.get();
        chromaticAccretionPulseThreeTick = ACCRETION_PULSE_THREE.get();
        chromaticAccretionPulseFourTick = ACCRETION_PULSE_FOUR.get();
        chromaticAccretionStoredDamageShare = ACCRETION_STORED_SHARE.get();
        chromaticAccretionStoredDamageCapFraction = ACCRETION_STORED_CAP_FRACTION.get();

        starlessSwallowedDamageStep = STARLESS_SWALLOWED_DAMAGE_STEP.get();
        starlessSwallowedDamageCap = STARLESS_SWALLOWED_DAMAGE_CAP.get();

        gargantuaBlastDamageFraction = GARGANTUA_BLAST_DAMAGE.get();
        crabNebulaWindDamageFraction = CRAB_WIND_DAMAGE.get();

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
        serverMaxActiveEntities = SERVER_MAX_ACTIVE_ENTITIES.get();
        serverMaxEntitiesPerSpell = SERVER_MAX_ENTITIES_PER_SPELL.get();
        serverTargetScanLimit = SERVER_TARGET_SCAN_LIMIT.get();

        validateTimelines();
        validateGenericTimelines();
    }

    /**
     * Earlier development builds accidentally nested each generic spell below the previous
     * {@code spells} section. Forge keeps those unknown values in the file, so call them out once
     * instead of silently making a server owner believe their old tuning is still active.
     */
    private static void warnLegacyGenericPath(ModConfig config) {
        if (legacyPathWarningShown || config.getConfigData() == null) {
            return;
        }
        try {
            if (!config.getConfigData().contains(List.of("spells", "spells"))) {
                legacyPathWarningShown = true;
                return;
            }
            Path backupPath = null;
            try {
                Path configPath = config.getFullPath();
                if (configPath != null) {
                    backupPath = configPath.resolveSibling(
                            configPath.getFileName() + ".legacy-spells.bak");
                    if (!Files.exists(backupPath)) {
                        Files.copy(configPath, backupPath);
                    }
                }
            } catch (java.io.IOException exception) {
                ExampleMod.LOGGER.warn("Found legacy nested spell config entries, but could not create a backup", exception);
            }
            if (backupPath == null) {
                ExampleMod.LOGGER.warn(
                        "Found legacy nested spell config entries under [spells.spells]. "
                                + "Generic spell settings now belong under [spells.<spellId>]; "
                                + "the config file path is unavailable, so copy your values to the "
                                + "new sections before removing the old entries.");
            } else {
                ExampleMod.LOGGER.warn(
                        "Found legacy nested spell config entries under [spells.spells]. "
                                + "Generic spell settings now belong under [spells.<spellId>]; "
                                + "the original file was preserved at {}. Copy your values to the "
                                + "new sections before removing the old entries.", backupPath);
            }
        } catch (RuntimeException exception) {
            ExampleMod.LOGGER.debug("Could not inspect the legacy spell config path", exception);
        }
        legacyPathWarningShown = true;
    }

    /** Keep configurable timelines ordered even when a hand-edited file has inconsistent values. */
    private static void validateTimelines() {
        celestialImpactTick = clampTimeline(
                "celestialJudgment.impactTick", celestialImpactTick, 0,
                Math.max(0, celestialLifetimeTicks - 1));
        stargraveDamageTick = clampTimeline(
                "stargraveSingularity.damageTick", stargraveDamageTick, 1,
                Math.max(1, stargraveLifetimeTicks - 1));
        eclipseDamageTick = clampTimeline(
                "eclipseSeverance.damageTick", eclipseDamageTick, 0,
                Math.max(0, eclipseLifetimeTicks - 1));

        int lifetime = Math.max(8, chromaticAccretionLifetimeTicks);
        if (lifetime != chromaticAccretionLifetimeTicks) {
            ExampleMod.LOGGER.warn(
                    "chromaticAccretion.lifetimeTicks={} is too short for its phases; using {} ticks.",
                    chromaticAccretionLifetimeTicks, lifetime);
            chromaticAccretionLifetimeTicks = lifetime;
        }
        chromaticAccretionFormationEndTick = clampTimeline(
                "chromaticAccretion.formationEndTick", chromaticAccretionFormationEndTick,
                1, lifetime - 2);
        chromaticAccretionCollapseTick = clampTimeline(
                "chromaticAccretion.collapseTick", chromaticAccretionCollapseTick,
                chromaticAccretionFormationEndTick + 1, lifetime - 1);
        int previousPulse = chromaticAccretionFormationEndTick;
        chromaticAccretionPulseOneTick = clampTimeline(
                "chromaticAccretion.pulseOneTick", chromaticAccretionPulseOneTick,
                previousPulse + 1, chromaticAccretionCollapseTick - 4);
        previousPulse = chromaticAccretionPulseOneTick;
        chromaticAccretionPulseTwoTick = clampTimeline(
                "chromaticAccretion.pulseTwoTick", chromaticAccretionPulseTwoTick,
                previousPulse + 1, chromaticAccretionCollapseTick - 3);
        previousPulse = chromaticAccretionPulseTwoTick;
        chromaticAccretionPulseThreeTick = clampTimeline(
                "chromaticAccretion.pulseThreeTick", chromaticAccretionPulseThreeTick,
                previousPulse + 1, chromaticAccretionCollapseTick - 2);
        previousPulse = chromaticAccretionPulseThreeTick;
        chromaticAccretionPulseFourTick = clampTimeline(
                "chromaticAccretion.pulseFourTick", chromaticAccretionPulseFourTick,
                previousPulse + 1, chromaticAccretionCollapseTick - 1);

        int novaLifetime = Math.max(8, funeralNovaLifetimeTicks);
        funeralNovaLifetimeTicks = novaLifetime;
        funeralNovaBlackHoleStartTick = clampTimeline(
                "funeralNova.blackHoleStartTick", funeralNovaBlackHoleStartTick, 0, novaLifetime - 8);
        funeralNovaAccretionStartTick = clampTimeline(
                "funeralNova.accretionStartTick", funeralNovaAccretionStartTick,
                funeralNovaBlackHoleStartTick + 1, novaLifetime - 7);
        funeralNovaCollapseStartTick = clampTimeline(
                "funeralNova.collapseStartTick", funeralNovaCollapseStartTick,
                funeralNovaAccretionStartTick + 1, novaLifetime - 6);
        funeralNovaVoidStartTick = clampTimeline(
                "funeralNova.voidStartTick", funeralNovaVoidStartTick,
                funeralNovaCollapseStartTick + 1, novaLifetime - 5);
        funeralNovaFlashStartTick = clampTimeline(
                "funeralNova.flashStartTick", funeralNovaFlashStartTick,
                funeralNovaVoidStartTick + 1, novaLifetime - 4);
        funeralNovaHypernovaStartTick = clampTimeline(
                "funeralNova.hypernovaStartTick", funeralNovaHypernovaStartTick,
                funeralNovaFlashStartTick + 1, novaLifetime - 3);
        funeralNovaAfterglowStartTick = clampTimeline(
                "funeralNova.afterglowStartTick", funeralNovaAfterglowStartTick,
                funeralNovaHypernovaStartTick + 1, novaLifetime - 2);
        funeralNovaFadeOutStartTick = clampTimeline(
                "funeralNova.fadeOutStartTick", funeralNovaFadeOutStartTick,
                funeralNovaAfterglowStartTick + 1, novaLifetime - 1);
        funeralNovaAccretionDamageTick = clampTimeline(
                "funeralNova.accretionDamageTick", funeralNovaAccretionDamageTick,
                0, novaLifetime - 1);
        funeralNovaCollapseDamageTick = clampTimeline(
                "funeralNova.collapseDamageTick", funeralNovaCollapseDamageTick,
                0, novaLifetime - 1);
    }

    private static int clampTimeline(String path, int value, int minimum, int maximum) {
        int safeMaximum = Math.max(minimum, maximum);
        int clamped = Math.max(minimum, Math.min(value, safeMaximum));
        if (clamped != value) {
            ExampleMod.LOGGER.warn(
                    "{}={} is outside the valid timeline; using {} (allowed {}..{}).",
                    path, value, clamped, minimum, safeMaximum);
        }
        return clamped;
    }

    private static void validateGenericTimelines() {
        for (Map.Entry<String, GenericValues> entry : GENERIC_SPELLS.entrySet()) {
            String id = entry.getKey();
            GenericValues values = entry.getValue();
            int lifetime = Math.max(1, values.lifetimeTicks().get());
            int phaseOne = values.phaseOneTick().get();
            int phaseTwo = values.phaseTwoTick().get();
            int phaseThree = values.phaseThreeTick().get();
            if (phaseOne >= lifetime || phaseTwo <= phaseOne || phaseThree <= phaseTwo
                    || phaseThree >= lifetime) {
                ExampleMod.LOGGER.warn(
                        "{} phase ticks ({}, {}, {}) do not fit lifetime {}; "
                                + "runtime accessors will clamp them to a safe order.",
                        id, phaseOne, phaseTwo, phaseThree, lifetime);
            }
        }
    }
    private record GenericValues(
            ForgeConfigSpec.IntValue cooldown,
            ForgeConfigSpec.IntValue mana,
            ForgeConfigSpec.IntValue castTime,
            ForgeConfigSpec.IntValue range,
            ForgeConfigSpec.IntValue lifetimeTicks,
            ForgeConfigSpec.DoubleValue effectRadius,
            ForgeConfigSpec.DoubleValue damageFraction,
            ForgeConfigSpec.DoubleValue secondaryDamageFraction,
            ForgeConfigSpec.IntValue damageIntervalTicks,
            ForgeConfigSpec.IntValue phaseOneTick,
            ForgeConfigSpec.IntValue phaseTwoTick,
            ForgeConfigSpec.IntValue phaseThreeTick,
            ForgeConfigSpec.IntValue maxTargets) {
    }

    private record GenericDefaults(
            int lifetimeTicks,
            double effectRadius,
            double damageFraction,
            double secondaryDamageFraction,
            int damageIntervalTicks,
            int phaseOneTick,
            int phaseTwoTick,
            int phaseThreeTick,
            int maxTargets) {
    }
}

package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.entity.GargantuaEntity;
import com.gang.lightpollution.entity.EventHorizonEntity;
import com.gang.lightpollution.entity.RedshiftAbyssEntity;
import com.gang.lightpollution.entity.CosmicHorseshoeEntity;
import com.gang.lightpollution.entity.MicroquasarEntity;
import com.gang.lightpollution.entity.HelixNebulaEntity;
import com.gang.lightpollution.entity.MagnetarEntity;
import com.gang.lightpollution.entity.TidalDisruptionEntity;
import com.gang.lightpollution.entity.QuasarJetEntity;
import com.gang.lightpollution.entity.PinwheelEntity;
import com.gang.lightpollution.entity.CrabNebulaEntity;
import com.gang.lightpollution.entity.CelestialJudgmentEntity;
import com.gang.lightpollution.entity.ChromaticAccretionEntity;
import com.gang.lightpollution.entity.ConstellationEntity;
import com.gang.lightpollution.fx.ConstellationShape;
import com.gang.lightpollution.entity.EclipseSeveranceEntity;
import com.gang.lightpollution.entity.LeviathanEntity;
import com.gang.lightpollution.entity.SecondSunEntity;
import com.gang.lightpollution.entity.SilhouetteEntity;
import com.gang.lightpollution.entity.SingularityEntity;
import com.gang.lightpollution.entity.SkyCollapseEntity;
import com.gang.lightpollution.fx.SkyCollapseShape;
import com.gang.lightpollution.entity.StellarConvergenceEntity;
import com.gang.lightpollution.fx.StellarConvergenceShape;
import com.gang.lightpollution.entity.FuneralNovaEntity;
import com.gang.lightpollution.entity.StargraveSingularityEntity;
import com.gang.lightpollution.entity.StarfallEntity;
import com.gang.lightpollution.fx.StarfallShape;
import com.gang.lightpollution.entity.StarlessEntity;
import com.gang.lightpollution.entity.WorldTreeEntity;
import com.gang.lightpollution.fx.WorldTreeShape;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Computes light markers from the synchronized client-side spell timelines. */
public final class SpellLightEmitter {
    private static final StableLightSelector LIGHT_SELECTOR = new StableLightSelector();
    private static final Set<CelestialJudgmentEntity> CELESTIAL =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ChromaticAccretionEntity> CHROMATIC =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<EclipseSeveranceEntity> ECLIPSE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<FuneralNovaEntity> FUNERAL_NOVA =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<StargraveSingularityEntity> STARGRAVE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<StarlessEntity> STARLESS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ConstellationEntity> CONSTELLATION =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<SilhouetteEntity> SILHOUETTE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<StarfallEntity> STARFALL =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<SkyCollapseEntity> SKY_COLLAPSE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<StellarConvergenceEntity> CONVERGENCE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<SecondSunEntity> SECOND_SUN =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<SingularityEntity> SINGULARITY =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<EventHorizonEntity> EVENT_HORIZON = new java.util.HashSet<>();
    private static final Set<RedshiftAbyssEntity> REDSHIFT_ABYSS = new java.util.HashSet<>();
    private static final Set<GargantuaEntity> GARGANTUA =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<CosmicHorseshoeEntity> COSMIC_HORSESHOE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<MicroquasarEntity> MICROQUASAR =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<HelixNebulaEntity> HELIX_NEBULA =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<MagnetarEntity> MAGNETAR =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<TidalDisruptionEntity> TIDAL_DISRUPTION =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<QuasarJetEntity> QUASAR_JET =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<PinwheelEntity> PINWHEEL =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<CrabNebulaEntity> CRAB_NEBULA =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<LeviathanEntity> LEVIATHAN =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<WorldTreeEntity> WORLD_TREE =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Most meteors that may light the scene at once. Several are always in the
     * air; letting every one of them cast would multiply the shadow cost by the
     * number of meteors in flight.
     */
    private static final int STARFALL_LIGHT_CAP = 6;
    /** Points along Leviathan's spine that emit light. */
    private static final int LEVIATHAN_LIGHT_SAMPLES = 5;
    /** Most sky shards that may light the scene at once. */
    private static final int SKY_COLLAPSE_LIGHT_CAP = 3;
    /**
     * How far out Second Sun's light is placed, in blocks. Far enough that its
     * shadows are near parallel across the visible area, which is what makes it
     * read as a sun rather than a floodlight.
     */
    private static final double SECOND_SUN_LIGHT_DISTANCE = 260.0D;
    /** Reach of Second Sun's light. It has to cover everything it illuminates. */
    private static final float SECOND_SUN_LIGHT_RADIUS = 340.0F;

    private static volatile Light testLight;

    private SpellLightEmitter() {
    }

    public static void add(Object entity) {
        if (entity instanceof CelestialJudgmentEntity value) {
            CELESTIAL.add(value);
            CelestialJudgmentVisuals.add(value);
        }
        if (entity instanceof ChromaticAccretionEntity value) CHROMATIC.add(value);
        if (entity instanceof EclipseSeveranceEntity value) ECLIPSE.add(value);
        if (entity instanceof FuneralNovaEntity value) FUNERAL_NOVA.add(value);
        if (entity instanceof StargraveSingularityEntity value) STARGRAVE.add(value);
        if (entity instanceof StarlessEntity value) STARLESS.add(value);
        if (entity instanceof ConstellationEntity value) CONSTELLATION.add(value);
        if (entity instanceof SilhouetteEntity value) SILHOUETTE.add(value);
        if (entity instanceof StarfallEntity value) STARFALL.add(value);
        if (entity instanceof SkyCollapseEntity value) SKY_COLLAPSE.add(value);
        if (entity instanceof StellarConvergenceEntity value) CONVERGENCE.add(value);
        if (entity instanceof SecondSunEntity value) SECOND_SUN.add(value);
        if (entity instanceof SingularityEntity value) SINGULARITY.add(value);
        if (entity instanceof GargantuaEntity value) GARGANTUA.add(value);
        if (entity instanceof EventHorizonEntity value) EVENT_HORIZON.add(value);
        if (entity instanceof RedshiftAbyssEntity value) REDSHIFT_ABYSS.add(value);
        if (entity instanceof CosmicHorseshoeEntity value) COSMIC_HORSESHOE.add(value);
        if (entity instanceof MicroquasarEntity value) MICROQUASAR.add(value);
        if (entity instanceof HelixNebulaEntity value) HELIX_NEBULA.add(value);
        if (entity instanceof MagnetarEntity value) MAGNETAR.add(value);
        if (entity instanceof TidalDisruptionEntity value) TIDAL_DISRUPTION.add(value);
        if (entity instanceof QuasarJetEntity value) QUASAR_JET.add(value);
        if (entity instanceof PinwheelEntity value) PINWHEEL.add(value);
        if (entity instanceof CrabNebulaEntity value) CRAB_NEBULA.add(value);
        if (entity instanceof LeviathanEntity value) LEVIATHAN.add(value);
        if (entity instanceof WorldTreeEntity value) WORLD_TREE.add(value);
    }

    public static void remove(Object entity) {
        if (entity instanceof CelestialJudgmentEntity value) {
            CELESTIAL.remove(value);
            CelestialJudgmentVisuals.remove(value);
        }
        if (entity instanceof ChromaticAccretionEntity value) CHROMATIC.remove(value);
        if (entity instanceof EclipseSeveranceEntity value) ECLIPSE.remove(value);
        if (entity instanceof FuneralNovaEntity value) FUNERAL_NOVA.remove(value);
        if (entity instanceof StargraveSingularityEntity value) STARGRAVE.remove(value);
        if (entity instanceof StarlessEntity value) STARLESS.remove(value);
        if (entity instanceof ConstellationEntity value) CONSTELLATION.remove(value);
        if (entity instanceof SilhouetteEntity value) SILHOUETTE.remove(value);
        if (entity instanceof StarfallEntity value) STARFALL.remove(value);
        if (entity instanceof SkyCollapseEntity value) SKY_COLLAPSE.remove(value);
        if (entity instanceof StellarConvergenceEntity value) CONVERGENCE.remove(value);
        if (entity instanceof SecondSunEntity value) SECOND_SUN.remove(value);
        if (entity instanceof SingularityEntity value) SINGULARITY.remove(value);
        if (entity instanceof GargantuaEntity value) GARGANTUA.remove(value);
        if (entity instanceof EventHorizonEntity value) EVENT_HORIZON.remove(value);
        if (entity instanceof RedshiftAbyssEntity value) REDSHIFT_ABYSS.remove(value);
        if (entity instanceof CosmicHorseshoeEntity value) COSMIC_HORSESHOE.remove(value);
        if (entity instanceof MicroquasarEntity value) MICROQUASAR.remove(value);
        if (entity instanceof HelixNebulaEntity value) HELIX_NEBULA.remove(value);
        if (entity instanceof MagnetarEntity value) MAGNETAR.remove(value);
        if (entity instanceof TidalDisruptionEntity value) TIDAL_DISRUPTION.remove(value);
        if (entity instanceof QuasarJetEntity value) QUASAR_JET.remove(value);
        if (entity instanceof PinwheelEntity value) PINWHEEL.remove(value);
        if (entity instanceof CrabNebulaEntity value) CRAB_NEBULA.remove(value);
        if (entity instanceof LeviathanEntity value) LEVIATHAN.remove(value);
        if (entity instanceof WorldTreeEntity value) WORLD_TREE.remove(value);
    }

    public static void clear() {
        LIGHT_SELECTOR.clear();
        CELESTIAL.clear();
        CelestialJudgmentVisuals.clear();
        CHROMATIC.clear();
        ECLIPSE.clear();
        FUNERAL_NOVA.clear();
        STARGRAVE.clear();
        STARLESS.clear();
        CONSTELLATION.clear();
        SILHOUETTE.clear();
        STARFALL.clear();
        SKY_COLLAPSE.clear();
        CONVERGENCE.clear();
        SECOND_SUN.clear();
        SINGULARITY.clear();
        GARGANTUA.clear();
        EVENT_HORIZON.clear();
        REDSHIFT_ABYSS.clear();
        COSMIC_HORSESHOE.clear();
        MICROQUASAR.clear();
        HELIX_NEBULA.clear();
        MAGNETAR.clear();
        TIDAL_DISRUPTION.clear();
        QUASAR_JET.clear();
        PINWHEEL.clear();
        CRAB_NEBULA.clear();
        LEVIATHAN.clear();
        WORLD_TREE.clear();
        testLight = null;
    }

    /** Places a client-only light using the legacy/default purple color. */
    public static void setTestLight(Vec3 position) {
        setTestLight(position, TestColor.PURPLE);
    }

    /** Places a client-only light used to validate scene lighting. */
    public static void setTestLight(Vec3 position, TestColor color) {
        if (position == null) {
            testLight = null;
            return;
        }
        TestColor resolvedColor = color == null ? TestColor.PURPLE : color;
        testLight = new Light(position, 18.0F, 3.2F,
                resolvedColor.red(), resolvedColor.green(), resolvedColor.blue());
    }

    /** Parses a color name accepted by the client test-light command. */
    public static TestColor testColor(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "red" -> TestColor.RED;
            case "orange" -> TestColor.ORANGE;
            case "yellow" -> TestColor.YELLOW;
            case "green" -> TestColor.GREEN;
            case "cyan" -> TestColor.CYAN;
            case "blue" -> TestColor.BLUE;
            case "purple" -> TestColor.PURPLE;
            default -> null;
        };
    }

    /** Names exposed by the test-light command's tab completion. */
    public static List<String> testColorNames() {
        return List.of("red", "orange", "yellow", "green", "cyan", "blue", "purple");
    }

    /** Removes the client-only validation light without touching spell lights. */
    public static void clearTestLight() {
        testLight = null;
    }

    public static List<Light> collect(float partialTick) {
        long measurement = com.gang.lightpollution.client.perf.PerfTracker.begin(
                com.gang.lightpollution.client.perf.PerfTracker.Section.LIGHT_COLLECTION);
        try {
            return collectInternal(partialTick);
        } finally {
            com.gang.lightpollution.client.perf.PerfTracker.end(
                    com.gang.lightpollution.client.perf.PerfTracker.Section.LIGHT_COLLECTION, measurement);
        }
    }

    private static List<Light> collectInternal(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();

        CELESTIAL.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        CHROMATIC.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        ECLIPSE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        FUNERAL_NOVA.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        STARGRAVE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        STARLESS.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        CONSTELLATION.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        SILHOUETTE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        STARFALL.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        SKY_COLLAPSE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        CONVERGENCE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        SECOND_SUN.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        SINGULARITY.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        EVENT_HORIZON.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        REDSHIFT_ABYSS.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        GARGANTUA.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        COSMIC_HORSESHOE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        MICROQUASAR.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        HELIX_NEBULA.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        MAGNETAR.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        TIDAL_DISRUPTION.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        QUASAR_JET.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        PINWHEEL.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        CRAB_NEBULA.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        LEVIATHAN.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        WORLD_TREE.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);

        if (!SpellLightConfig.enabled) return List.of();

        List<Light> result = new ArrayList<>();
        Light currentTestLight = testLight;
        if (currentTestLight != null) {
            result.add(currentTestLight);
        }
        for (var view : CelestialJudgmentVisuals.views(partialTick)) {
            var time = view.time();
            float flash = time.flash(18) * SpellLightConfig.cinematicFlashStrength;
            float fade = time.fade();
            result.add(new Light(view.feet().add(0, view.height() * 0.45, 0), 20,
                    fade * (0.25F + time.formation() * 0.45F + flash * 2.2F), 0.36F, 0.82F, 1));
            if (time.formation() > 0.01F) result.add(new Light(view.sky().add(0, -3.2, 0), 28,
                    fade * time.formation() * (0.45F + time.charge() * 0.8F + flash), 0.28F, 0.68F, 1));
            if (time.beamTravel() > 0 && view.age() < time.impact()) {
                Vec3 moving = view.sky().add(0, -3.2, 0).lerp(view.feet(), time.beamTravel());
                result.add(new Light(moving, 14, fade * time.beamTravel() * 0.65F, 0.56F, 0.85F, 1));
            }
        }
        for (ChromaticAccretionEntity entity : CHROMATIC) {
            float age = entity.getVisualAgeTicks(partialTick);
            float formation = Mth.clamp(age
                    / (float) Math.max(1, SpellConfig.chromaticAccretionFormationEndTick),
                    0.0F, 1.0F);
            float collapse = bell(age - SpellConfig.chromaticAccretionCollapseTick, 16.0F);
            int collapseTick = SpellConfig.chromaticAccretionCollapseTick;
            int lifetime = Math.max(collapseTick + 1, SpellConfig.chromaticAccretionLifetimeTicks);
            float fade = 1.0F - Mth.clamp((age - collapseTick)
                    / (float) (lifetime - collapseTick), 0.0F, 1.0F);
            result.add(new Light(interpolated(entity, partialTick).add(0.0D, 0.8D, 0.0D),
                    10.0F + entity.getVisualRadius() * 3.5F,
                    (0.25F + formation * 0.75F + collapse * 2.2F) * Math.max(fade, 0.15F),
                    0.18F, 0.78F, 1.0F));
        }
        for (EclipseSeveranceEntity entity : ECLIPSE) {
            float age = entity.tickCount + partialTick;
            int lifetime = Math.max(1, SpellConfig.eclipseLifetimeTicks);
            int activeFadeStart = Math.max(0, lifetime - 14);
            float active = 1.0F - Mth.clamp((age - activeFadeStart)
                    / (float) Math.max(1, lifetime - activeFadeStart), 0.0F, 1.0F);
            float impact = bell(age - SpellConfig.eclipseDamageTick, 3.0F);
            result.add(new Light(interpolated(entity, partialTick).add(0.0D, 1.05D, 0.0D),
                    18.0F, (0.35F + active * 0.85F + impact * 1.35F),
                    0.28F, 0.72F, 1.0F));
        }
        for (FuneralNovaEntity entity : FUNERAL_NOVA) {
            float age = entity.getVisualAgeTicks(partialTick);
            float formation = Mth.clamp(age / (float) Math.max(1, SpellConfig.funeralNovaAccretionStartTick), 0.0F, 1.0F);
            float collapse = bell(age - SpellConfig.funeralNovaCollapseDamageTick, 18.0F);
            float hypernova = bell(age - SpellConfig.funeralNovaHypernovaStartTick, 26.0F);
            float fade = 1.0F - Mth.clamp(
                    (age - SpellConfig.funeralNovaFadeOutStartTick) / 44.0F, 0.0F, 1.0F);
            result.add(new Light(
                    interpolated(entity, partialTick).add(0.0D, 1.5D, 0.0D),
                    18.0F + hypernova * 10.0F,
                    (0.30F + formation * 0.75F + collapse * 1.6F + hypernova * 4.0F)
                            * Math.max(fade, 0.08F),
                    0.52F, 0.24F, 1.0F));
        }
        for (StargraveSingularityEntity entity : STARGRAVE) {
            float age = entity.tickCount + partialTick;
            int damageTick = SpellConfig.stargraveDamageTick;
            int lifetime = Math.max(damageTick + 1, SpellConfig.stargraveLifetimeTicks);
            float collapse = bell(age - damageTick, 12.0F);
            int fadeStart = Math.max(0, lifetime - 30);
            float fade = 1.0F - Mth.clamp((age - fadeStart)
                    / (float) Math.max(1, lifetime - fadeStart), 0.0F, 1.0F);
            result.add(new Light(interpolated(entity, partialTick).add(0.0D, 1.5D, 0.0D),
                    18.0F + entity.getVisualRadius() * 3.0F,
                    (0.45F + collapse * 3.0F) * Math.max(fade, 0.1F),
                    0.55F, 0.22F, 1.0F));
        }
        // Starless emits nothing while it is open -- that is the point -- and then
        // gives everything back at the release, scaled by what it swallowed.
        for (StarlessEntity entity : STARLESS) {
            float flash = entity.getReleaseFlash(partialTick);
            if (flash <= 0.002F) {
                continue;
            }
            float scale = entity.getReleaseScale();
            result.add(new Light(entity.voidCenter(partialTick),
                    16.0F + 6.0F * scale,
                    flash * 4.2F * scale,
                    1.0F, 0.96F, 0.99F));
        }
        // A single strong star in a wide orbit. One moving source is what makes
        // every occluder throw a long shadow that swings right around it as the
        // star passes, which many dim sources cannot do -- they average into a
        // flat wash and cancel each other's shadows out.
        List<com.gang.lightpollution.fx.ConstellationSource> stars = new ArrayList<>(CONSTELLATION);
        stars.addAll(com.gang.lightpollution.fx.FxRegistry.constellations());
        for (var source : stars) {
            int life = CinematicVisuals.constellationLifetime(source);
            float age = source.getVisualAgeTicks(partialTick);
            float brightness = ConstellationShape.starBrightness(age, life);
            if (brightness <= 0.02F) continue;
            var p = source.shapeParams();
            result.add(new Light(ConstellationShape.starPosition(p, source.anchorCenter(partialTick), age, life),
                    26.0F * p.scale(),
                    brightness * (2.6F + CinematicVisuals.burnPulse(source, partialTick) * 0.35F),
                    1.0F, 0.78F, 0.42F));
        }
        // Falling meteors are moving lights, which is what keeps the ground
        // shadows sweeping for the whole bombardment. The cap matters: shadow cost
        // is (lights x samples) per pixel, and forty meteors would each want a
        // trace.
        //
        // The finale is emitted first. At the moment it begins the tail of the
        // rain is still airborne alongside it, so iterating in index order would
        // spend the budget on small trailing meteors and leave the one large
        // arrival unlit, which is exactly backwards.
        for (StarfallEntity entity : STARFALL) {
            int emitted = 0;
            for (int pass = 0; pass < 2 && emitted < STARFALL_LIGHT_CAP; pass++) {
                boolean wantFinale = pass == 0;
                for (int meteor = 0; meteor < StarfallShape.METEOR_COUNT
                        && emitted < STARFALL_LIGHT_CAP; meteor++) {
                    boolean finale = StarfallEntity.isFinaleMeteor(meteor);
                    if (finale != wantFinale) {
                        continue;
                    }
                    float brightness = entity.meteorBrightness(meteor, partialTick);
                    if (brightness <= 0.05F) {
                        continue;
                    }
                    result.add(new Light(entity.meteorPosition(meteor, partialTick),
                            finale ? 40.0F : 14.0F,
                            brightness * (finale ? 2.6F : 1.1F),
                            1.0F, 0.86F, 0.62F));
                    emitted++;
                }
            }
        }

        // Sky Collapse: the shards are the light, and each one is huge, so a few
        // of them lighting the ground is enough. The fracture itself is not a
        // light -- it is far enough out that giving it one would light the whole
        // world from a point that is nowhere.
        for (SkyCollapseEntity entity : SKY_COLLAPSE) {
            int emitted = 0;
            for (int shard = SkyCollapseShape.SHARD_COUNT - 1;
                    shard >= 0 && emitted < SKY_COLLAPSE_LIGHT_CAP; shard--) {
                float brightness = entity.shardBrightness(shard, partialTick);
                if (brightness <= 0.05F) {
                    continue;
                }
                boolean keystone = SkyCollapseEntity.isKeystone(shard);
                result.add(new Light(entity.shardPosition(shard, partialTick),
                        keystone ? 46.0F : 22.0F,
                        brightness * (keystone ? 2.8F : 1.3F),
                        0.78F, 0.88F, 1.0F));
                emitted++;
            }
        }

        // Stellar Convergence: this is the case the multi-light pass was built
        // for. Nine coloured sources at once, each throwing its own shadow.
        for (StellarConvergenceEntity entity : CONVERGENCE) {
            for (int star = 0; star < StellarConvergenceShape.STAR_COUNT; star++) {
                float brightness = entity.starBrightness(star, partialTick);
                if (brightness <= 0.05F) {
                    continue;
                }
                float[] rgb = entity.starColour(star);
                result.add(new Light(entity.starPosition(star, partialTick),
                        20.0F, brightness * 1.5F, rgb[0], rgb[1], rgb[2]));
            }
            float column = entity.columnStrength(partialTick);
            if (column > 0.05F) {
                // The column's own light, at its foot, so the ground around it is
                // lit rather than only the shaft being visible.
                result.add(new Light(entity.groundCentre(partialTick),
                        32.0F, column * 2.4F, 1.0F, 1.0F, 1.0F));
            }
        }

        // Second Sun: one light standing in for a body on the sky. Placed a long
        // way off along its own direction so the shadows it throws are near
        // parallel, which is what sells it as a sun rather than a lamp.
        for (SecondSunEntity sun : SECOND_SUN) {
            float brightness = sun.brightness(partialTick);
            if (brightness <= 0.05F) {
                continue;
            }
            Vec3 anchor = sun.groundCentre(partialTick);
            float temperature = sun.temperature(partialTick);
            result.add(new Light(
                    sun.discPosition(anchor, partialTick, SECOND_SUN_LIGHT_DISTANCE),
                    SECOND_SUN_LIGHT_RADIUS, brightness * 2.2F,
                    1.0F,
                    0.72F + temperature * 0.26F,
                    0.35F + temperature * 0.6F));
        }
        // Singularity: the core is a hole, so what lights the scene is the
        // infalling skin and the bolts around it -- dim while it charges, then
        // one very bright frame when it fails.
        for (SingularityEntity entity : SINGULARITY) {
            Vec3 centre = entity.coreCentre(partialTick);
            float core = entity.coreBrightness(partialTick);
            if (core > 0.05F) {
                result.add(new Light(centre, 26.0F, core * 0.9F,
                        0.72F, 0.42F, 1.0F));
            }
            float flash = entity.blastFlash(partialTick);
            if (flash > 0.01F) {
                result.add(new Light(centre, 60.0F, flash * 4.5F,
                        0.94F, 0.86F, 1.0F));
            }
        }
        for (EventHorizonEntity entity : EVENT_HORIZON) {
            float light = entity.envelope(partialTick);
            if (light > .01F) result.add(new Light(entity.position(),
                    entity.unitRadius(partialTick) * SpellLightConfig.eventHorizonVisualScale * 8,
                    light * SpellLightConfig.eventHorizonDiskBrightness * 1.6F, 1, .88F, .72F));
        }
        for (RedshiftAbyssEntity entity : REDSHIFT_ABYSS) {
            float light = entity.envelope(partialTick);
            if (light > .01F) result.add(new Light(entity.position(),
                    entity.unitRadius(partialTick) * SpellLightConfig.redshiftAbyssVisualScale * 14,
                    light * SpellLightConfig.redshiftAbyssDiskBrightness * 1.6F, 1, .55F, .22F));
        }
        // Gargantua: the accretion disk is the light source, not the hole. Its
        // colour is the disk's own 4500 K amber rather than anything blue -- the
        // film's disk is deliberately cool, because a real quasar disk would have
        // sterilised everything nearby. Brightness rides the same growth curve as
        // the geometry, so the world lights up as it swells.
        for (GargantuaEntity entity : GARGANTUA) {
            Vec3 centre = entity.centre(partialTick);
            float radius = entity.gravitationalRadius(partialTick);
            float brightness = entity.brightness(partialTick);
            if (brightness > 0.03F) {
                result.add(new Light(centre,
                        radius * GargantuaEntity.DISK_OUTER_RADIUS * 0.8F,
                        brightness * 1.1F, 1.0F, 0.78F, 0.42F));
            }
            float flash = entity.blastFlash(partialTick);
            if (flash > 0.01F) {
                result.add(new Light(centre, 74.0F, flash * 5.0F,
                        1.0F, 0.94F, 0.86F));
            }
        }
        // Leviathan: the body is the light. Sampled at a few points along the
        // spine rather than every segment -- a 48-light body would blow the shadow
        // budget on its own, and the eye cannot tell the difference between five
        // sources spread down a curve and forty-eight.
        for (LeviathanEntity entity : LEVIATHAN) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.05F) {
                continue;
            }
            float extended = entity.extended(partialTick);
            for (int sample = 0; sample < LEVIATHAN_LIGHT_SAMPLES; sample++) {
                float t = sample / (float) (LEVIATHAN_LIGHT_SAMPLES - 1) * extended;
                // The head is the brightest part, so it carries the strongest
                // source and the tail only a glow.
                float weight = 1.0F - t * 0.55F;
                result.add(new Light(entity.spinePoint(t, partialTick),
                        20.0F, brightness * weight * 1.3F,
                        0.42F, 0.66F, 1.0F));
            }
            float flash = entity.biteFlash(partialTick);
            if (flash > 0.01F) {
                result.add(new Light(entity.spinePoint(0.0F, partialTick),
                        44.0F, flash * 3.6F, 0.88F, 0.95F, 1.0F));
            }
        }
        // World Tree: the trunk is the source, plus one in the crown once it
        // opens. The roots are not lights; seven creeping sources would eat the
        // shadow budget for something that is a line on the ground.
        for (WorldTreeEntity entity : WORLD_TREE) {
            float fade = entity.fade(partialTick);
            if (fade <= 0.05F) {
                continue;
            }
            Vec3 seed = entity.seedPoint(partialTick);
            float trunk = entity.trunkProgress(partialTick);
            float hardened = entity.hardened(partialTick);
            // Warm gold while living, cooling toward crystal as it hardens.
            float green = 0.74F - hardened * 0.06F;
            float blue = 0.38F + hardened * 0.55F;
            if (trunk > 0.05F) {
                result.add(new Light(
                        seed.add(0.0D, WorldTreeShape.TRUNK_HEIGHT * trunk * 0.5D, 0.0D),
                        30.0F, (0.7F + trunk * 1.4F) * fade, 1.0F, green, blue));
            }
            float crown = entity.crownProgress(partialTick);
            if (crown > 0.05F) {
                result.add(new Light(
                        seed.add(0.0D, WorldTreeShape.TRUNK_HEIGHT * trunk
                                + WorldTreeShape.BRANCH_REACH * 0.4D, 0.0D),
                        34.0F, crown * 2.2F * fade,
                        1.0F, Math.min(1.0F, green + 0.1F), Math.min(1.0F, blue + 0.1F)));
            }
        }
        CinematicLightSources.append(result, partialTick);
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        double visibleReach = minecraft.options.getEffectiveRenderDistance() * 28.0 + 64.0;
        result.removeIf(light -> light.radius() <= 0 || light.intensity() <= 0
                || light.position().distanceToSqr(eye) > Math.pow(visibleReach + light.radius(), 2));
        return limitLights(drainByGargantuas(drainByVoids(result, partialTick), partialTick));
    }

    /** Prefer screen contribution with a retention margin rather than reselecting on intensity ties. */
    private static List<Light> limitLights(List<Light> lights) {
        return LIGHT_SELECTOR.select(lights, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(),
                com.gang.lightpollution.client.perf.AdaptiveVisualQuality.lightLimit(SpellLightConfig.maxLights));
    }

    /**
     * Puts out the spell lights Gargantua has drawn in.
     *
     * <p>The geodesic pass already bends the light of the world around the hole, but
     * a light *source* inside the pull is a different thing: its own glow should be
     * going down the hole, not carrying on lighting the terrain. Extinguishing it here
     * makes the light, its falloff and its cast shadows all fade together, the same
     * way the Starless drain does.</p>
     *
     * <p>Gargantua's own disk light is exempt, or it would eat itself.</p>
     */
    private static List<Light> drainByGargantuas(List<Light> lights, float partialTick) {
        if (GARGANTUA.isEmpty() || lights.isEmpty()) {
            return lights;
        }
        List<Light> drained = new ArrayList<>(lights.size());
        for (Light light : lights) {
            float keep = 1.0F;
            for (GargantuaEntity entity : GARGANTUA) {
                Vec3 centre = entity.centre(partialTick);
                double distance = light.position().distanceTo(centre);
                if (distance >= GargantuaEntity.PULL_RADIUS || distance < 0.001D) {
                    continue;
                }
                // Its own disk light sits at the centre; skip it rather than
                // letting the hole extinguish itself.
                if (distance < 0.5D) {
                    continue;
                }
                float horizon = entity.gravitationalRadius(partialTick)
                        * GargantuaEntity.HORIZON_RADIUS;
                float reach;
                if (distance <= horizon) {
                    // Past the horizon a light is simply gone.
                    reach = 1.0F;
                } else {
                    float outer = (float) ((distance - horizon)
                            / Math.max(GargantuaEntity.PULL_RADIUS - horizon, 0.001D));
                    reach = 1.0F - outer;
                    reach = reach * reach * (3.0F - 2.0F * reach);
                }
                keep *= 1.0F - entity.opened(partialTick) * reach;
            }
            if (keep >= 0.999F) {
                drained.add(light);
            } else if (light.intensity() * keep > 0.01F) {
                drained.add(new Light(light.position(), light.radius(),
                        light.intensity() * keep,
                        light.red(), light.green(), light.blue()));
            }
        }
        return drained;
    }

    /**
     * Applies every open Starless void to the collected lights.
     *
     * <p>This is the whole "swallows light" mechanic and it lives here rather than
     * in a shader on purpose: the lighting pass already consumes a per-frame light
     * list, so scaling an entry's intensity is enough to make the light, its
     * falloff and its cast shadows all fade together. Doing it in the shader would
     * need a second record type and would still have to reproduce this same
     * distance weighting.</p>
     */
    private static List<Light> drainByVoids(List<Light> lights, float partialTick) {
        if (STARLESS.isEmpty() || lights.isEmpty()) {
            return lights;
        }

        List<Light> drained = new ArrayList<>(lights.size());
        for (Light light : lights) {
            float keep = 1.0F;
            for (StarlessEntity entity : STARLESS) {
                float strength = entity.getDrainStrength(partialTick);
                if (strength <= 0.001F) {
                    continue;
                }
                double distance = light.position().distanceTo(entity.voidCenter(partialTick));
                if (distance >= StarlessEntity.EFFECT_RADIUS) {
                    continue;
                }
                // Anything the sphere has actually reached is extinguished
                // outright -- the void swallowing a light has to read as the
                // light going out, not as it getting slightly dimmer. Beyond the
                // horizon the pull tapers to nothing at the rim of the effect.
                float voidRadius = Math.max(entity.getVoidRadius(partialTick), 0.5F);
                float reach;
                if (distance <= voidRadius) {
                    reach = 1.0F;
                } else {
                    float outer = (float) (distance - voidRadius)
                            / Math.max((float) StarlessEntity.EFFECT_RADIUS - voidRadius, 0.001F);
                    reach = 1.0F - outer;
                    reach = reach * reach * (3.0F - 2.0F * reach);
                }
                keep *= 1.0F - strength * reach;
            }
            if (keep >= 0.999F) {
                drained.add(light);
            } else if (light.intensity() * keep > 0.01F) {
                drained.add(new Light(light.position(), light.radius(),
                        light.intensity() * keep,
                        light.red(), light.green(), light.blue()));
            }
        }
        return drained;
    }

    /** Open voids for the screen-space pass; the drain itself is applied in collect. */
    public static List<StarlessEntity> collectVoids() {
        return STARLESS.isEmpty() ? List.of() : new ArrayList<>(STARLESS);
    }

    /**
     * The silhouette field with the strongest inversion right now, or null. Only
     * one can apply: two overlapping negatives would cancel into nonsense, so the
     * blend pass takes the dominant field rather than summing them.
     */
    @org.jetbrains.annotations.Nullable
    public static SilhouetteEntity strongestSilhouette(float partialTick) {
        SilhouetteEntity best = null;
        float bestStrength = 0.0F;
        for (SilhouetteEntity entity : SILHOUETTE) {
            float strength = entity.getInversionStrength(partialTick);
            if (strength > bestStrength) {
                bestStrength = strength;
                best = entity;
            }
        }
        return best;
    }

    /** Live starfalls, for the client-side meteor visuals. */
    public static List<StarfallEntity> collectStarfalls() {
        return STARFALL.isEmpty() ? List.of() : new ArrayList<>(STARFALL);
    }

    /** Live constellations, for the client-side star particles. */
    public static List<ConstellationEntity> collectConstellations() {
        return CONSTELLATION.isEmpty() ? List.of() : new ArrayList<>(CONSTELLATION);
    }

    /** Live sky collapses, for the fracture and falling slabs. */
    public static List<SkyCollapseEntity> collectSkyCollapses() {
        return SKY_COLLAPSE.isEmpty() ? List.of() : new ArrayList<>(SKY_COLLAPSE);
    }

    /** Live convergences, for the stars, filaments and column. */
    public static List<StellarConvergenceEntity> collectConvergences() {
        return CONVERGENCE.isEmpty() ? List.of() : new ArrayList<>(CONVERGENCE);
    }

    /** Live second suns, for the sky disc. */
    public static List<SecondSunEntity> collectSecondSuns() {
        return SECOND_SUN.isEmpty() ? List.of() : new ArrayList<>(SECOND_SUN);
    }

    /** Live singularities, for the core, bolts and shockwaves. */
    public static List<SingularityEntity> collectSingularities() {
        return SINGULARITY.isEmpty() ? List.of() : new ArrayList<>(SINGULARITY);
    }

    /** Live leviathans, for the body, fins and jaws. */
    public static List<EventHorizonEntity> collectEventHorizons() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        EVENT_HORIZON.removeIf(e -> !e.isAlive() || e.level() != level);
        return EVENT_HORIZON.isEmpty() ? List.of() : new ArrayList<>(EVENT_HORIZON);
    }
    public static List<RedshiftAbyssEntity> collectRedshiftAbysses() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        REDSHIFT_ABYSS.removeIf(e -> !e.isAlive() || e.level() != level);
        return REDSHIFT_ABYSS.isEmpty() ? List.of() : new ArrayList<>(REDSHIFT_ABYSS);
    }

    public static List<GargantuaEntity> collectGargantuas() {
        return GARGANTUA.isEmpty() ? List.of() : new ArrayList<>(GARGANTUA);
    }

    /** Live horseshoe lenses, for the arc and the scene it bends. */
    public static List<CosmicHorseshoeEntity> collectCosmicHorseshoes() {
        return COSMIC_HORSESHOE.isEmpty() ? List.of() : new ArrayList<>(COSMIC_HORSESHOE);
    }

    /** Live microquasars, for the corkscrew jets. */
    public static List<MicroquasarEntity> collectMicroquasars() {
        return MICROQUASAR.isEmpty() ? List.of() : new ArrayList<>(MICROQUASAR);
    }

    /** Live Helix nebulae, for the shell of cometary knots. */
    public static List<HelixNebulaEntity> collectHelixNebulae() {
        return HELIX_NEBULA.isEmpty() ? List.of() : new ArrayList<>(HELIX_NEBULA);
    }

    /** Live magnetars, for the dipole field loops. */
    public static List<MagnetarEntity> collectMagnetars() {
        return MAGNETAR.isEmpty() ? List.of() : new ArrayList<>(MAGNETAR);
    }

    public static List<LeviathanEntity> collectLeviathans() {
        return LEVIATHAN.isEmpty() ? List.of() : new ArrayList<>(LEVIATHAN);
    }

    /** Live world trees, for the roots, trunk, branches and crown. */
    public static List<WorldTreeEntity> collectWorldTrees() {
        return WORLD_TREE.isEmpty() ? List.of() : new ArrayList<>(WORLD_TREE);
    }

    /** Live tidal disruption effects. */
    public static List<TidalDisruptionEntity> collectTidalDisruptions() {
        return TIDAL_DISRUPTION.isEmpty() ? List.of() : new ArrayList<>(TIDAL_DISRUPTION);
    }

    /** Live quasar jet effects. */
    public static List<QuasarJetEntity> collectQuasarJets() {
        return QUASAR_JET.isEmpty() ? List.of() : new ArrayList<>(QUASAR_JET);
    }

    /** Live pinwheel effects. */
    public static List<PinwheelEntity> collectPinwheels() {
        return PINWHEEL.isEmpty() ? List.of() : new ArrayList<>(PINWHEEL);
    }

    /** Live crab nebula effects. */
    public static List<CrabNebulaEntity> collectCrabNebulas() {
        return CRAB_NEBULA.isEmpty() ? List.of() : new ArrayList<>(CRAB_NEBULA);
    }

    private static Vec3 interpolated(net.minecraft.world.entity.Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    private static float bell(float delta, float width) {
        float normalized = delta / Math.max(width, 0.001F);
        return (float) Math.exp(-normalized * normalized * 2.0F);
    }

    public record Light(Vec3 position, float radius, float intensity,
                        float red, float green, float blue) {
    }

    /** Saturated RGB presets for the client-only test light. */
    public enum TestColor {
        RED(1.0F, 0.05F, 0.03F),
        ORANGE(1.0F, 0.30F, 0.03F),
        YELLOW(1.0F, 0.88F, 0.04F),
        GREEN(0.08F, 1.0F, 0.12F),
        CYAN(0.03F, 0.88F, 1.0F),
        BLUE(0.08F, 0.25F, 1.0F),
        // Keep the original no-argument test-light tint for backwards compatibility.
        PURPLE(0.48F, 0.25F, 1.0F);

        private final float red;
        private final float green;
        private final float blue;

        TestColor(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public float red() {
            return red;
        }

        public float green() {
            return green;
        }

        public float blue() {
            return blue;
        }
    }
}

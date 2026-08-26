package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.CelestialJudgmentEntity;
import com.gang.lightpollution.entity.ChromaticAccretionEntity;
import com.gang.lightpollution.entity.ConstellationEntity;
import com.gang.lightpollution.entity.EclipseSeveranceEntity;
import com.gang.lightpollution.entity.LeviathanEntity;
import com.gang.lightpollution.entity.SecondSunEntity;
import com.gang.lightpollution.entity.SilhouetteEntity;
import com.gang.lightpollution.entity.SingularityEntity;
import com.gang.lightpollution.entity.SkyCollapseEntity;
import com.gang.lightpollution.entity.StellarConvergenceEntity;
import com.gang.lightpollution.entity.FuneralNovaEntity;
import com.gang.lightpollution.entity.StargraveSingularityEntity;
import com.gang.lightpollution.entity.StarfallEntity;
import com.gang.lightpollution.entity.StarlessEntity;
import com.gang.lightpollution.entity.WorldTreeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Computes light markers from the synchronized client-side spell timelines. */
public final class SpellLightEmitter {
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
        if (entity instanceof CelestialJudgmentEntity value) CELESTIAL.add(value);
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
        if (entity instanceof LeviathanEntity value) LEVIATHAN.add(value);
        if (entity instanceof WorldTreeEntity value) WORLD_TREE.add(value);
    }

    public static void remove(Object entity) {
        if (entity instanceof CelestialJudgmentEntity value) CELESTIAL.remove(value);
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
        if (entity instanceof LeviathanEntity value) LEVIATHAN.remove(value);
        if (entity instanceof WorldTreeEntity value) WORLD_TREE.remove(value);
    }

    public static void clear() {
        CELESTIAL.clear();
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

        List<Light> result = new ArrayList<>();
        Light currentTestLight = testLight;
        if (currentTestLight != null) {
            result.add(currentTestLight);
        }
        for (CelestialJudgmentEntity entity : CELESTIAL) {
            float age = entity.tickCount + partialTick;
            float formation = Mth.clamp(age / 36.0F, 0.0F, 1.0F);
            float impact = bell(age - CelestialJudgmentEntity.IMPACT_TICK, 7.0F);
            result.add(new Light(interpolated(entity, partialTick).add(0.0D, 2.2D, 0.0D),
                    20.0F, 0.35F + formation * 0.75F + impact * 2.5F,
                    0.36F, 0.82F, 1.0F));
        }
        for (ChromaticAccretionEntity entity : CHROMATIC) {
            float age = entity.getVisualAgeTicks(partialTick);
            float formation = Mth.clamp(age / ChromaticAccretionEntity.FORMATION_END_TICK, 0.0F, 1.0F);
            float collapse = bell(age - ChromaticAccretionEntity.COLLAPSE_TICK, 16.0F);
            float fade = 1.0F - Mth.clamp((age - 136.0F) / 34.0F, 0.0F, 1.0F);
            result.add(new Light(interpolated(entity, partialTick).add(0.0D, 0.8D, 0.0D),
                    10.0F + entity.getVisualRadius() * 3.5F,
                    (0.25F + formation * 0.75F + collapse * 2.2F) * Math.max(fade, 0.15F),
                    0.18F, 0.78F, 1.0F));
        }
        for (EclipseSeveranceEntity entity : ECLIPSE) {
            float age = entity.tickCount + partialTick;
            float active = 1.0F - Mth.clamp((age - 16.0F) / 14.0F, 0.0F, 1.0F);
            float impact = bell(age - EclipseSeveranceEntity.DAMAGE_TICK, 3.0F);
            result.add(new Light(interpolated(entity, partialTick).add(0.0D, 1.05D, 0.0D),
                    18.0F, (0.35F + active * 0.85F + impact * 1.35F),
                    0.28F, 0.72F, 1.0F));
        }
        for (FuneralNovaEntity entity : FUNERAL_NOVA) {
            float age = entity.getVisualAgeTicks(partialTick);
            float formation = Mth.clamp(age / (float) FuneralNovaEntity.ACCRETION_START_TICK, 0.0F, 1.0F);
            float collapse = bell(age - FuneralNovaEntity.COLLAPSE_DAMAGE_TICK, 18.0F);
            float hypernova = bell(age - FuneralNovaEntity.HYPERNOVA_START_TICK, 26.0F);
            float fade = 1.0F - Mth.clamp(
                    (age - FuneralNovaEntity.FADE_OUT_START_TICK) / 44.0F, 0.0F, 1.0F);
            result.add(new Light(
                    interpolated(entity, partialTick).add(0.0D, 1.5D, 0.0D),
                    18.0F + hypernova * 10.0F,
                    (0.30F + formation * 0.75F + collapse * 1.6F + hypernova * 4.0F)
                            * Math.max(fade, 0.08F),
                    0.52F, 0.24F, 1.0F));
        }
        for (StargraveSingularityEntity entity : STARGRAVE) {
            float age = entity.tickCount + partialTick;
            float collapse = bell(age - StargraveSingularityEntity.DAMAGE_TICK, 12.0F);
            float fade = 1.0F - Mth.clamp((age - 170.0F) / 30.0F, 0.0F, 1.0F);
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
        for (ConstellationEntity entity : CONSTELLATION) {
            for (int star = 0; star < ConstellationEntity.STAR_COUNT; star++) {
                float brightness = entity.starBrightness(star, partialTick);
                if (brightness <= 0.02F) {
                    continue;
                }
                result.add(new Light(entity.starPosition(star, partialTick),
                        26.0F,
                        brightness * 2.6F,
                        1.0F, 0.93F, 0.72F));
            }
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
                for (int meteor = 0; meteor < StarfallEntity.METEOR_COUNT
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
            for (int shard = SkyCollapseEntity.SHARD_COUNT - 1;
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
            for (int star = 0; star < StellarConvergenceEntity.STAR_COUNT; star++) {
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
                        seed.add(0.0D, WorldTreeEntity.TRUNK_HEIGHT * trunk * 0.5D, 0.0D),
                        30.0F, (0.7F + trunk * 1.4F) * fade, 1.0F, green, blue));
            }
            float crown = entity.crownProgress(partialTick);
            if (crown > 0.05F) {
                result.add(new Light(
                        seed.add(0.0D, WorldTreeEntity.TRUNK_HEIGHT * trunk
                                + WorldTreeEntity.BRANCH_REACH * 0.4D, 0.0D),
                        34.0F, crown * 2.2F * fade,
                        1.0F, Math.min(1.0F, green + 0.1F), Math.min(1.0F, blue + 0.1F)));
            }
        }
        return drainByVoids(result, partialTick);
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
    public static List<LeviathanEntity> collectLeviathans() {
        return LEVIATHAN.isEmpty() ? List.of() : new ArrayList<>(LEVIATHAN);
    }

    /** Live world trees, for the roots, trunk, branches and crown. */
    public static List<WorldTreeEntity> collectWorldTrees() {
        return WORLD_TREE.isEmpty() ? List.of() : new ArrayList<>(WORLD_TREE);
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

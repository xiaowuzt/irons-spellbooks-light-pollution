package com.gang.lightpollution.registry;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.GargantuaEntity;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<EntityType<CelestialJudgmentEntity>> CELESTIAL_JUDGMENT =
            ENTITY_TYPES.register("celestial_judgment", () -> EntityType.Builder
                    .of(CelestialJudgmentEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":celestial_judgment"));

    public static final RegistryObject<EntityType<StargraveSingularityEntity>> STARGRAVE_SINGULARITY =
            ENTITY_TYPES.register("stargrave_singularity", () -> EntityType.Builder
                    .of(StargraveSingularityEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":stargrave_singularity"));

    public static final RegistryObject<EntityType<EclipseSeveranceEntity>> ECLIPSE_SEVERANCE =
            ENTITY_TYPES.register("eclipse_severance", () -> EntityType.Builder
                    .of(EclipseSeveranceEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(32).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":eclipse_severance"));

    public static final RegistryObject<EntityType<FuneralNovaEntity>> FUNERAL_NOVA =
            ENTITY_TYPES.register("funeral_nova", () -> EntityType.Builder
                    .of(FuneralNovaEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(48).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":funeral_nova"));

    public static final RegistryObject<EntityType<ChromaticAccretionEntity>> CHROMATIC_ACCRETION =
            ENTITY_TYPES.register("chromatic_accretion", () -> EntityType.Builder
                    .of(ChromaticAccretionEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(48).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":chromatic_accretion"));

    public static final RegistryObject<EntityType<StarlessEntity>> STARLESS =
            ENTITY_TYPES.register("starless", () -> EntityType.Builder
                    .of(StarlessEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(48).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":starless"));

    public static final RegistryObject<EntityType<ConstellationEntity>> CONSTELLATION =
            ENTITY_TYPES.register("constellation", () -> EntityType.Builder
                    .of(ConstellationEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(48).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":constellation"));

    public static final RegistryObject<EntityType<SilhouetteEntity>> SILHOUETTE =
            ENTITY_TYPES.register("silhouette", () -> EntityType.Builder
                    .of(SilhouetteEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(48).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":silhouette"));

    public static final RegistryObject<EntityType<StarfallEntity>> STARFALL =
            ENTITY_TYPES.register("starfall", () -> EntityType.Builder
                    .of(StarfallEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(64).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":starfall"));

    public static final RegistryObject<EntityType<SkyCollapseEntity>> SKY_COLLAPSE =
            ENTITY_TYPES.register("sky_collapse", () -> EntityType.Builder
                    .of(SkyCollapseEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":sky_collapse"));

    public static final RegistryObject<EntityType<StellarConvergenceEntity>> STELLAR_CONVERGENCE =
            ENTITY_TYPES.register("stellar_convergence", () -> EntityType.Builder
                    .of(StellarConvergenceEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":stellar_convergence"));

    public static final RegistryObject<EntityType<SingularityEntity>> SINGULARITY =
            ENTITY_TYPES.register("singularity", () -> EntityType.Builder
                    .of(SingularityEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":singularity"));

    public static final RegistryObject<EntityType<LeviathanEntity>> LEVIATHAN =
            ENTITY_TYPES.register("leviathan", () -> EntityType.Builder
                    .of(LeviathanEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(128).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":leviathan"));

    public static final RegistryObject<EntityType<SecondSunEntity>> SECOND_SUN =
            ENTITY_TYPES.register("second_sun", () -> EntityType.Builder
                    .of(SecondSunEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(128).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":second_sun"));

    public static final RegistryObject<EntityType<WorldTreeEntity>> WORLD_TREE =
            ENTITY_TYPES.register("world_tree", () -> EntityType.Builder
                    .of(WorldTreeEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":world_tree"));

    public static final RegistryObject<EntityType<GargantuaEntity>> GARGANTUA =
            ENTITY_TYPES.register("gargantua", () -> EntityType.Builder
                    .of(GargantuaEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":gargantua"));

    public static final RegistryObject<EntityType<CosmicHorseshoeEntity>> COSMIC_HORSESHOE =
            ENTITY_TYPES.register("cosmic_horseshoe", () -> EntityType.Builder
                    .of(CosmicHorseshoeEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":cosmic_horseshoe"));

    public static final RegistryObject<EntityType<MicroquasarEntity>> MICROQUASAR =
            ENTITY_TYPES.register("microquasar", () -> EntityType.Builder
                    .of(MicroquasarEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":microquasar"));

    public static final RegistryObject<EntityType<HelixNebulaEntity>> HELIX_NEBULA =
            ENTITY_TYPES.register("helix_nebula", () -> EntityType.Builder
                    .of(HelixNebulaEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":helix_nebula"));

    public static final RegistryObject<EntityType<MagnetarEntity>> MAGNETAR =
            ENTITY_TYPES.register("magnetar", () -> EntityType.Builder
                    .of(MagnetarEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":magnetar"));

    public static final RegistryObject<EntityType<TidalDisruptionEntity>> TIDAL_DISRUPTION =
            ENTITY_TYPES.register("tidal_disruption", () -> EntityType.Builder
                    .of(TidalDisruptionEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":tidal_disruption"));

    public static final RegistryObject<EntityType<QuasarJetEntity>> QUASAR_JET =
            ENTITY_TYPES.register("quasar_jet", () -> EntityType.Builder
                    .of(QuasarJetEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":quasar_jet"));

    public static final RegistryObject<EntityType<PinwheelEntity>> PINWHEEL =
            ENTITY_TYPES.register("pinwheel", () -> EntityType.Builder
                    .of(PinwheelEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":pinwheel"));

    public static final RegistryObject<EntityType<CrabNebulaEntity>> CRAB_NEBULA =
            ENTITY_TYPES.register("crab_nebula", () -> EntityType.Builder
                    .of(CrabNebulaEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(96).updateInterval(1).fireImmune()
                    .build(ExampleMod.MODID + ":crab_nebula"));

    private ModEntities() {
    }
}

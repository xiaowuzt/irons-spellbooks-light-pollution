package com.gang.lightpollution;

import com.gang.lightpollution.client.renderer.CelestialJudgmentRenderer;
import com.gang.lightpollution.registry.ModCreativeTabs;
import com.gang.lightpollution.registry.ModEntities;
import com.gang.lightpollution.registry.ModItems;
import com.gang.lightpollution.registry.ModSounds;
import com.gang.lightpollution.spell.ModSpells;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ExampleMod.MODID)
public final class ExampleMod {
    public static final String MODID = "irons_spellbooks_light_pollution";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.register(modBus);
        ModSounds.register(modBus);
        ModCreativeTabs.register(modBus);
        ModSpells.SPELLS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SpellLightConfig.SPEC);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientModEvents {
        private ClientModEvents() {
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.CELESTIAL_JUDGMENT.get(), CelestialJudgmentRenderer::new);
            // Constellation and Starfall are registered here rather than from
            // their own client classes. Their registrations used to live in a
            // nested @EventBusSubscriber class, which Forge reported as
            // subscribed but whose handler never took effect for Starfall: the
            // entity reached the client with no renderer and vanilla's entity
            // loop dereferenced the null. Keep every anchor's renderer in this
            // one handler so a missing one is visible at a glance.
            event.registerEntityRenderer(ModEntities.CONSTELLATION.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.STARFALL.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.SKY_COLLAPSE.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.STELLAR_CONVERGENCE.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.SECOND_SUN.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.SINGULARITY.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.LEVIATHAN.get(), NoopRenderer::new);
            event.registerEntityRenderer(ModEntities.WORLD_TREE.get(), NoopRenderer::new);
        }
    }
}

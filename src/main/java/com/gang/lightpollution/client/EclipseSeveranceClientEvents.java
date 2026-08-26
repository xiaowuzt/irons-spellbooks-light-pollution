package com.gang.lightpollution.client;

import com.gang.lightpollution.client.renderer.EclipseSeveranceRenderer;
import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registration for the Eclipse Severance visual anchor. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EclipseSeveranceClientEvents {
    private EclipseSeveranceClientEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ECLIPSE_SEVERANCE.get(), EclipseSeveranceRenderer::new);
    }
}

package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client registration for the Silhouette field.
 *
 * <p>The field has no geometry: the inversion happens in the light blend pass,
 * which is the only place holding both the scene and the spell-light radiance.
 * Minecraft still requires every entity type to have a renderer, so the anchor
 * gets the no-op one.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class SilhouetteClientEvents {
    private SilhouetteClientEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SILHOUETTE.get(), NoopRenderer::new);
    }
}

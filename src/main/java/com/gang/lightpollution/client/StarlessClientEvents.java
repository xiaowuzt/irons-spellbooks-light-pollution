package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client registration for the Starless anchor.
 *
 * <p>The void has no geometry of its own: it is drawn entirely by the screen-space
 * pass in {@code SpellLightPostProcessor.renderStarless}, because an absence of
 * light is a operation on what is behind it rather than a surface. A renderer is
 * still required — Minecraft validates that every entity type has one — so the
 * anchor gets the no-op renderer.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StarlessClientEvents {
    private StarlessClientEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STARLESS.get(), NoopRenderer::new);
    }
}

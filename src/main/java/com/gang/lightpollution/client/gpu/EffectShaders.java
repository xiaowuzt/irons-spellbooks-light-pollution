package com.gang.lightpollution.client.gpu;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * Loads the effect shader, and copes with it not loading.
 *
 * <p>A shader that fails to compile on some driver should cost that driver the fragment-stage effects
 * and nothing else. {@link EffectRenderType#ready()} stays false, callers keep the plain text path, and
 * the failure is one line in the log rather than a crash on startup.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class EffectShaders {
    private EffectShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(ExampleMod.MODID, "effect_text"),
                            EffectVertexFormat.FORMAT),
                    loaded -> {
                        EffectRenderType.setShader(loaded);
                        ExampleMod.LOGGER.info(
                                "effect_text shader loaded; fragment-stage text effects are available");
                    });
        } catch (IOException | RuntimeException failed) {
            ExampleMod.LOGGER.warn(
                    "effect_text shader did not load; fragment-stage text effects are off", failed);
        }
    }
}

package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.renderer.BlackHoleRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlackHoleShaders {
    private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private BlackHoleShaders() {}
    public static ShaderInstance get(String id) { return SHADERS.get(id); }
    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        SHADERS.clear(); BlackHoleRenderer.reset();
        for (String name : new String[]{"schwarzschild_lens", "radiant_collapse", "stasis_singularity", "black_hole_low", "black_hole_resolve"}) {
            var id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, name);
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION_TEX), s -> SHADERS.put(name, s));
            } catch (IOException | RuntimeException failure) { ShaderCompileDiagnostics.report(event.getResourceProvider(), id, failure); }
        }
    }
}

package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.renderer.RedshiftAbyssRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.util.EnumMap;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RedshiftAbyssShaders {
    public enum Pass { TRACE, DOWNSAMPLE, BLOOM, COMPOSITE }
    private static final EnumMap<Pass, ShaderInstance> SHADERS = new EnumMap<>(Pass.class);
    private RedshiftAbyssShaders() {}
    public static ShaderInstance get(Pass pass) { return SHADERS.get(pass); }
    public static boolean ready() { return SHADERS.size() == Pass.values().length; }
    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        SHADERS.clear();
        RedshiftAbyssRenderer.reset();
        for (Pass pass : Pass.values()) {
            var id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "redshift_abyss_" + pass.name().toLowerCase(java.util.Locale.ROOT));
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION_TEX), s -> SHADERS.put(pass, s));
            } catch (IOException | RuntimeException failure) {
                ShaderCompileDiagnostics.report(event.getResourceProvider(), id, failure);
            }
        }
    }
}

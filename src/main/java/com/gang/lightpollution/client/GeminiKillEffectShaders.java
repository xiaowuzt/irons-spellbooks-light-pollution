package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/** Registers the six world shaders used by Gemini's Hypernova effect. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GeminiKillEffectShaders {
    @Nullable private static ShaderInstance magic;
    @Nullable private static ShaderInstance hole;
    @Nullable private static ShaderInstance particle;
    @Nullable private static ShaderInstance nova;
    @Nullable private static ShaderInstance orb;
    @Nullable private static ShaderInstance ray;

    private GeminiKillEffectShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        register(event, "funeral_nova_magic", shader -> magic = shader);
        register(event, "funeral_nova_hole", shader -> hole = shader);
        register(event, "funeral_nova_particle", shader -> particle = shader);
        register(event, "funeral_nova_nova", shader -> nova = shader);
        register(event, "funeral_nova_orb", shader -> orb = shader);
        register(event, "funeral_nova_ray", shader -> ray = shader);
    }

    private static void register(RegisterShadersEvent event, String path,
                                 java.util.function.Consumer<ShaderInstance> callback)
            throws IOException {
        ShaderInstance shader = new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path),
                DefaultVertexFormat.POSITION_TEX_COLOR);
        event.registerShader(shader, callback);
    }

    @Nullable public static ShaderInstance magic() {
        return magic;
    }

    @Nullable public static ShaderInstance hole() {
        return hole;
    }

    @Nullable public static ShaderInstance particle() {
        return particle;
    }

    @Nullable public static ShaderInstance nova() {
        return nova;
    }

    @Nullable public static ShaderInstance orb() {
        return orb;
    }

    @Nullable public static ShaderInstance ray() {
        return ray;
    }

    public static boolean ready() {
        return magic != null && hole != null && particle != null
                && nova != null && orb != null && ray != null;
    }
}

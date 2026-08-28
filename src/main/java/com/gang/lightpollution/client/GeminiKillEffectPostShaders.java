package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.renderer.SpellLightPostProcessor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/** Registers the compile-time variants in Gemini's KillEffect post chain. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GeminiKillEffectPostShaders {
    private static final Map<Pass, ShaderInstance> SHADERS = new EnumMap<>(Pass.class);

    private GeminiKillEffectPostShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        for (Pass pass : Pass.values()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    ExampleMod.MODID, "gemini_kill_post_" + pass.path);
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(), id,
                                DefaultVertexFormat.POSITION_TEX),
                        shader -> SHADERS.put(pass, shader));
            } catch (IOException | RuntimeException failure) {
                // Letting this propagate takes the whole game down during mod
                // loading, which is what a single bad post-effect shader used to
                // do in a modpack. Every consumer already null-checks
                // shader(pass) and disables its pass, so log the real GLSL error
                // and let the rest of the chain register.
                ShaderCompileDiagnostics.report(event.getResourceProvider(), id, failure);
            }
        }
    }

    @Nullable
    public static ShaderInstance shader(Pass pass) {
        return SHADERS.get(pass);
    }

    /**
     * The spell-light occupancy masks are baked from {@code BakedModel}s and
     * cached per {@code BlockState}. Block states outlive a resource reload but
     * their baked models do not, so the cache has to be dropped here or a
     * resource pack that changes a block's shape or a texture's alpha leaves the
     * old masks in place.
     */
    @SubscribeEvent
    public static void onModelsBaked(ModelEvent.BakingCompleted event) {
        SpellLightPostProcessor.onResourcesReloaded();
    }

    public static boolean ready() {
        for (Pass pass : Pass.values()) {
            if (!SHADERS.containsKey(pass)) {
                return false;
            }
        }
        return true;
    }

    public enum Pass {
        BRIGHT("bright"),
        BRIGHT_EDGE("bright_edge"),
        BLUR_H("blur_h"),
        BLUR_V("blur_v"),
        COMPOSITE("composite"),
        LIGHT_NORMALS("light_normals"),
        LIGHT_VOXELIZE("light_voxelize"),
        LIGHT_BUILD_LOD("light_build_lod"),
        SCREEN_LIGHTING("screen_lighting"),
        LIGHT_TEMPORAL("light_temporal"),
        LIGHT_SPATIAL("light_spatial"),
        LIGHT_BLEND("light_blend"),
        LIGHT_COPY_DEPTH("light_copy_depth"),
        LIGHT_COPY_FRAME("light_copy_frame"),
        DISTORTION("distortion"),
        GODRAY("godray"),
        VOLUMETRIC_GODRAY("volumetric_godray"),
        SSRT("ssrt"),
        CHROMATIC("chromatic"),
        BLACK_HOLE("black_hole"),
        GLOW_FLASH("glow_flash"),
        FLASH_SCREEN("flash_screen"),
        SHOCKWAVE("shockwave"),
        AFTERIMAGE("afterimage"),
        ACES("aces"),
        STARLESS("starless"),
        METEOR_SHOCK("meteor_shock"),
        SINGULARITY_LENS("singularity_lens"),
        GARGANTUA_LENS("gargantua_lens"),
        COSMIC_HORSESHOE_LENS("cosmic_horseshoe_lens");

        private final String path;

        Pass(String path) {
            this.path = path;
        }
    }
}

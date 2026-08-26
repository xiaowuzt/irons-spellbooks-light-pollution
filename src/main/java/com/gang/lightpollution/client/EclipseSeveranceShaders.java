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

/**
 * Registration and uniform contract for the Forge port of Gemini's sweep VFX.
 *
 * <p>This class intentionally does not subscribe to a render-stage event. The
 * spell renderer owns timing, geometry, framebuffer copies, and state restore;
 * it can obtain the three shader instances here without creating a second
 * renderer beside EclipseSeveranceWorldRenderer.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EclipseSeveranceShaders {
    private static final ResourceLocation ARC_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "eclipse_sweep_arc");
    private static final ResourceLocation PARTICLE_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "eclipse_sweep_particle");
    private static final ResourceLocation POST_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "eclipse_sweep_post");

    // Celestial preset copied from Gemini's SweepingAttackVFX defaults.
    public static final int CELESTIAL_DURATION_MS = 1450;
    public static final int CELESTIAL_SWEEP_DURATION_MS = 261;
    public static final int CELESTIAL_PARTICLE_DURATION_MS = 1044;
    public static final int CELESTIAL_RING_DURATION_MS = 1131;
    public static final int CELESTIAL_FADE_OUT_START_MS = 696;
    public static final float CELESTIAL_ARC_ANGLE_DEGREES = 145.0F;
    public static final float CELESTIAL_HEIGHT_OFFSET = 1.05F;
    public static final float CELESTIAL_RADIUS = 2.35F;
    public static final float CELESTIAL_THICKNESS = 0.82F;
    public static final float CELESTIAL_VERTICAL_LIFT = 0.42F;
    public static final float CELESTIAL_GLOW = 1.55F;
    public static final float CELESTIAL_INTENSITY = 1.25F;
    public static final float CELESTIAL_OPACITY = 0.92F;
    public static final float CELESTIAL_NOISE = 0.35F;
    public static final float CELESTIAL_FLOW_SPEED = 1.25F;
    public static final float CELESTIAL_ECHO_SPACING = 0.075F;
    public static final float CELESTIAL_RING_THICKNESS = 0.34F;
    public static final float CELESTIAL_RING_COUNT = 3.0F;
    public static final int CELESTIAL_LAYERS = 4;
    public static final int CELESTIAL_ECHOES = 3;
    public static final int CELESTIAL_SPEED_LINE_COUNT = 30;
    public static final float CELESTIAL_LINE_LENGTH = 0.95F;
    public static final float CELESTIAL_LINE_WIDTH = 0.028F;
    public static final int CELESTIAL_PARTICLE_COUNT = 88;
    public static final float CELESTIAL_PARTICLE_SPEED = 6.5F;
    public static final float CELESTIAL_PARTICLE_SPREAD = 0.7F;
    public static final float CELESTIAL_PARTICLE_SIZE = 0.18F;
    public static final float CELESTIAL_PARTICLE_GRAVITY = 2.6F;
    public static final int CELESTIAL_LIGHTNING_BOLTS = 7;
    public static final float CELESTIAL_LIGHTNING_WIDTH = 0.032F;
    public static final float CELESTIAL_RING_SCALE = 1.45F;
    public static final float CELESTIAL_DISTORTION = 0.38F;
    public static final float CELESTIAL_CHROMATIC = 0.28F;
    public static final float CELESTIAL_FLASH = 0.32F;
    public static final float CELESTIAL_VIGNETTE = 0.18F;
    public static final float CELESTIAL_PRIMARY_R = 0.39215687F;
    public static final float CELESTIAL_PRIMARY_G = 0.84705883F;
    public static final float CELESTIAL_PRIMARY_B = 1.0F;
    public static final float CELESTIAL_ACCENT_R = 0.7607843F;
    public static final float CELESTIAL_ACCENT_G = 0.4862745F;
    public static final float CELESTIAL_ACCENT_B = 1.0F;

    @Nullable
    private static ShaderInstance arcShader;
    @Nullable
    private static ShaderInstance particleShader;
    @Nullable
    private static ShaderInstance postShader;
    private static boolean compatibilityWarningLogged;
    private static boolean runtimeFallbackLogged;

    private EclipseSeveranceShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        // Never retain shader instances from a previous resource reload. If a
        // shader cannot compile in a user's renderer, the geometry fallback must
        // remain selectable instead of accidentally using a stale instance.
        arcShader = null;
        particleShader = null;
        postShader = null;

        if (shaderPipelineBlocked()) {
            return;
        }

        if (!tryRegister(event, ARC_ID, DefaultVertexFormat.POSITION_TEX_COLOR,
                shader -> arcShader = shader)) {
            return;
        }
        if (!tryRegister(event, PARTICLE_ID, DefaultVertexFormat.POSITION_TEX_COLOR,
                shader -> particleShader = shader)) {
            return;
        }
        tryRegister(event, POST_ID, DefaultVertexFormat.POSITION_TEX,
                shader -> postShader = shader);
    }

    private static boolean tryRegister(RegisterShadersEvent event, ResourceLocation id,
                                       com.mojang.blaze3d.vertex.VertexFormat format,
                                       java.util.function.Consumer<ShaderInstance> callback) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id, format), callback);
            return true;
        } catch (IOException | RuntimeException | LinkageError failure) {
            if (!compatibilityWarningLogged) {
                compatibilityWarningLogged = true;
                ExampleMod.LOGGER.warn(
                        "Eclipse Severance shader {} is unavailable; using the compatibility renderer instead. "
                                + "This is usually caused by an Oculus/Iris shader-pack conflict.",
                        id, failure);
            }
            return false;
        }
    }

    private static boolean shaderPipelineBlocked() {
        return Boolean.getBoolean("dirge.eclipseShadersDisabled");
    }

    /**
     * Disables the shader path after a renderer rejects a draw at runtime.
     * Some Oculus/Connector combinations accept shader construction during
     * resource reload and fail only when the first batch is submitted. Nulling
     * the instances lets the registered geometry renderer take over on the
     * next event without taking down the client.
     */
    public static void disableForCompatibility(Throwable failure) {
        arcShader = null;
        particleShader = null;
        postShader = null;
        if (!runtimeFallbackLogged) {
            runtimeFallbackLogged = true;
            ExampleMod.LOGGER.warn(
                    "Eclipse Severance shader rendering failed at runtime; "
                            + "switching to the compatibility renderer for this session.",
                    failure);
        }
    }

    @Nullable
    public static ShaderInstance arcShader() {
        return arcShader;
    }

    @Nullable
    public static ShaderInstance particleShader() {
        return particleShader;
    }

    @Nullable
    public static ShaderInstance postShader() {
        return postShader;
    }

    public static boolean ready() {
        return arcShader != null && particleShader != null && postShader != null;
    }

    /** Applies the exact Gemini uniform layout to an arc or particle shader. */
    public static void applySweepUniforms(ShaderInstance shader, SweepUniformData data) {
        setVec4(shader, "Params", data.params());
        setVec4(shader, "Geometry", data.geometry());
        setVec4(shader, "Style", data.style());
        setVec4(shader, "Motion", data.motion());
        setVec4(shader, "Primary", data.primary());
        setVec4(shader, "Accent", data.accent());
        setVec4(shader, "Core", data.core());
        setVec4(shader, "Misc", data.misc());
    }

    /**
     * Creates the default Celestial preset used by Gemini. Radius and seed are
     * parameters so the spell can scale the same art direction to its hitbox.
     */
    public static SweepUniformData celestialSweep(float timeSeconds, float sweepProgress,
                                                   float effectProgress, float radius, float seed) {
        return new SweepUniformData(
                new Vec4(timeSeconds, sweepProgress, effectProgress, CELESTIAL_INTENSITY),
                new Vec4(radius, CELESTIAL_THICKNESS, CELESTIAL_VERTICAL_LIFT, CELESTIAL_GLOW),
                // Crescent style = 1, Gradient color flow = 1, four layers.
                new Vec4(1.0F, 1.0F, CELESTIAL_LAYERS, CELESTIAL_NOISE),
                new Vec4(CELESTIAL_FLOW_SPEED, CELESTIAL_ECHO_SPACING,
                        CELESTIAL_RING_THICKNESS, CELESTIAL_RING_COUNT),
                new Vec4(CELESTIAL_PRIMARY_R, CELESTIAL_PRIMARY_G,
                        CELESTIAL_PRIMARY_B, CELESTIAL_OPACITY),
                new Vec4(CELESTIAL_ACCENT_R, CELESTIAL_ACCENT_G, CELESTIAL_ACCENT_B, 0.0F),
                new Vec4(1.0F, 1.0F, 1.0F, 0.0F),
                new Vec4(CELESTIAL_RING_SCALE, CELESTIAL_LINE_LENGTH,
                        CELESTIAL_LINE_WIDTH, seed));
    }

    public static SweepUniformData celestialSweep(float timeSeconds, float sweepProgress,
                                                   float effectProgress, float seed) {
        return celestialSweep(timeSeconds, sweepProgress, effectProgress,
                CELESTIAL_RADIUS, seed);
    }

    /** Applies the Gemini post-process uniform layout to the post shader. */
    public static void applyPostUniforms(ShaderInstance shader, PostUniformData data) {
        setVec4(shader, "Params", data.params());
        setVec4(shader, "Strength", data.strength());
        setVec4(shader, "Tint", data.tint());
    }

    public static PostUniformData celestialPost(int framebufferWidth, int framebufferHeight,
                                                float timeSeconds, float distortion,
                                                float chromatic, float flash, float vignette) {
        return new PostUniformData(
                new Vec4(framebufferWidth, framebufferHeight, timeSeconds, 0.0F),
                new Vec4(distortion, chromatic, flash, vignette),
                new Vec4(CELESTIAL_PRIMARY_R, CELESTIAL_PRIMARY_G, CELESTIAL_PRIMARY_B, 0.0F));
    }

    public static PostUniformData celestialPost(int framebufferWidth, int framebufferHeight,
                                                float timeSeconds) {
        return celestialPost(framebufferWidth, framebufferHeight, timeSeconds,
                CELESTIAL_DISTORTION, CELESTIAL_CHROMATIC,
                CELESTIAL_FLASH, CELESTIAL_VIGNETTE);
    }

    private static void setVec4(ShaderInstance shader, String name, Vec4 value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value.x(), value.y(), value.z(), value.w());
        }
    }

    public record Vec4(float x, float y, float z, float w) {
    }

    public record SweepUniformData(Vec4 params, Vec4 geometry, Vec4 style, Vec4 motion,
                                   Vec4 primary, Vec4 accent, Vec4 core, Vec4 misc) {
    }

    public record PostUniformData(Vec4 params, Vec4 strength, Vec4 tint) {
    }
}

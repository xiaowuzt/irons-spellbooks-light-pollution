package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.TidalDisruptionEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * Draws a tidal disruption's debris stream as one long wrapping ribbon.
 *
 * <p>The curve comes from the entity, so what is drawn and what lashes are the same
 * function. Ribbon construction is shared with the other curve-based effects; see
 * {@link CurveRibbon} for why the edge vectors have to be per point rather than per
 * segment.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class TidalDisruptionWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 256.0D * 256.0D;
    /** Quads along the stream. It wraps more than a turn and a half, so it needs them. */
    private static final int SEGMENTS = 150;

    /** Leading tip: deepest in the tidal field, hottest. */
    private static final float TIP_R = 0.78F;
    private static final float TIP_G = 0.88F;
    private static final float TIP_B = 1.00F;
    /** Trailing end: cooler, still recognisably stellar. */
    private static final float TAIL_R = 1.00F;
    private static final float TAIL_G = 0.42F;
    private static final float TAIL_B = 0.20F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private TidalDisruptionWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<TidalDisruptionEntity> events = SpellLightEmitter.collectTidalDisruptions();
        if (events.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.tidalStream();
        if (minecraft.level == null || shader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlState state = GlState.capture();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(SpellRenderStage.levelPose(event));
            RenderSystem.applyModelViewMatrix();
            drawStreams(events, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawStreams(List<TidalDisruptionEntity> events, Vec3 camera,
                                    float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (TidalDisruptionEntity entity : events) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            // The accretion flare at the hole. Faint while the star is only being stretched,
            // then overwhelming when the bound debris comes back — driven by the same
            // t^(-5/3) curve the entity computes.
            float flare = entity.flare(partialTick);
            EffectCore.add(centre, 2.0D + flare * 3.4D,
                    1.00F, 0.86F, 0.70F, brightness * (0.7F + flare * 3.2F));

            vertices += CurveRibbon.emit(builder, camera, SEGMENTS, 0.0D, 1.0D,
                    fraction -> entity.streamPoint(centre, age, fraction),
                    TidalDisruptionEntity::streamWidth,
                    fraction -> streamColour(fraction, brightness));
        }
        draw(builder, shader, vertices);
    }

    /**
     * Colour along the stream, hot at the leading tip and cool at the trailing end.
     *
     * <p>That direction is the physics: the tip has been in the tidal field longest and sits
     * deepest in the potential. Running it the other way would be a prettier gradient and a
     * wrong one.</p>
     */
    private static int streamColour(double fraction, float brightness) {
        float t = (float) Math.max(0.0D, Math.min(1.0D, fraction));
        float r = TIP_R + (TAIL_R - TIP_R) * t;
        float g = TIP_G + (TAIL_G - TIP_G) * t;
        float b = TIP_B + (TAIL_B - TIP_B) * t;
        return CurveRibbon.pack(r, g, b, brightness * 0.85F);
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices) {
        if (shader == null || !builder.building() || vertices <= 0) {
            finish(builder);
            return;
        }
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        }
    }

    private static void finish(BufferBuilder builder) {
        if (builder == null || !builder.building()) {
            return;
        }
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            if (rendered != null) {
                rendered.release();
            }
        } catch (RuntimeException ignored) {
            effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
        }
    }

    private record GlState(boolean blend, boolean depth, boolean cull, boolean depthWrite,
                           int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        private static GlState capture() {
            return new GlState(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        }

        private void restore() {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.depthMask(depthWrite);
        }
    }
}

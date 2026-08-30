package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.api.TidalDisruptionParams;
import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.TidalDisruptionShape;
import com.gang.lightpollution.fx.TidalDisruptionSource;
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

import java.util.List;

/**
 * Draws a tidal disruption's debris stream as one long wrapping tube.
 *
 * <p>The curve comes from the entity, so what is drawn and what lashes are the same function.
 * Real tube geometry rather than a camera-facing ribbon: see {@link CurveTube} for why that
 * matters — a ribbon has no cross-section, never occludes itself, and collapses to nothing
 * wherever the curve happens to point at the viewer.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class TidalDisruptionWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 256.0D * 256.0D;
    /** Quads along the stream. It wraps more than a turn and a half, so it needs them. */
    private static final int SEGMENTS = 150;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private TidalDisruptionWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        // The spell's own anchors plus anything another mod asked for through the API. The
        // renderer does not distinguish them, which is the point of the source interface.
        List<TidalDisruptionSource> events =
                new java.util.ArrayList<>(SpellLightEmitter.collectTidalDisruptions());
        events.addAll(FxRegistry.tidalDisruptions());
        if (events.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.strand();
        if (minecraft.level == null || shader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlStateGuard state = GlStateGuard.capture();
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

    private static void drawStreams(List<TidalDisruptionSource> events, Vec3 camera,
                                    float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (TidalDisruptionSource entity : events) {
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

            int alpha = (int) Math.max(0.0F, Math.min(255.0F, brightness * 235.0F));
            // Straight to the shared shape maths rather than through a method on the source. The
            // renderer works the same for a spell anchor and an API instance because both hand over
            // the same params, and routing through the implementation would undo that.
            TidalDisruptionParams params = entity.shapeParams();
            vertices += CurveTube.emit(builder, camera, SEGMENTS, 0.0D, 1.0D,
                    fraction -> TidalDisruptionShape.streamPoint(params, centre, age, fraction),
                    fraction -> TidalDisruptionShape.streamWidth(params, fraction),
                    CurveTube.MODE_DEBRIS, 0.0F,
                    Math.min(1.0F, brightness * 0.16F), alpha);
        }
        draw(builder, shader, vertices);
    }

    /**
     * Begin the buffer in the format the tubes actually write.
     *
     * <p>POSITION_TEX_COLOR_NORMAL, not POSITION_TEX_COLOR. TubeMeshBuilder emits a normal per
     * vertex and the strand shader declares one; beginning in the shorter format reinterprets
     * the vertex data against the wrong stride, which no compiler can catch and which shows up
     * as garbage geometry rather than as an error.</p>
     */
    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
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
}

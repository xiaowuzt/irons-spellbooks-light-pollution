package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.api.MagnetarParams;
import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.MagnetarShape;
import com.gang.lightpollution.fx.MagnetarSource;
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
 * Draws a magnetar's magnetosphere as the closed dipole loops it actually is.
 *
 * <p>Loop shapes are sampled from the entity, so the field a player sees is the field that
 * shocks them — the gaps between loops are real gaps, which is the whole mechanic.</p>
 *
 * <p>Real tube geometry rather than camera-facing ribbons. The ribbons were what made these
 * look like flat images pasted over the world: no cross-section, no self-occlusion, and they
 * vanished wherever a loop happened to point at the viewer.</p>
 *
 * <p>Every loop goes into one buffer, so the magnetosphere is a single draw call.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MagnetarWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 256.0D * 256.0D;
    /**
     * Quads along one field line.
     *
     * <p>A dipole loop is a tight curve near the poles, so it needs subdividing to stay
     * smooth. These are rings of a closed tube now rather than segments of a flat ribbon, so
     * there is no shared-edge problem to get wrong — the geometry is continuous by
     * construction and the shading comes from a real normal.</p>
     */
    private static final int RIBBON_SEGMENTS = 72;
    /** Radius of a field line's tube, in blocks. Thin — these are filaments. */
    private static final double FIELD_RADIUS = 0.22D;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private MagnetarWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        // The spell's own anchors plus anything another mod asked for through the API. The
        // renderer does not distinguish them, which is the point of the source interface.
        List<MagnetarSource> stars =
                new java.util.ArrayList<>(SpellLightEmitter.collectMagnetars());
        stars.addAll(FxRegistry.magnetars());
        if (stars.isEmpty()) {
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
            drawField(stars, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawField(List<MagnetarSource> stars, Vec3 camera,
                                  float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (MagnetarSource entity : stars) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float woundFraction = entity.wound(partialTick);
            // Straight to the shared shape maths rather than through a method on the source, so a
            // spell anchor and an API instance go down the same path.
            MagnetarParams params = entity.shapeParams();
            // The neutron star. Without it the loops encircle nothing and the middle of the
            // effect is empty, which is what the field lines are supposed to be anchored to.
            EffectCore.add(centre, MagnetarShape.STAR_RADIUS,
                    0.86F, 0.80F, 1.00F, brightness * (1.0F + woundFraction * 1.4F));

            int alpha = (int) Math.max(0.0F, Math.min(255.0F, brightness * 235.0F));
            for (int line = 0; line < MagnetarShape.FIELD_LINES; ++line) {
                final int index = line;
                vertices += CurveTube.emit(builder, camera, RIBBON_SEGMENTS, 0.0D, 1.0D,
                        along -> MagnetarShape.fieldPoint(params, centre, index, along, woundFraction),
                        // Thicker at the poles where the field crowds, thinner at the bulge.
                        along -> FIELD_RADIUS
                                * (0.55D + 0.45D * Math.abs(Math.cos(along * Math.PI))),
                        CurveTube.MODE_FIELD, woundFraction,
                        // Low enough that the violet-to-white ramp survives. At 0.5 the
                        // shader's intensity reached 3.6, which multiplied every channel past
                        // full and clipped the whole ramp to white — the colour was being
                        // computed correctly and then thrown away by the exposure.
                        Math.min(1.0F, brightness * 0.13F), alpha);
            }
        }
        draw(builder, shader, vertices);
    }

    /**
     * Begin the buffer in the format the tubes actually write.
     *
     * <p>POSITION_TEX_COLOR_NORMAL, not POSITION_TEX_COLOR. TubeMeshBuilder emits a normal per
     * vertex and the strand shader declares one; beginning in the shorter format leaves the
     * vertex data reinterpreted against the wrong stride, which no compiler can catch and which
     * shows up as garbage geometry rather than as an error.</p>
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

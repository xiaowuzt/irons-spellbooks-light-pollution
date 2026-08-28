package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.MicroquasarEntity;
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
 * Draws SS 433's twin jets as the corkscrew the precessing disk actually leaves, in real tube
 * geometry rather than camera-facing ribbons.
 *
 * <p>The helix is sampled from the entity, so the shape drawn and the shape that deals
 * damage are the same function — a target under a visible knot is a target the sweep
 * will hit.</p>
 *
 * <p>Both jets go into one buffer, so the whole effect is a single draw call.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class MicroquasarWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 256.0D * 256.0D;
    /**
     * Radius of a jet's tube, in blocks.
     *
     * <p>Narrow. The jets of this object are famously collimated, and a fat tube would read as a
     * pipe rather than a beam.</p>
     */
    private static final double JET_RADIUS = 0.30D;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private MicroquasarWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<MicroquasarEntity> jets = SpellLightEmitter.collectMicroquasars();
        if (jets.isEmpty()) {
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
            drawJets(jets, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawJets(List<MicroquasarEntity> jets, Vec3 camera,
                                 float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (MicroquasarEntity entity : jets) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            // The accretion disk the jets are launched from. Without it they come out of
            // nothing, which is exactly how it looked.
            EffectCore.add(centre, 2.6D, 1.00F, 0.90F, 0.72F, brightness * 1.6F);

            int alpha = (int) Math.max(0.0F, Math.min(255.0F, brightness * 235.0F));
            for (int side = 0; side < 2; ++side) {
                final boolean forward = side == 0;
                // aux says which jet this is, and the shader turns that into the Doppler
                // asymmetry: approaching is blue and bright, receding red and dim. It has to be
                // per jet rather than per point because a tube carries one colour for the whole
                // emit, and splitting the tube to vary it would put a seam at every split.
                vertices += CurveTube.emit(builder, camera,
                        MicroquasarEntity.BULLETS_PER_JET * 3, 0.004D, 1.0D,
                        fraction -> entity.helixPoint(centre, age, forward, fraction),
                        fraction -> JET_RADIUS * (1.0D - fraction * 0.45D),
                        CurveTube.MODE_JET, forward ? 1.0F : 0.0F,
                        Math.min(1.0F, brightness * 0.17F), alpha);
            }
        }
        draw(builder, shader, vertices);
    }

    /**
     * Begin the buffer in the format the tubes actually write.
     *
     * <p>POSITION_TEX_COLOR_NORMAL, because TubeMeshBuilder writes a normal per vertex and the
     * strand shader declares one. The shorter format reinterprets the data against the wrong
     * stride, which no compiler catches and which shows up as garbage geometry.</p>
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

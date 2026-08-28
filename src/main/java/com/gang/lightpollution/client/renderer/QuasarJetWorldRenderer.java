package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.QuasarJetEntity;
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
 * Draws a quasar jet: a faint collimated channel, bright knots racing along it, and the
 * terminal lobe where it stops.
 *
 * <p>Two passes, distinguished by the sign of {@code ColorModulator.a}: the channel, then
 * the knots and lobe as discs. Keeping the channel dim is deliberate rather than a
 * brightness choice — only the knots do damage, and a bright continuous beam would tell the
 * player the opposite.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class QuasarJetWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 288.0D * 288.0D;
    /** The channel is straight, so it barely needs subdividing — only enough for the ramp. */
    private static final int CHANNEL_SEGMENTS = 24;
    /** Radius of a knot's disc, in blocks. */
    private static final double KNOT_RADIUS = 2.4D;

    /** Synchrotron emission from an ultra-relativistic jet: blue-white. */
    private static final float BEAM_R = 0.66F;
    private static final float BEAM_G = 0.82F;
    private static final float BEAM_B = 1.00F;
    /** The hotspot, where the beam is thermalised against the surrounding medium. */
    private static final float LOBE_R = 1.00F;
    private static final float LOBE_G = 0.74F;
    private static final float LOBE_B = 0.52F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private QuasarJetWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<QuasarJetEntity> jets = SpellLightEmitter.collectQuasarJets();
        if (jets.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.quasarBeam();
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
            drawChannels(jets, camera, partialTick, shader);
            drawBodies(jets, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawChannels(List<QuasarJetEntity> jets, Vec3 camera,
                                     float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;
        for (QuasarJetEntity entity : jets) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            Vec3 direction = entity.direction();
            float launched = entity.launched(partialTick);
            // The nucleus. The jet has to be coming out of something.
            EffectCore.add(centre, 3.0D, 0.92F, 0.95F, 1.00F, brightness * 2.1F);
            double reach = QuasarJetEntity.JET_LENGTH * launched;
            int colour = CurveRibbon.pack(BEAM_R, BEAM_G, BEAM_B, brightness * 0.9F);

            vertices += CurveRibbon.emit(builder, camera, CHANNEL_SEGMENTS, 0.0D, 1.0D,
                    fraction -> centre.add(direction.scale(reach * fraction)),
                    // Flares a little with distance, as the confining pressure drops.
                    fraction -> QuasarJetEntity.JET_HALF_WIDTH * (1.0D + fraction * 0.5D),
                    fraction -> colour);
        }
        draw(builder, shader, vertices, 1.0F);
    }

    /** The knots and the terminal lobe, both as camera-facing discs. */
    private static void drawBodies(List<QuasarJetEntity> jets, Vec3 camera,
                                   float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;
        for (QuasarJetEntity entity : jets) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);

            for (int knot = 0; knot < QuasarJetEntity.KNOT_COUNT; ++knot) {
                Vec3 at = entity.knotPosition(centre, knot, age);
                if (at == null) {
                    continue;
                }
                double progress = entity.knotProgress(knot, age);
                // Knots dim as they go, as the emitting material expands and cools.
                float shade = (float) (1.0D - progress * 0.45D);
                vertices += disc(builder, camera, at, KNOT_RADIUS,
                        CurveRibbon.pack(BEAM_R, BEAM_G, BEAM_B,
                                brightness * shade * 0.95F));
            }

            if (entity.launched(partialTick) > 0.98F) {
                vertices += disc(builder, camera, entity.lobeCentre(centre),
                        QuasarJetEntity.LOBE_RADIUS,
                        CurveRibbon.pack(LOBE_R, LOBE_G, LOBE_B, brightness * 0.55F));
            }
        }
        // Negative alpha switches the shader to its disc branch.
        draw(builder, shader, vertices, -1.0F);
    }

    /** A camera-facing square carrying a centred unit disc in its UVs. */
    private static int disc(BufferBuilder builder, Vec3 camera, Vec3 at, double radius,
                            int colour) {
        Vec3 toCamera = camera.subtract(at);
        if (toCamera.lengthSqr() < 1.0e-8D) {
            return 0;
        }
        Vec3 forward = toCamera.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0e-8D) {
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        right = right.normalize().scale(radius);
        Vec3 up = right.cross(forward).normalize().scale(radius);

        float cx = (float) (at.x - camera.x);
        float cy = (float) (at.y - camera.y);
        float cz = (float) (at.z - camera.z);
        emit(builder, cx, cy, cz, right, up, -1, -1, 0.0F, 0.0F, colour);
        emit(builder, cx, cy, cz, right, up, 1, -1, 1.0F, 0.0F, colour);
        emit(builder, cx, cy, cz, right, up, 1, 1, 1.0F, 1.0F, colour);
        emit(builder, cx, cy, cz, right, up, -1, 1, 0.0F, 1.0F, colour);
        return 4;
    }

    private static void emit(BufferBuilder builder, float cx, float cy, float cz,
                             Vec3 right, Vec3 up, int sx, int sy,
                             float u, float v, int colour) {
        builder.vertex(cx + (float) (right.x * sx + up.x * sy),
                        cy + (float) (right.y * sx + up.y * sy),
                        cz + (float) (right.z * sx + up.z * sy))
                .uv(u, v)
                .color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF,
                        colour & 0xFF, (colour >>> 24) & 0xFF)
                .endVertex();
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices,
                             float modulatorAlpha) {
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
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, modulatorAlpha);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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

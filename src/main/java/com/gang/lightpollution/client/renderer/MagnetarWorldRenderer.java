package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.MagnetarEntity;
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
 * Draws a magnetar's magnetosphere as the closed dipole loops it actually is.
 *
 * <p>Loop shapes are sampled from the entity, so the field a player sees is the field that
 * shocks them — the gaps between loops are real gaps, which is the whole mechanic.</p>
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
     * smooth. Same reasoning as the microquasar's helix, and the same seam fix: the edge
     * vectors are computed per point and shared between neighbouring quads.</p>
     */
    private static final int RIBBON_SEGMENTS = 72;
    /** Half-width of a field line's ribbon, in blocks. Thin — these are filaments. */
    private static final float FIELD_HALF_WIDTH = 0.42F;

    /** The field at rest: cold violet, the colour of a magnetosphere not yet stressed. */
    private static final float CALM_R = 0.52F;
    private static final float CALM_G = 0.38F;
    private static final float CALM_B = 1.00F;
    /** Fully wound, about to let go: driven toward white-hot. */
    private static final float LOADED_R = 1.00F;
    private static final float LOADED_G = 0.86F;
    private static final float LOADED_B = 0.62F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private MagnetarWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<MagnetarEntity> stars = SpellLightEmitter.collectMagnetars();
        if (stars.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.magnetarField();
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

    private static void drawField(List<MagnetarEntity> stars, Vec3 camera,
                                  float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (MagnetarEntity entity : stars) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float woundFraction = entity.wound(partialTick);
            int colour = fieldColour(brightness, woundFraction);
            // The neutron star. Without it the loops encircle nothing and the middle of the
            // effect is empty, which is what the field lines are supposed to be anchored to.
            EffectCore.add(centre, 1.9D + woundFraction * 0.7D,
                    0.86F, 0.80F, 1.00F, brightness * (1.0F + woundFraction * 1.4F));

            for (int line = 0; line < MagnetarEntity.FIELD_LINES; ++line) {
                vertices += ribbon(builder, entity, camera, centre, line,
                        woundFraction, colour);
            }
        }
        draw(builder, shader, vertices);
    }

    /**
     * Colour of the field, from how far it has wound up.
     *
     * <p>One ramp for the whole magnetosphere rather than per line, because the stress is
     * global: the star is winding its entire field, and having some loops calm while others
     * are loaded would misrepresent what is about to happen.</p>
     */
    private static int fieldColour(float brightness, float wound) {
        float t = Math.max(0.0F, Math.min(1.0F, wound));
        float r = CALM_R + (LOADED_R - CALM_R) * t;
        float g = CALM_G + (LOADED_G - CALM_G) * t;
        float b = CALM_B + (LOADED_B - CALM_B) * t;
        int alpha = (int) Math.max(0.0F, Math.min(255.0F, brightness * 200.0F));
        return (alpha << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    /**
     * One field line, as a continuous ribbon along the dipole loop.
     *
     * <p>Edge vectors per point, shared between neighbouring quads, so the strip is one
     * surface. Computing them per segment gives every quad its own orientation and leaves
     * visible notches between them — the mistake the microquasar's jets made first.</p>
     */
    private static int ribbon(BufferBuilder builder, MagnetarEntity entity, Vec3 camera,
                              Vec3 centre, int line, float woundFraction, int colour) {
        Vec3[] points = new Vec3[RIBBON_SEGMENTS + 1];
        Vec3[] across = new Vec3[RIBBON_SEGMENTS + 1];
        for (int i = 0; i <= RIBBON_SEGMENTS; ++i) {
            // Skirting both exact poles: the dipole relation degenerates there, where the
            // loop closes to zero radius and the frame has no well-defined direction.
            double along = 0.02D + 0.96D * (i / (double) RIBBON_SEGMENTS);
            points[i] = entity.fieldPoint(centre, line, along, woundFraction);
        }
        for (int i = 0; i <= RIBBON_SEGMENTS; ++i) {
            Vec3 before = points[Math.max(0, i - 1)];
            Vec3 after = points[Math.min(RIBBON_SEGMENTS, i + 1)];
            Vec3 tangent = after.subtract(before);
            if (tangent.lengthSqr() < 1.0e-9D) {
                tangent = new Vec3(0.0D, 1.0D, 0.0D);
            }
            Vec3 toCamera = camera.subtract(points[i]);
            Vec3 edge = tangent.cross(toCamera);
            if (edge.lengthSqr() < 1.0e-9D) {
                edge = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            across[i] = edge.normalize().scale(FIELD_HALF_WIDTH);
        }

        int emitted = 0;
        for (int i = 0; i < RIBBON_SEGMENTS; ++i) {
            float alongFrom = i / (float) RIBBON_SEGMENTS;
            float alongTo = (i + 1) / (float) RIBBON_SEGMENTS;
            emitted += quad(builder, camera, points[i], across[i], alongFrom,
                    points[i + 1], across[i + 1], alongTo, colour);
        }
        return emitted;
    }

    /**
     * One quad of the ribbon, using the shared half-width vectors at each end.
     */
    private static int quad(BufferBuilder builder, Vec3 camera,
                            Vec3 from, Vec3 fromAcross, float alongFrom,
                            Vec3 to, Vec3 toAcross, float alongTo, int colour) {
        float fx = (float) (from.x - camera.x);
        float fy = (float) (from.y - camera.y);
        float fz = (float) (from.z - camera.z);
        float tx = (float) (to.x - camera.x);
        float ty = (float) (to.y - camera.y);
        float tz = (float) (to.z - camera.z);
        float fax = (float) fromAcross.x;
        float fay = (float) fromAcross.y;
        float faz = (float) fromAcross.z;
        float tax = (float) toAcross.x;
        float tay = (float) toAcross.y;
        float taz = (float) toAcross.z;

        vertex(builder, fx - fax, fy - fay, fz - faz, alongFrom, 0.0F, colour);
        vertex(builder, tx - tax, ty - tay, tz - taz, alongTo, 0.0F, colour);
        vertex(builder, tx + tax, ty + tay, tz + taz, alongTo, 1.0F, colour);
        vertex(builder, fx + fax, fy + fay, fz + faz, alongFrom, 1.0F, colour);
        return 4;
    }

    private static int channel(float value) {
        return (int) Math.max(0.0F, Math.min(255.0F, value * 255.0F));
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
    }

    private static void vertex(BufferBuilder builder,
                               float x, float y, float z, float u, float v, int color) {
        builder.vertex(x, y, z).uv(u, v)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
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

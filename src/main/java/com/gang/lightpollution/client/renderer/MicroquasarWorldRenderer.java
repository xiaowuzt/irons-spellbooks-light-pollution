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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * Draws SS 433's twin jets as the corkscrew the precessing disk actually leaves.
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
     * Quads along one jet.
     *
     * <p>This many is for the curve, not for the seams: the helix turns one and a half
     * times over its length, and a coarse strip cuts the corners visibly. The seams are
     * dealt with by sharing the edge vectors between neighbouring quads, which is a
     * separate matter — chopping the strip finer would not have fixed those on its own.</p>
     */
    private static final int RIBBON_SEGMENTS = 120;
    /**
     * Half-width of the ribbon at the disk, in blocks.
     *
     * <p>Narrow. The jets of this object are famously collimated, and a wide band would
     * read as a flat sheet rather than a beam — the shader's cross-section profile does the
     * work of making it look thicker than this.</p>
     */
    private static final float JET_HALF_WIDTH = 0.55F;

    /**
     * Rest colour of the jet plasma, before beaming.
     *
     * <p>Synchrotron emission from a relativistic jet is intrinsically blue-white. The
     * red on the receding side below is not a different material — it is the same plasma
     * Doppler-shifted, which is the point.</p>
     */
    private static final float REST_R = 0.62F;
    private static final float REST_G = 0.78F;
    private static final float REST_B = 1.00F;

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
        ShaderInstance shader = ConstellationShaders.microquasarJet();
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

            for (int side = 0; side < 2; ++side) {
                vertices += ribbon(builder, entity, camera, centre, age,
                        side == 0, brightness);
            }
        }
        draw(builder, shader, vertices);
    }

    /**
     * One jet, as a continuous ribbon rather than a run of independent quads.
     *
     * <p>The half-width vector is computed per point and shared by the two quads that meet
     * there, which is the difference between a ribbon and a row of tiles. Computing it per
     * segment instead gave each quad its own orientation and width, so consecutive quads
     * did not share an edge: from a distance it passed for a beam, but up close every
     * segment showed as its own square with notches between them. That sharing is what
     * fixed it — the strip is one continuous surface now regardless of how short the
     * segments are.</p>
     */
    private static int ribbon(BufferBuilder builder, MicroquasarEntity entity, Vec3 camera,
                              Vec3 centre, float age, boolean forward, float brightness) {
        Vec3[] points = new Vec3[RIBBON_SEGMENTS + 1];
        Vec3[] across = new Vec3[RIBBON_SEGMENTS + 1];
        for (int i = 0; i <= RIBBON_SEGMENTS; ++i) {
            double fraction = i / (double) RIBBON_SEGMENTS;
            points[i] = entity.helixPoint(centre, age, forward, fraction);
        }
        for (int i = 0; i <= RIBBON_SEGMENTS; ++i) {
            // Tangent from the neighbours, so the frame turns smoothly with the curve
            // instead of stepping at each joint.
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
            double taper = 1.0D - (i / (double) RIBBON_SEGMENTS) * 0.5D;
            across[i] = edge.normalize().scale(JET_HALF_WIDTH * taper);
        }

        int emitted = 0;
        for (int i = 0; i < RIBBON_SEGMENTS; ++i) {
            float alongFrom = i / (float) RIBBON_SEGMENTS;
            float alongTo = (i + 1) / (float) RIBBON_SEGMENTS;
            Vec3 direction = points[i + 1].subtract(points[i]);
            Vec3 mid = points[i].add(points[i + 1]).scale(0.5D);
            int colour = beamedColour(direction, camera.subtract(mid), brightness,
                    (alongFrom + alongTo) * 0.5F);

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

    /**
     * Relativistic beaming, from the angle between this segment and the viewer.
     *
     * <p>The whole reason SS 433's jets look like a mismatched pair rather than a
     * symmetric bowtie. Emission from a source moving at beta toward the observer is
     * boosted by the Doppler factor cubed,
     *
     * <pre>   D = sqrt(1 - beta^2) / (1 - beta cos(theta))   ,   boost = D^3</pre>
     *
     * <p>so the jet coming at you is brighter and blue-shifted while the one going away
     * is dimmer and red-shifted, from one speed and one angle. Gargantua deliberately
     * left beaming out of its disk; this is the first effect here that needs it, because
     * without it the two jets are identical and the object stops being recognisable.</p>
     */
    private static int beamedColour(Vec3 direction, Vec3 toCamera, float brightness,
                                   float along) {
        double beta = MicroquasarEntity.JET_BETA;
        Vec3 motion = direction.normalize();
        Vec3 view = toCamera.lengthSqr() < 1.0e-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : toCamera.normalize();
        double cosTheta = motion.dot(view);
        double doppler = Math.sqrt(1.0D - beta * beta) / (1.0D - beta * cosTheta);
        double boost = doppler * doppler * doppler;

        // Boost runs about 0.47 to 2.1 at beta = 0.26. Centred on 1 so the mean stays
        // near the rest brightness, then clamped so the receding jet stays visible
        // rather than going black.
        float gain = (float) Math.max(0.35D, Math.min(2.2D, boost));

        // The shift, expressed as the colour it actually produces: approaching plasma
        // moves toward blue-white, receding toward red. Driven by the same Doppler
        // factor, not by a separate hand-picked pair of colours.
        float shift = (float) Math.max(-1.0D, Math.min(1.0D, (doppler - 1.0D) * 3.4D));
        float r = REST_R + Math.max(0.0F, -shift) * 0.38F;
        float g = REST_G - Math.abs(shift) * 0.22F;
        float b = REST_B - Math.max(0.0F, -shift) * 0.55F;
        if (shift > 0.0F) {
            r -= shift * 0.30F;
            g -= shift * 0.08F;
        }

        float fade = brightness * gain * (1.0F - along * 0.25F);
        int alpha = (int) Math.max(0.0F, Math.min(255.0F, fade * 190.0F));
        return (alpha << 24)
                | (channel(r) << 16)
                | (channel(g) << 8)
                | channel(b);
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

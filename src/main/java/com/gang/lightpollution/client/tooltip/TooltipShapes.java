package com.gang.lightpollution.client.tooltip;

import com.gang.lightpollution.client.ConstellationShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The two large shapes a tooltip can be threaded through: a tilted ring and a rotating sigil.
 *
 * <p>Ported from ArcaneVortex's {@code renderRingHalf} and its triangular border, credited in
 * {@code CREDITS.txt}.</p>
 *
 * <p>The ring's band carries a star field, which is the point of the shape rather than a flourish:
 * ArcaneVortex draws its ring twice and puts the inner pass through its cosmic render type, so what
 * you see is an opening rather than a drawn circle. That surface is reproduced here from this mod's
 * own volumetric field instead of by porting their cosmic stack.</p>
 *
 * <p>The sigil is flat colour. Its original went through {@code volumetric_shader} plus that same
 * cosmic stack — to the point of abandoning the draw outright when the cosmic shader is missing —
 * but there the shader only supplied surface material, and the idea is the counter-rotation.</p>
 */
public final class TooltipShapes {
    /** Segments around the ring. */
    private static final int RING_SEGMENTS = 64;
    /**
     * How far the ring is tilted away from face-on, 0 to 1.
     *
     * <p>0.9 squashes it to 0.19 of its width, which is nearly edge-on. That extreme is the point:
     * a ring seen face-on cannot look like it passes behind anything.</p>
     */
    private static final float RING_TILT = 0.9F;
    /** Degrees the ring's plane is rolled, so it crosses the panel on a diagonal. */
    private static final float RING_ROLL = -35.0F;
    /** Degrees per second the ring turns about its own axis. */
    private static final float RING_SPIN = 14.0F;

    /** Corners of the sigil, in degrees. Point-up, as a triangle drawn on paper. */
    private static final float[] SIGIL_CORNERS = {-90.0F, 30.0F, 150.0F};
    /** Sigil span, as a multiple of the panel's shorter side. */
    private static final float SIGIL_SCALE = 2.6F;
    /** Sigil band thickness, in pixels. */
    private static final float SIGIL_THICKNESS = 70.0F;
    /** Degrees per second the sigil turns. */
    private static final float SIGIL_SPIN = 20.0F;

    private TooltipShapes() {
    }

    /** Which side of the ring to draw, relative to the panel. */
    public enum Half {
        BEHIND, IN_FRONT
    }

    /**
     * One half of a tilted ring around the panel, with a star field inside the band.
     *
     * <p>The trick worth having: the caller draws the far half, then the panel, then the near half,
     * and the ring appears to pass <em>through</em> the tooltip. Nothing here is really 3D — a
     * pseudo-depth is computed per segment purely to decide which half a segment belongs to, and
     * the split is what sells the illusion.</p>
     *
     * <p>The band is not a coloured surface. ArcaneVortex draws its ring twice and puts the inner
     * pass through its cosmic render type, and that is the point of the shape: a flat ring reads as
     * a drawn circle, while a ring you can see stars through reads as an opening. Here the surface
     * comes from {@code ring_surface}, which samples the same volumetric field Gargantua's bent rays
     * look out at.</p>
     *
     * <p>Drawn immediately rather than into the GUI batch, because it needs its own shader. The
     * flush first is what keeps the order right: anything already queued goes down before this
     * does.</p>
     */
    public static void ringHalf(GuiGraphics graphics, float centreX, float centreY,
                                float outerRadius, float innerRadius, Half half,
                                int accent, float alpha, float seconds) {
        if (outerRadius <= innerRadius || alpha <= 0.0F) {
            return;
        }
        ShaderInstance shader = ConstellationShaders.ringSurface();
        if (shader == null) {
            return;
        }
        float squash = 1.0F - RING_TILT * 0.9F;
        int packedAlpha = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        int red = (accent >> 16) & 0xFF;
        int green = (accent >> 8) & 0xFF;
        int blue = accent & 0xFF;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(centreX, centreY, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(RING_ROLL));
            Matrix4f matrix = pose.last().pose();

            graphics.flush();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            // Turning the sample angle rather than the pose: rolling the whole ring would carry the
            // front/back split round with it, and the split has to stay fixed to the screen or the
            // ring would appear to swap sides twice a revolution.
            float turn = (seconds * RING_SPIN) * Mth.DEG_TO_RAD;
            int emitted = 0;

            for (int segment = 0; segment < RING_SEGMENTS; segment++) {
                float t0 = segment / (float) RING_SEGMENTS;
                float t1 = (segment + 1) / (float) RING_SEGMENTS;
                float a0 = turn + Mth.TWO_PI * t0;
                float a1 = turn + Mth.TWO_PI * t1;
                // Which half this segment is in. The far half of a tilted ring is its upper part on
                // screen, so the sign of the squashed sine is the whole test.
                float side = (Mth.sin(a0) + Mth.sin(a1)) * 0.5F;
                if ((side <= 0.0F) != (half == Half.BEHIND)) {
                    continue;
                }
                // The u coordinate is the unturned fraction, so a star stays on its own part of the
                // band as the ring turns rather than streaming along it.
                emit(builder, matrix, Mth.cos(a0) * outerRadius,
                        Mth.sin(a0) * outerRadius * squash, t0, 0.0F, red, green, blue, packedAlpha);
                emit(builder, matrix, Mth.cos(a0) * innerRadius,
                        Mth.sin(a0) * innerRadius * squash, t0, 1.0F, red, green, blue, packedAlpha);
                emit(builder, matrix, Mth.cos(a1) * innerRadius,
                        Mth.sin(a1) * innerRadius * squash, t1, 1.0F, red, green, blue, packedAlpha);
                emit(builder, matrix, Mth.cos(a1) * outerRadius,
                        Mth.sin(a1) * outerRadius * squash, t1, 0.0F, red, green, blue, packedAlpha);
                emitted += 4;
            }

            if (emitted <= 0) {
                BufferBuilder.RenderedBuffer discarded = builder.end();
                if (discarded != null) {
                    discarded.release();
                }
                return;
            }

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(() -> shader);
            // Own uniform rather than GameTime: that one wraps every 24000 ticks, and the whole
            // field would reshuffle in one frame when it did.
            Uniform drift = shader.getUniform("Drift");
            if (drift != null) {
                drift.set(seconds * 0.02F);
            }
            BufferUploader.drawWithShader(builder.end());
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        } finally {
            pose.popPose();
        }
    }

    private static void emit(BufferBuilder builder, Matrix4f pose, float x, float y,
                             float u, float v, int red, int green, int blue, int alpha) {
        builder.vertex(pose, x, y, 0.0F)
                .uv(u, v)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    /**
     * Two counter-rotating triangular bands behind the panel.
     *
     * <p>Two triangles half a turn apart make a six-pointed figure, and turning them opposite ways
     * means the figure never settles — the points slide past each other instead of holding a shape.
     * Three nested shells per triangle at slightly different scales give the band an edge without
     * needing an outline pass.</p>
     */
    public static void sigil(GuiGraphics graphics, int left, int top, int right, int bottom,
                             int accent, float alpha, float seconds) {
        float centreX = (left + right) * 0.5F;
        float centreY = (top + bottom) * 0.5F;
        float span = Math.min(right - left, bottom - top) * SIGIL_SCALE;
        if (span <= 0.0F || alpha <= 0.0F) {
            return;
        }
        float turn = (seconds * SIGIL_SPIN) % 360.0F;

        // Opposite directions. Turning both the same way reads as one rigid figure rotating, which
        // loses the interference that makes this worth drawing.
        band(graphics, centreX, centreY, span, turn, accent, alpha, seconds);
        band(graphics, centreX, centreY, span, 180.0F - turn, accent, alpha * 0.75F, seconds);
    }

    private static void band(GuiGraphics graphics, float centreX, float centreY, float span,
                             float turn, int accent, float alpha, float seconds) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(centreX, centreY, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(turn));
            // Outer and inner shells dimmer than the middle one, which is what gives the band an
            // edge without a separate outline pass.
            triangle(graphics, span * 1.05F, SIGIL_THICKNESS * 1.05F, accent, alpha * 0.45F);
            triangle(graphics, span * 0.95F, SIGIL_THICKNESS * 0.95F, accent, alpha * 0.45F);
            triangle(graphics, span, SIGIL_THICKNESS, accent, alpha);
        } finally {
            pose.popPose();
        }
    }

    /** One triangular annulus: three quads, each spanning one edge between outer and inner. */
    private static void triangle(GuiGraphics graphics, float span, float thickness,
                                 int accent, float alpha) {
        float outer = span * 0.5F;
        float inner = Math.max(outer - thickness, outer * 0.3F);
        int colour = (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24)
                | TooltipElements.shade(accent, 1.0F);
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();

        for (int corner = 0; corner < SIGIL_CORNERS.length; corner++) {
            float a0 = SIGIL_CORNERS[corner] * Mth.DEG_TO_RAD;
            float a1 = SIGIL_CORNERS[(corner + 1) % SIGIL_CORNERS.length] * Mth.DEG_TO_RAD;
            vertex(consumer, matrix, Mth.cos(a0) * outer, Mth.sin(a0) * outer, colour);
            vertex(consumer, matrix, Mth.cos(a0) * inner, Mth.sin(a0) * inner, colour);
            vertex(consumer, matrix, Mth.cos(a1) * inner, Mth.sin(a1) * inner, colour);
            vertex(consumer, matrix, Mth.cos(a1) * outer, Mth.sin(a1) * outer, colour);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, int argb) {
        consumer.vertex(pose, x, y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }
}

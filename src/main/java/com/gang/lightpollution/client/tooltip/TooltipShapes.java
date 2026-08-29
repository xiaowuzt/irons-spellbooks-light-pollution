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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
 * <p>The sigil's shells are two different materials, and that is the look. Its middle shell is
 * ArcaneVortex's {@code volumetric_shader}, ported as it stands — it needed no textures and no part
 * of their cosmic stack, only a time and the camera rotation. The two offset shells show the star
 * field. Drawn flat the figure is just two triangles, which is what it looked like before either
 * shader was here.</p>
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
    /**
     * Sigil span, as a multiple of the panel's diagonal.
     *
     * <p>Driven by the diagonal rather than the shorter side, which is what the original used. That
     * worked there because its tooltips were roughly square; these carry one very long stat line and
     * run fifteen times wider than tall, so the shorter side gave a star smaller than the box it was
     * supposed to sit behind.</p>
     */
    private static final float SIGIL_SPAN_RATIO = 0.9F;
    /** Span bounds in pixels. Raise the upper one for a bigger star; it is the only knob needed. */
    private static final float SIGIL_SPAN_MIN = 260.0F;
    private static final float SIGIL_SPAN_MAX = 900.0F;
    /**
     * Band thickness as a fraction of the outer radius.
     *
     * <p>Proportional, where the original used a flat 70 pixels. A fixed thickness only looks right
     * at one box size: against a small box it swallowed the whole triangle — at our proportions the
     * band came out at seventy percent of the radius and the sigil drew as two solid triangles.</p>
     */
    private static final float SIGIL_BAND = 0.13F;
    /** Degrees per second the sigil turns. */
    private static final float SIGIL_SPIN = 20.0F;
    /** Scale of the two star-field shells either side of the plasma one. */
    private static final float SIGIL_RIM_OUT = 1.05F;
    private static final float SIGIL_RIM_IN = 0.95F;

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
            setUniform(shader, "Drift", seconds * 0.02F);
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
     * A six-pointed star behind the panel, turning slowly as one body.
     *
     * <p>Two triangles half a turn apart. They rotate in the <em>same</em> direction, which is the
     * thing to get right: an earlier version turned them opposite ways, and a Star of David whose
     * halves counter-rotate never resolves into a star at all — the points slide through each other
     * and it reads as two triangles that happen to overlap.</p>
     *
     * <p>Each triangle is three shells. The middle one is the plasma march; the two either side of
     * it are the star field, so the bright band ends up sandwiched between two sparkling rims. That
     * sandwich is the look, and it is why the outer and inner shells cannot be dropped.</p>
     */
    public static void sigil(GuiGraphics graphics, int left, int top, int right, int bottom,
                             int accent, float alpha, float seconds) {
        float centreX = (left + right) * 0.5F;
        float centreY = (top + bottom) * 0.5F;
        float width = right - left;
        float height = bottom - top;
        float span = Mth.clamp(
                (float) Math.sqrt(width * width + height * height) * SIGIL_SPAN_RATIO,
                SIGIL_SPAN_MIN, SIGIL_SPAN_MAX);
        if (alpha <= 0.0F) {
            return;
        }
        float turn = (seconds * SIGIL_SPIN) % 360.0F;

        // Same direction, offset by half a turn. Both halves of a Star of David have to travel
        // together or it is not a star.
        band(graphics, centreX, centreY, span, turn, accent, alpha, seconds);
        band(graphics, centreX, centreY, span, turn + 180.0F, accent, alpha, seconds);
    }

    private static void band(GuiGraphics graphics, float centreX, float centreY, float span,
                             float turn, int accent, float alpha, float seconds) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(centreX, centreY, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(turn));
            // Rims first, plasma over them, matching the original's order.
            triangle(graphics, span * SIGIL_RIM_OUT, accent, alpha, Surface.STARS, seconds);
            triangle(graphics, span * SIGIL_RIM_IN, accent, alpha, Surface.STARS, seconds);
            triangle(graphics, span, accent, alpha, Surface.PLASMA, seconds);
        } finally {
            pose.popPose();
        }
    }

    /** Which program shades a shell. */
    private enum Surface {
        STARS, PLASMA
    }

    /**
     * One triangular annulus: three quads, each spanning one edge between outer and inner.
     *
     * <p>UVs run along the perimeter on x and across the band on y, so both surfaces have the same
     * coordinate to work in as the ring does.</p>
     */
    private static void triangle(GuiGraphics graphics, float span, int accent, float alpha,
                                 Surface surface, float seconds) {
        ShaderInstance shader = surface == Surface.PLASMA
                ? ConstellationShaders.volumetric()
                : ConstellationShaders.ringSurface();
        if (shader == null || alpha <= 0.0F) {
            return;
        }
        float outer = span * 0.5F;
        float inner = outer * (1.0F - SIGIL_BAND);
        int packedAlpha = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        int red = (accent >> 16) & 0xFF;
        int green = (accent >> 8) & 0xFF;
        int blue = accent & 0xFF;

        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int corner = 0; corner < SIGIL_CORNERS.length; corner++) {
            float a0 = SIGIL_CORNERS[corner] * Mth.DEG_TO_RAD;
            float a1 = SIGIL_CORNERS[(corner + 1) % SIGIL_CORNERS.length] * Mth.DEG_TO_RAD;
            float u0 = corner / (float) SIGIL_CORNERS.length;
            float u1 = (corner + 1) / (float) SIGIL_CORNERS.length;
            emit(builder, matrix, Mth.cos(a0) * outer, Mth.sin(a0) * outer,
                    u0, 0.0F, red, green, blue, packedAlpha);
            emit(builder, matrix, Mth.cos(a0) * inner, Mth.sin(a0) * inner,
                    u0, 1.0F, red, green, blue, packedAlpha);
            emit(builder, matrix, Mth.cos(a1) * inner, Mth.sin(a1) * inner,
                    u1, 1.0F, red, green, blue, packedAlpha);
            emit(builder, matrix, Mth.cos(a1) * outer, Mth.sin(a1) * outer,
                    u1, 0.0F, red, green, blue, packedAlpha);
        }

        // Blend OFF, which is the whole reason the reference looks the way it does. Their two sigil
        // render types both use the "no_transparency" shard, so every shell is written opaque. That
        // is what makes the layering read: the rims cover 1.00 to 1.05 and 0.826 to 0.87 of the
        // radius, the plasma is drawn last and covers 0.87 to 1.00 outright, and the result is a
        // bright band with a thin rim either side. Blending them instead mixes all three into one
        // muddy translucent shape, which is exactly what this looked like before.
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        setUniform(shader, "Drift", seconds * (surface == Surface.PLASMA ? 0.04F : 0.02F));
        if (surface == Surface.PLASMA) {
            // The march direction is rotated by the camera, so the field sits in the world rather
            // than on the screen and turning your head moves through it.
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            setUniform(shader, "Yaw", camera.getYRot() * Mth.DEG_TO_RAD);
            setUniform(shader, "Pitch", -camera.getXRot() * Mth.DEG_TO_RAD);
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, int argb) {
        consumer.vertex(pose, x, y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }
}

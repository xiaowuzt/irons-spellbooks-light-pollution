package com.gang.lightpollution.client.tooltip;

import com.gang.lightpollution.client.RoundedRect;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The decorative pieces a tooltip frame is built from, each one drawn once and reusable.
 *
 * <p>Ported from the nine separate tooltip renderers in ArcaneVortex, credited in
 * {@code CREDITS.txt}. Those nine share a vocabulary and differ mostly in which pieces they use and
 * in palette, so they collapse to this handful of methods plus a recipe per style.</p>
 *
 * <p>Nothing here needs a custom shader or an imported texture. The elements that did — a textured
 * ring band needing a full framebuffer copy, and text rasterised through AWT against a font that is
 * not even present in the source mod — are deliberately absent.</p>
 *
 * <p>Colour comes from the spell's own accent rather than from the palettes those renderers used.
 * Their palettes belonged to specific weapons; here a tooltip should look like the spell it
 * describes.</p>
 */
public final class TooltipElements {
    /** Points in the particle field. */
    private static final int PARTICLES = 32;
    /** Side of one particle, in pixels. */
    private static final int PARTICLE_SIZE = 2;
    /** How far the field reaches, as a fraction of the panel. */
    private static final float PARTICLE_SPREAD = 0.4F;

    /** Side of a corner mark, in pixels. */
    private static final int MARK_SIZE = 8;
    /** Degrees per second a corner mark turns. */
    private static final float MARK_SPIN = 20.0F;

    /** Ghost copies orbiting behind the panel. */
    private static final int GHOSTS = 3;
    /** Radius of that orbit, in pixels. */
    private static final float GHOST_ORBIT = 20.0F;
    /** Seconds per revolution. */
    private static final float GHOST_PERIOD = 6.0F;

    private TooltipElements() {
    }

    /**
     * A drifting field of points over the panel.
     *
     * <p>Each point runs its own Lissajous figure — sine on x against cosine on y at a different
     * frequency — so the set never settles into a visible pattern the way a shared orbit would. The
     * per-point phase offsets are what stop them moving as one body.</p>
     *
     * <p>The cheapest thing here by a wide margin: no shader, no texture, thirty-two two-pixel
     * fills.</p>
     */
    public static void particles(GuiGraphics graphics, int left, int top, int right, int bottom,
                                 int accent, float seconds) {
        float centreX = (left + right) * 0.5F;
        float centreY = (top + bottom) * 0.5F;
        float width = right - left;
        float height = bottom - top;

        for (int index = 0; index < PARTICLES; index++) {
            float x = centreX + Mth.sin(seconds + index * 1.0F) * width * PARTICLE_SPREAD;
            float y = centreY + Mth.cos(seconds + index * 1.5F) * height * PARTICLE_SPREAD;
            float alpha = 0.3F + 0.33F * Mth.sin(seconds * 5.0F + index * 0.5F);
            if (alpha <= 0.0F) {
                continue;
            }
            int colour = (Math.min(255, (int) (alpha * 255.0F)) << 24)
                    | shade(accent, 0.55F + 0.45F * (index / (float) PARTICLES));
            graphics.fill((int) x, (int) y,
                    (int) x + PARTICLE_SIZE, (int) y + PARTICLE_SIZE, colour);
        }
    }

    /**
     * Four small marks at the corners, each turning about its own centre.
     *
     * <p>Adjacent corners turn opposite ways. Turning them all the same way reads as the whole frame
     * rotating, which it is not.</p>
     *
     * <p>Drawn as geometry rather than from a texture — the original used an eight-pixel rune sprite,
     * and a four-armed cross is both cheaper and on-theme here.</p>
     */
    public static void cornerMarks(GuiGraphics graphics, int left, int top, int right, int bottom,
                                   int accent, float seconds) {
        float turn = (seconds * MARK_SPIN) % 360.0F;
        int alpha = (int) (128.0 + 127.0 * Math.sin(seconds * 5.0));
        int colour = (Mth.clamp(alpha, 0, 255) << 24) | shade(accent, 1.0F);

        for (int corner = 0; corner < 4; corner++) {
            float x = (corner == 1 || corner == 2) ? right : left;
            float y = corner >= 2 ? bottom : top;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x, y, 0.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(corner % 2 == 0 ? turn : -turn));
                int arm = MARK_SIZE / 2;
                graphics.fill(-arm, 0, arm, 1, colour);
                graphics.fill(0, -arm, 1, arm, colour);
            } finally {
                pose.popPose();
            }
        }
    }

    /**
     * A sprite behind the panel, with ghost copies orbiting it.
     *
     * <p>Three ghosts at a third alpha spaced evenly around a small circle, then one solid copy in
     * the middle. The ghosts lag the eye rather than reading as separate images, which is what makes
     * the thing look like it is drifting rather than duplicated.</p>
     *
     * <p>The original blitted a weapon illustration. This takes any sprite, and the caller passes the
     * spell's own icon — a tooltip should not be advertising a different mod's sword.</p>
     */
    public static void backdrop(GuiGraphics graphics, ResourceLocation sprite,
                                int centreX, int centreY, int size, float alpha, float seconds) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int half = size / 2;
        float turn = (seconds / GHOST_PERIOD) * Mth.TWO_PI;

        for (int ghost = 0; ghost < GHOSTS; ghost++) {
            float angle = turn + ghost * Mth.TWO_PI / GHOSTS;
            int x = centreX - half + (int) (Mth.cos(angle) * GHOST_ORBIT);
            int y = centreY - half + (int) (Mth.sin(angle) * GHOST_ORBIT);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.3F);
            graphics.blit(sprite, x, y, 0.0F, 0.0F, size, size, size, size);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(sprite, centreX - half, centreY - half, 0.0F, 0.0F,
                size, size, size, size);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * A band of colour sliding across a strip, brightest in the middle.
     *
     * <p>The original drove this from a fragment shader sampling a greyscale mask, but the shader was
     * seventy-four lines that amounted to {@code position = uv.x * span - time * speed} and a
     * multiply by the mask's mean brightness. With the mask gone — it only ever restricted the
     * gradient to the text's silhouette — the same result is four vertices of interpolated colour,
     * so there is no shader here at all.</p>
     */
    public static void flowingBand(GuiGraphics graphics, int left, int top, int right, int bottom,
                                   int accent, float span, float speed, float alpha,
                                   float seconds) {
        if (right <= left || bottom <= top || alpha <= 0.0F) {
            return;
        }
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();
        // Subdivided across, because the band's brightness varies along x and a single quad can
        // only interpolate linearly between its two ends.
        int steps = Math.max(2, (right - left) / 4);
        for (int step = 0; step < steps; step++) {
            float t0 = step / (float) steps;
            float t1 = (step + 1) / (float) steps;
            int colour0 = bandColour(accent, t0, span, speed, alpha, seconds);
            int colour1 = bandColour(accent, t1, span, speed, alpha, seconds);
            float x0 = left + (right - left) * t0;
            float x1 = left + (right - left) * t1;
            vertex(consumer, pose, x0, top, colour0);
            vertex(consumer, pose, x0, bottom, colour0);
            vertex(consumer, pose, x1, bottom, colour1);
            vertex(consumer, pose, x1, top, colour1);
        }
    }

    private static int bandColour(int accent, float t, float span, float speed, float alpha,
                                  float seconds) {
        // One travelling hump. fract puts it back at the start rather than letting it run away.
        float position = Mth.frac(t * span - seconds * speed * 0.1F);
        float hump = Mth.sin(position * Mth.PI);
        float strength = Mth.clamp(hump * hump * alpha, 0.0F, 1.0F);
        return (Math.min(255, (int) (strength * 255.0F)) << 24) | shade(accent, 0.7F + hump * 0.3F);
    }

    /**
     * A rule that is transparent at both ends and solid in the middle.
     *
     * <p>Fading at both ends rather than one. A rule opaque at one edge and gone at the other reads
     * as an unfinished gradient instead of a divider.</p>
     */
    public static void separator(GuiGraphics graphics, int left, int y, int right, int accent) {
        int middle = (left + right) / 2;
        int edge = shade(accent, 0.9F);
        int core = 0xC8000000 | shade(accent, 1.0F);
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();

        vertex(consumer, pose, left, y, edge);
        vertex(consumer, pose, left, y + 2, edge);
        vertex(consumer, pose, middle, y + 2, core);
        vertex(consumer, pose, middle, y, core);

        vertex(consumer, pose, middle, y, core);
        vertex(consumer, pose, middle, y + 2, core);
        vertex(consumer, pose, right, y + 2, edge);
        vertex(consumer, pose, right, y, edge);
    }

    /** The rounded panel: a dark fill under a highlight that travels around the outline. */
    public static void panel(GuiGraphics graphics, int left, int top, int right, int bottom,
                             float radius, int accent, float borderSpeed) {
        RoundedRect.Corners corners = RoundedRect.Corners.uniform(radius);
        RoundedRect.fill(graphics, left, top, right, bottom, corners,
                RoundedRect.Ramp.of(RoundedRect.Gradient.VERTICAL, 0.0F,
                        0xF2000000 | shade(accent, 0.09F),
                        0xF2000000 | shade(accent, 0.24F)),
                16);
        // Three stops, not two: two would cycle from bright straight back to dim, which reads as a
        // flicker at the seam rather than a highlight sliding round a dim edge.
        RoundedRect.border(graphics, left, top, right, bottom, corners, 1.0F,
                RoundedRect.Ramp.of(RoundedRect.Gradient.BORDER_CIRCULAR, borderSpeed,
                        0xFF000000 | shade(accent, 0.40F),
                        0xFF000000 | shade(accent, 1.00F),
                        0xFF000000 | shade(accent, 0.40F)),
                16);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, int argb) {
        consumer.vertex(pose, x, y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    /** Scale a packed RGB, clamped. */
    public static int shade(int rgb, float factor) {
        int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}

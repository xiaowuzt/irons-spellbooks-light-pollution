package com.gang.lightpollution.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Rounded rectangles with per-corner radii, filled or outlined, in five gradient modes.
 *
 * <p>Ported from ArcaneVortex's {@code RoundedRectRenderer} and {@code GradientConfig}, credited in
 * {@code CREDITS.txt}. Its one dependency there was a private render type, which is inlined here as
 * the vanilla GUI type.</p>
 *
 * <p>Everything is emitted into the batched GUI buffer rather than drawn immediately, corners
 * included. A separate immediate draw would bypass the batch and could land underneath GUI content
 * submitted before it.</p>
 *
 * <p>The outline is built differently from the original. That one walked the four edges and four
 * arcs as eight separate cases, each recomputing its own start distance by re-adding every previous
 * term — the bottom-left corner's offset is a sum of six things. Here the perimeter is walked once
 * into a list of samples carrying their own arc length, so the distance bookkeeping happens in one
 * place and {@code BORDER_CIRCULAR} gets a single continuous coordinate by construction.</p>
 */
public final class RoundedRect {
    /** How a ramp maps position to colour. */
    public enum Gradient {
        /** Across, left to right. */
        HORIZONTAL,
        /** Down, top to bottom. */
        VERTICAL,
        /** Out from the centre. */
        RADIAL,
        /** Across, and sliding over time. */
        ANIMATED,
        /** Around the outline, and sliding over time. Only meaningful for {@link #border}. */
        BORDER_CIRCULAR
    }

    /** The four radii, clockwise from the top left. */
    public record Corners(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        public static Corners uniform(float radius) {
            return new Corners(radius, radius, radius, radius);
        }

        /** No radius may exceed half the shorter side, or opposite corners would cross. */
        Corners clamped(float width, float height) {
            float max = Math.min(width, height) * 0.5F;
            return new Corners(Math.min(topLeft, max), Math.min(topRight, max),
                    Math.min(bottomRight, max), Math.min(bottomLeft, max));
        }
    }

    /**
     * A colour ramp.
     *
     * <p>Alpha is honoured, which the original's blend did not do: it rewrote an alpha of zero to
     * fully opaque so callers could pass bare RGB. That makes a transparent stop impossible, and a
     * stop that fades out is exactly what a glow needs, so stops here are plain ARGB.</p>
     *
     * @param colours ARGB stops, cycled; a single stop is a flat colour
     * @param speed   cycles per ten seconds, for the animated modes
     */
    public record Ramp(int[] colours, float speed, Gradient type, boolean clockwise) {
        public static Ramp flat(int argb) {
            return new Ramp(new int[] {argb}, 0.0F, Gradient.HORIZONTAL, true);
        }

        public static Ramp of(Gradient type, float speed, int... colours) {
            return new Ramp(colours, speed, type, true);
        }

        public int colourAt(float progress, long millis) {
            if (colours.length == 0) {
                return 0xFFFFFFFF;
            }
            if (colours.length == 1) {
                return colours[0];
            }
            float shifted = progress;
            if (type == Gradient.ANIMATED || type == Gradient.BORDER_CIRCULAR) {
                float period = 10_000.0F / Math.max(0.01F, speed);
                float offset = (millis % (long) period) / period;
                shifted += clockwise ? offset : -offset;
            }
            shifted %= 1.0F;
            if (shifted < 0.0F) {
                shifted += 1.0F;
            }
            float scaled = shifted * colours.length;
            int index = (int) scaled % colours.length;
            return blend(colours[index], colours[(index + 1) % colours.length],
                    scaled - (int) scaled);
        }
    }

    /** One point on the outline: where it is, which way is inward, and how far round it sits. */
    private record Sample(float x, float y, float inwardX, float inwardY, float distance) {
    }

    private RoundedRect() {
    }

    /**
     * Fill a rounded rectangle: a central band, two edge bands, and four corner fans.
     *
     * @param segments divisions per corner arc
     */
    public static void fill(GuiGraphics graphics, float left, float top, float right, float bottom,
                           Corners radii, Ramp ramp, int segments) {
        float width = right - left;
        float height = bottom - top;
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        Corners r = radii.clamped(width, height);
        long millis = System.currentTimeMillis();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();

        float innerTop = top + Math.max(r.topLeft(), r.topRight());
        float innerBottom = bottom - Math.max(r.bottomLeft(), r.bottomRight());
        // The middle spans the full width; the two caps are inset by their own corners. Three
        // bands rather than one because the caps are narrower than the middle.
        quad(consumer, pose, left, innerTop, right, innerBottom, left, top, width, height, ramp,
                millis);
        quad(consumer, pose, left + r.topLeft(), top, right - r.topRight(), innerTop,
                left, top, width, height, ramp, millis);
        quad(consumer, pose, left + r.bottomLeft(), innerBottom, right - r.bottomRight(), bottom,
                left, top, width, height, ramp, millis);

        fan(consumer, pose, left + r.topLeft(), top + r.topLeft(), r.topLeft(),
                180.0, 270.0, segments, left, top, width, height, ramp, millis);
        fan(consumer, pose, right - r.topRight(), top + r.topRight(), r.topRight(),
                270.0, 360.0, segments, left, top, width, height, ramp, millis);
        fan(consumer, pose, right - r.bottomRight(), bottom - r.bottomRight(), r.bottomRight(),
                0.0, 90.0, segments, left, top, width, height, ramp, millis);
        fan(consumer, pose, left + r.bottomLeft(), bottom - r.bottomLeft(), r.bottomLeft(),
                90.0, 180.0, segments, left, top, width, height, ramp, millis);
    }

    /**
     * Outline a rounded rectangle, walking the perimeter once.
     *
     * <p>With {@link Gradient#BORDER_CIRCULAR} the colour is driven by distance around that walk,
     * which is what makes the light appear to travel along the edge rather than pulse in place.</p>
     *
     * @param thickness inward thickness in pixels
     * @param segments  divisions per corner arc
     */
    public static void border(GuiGraphics graphics, float left, float top, float right, float bottom,
                             Corners radii, float thickness, Ramp ramp, int segments) {
        float width = right - left;
        float height = bottom - top;
        if (width <= 0.0F || height <= 0.0F || thickness <= 0.0F) {
            return;
        }
        Corners r = radii.clamped(width, height);
        List<Sample> outline = walk(left, top, right, bottom, r, segments);
        if (outline.size() < 2) {
            return;
        }
        float perimeter = outline.get(outline.size() - 1).distance();
        if (perimeter <= 0.0F) {
            return;
        }

        long millis = System.currentTimeMillis();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();

        for (int index = 0; index + 1 < outline.size(); index++) {
            Sample a = outline.get(index);
            Sample b = outline.get(index + 1);
            int colourA = ramp.colourAt(a.distance() / perimeter, millis);
            int colourB = ramp.colourAt(b.distance() / perimeter, millis);
            // Outer edge, then inward by the thickness. Same traversal order as a vanilla fill.
            vertex(consumer, pose, a.x(), a.y(), colourA);
            vertex(consumer, pose, a.x() + a.inwardX() * thickness,
                    a.y() + a.inwardY() * thickness, colourA);
            vertex(consumer, pose, b.x() + b.inwardX() * thickness,
                    b.y() + b.inwardY() * thickness, colourB);
            vertex(consumer, pose, b.x(), b.y(), colourB);
        }
    }

    /**
     * The perimeter as a closed polyline, each sample carrying its cumulative arc length.
     *
     * <p>Clockwise from the top-left corner's end. The last sample repeats the first so the final
     * segment closes the loop.</p>
     */
    private static List<Sample> walk(float left, float top, float right, float bottom,
                                     Corners r, int segments) {
        List<Sample> out = new ArrayList<>();
        float[] distance = {0.0F};

        edge(out, distance, left + r.topLeft(), top, right - r.topRight(), top, 0.0F, 1.0F);
        arc(out, distance, right - r.topRight(), top + r.topRight(), r.topRight(),
                270.0, 360.0, segments);
        edge(out, distance, right, top + r.topRight(), right, bottom - r.bottomRight(),
                -1.0F, 0.0F);
        arc(out, distance, right - r.bottomRight(), bottom - r.bottomRight(), r.bottomRight(),
                0.0, 90.0, segments);
        edge(out, distance, right - r.bottomRight(), bottom, left + r.bottomLeft(), bottom,
                0.0F, -1.0F);
        arc(out, distance, left + r.bottomLeft(), bottom - r.bottomLeft(), r.bottomLeft(),
                90.0, 180.0, segments);
        edge(out, distance, left, bottom - r.bottomLeft(), left, top + r.topLeft(), 1.0F, 0.0F);
        arc(out, distance, left + r.topLeft(), top + r.topLeft(), r.topLeft(),
                180.0, 270.0, segments);

        if (!out.isEmpty()) {
            Sample first = out.get(0);
            out.add(new Sample(first.x(), first.y(), first.inwardX(), first.inwardY(),
                    distance[0]));
        }
        return out;
    }

    /** A straight run, subdivided so an animated ramp has somewhere to interpolate. */
    private static void edge(List<Sample> out, float[] distance, float x1, float y1,
                             float x2, float y2, float inwardX, float inwardY) {
        float length = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (length <= 0.0F) {
            return;
        }
        // About one sample every six pixels. Fewer makes a travelling highlight visibly faceted.
        int steps = Math.max(1, (int) (length / 6.0F));
        for (int step = 0; step < steps; step++) {
            float t = step / (float) steps;
            out.add(new Sample(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, inwardX, inwardY,
                    distance[0] + length * t));
        }
        distance[0] += length;
    }

    /** A corner arc. Inward is toward the arc centre, which is what keeps the band even. */
    private static void arc(List<Sample> out, float[] distance, float cx, float cy, float radius,
                            double fromDegrees, double toDegrees, int segments) {
        if (radius <= 0.0F) {
            return;
        }
        double sweep = Math.toRadians(toDegrees - fromDegrees);
        float length = (float) (Math.abs(sweep) * radius);
        int steps = Math.max(2, segments);
        for (int step = 0; step < steps; step++) {
            double angle = Math.toRadians(fromDegrees) + sweep * step / steps;
            float ox = (float) Math.cos(angle);
            float oy = (float) Math.sin(angle);
            out.add(new Sample(cx + ox * radius, cy + oy * radius, -ox, -oy,
                    distance[0] + length * step / steps));
        }
        distance[0] += length;
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, float x1, float y1,
                             float x2, float y2, float rectX, float rectY,
                             float rectW, float rectH, Ramp ramp, long millis) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        vertex(consumer, pose, x1, y1, at(x1, y1, rectX, rectY, rectW, rectH, ramp, millis));
        vertex(consumer, pose, x1, y2, at(x1, y2, rectX, rectY, rectW, rectH, ramp, millis));
        vertex(consumer, pose, x2, y2, at(x2, y2, rectX, rectY, rectW, rectH, ramp, millis));
        vertex(consumer, pose, x2, y1, at(x2, y1, rectX, rectY, rectW, rectH, ramp, millis));
    }

    private static void fan(VertexConsumer consumer, Matrix4f pose, float cx, float cy,
                            float radius, double fromDegrees, double toDegrees, int segments,
                            float rectX, float rectY, float rectW, float rectH,
                            Ramp ramp, long millis) {
        if (radius <= 0.0F) {
            return;
        }
        double step = Math.toRadians(toDegrees - fromDegrees) / segments;
        double start = Math.toRadians(fromDegrees);
        for (int index = 0; index < segments; index++) {
            double a0 = start + step * index;
            double a1 = start + step * (index + 1);
            float x0 = cx + (float) (Math.cos(a0) * radius);
            float y0 = cy + (float) (Math.sin(a0) * radius);
            float x1 = cx + (float) (Math.cos(a1) * radius);
            float y1 = cy + (float) (Math.sin(a1) * radius);
            int centre = at(cx, cy, rectX, rectY, rectW, rectH, ramp, millis);
            // Later angle before earlier, which is the winding a vanilla fill uses.
            vertex(consumer, pose, cx, cy, centre);
            vertex(consumer, pose, cx, cy, centre);
            vertex(consumer, pose, x1, y1, at(x1, y1, rectX, rectY, rectW, rectH, ramp, millis));
            vertex(consumer, pose, x0, y0, at(x0, y0, rectX, rectY, rectW, rectH, ramp, millis));
        }
    }

    /** The ramp's colour for a point inside the rectangle. */
    private static int at(float px, float py, float rectX, float rectY, float rectW, float rectH,
                          Ramp ramp, long millis) {
        float progress = switch (ramp.type()) {
            case HORIZONTAL, ANIMATED -> rectW <= 0.0F ? 0.0F : (px - rectX) / rectW;
            case VERTICAL -> rectH <= 0.0F ? 0.0F : (py - rectY) / rectH;
            case RADIAL -> {
                float dx = px - (rectX + rectW * 0.5F);
                float dy = py - (rectY + rectH * 0.5F);
                float reach = (float) Math.sqrt(rectW * rectW + rectH * rectH) * 0.5F;
                yield reach <= 0.0F ? 0.0F : (float) Math.sqrt(dx * dx + dy * dy) / reach;
            }
            // Only meaningful around an outline, so a fill gets the ramp's first stop.
            case BORDER_CIRCULAR -> 0.0F;
        };
        return ramp.colourAt(Math.max(0.0F, Math.min(1.0F, progress)), millis);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, int argb) {
        consumer.vertex(pose, x, y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    private static int blend(int from, int to, float t) {
        int a = channel(from, 24, to, t);
        int r = channel(from, 16, to, t);
        int g = channel(from, 8, to, t);
        int b = channel(from, 0, to, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int shift, int to, float t) {
        int a = (from >>> shift) & 0xFF;
        int b = (to >>> shift) & 0xFF;
        return Math.max(0, Math.min(255, (int) (a + (b - a) * t)));
    }
}

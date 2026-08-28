package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a camera-facing ribbon along an arbitrary curve.
 *
 * <p>Extracted because four effects in this mod draw a curve as a strip — the microquasar's
 * corkscrew, the magnetar's dipole loops, a disruption's debris stream, and a pinwheel's
 * arms — and the part they share is precisely the part that is easy to get wrong.</p>
 *
 * <p>The half-width vector is computed per point and shared by the two quads meeting there.
 * That is the whole reason this class exists: computing it per segment gives every quad its
 * own orientation, so consecutive quads do not share an edge, and from close up the strip
 * reads as a row of separate tiles with notches between them. It looks fine from a distance,
 * which is what makes it a trap.</p>
 *
 * <p>The tangent at each point comes from its neighbours rather than from the segment ahead,
 * so the frame turns smoothly through a bend instead of stepping at each joint.</p>
 */
public final class CurveRibbon {
    /** Supplies a point on the curve at a fraction from 0 to 1. */
    public interface Curve {
        Vec3 at(double fraction);
    }

    /** Supplies the ribbon's half-width in blocks at a fraction from 0 to 1. */
    public interface Width {
        double at(double fraction);
    }

    /** Supplies the packed ARGB colour at a fraction from 0 to 1. */
    public interface Colour {
        int at(double fraction);
    }

    private CurveRibbon() {
    }

    /**
     * Emit one curve as a continuous strip of quads.
     *
     * @param from     inclusive start of the parameter range, to skirt a degenerate end
     * @param to       inclusive end of the parameter range
     * @return vertices emitted
     */
    public static int emit(BufferBuilder builder, Vec3 camera, int segments,
                          double from, double to,
                          Curve curve, Width width, Colour colour) {
        if (segments < 1) {
            return 0;
        }
        Vec3[] points = new Vec3[segments + 1];
        Vec3[] across = new Vec3[segments + 1];
        double[] fractions = new double[segments + 1];

        for (int i = 0; i <= segments; ++i) {
            fractions[i] = from + (to - from) * (i / (double) segments);
            points[i] = curve.at(fractions[i]);
        }
        for (int i = 0; i <= segments; ++i) {
            Vec3 before = points[Math.max(0, i - 1)];
            Vec3 after = points[Math.min(segments, i + 1)];
            Vec3 tangent = after.subtract(before);
            if (tangent.lengthSqr() < 1.0e-9D) {
                tangent = new Vec3(0.0D, 1.0D, 0.0D);
            }
            Vec3 toCamera = camera.subtract(points[i]);
            Vec3 edge = tangent.cross(toCamera);
            if (edge.lengthSqr() < 1.0e-9D) {
                edge = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            across[i] = edge.normalize().scale(width.at(fractions[i]));
        }

        int emitted = 0;
        for (int i = 0; i < segments; ++i) {
            float alongFrom = (float) (i / (double) segments);
            float alongTo = (float) ((i + 1) / (double) segments);
            int packed = colour.at((fractions[i] + fractions[i + 1]) * 0.5D);
            emitted += quad(builder, camera, points[i], across[i], alongFrom,
                    points[i + 1], across[i + 1], alongTo, packed);
        }
        return emitted;
    }

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

        // The two ends carry different along values. One value for all four corners leaves
        // the shader's along coordinate constant within a quad, so anything it drives —
        // knots, packets, a colour ramp — comes out uniform per quad and the strip renders
        // as alternating bright and dark rectangles.
        vertex(builder, fx - fax, fy - fay, fz - faz, alongFrom, 0.0F, colour);
        vertex(builder, tx - tax, ty - tay, tz - taz, alongTo, 0.0F, colour);
        vertex(builder, tx + tax, ty + tay, tz + taz, alongTo, 1.0F, colour);
        vertex(builder, fx + fax, fy + fay, fz + faz, alongFrom, 1.0F, colour);
        return 4;
    }

    private static void vertex(BufferBuilder builder,
                               float x, float y, float z, float u, float v, int colour) {
        builder.vertex(x, y, z).uv(u, v)
                .color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF,
                        colour & 0xFF, (colour >>> 24) & 0xFF)
                .endVertex();
    }

    /** Pack a colour and intensity into ARGB, clamping every channel. */
    public static int pack(float r, float g, float b, float alpha) {
        return (channel(alpha) << 24) | (channel(r) << 16)
                | (channel(g) << 8) | channel(b);
    }

    private static int channel(float value) {
        return (int) Math.max(0.0F, Math.min(255.0F, value * 255.0F));
    }
}

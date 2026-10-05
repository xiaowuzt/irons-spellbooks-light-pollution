package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a strand of real tube geometry along a curve.
 *
 * <p>Replaces {@link CurveRibbon} for anything that should look like an object rather than a
 * decal. A camera-facing ribbon has no cross-section, never occludes itself, and collapses to
 * nothing wherever the curve points at the viewer — which is why the strands built that way
 * read as flat images pasted over the world. This produces a closed tube with real geometric
 * normals, so it is round from every angle, its near side hides its far side, and the
 * highlight moves when the camera does.</p>
 *
 * <p>Colour is not passed per point. {@link TubeMeshBuilder#emit} packs one colour for a whole
 * call, so a gradient would have to be split into separate calls and every split would show as
 * a step — the same seam this was supposed to remove. The ramp lives in strand.fsh instead,
 * driven by the along coordinate, which makes it continuous by construction.</p>
 */
public final class CurveTube {
    /** Material selector, matching the MODE_* defines in strand.fsh. */
    public static final float MODE_FIELD = 0.0F / 255.0F;
    public static final float MODE_JET = 1.0F / 255.0F;
    public static final float MODE_DEBRIS = 2.0F / 255.0F;
    public static final float MODE_ARM = 3.0F / 255.0F;
    public static final float MODE_FILAMENT = 4.0F / 255.0F;
    public static final float MODE_PLASMA = 5.0F / 255.0F;
    public static final float MODE_RUNE = 6.0F / 255.0F;
    public static final float MODE_BEAM = 7.0F / 255.0F;

    /**
     * Sides around the tube.
     *
     * <p>Eight is enough because the shader shades per fragment from an interpolated normal:
     * the silhouette is an octagon but the shading is smooth, and at these radii the silhouette
     * is a pixel or two wide. A per-vertex facing term would need three times as many.</p>
     */
    private static final int SIDES = 8;
    private static final ThreadLocal<CurveScratch> SCRATCH = ThreadLocal.withInitial(CurveScratch::new);

    private static final class CurveScratch {
        Vec3[] points = new Vec3[0];
        float[] radii = new float[0];
        TubeMeshBuilder.Ring[] rings = new TubeMeshBuilder.Ring[0];

        void ensure(int count) {
            if (points.length >= count) return;
            int capacity = Integer.highestOneBit(Math.max(16, count - 1)) << 1;
            points = new Vec3[capacity];
            radii = new float[capacity];
            rings = new TubeMeshBuilder.Ring[capacity];
        }
    }

    private CurveTube() {
    }

    /**
     * Emit one strand.
     *
     * @param segments rings along the tube; one more point than segments is generated
     * @param from     inclusive start of the curve parameter, to skirt a degenerate end
     * @param to       inclusive end of the curve parameter
     * @param mode     one of the MODE_* constants
     * @param aux      per-strand parameter the shader interprets by mode
     * @return vertices emitted
     */
    public static int emit(BufferBuilder builder, Vec3 camera, int segments,
                          double from, double to,
                          CurveRibbon.Curve curve, CurveRibbon.Width radius,
                          float mode, float aux, float intensity, int alpha) {
        if (segments < 2) {
            return 0;
        }
        segments = CurveLod.segments(segments, camera, from, to, curve);
        CurveScratch scratch = SCRATCH.get();
        scratch.ensure(segments + 2);
        Vec3[] path = scratch.points;
        float[] radii = scratch.radii;
        for (int i = 0; i <= segments; ++i) {
            double fraction = from + (to - from) * (i / (double) segments);
            Vec3 point = curve.at(fraction);
            // Camera-relative, matching every other world renderer here: the camera rotation
            // goes in ModelViewMat and the vertices stay raw.
            path[i] = point.subtract(camera);
            radii[i] = (float) radius.at(fraction);
        }

        // Null rolls: these curves have no inherent up, so carrying the reference axis forward
        // from ring to ring is right. Rebuilding it from world up each ring would make the tube
        // twist wherever the curve passes near vertical.
        TubeMeshBuilder.curveFrames(path, radii, segments + 1, false, scratch.rings);
        return TubeMeshBuilder.emit(builder, scratch.rings, segments + 1, SIDES, TubeMeshBuilder.CIRCLE,
                mode, aux, intensity, alpha, 0.0F, 1.0F);
    }

    /**
     * Emit one strand as a closed loop, with no ends.
     *
     * <p>For curves that come back to where they started. The curve must be periodic over 0 to 1 —
     * this samples 0 up to but not including 1 and joins the last sample back to the first, so a
     * curve whose value at 1 differs from its value at 0 produces one stretched segment across the
     * gap rather than an error.</p>
     *
     * @param segments rings around the loop; also the number of segments, since it closes
     */
    public static int emitLoop(BufferBuilder builder, Vec3 camera, int segments,
                               CurveRibbon.Curve curve, CurveRibbon.Width radius,
                               float mode, float aux, float intensity, int alpha) {
        if (segments < 3) {
            return 0;
        }
        segments = CurveLod.segments(segments, camera, 0, 1, curve);
        CurveScratch scratch = SCRATCH.get();
        scratch.ensure(segments + 2);
        Vec3[] loop = scratch.points;
        float[] radii = scratch.radii;
        for (int i = 0; i < segments; ++i) {
            double fraction = i / (double) segments;
            loop[i] = curve.at(fraction).subtract(camera);
            radii[i] = (float) radius.at(fraction);
        }
        TubeMeshBuilder.curveFrames(loop, radii, segments, true, scratch.rings);
        return TubeMeshBuilder.emit(builder, scratch.rings, segments + 1, SIDES, TubeMeshBuilder.CIRCLE,
                mode, aux, intensity, alpha, 0.0F, 1.0F);
    }
}

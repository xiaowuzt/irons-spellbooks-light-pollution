package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.client.perf.AdaptiveQualityController;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import net.minecraft.world.phys.Vec3;

/** A footprint estimate for tessellation only; sampled curves are never used for visibility culling. */
final class CurveLod {
    private CurveLod() { }

    static int segments(int full, Vec3 camera, double from, double to, CurveRibbon.Curve curve) {
        // Short paths include four-corner runes and intentionally faceted silhouettes.
        // Fixed mode keeps the original tessellation and needs no footprint samples.
        if (full <= 16 || SpellLightConfig.adaptiveMode == AdaptiveQualityController.Mode.FIXED) return full;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int i = 0; i <= 8; i++) {
            Vec3 p = curve.at(from + (to - from) * i / 8.0);
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        Vec3 centre = new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        double radius = new Vec3(maxX - minX, maxY - minY, maxZ - minZ).length() * 0.5;
        if (!Double.isFinite(radius)) return full;
        // A generous estimate protects curved silhouettes between the coarse sample points.
        return Math.max(16, Math.min(full,
                AdaptiveVisualQuality.curveSegments(full, centre.distanceTo(camera), radius * 1.25)));
    }
}

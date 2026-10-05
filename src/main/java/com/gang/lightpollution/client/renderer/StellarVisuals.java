package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.fx.ConstellationShape;
import com.gang.lightpollution.fx.ConstellationSource;
import net.minecraft.world.phys.Vec3;

/** One orbiting star: photosphere, prominences, cooling wake, then quiet exhaustion (not a nova). */
final class StellarVisuals {
    private static final Vec3 AXIS = new Vec3(0.23, 0.93, 0.27).normalize();
    private StellarVisuals() { }

    static boolean draw(ConstellationSource source, Vec3 camera, float partial) {
        var p = source.shapeParams();
        int lifetime = CinematicVisuals.constellationLifetime(source);
        float age = source.getVisualAgeTicks(partial), seed = CinematicVisuals.seed(source, source.anchorCenter(partial));
        float b = ConstellationShape.starBrightness(age, lifetime);
        Vec3 anchor = source.anchorCenter(partial);
        Vec3 at = ConstellationShape.starPosition(p, anchor, age, lifetime);
        if (camera.distanceToSqr(at) > 256 * 256) return true;
        float tail = VisualEnvelope.tail(age, lifetime, 30);
        float pulse = CinematicVisuals.burnPulse(source, partial);
        float life = lifetime <= 0 ? 0.45F : VisualEnvelope.clamp(age / lifetime);
        float radius = ConstellationShape.bodyRadius(p) * (1 + life * 0.15F) * (1 - tail * 0.64F);
        float heat = (0.04F + life * 0.20F) * (1 - tail);
        var scene = AstralScene.current(camera);
        if (!scene.body(at, new Vec3(radius, radius, radius), AXIS,
                age, seed, heat, b, 1.06F + pulse * 0.16F, false)) return false;
        scene.volume(4, at, radius, AXIS, age, seed, b * 1.4F,
                heat, pulse, 0, 0);
        Vec3 u = CinematicVisuals.planeU(AXIS), v = AXIS.cross(u).normalize();
        AstralGeometry.draw(camera, at, age, seed, g -> {
            // Separate arching magnetic prominences, attached to two footpoints on the photosphere.
            for (int i = 0; i < 9; i++) {
                double a = i * 2.399963 + seed * 0.007 + age * 0.0065;
                Vec3 radial = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                Vec3 tangent = AXIS.scale(Math.cos(i * 1.7)).add(v.scale(Math.cos(a) * Math.sin(i * 1.7)))
                        .add(u.scale(-Math.sin(a) * Math.sin(i * 1.7))).normalize();
                double spread = 0.22 + (i % 3) * 0.075;
                Vec3 footA = at.add(radial.scale(radius * Math.cos(spread))).add(tangent.scale(radius * Math.sin(spread)));
                Vec3 footB = at.add(radial.scale(radius * Math.cos(spread))).subtract(tangent.scale(radius * Math.sin(spread)));
                Vec3 apex = at.add(radial.scale(radius * (2.2 + 0.30 * Math.sin(i * 4.1) + pulse * 0.32 + tail * 1.8)));
                g.tube(42, 0, 1, f -> AstralGeometry.bezier(footA, apex, footB, f),
                        f -> p.scale() * (0.035 + Math.sin(Math.PI * f) * 0.055),
                        CurveTube.MODE_PLASMA, 0, 0.27F + pulse * 0.12F, b * (1 - tail * 0.7F));
            }
            float history = Math.min(age, 42);
            Vec3 old = ConstellationShape.starPosition(p, anchor, age - history, lifetime);
            if (old.distanceToSqr(at) > 0.08) {
                for (int strand = 0; strand < 4; strand++) {
                    final int index = strand;
                    g.tube(86, 0.035, 1, f -> {
                        Vec3 prior = ConstellationShape.starPosition(p, anchor, age - (float) f * history, lifetime);
                        double spread = Math.sin(f * Math.PI) * radius * 0.55;
                        return prior.add(u.scale(spread * Math.cos(index * 1.57 + f * 7)))
                                .add(v.scale(spread * Math.sin(index * 1.57 + f * 7)))
                                .add(0, -f * f * p.scale() * 1.2, 0);
                    }, f -> p.scale() * (0.055 + 0.15 * (1 - f)) * (1 - f * 0.85),
                            CurveTube.MODE_PLASMA, 0, 0.18F, b * 0.70F);
                }
            }
        });
        scene.distort(at, radius * 2.6F, age, b * (0.17F + pulse * 0.10F), false);
        return true;
    }
}

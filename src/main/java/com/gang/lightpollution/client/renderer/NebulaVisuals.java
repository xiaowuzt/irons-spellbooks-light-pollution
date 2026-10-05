package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.fx.*;
import net.minecraft.world.phys.Vec3;

/** Gas/dust has volume and absorption; only the hot structure uses emissive tubes. */
final class NebulaVisuals {
    private static final Vec3 PULSAR_AXIS = new Vec3(0.31, 0.92, 0.24).normalize();
    private NebulaVisuals() { }

    static void helix(HelixNebulaSource source, Vec3 camera, float partial) {
        var p = source.shapeParams();
        var t = CinematicVisuals.helix(source, partial);
        var frame = HelixNebulaShape.frame(p);
        Vec3 c = source.centre(partial);
        float b = source.brightness(partial), seed = CinematicVisuals.seed(source, c);
        float radius = (float) HelixNebulaShape.shellRadius(p, t.age());
        var scene = AstralScene.current(camera);
        float starRadius = 0.82F * p.scale();
        if (!scene.body(c, new Vec3(starRadius, starRadius, starRadius), frame.normal(),
                t.age(), seed, 1, b, 1.25F, false))
            EffectCore.add(c, starRadius, 0.66F, 0.85F, 1, b * 1.4F);
        scene.volume(4, c, starRadius, frame.normal(), t.age(), seed, b, 1, t.flash(20), 0, 0);
        float wave = Math.max(0, t.age() - t.formEnd() * 0.22F) * 0.85F / Math.max(0.001F, radius);
        scene.volume(0, c, radius, frame.normal(), t.age(), seed, b * 1.2F,
                t.formed(), wave, 0, 0);
        if (t.flash(24) > 0) scene.distort(c, radius, t.age(),
                t.flash(24) * b * SpellLightConfig.cinematicFlashStrength * 0.6F, true);
    }

    /** Returns whether the real wind volume replaced the old camera-facing disc. */
    static boolean crab(CrabNebulaSource source, Vec3 camera, float partial) {
        var p = source.shapeParams();
        var t = CinematicVisuals.crab(source, partial);
        Vec3 c = source.centre(partial);
        float b = source.brightness(partial), scale = p.scale(), seed = CinematicVisuals.seed(source, c);
        float collapse = t.collapse(44);
        float radius = (float) CrabNebulaShape.shellRadius(p, t.age()) * (1 - collapse * 0.65F);
        float pulse = VisualEnvelope.pulse(t.age(), 0, 1.8F, 10);
        float flash = t.flash(20) * SpellLightConfig.cinematicFlashStrength;
        var scene = AstralScene.current(camera);
        float starRadius = scale * (0.67F - collapse * 0.16F);
        if (!scene.body(c, new Vec3(starRadius, starRadius, starRadius), PULSAR_AXIS,
                t.age(), seed, 1, b, 1 + pulse * 0.4F + flash * 0.4F, false))
            EffectCore.add(c, starRadius, 0.62F, 0.78F, 1, b * (1 + pulse));
        boolean volume = scene.volume(1, c, radius, PULSAR_AXIS, t.age(), seed,
                b * (0.9F + flash * 0.20F), t.formed(), 0, 0, 0);
        AstralGeometry.draw(camera, c, t.age(), seed, g -> {
            // A tilted termination torus and two short pulsar jets live inside the broken gas cage.
            for (int side : new int[]{-1, 1}) {
                g.tube(40, 0, 1, f -> c.add(PULSAR_AXIS.scale(side * (0.65 + f * 4.8) * scale)),
                        f -> scale * (0.11 + 0.16 * (1 - f)), CurveTube.MODE_BEAM, 0.45F,
                        0.17F + pulse * 0.16F, b * (1 - collapse * 0.8F));
            }
            // Branches begin AND end on a major filament. Fine capillaries, not extra attack arcs.
            for (int filament = 0; filament < CrabNebulaShape.FILAMENTS; filament++) {
                final int index = filament;
                float aux = FxHash.at(p.seed(), index, 7) < 0.34 ? 1 : 0;
                for (int branch = 0; branch < 3; branch++) {
                    double f0 = (branch * 0.29 + FxHash.at(p.seed(), index, 12) * 0.08);
                    Vec3 a = CinematicLightSources.crabPoint(source, c, index, f0, partial);
                    Vec3 z = CinematicLightSources.crabPoint(source, c, index, f0 + 0.115, partial);
                    Vec3 outward = a.subtract(c).normalize();
                    Vec3 mid = a.lerp(z, 0.5).add(outward.scale(scale * (1.2 + branch * 0.55) * (1 - collapse)));
                    g.tube(28, 0, 1, f -> AstralGeometry.bezier(a, mid, z, f),
                            f -> scale * (0.025 + 0.075 * Math.sin(f * Math.PI)) * (1 - collapse * 0.5F),
                            CurveTube.MODE_FILAMENT, aux, 0.13F, b * 0.60F);
                }
            }
        });
        if (flash > 0) scene.distort(c, radius * 0.8F, t.age(), b * flash * 0.55F, true);
        return volume;
    }

    /** Returns whether the dust sheet exists, so the base renderer can reduce its old rope thickness. */
    static boolean pinwheel(PinwheelSource source, Vec3 camera, float partial) {
        var p = source.shapeParams();
        var t = CinematicVisuals.pinwheel(source, partial);
        Vec3 c = source.centre(partial), normal = PinwheelShape.planeNormal(p);
        Vec3 swing = CinematicVisuals.pinwheelSwing(p, t.age());
        float b = source.brightness(partial), scale = p.scale(), seed = CinematicVisuals.seed(source, c);
        float flash = t.flash(22) * SpellLightConfig.cinematicFlashStrength;
        var scene = AstralScene.current(camera);
        for (int side : new int[]{-1, 1}) {
            Vec3 at = c.add(swing.scale(side));
            float radius = (side > 0 ? 1.28F : 0.85F) * scale, temperature = side > 0 ? 1 : 0.06F;
            if (!scene.body(at, new Vec3(radius, radius, radius), normal, t.age(), seed + side * 13,
                    temperature, b, 1.15F + flash * 0.25F, false))
                EffectCore.add(at, radius, side > 0 ? 0.46F : 1, side > 0 ? 0.72F : 0.49F,
                        side > 0 ? 1 : 0.16F, b);
            scene.volume(4, at, radius, normal, t.age(), seed + side * 13, b * 0.85F,
                    temperature, flash, 0, 0);
        }
        boolean volume = scene.volume(2, c, 26 * scale, normal, t.age(), seed, b * 1.3F,
                PinwheelShape.spunUp(t.age()), (float) PinwheelShape.rotation(t.age()), 0, flash);
        // Fresh dust forms where the two winds meet, then advects along the real spiral spines.
        Vec3 tangent = normal.cross(swing).normalize();
        Vec3 windAxis = swing.normalize();
        AstralGeometry.draw(camera, c, t.age(), seed, g -> {
            for (int side : new int[]{-1, 1}) {
                Vec3 start = c.add(swing.scale(side * 0.36));
                for (int fan = -1; fan <= 1; fan += 2) {
                    Vec3 end = c.add(tangent.scale(fan * 4.0 * scale)).add(windAxis.scale(2.0 * scale));
                    Vec3 bend = c.add(tangent.scale(fan * 1.9 * scale)).subtract(windAxis.scale(1.4 * scale));
                    g.tube(48, 0, 1, f -> AstralGeometry.bezier(start, bend, end, f),
                            f -> scale * 0.042 * (1 - f * 0.65), CurveTube.MODE_PLASMA, 0,
                            0.20F + flash * 0.15F, b * 0.58F);
                }
            }
        });
        scene.distort(c, 5.0F * scale, t.age(), b * (0.11F + flash * 0.45F), false);
        return volume;
    }
}

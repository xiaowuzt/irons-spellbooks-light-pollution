package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.fx.TidalDisruptionShape;
import com.gang.lightpollution.fx.TidalDisruptionSource;
import net.minecraft.world.phys.Vec3;

/** A disrupted star feeding a dark horizon; the authoritative debris spine is left to the base renderer. */
final class TidalDisruptionVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private TidalDisruptionVisuals() { }

    static void draw(TidalDisruptionSource source, Vec3 camera, float partial) {
        var t = CinematicVisuals.tidal(source, partial);
        var p = source.shapeParams();
        Vec3 centre = source.centre(partial), star = CinematicVisuals.tidalStar(source, partial);
        float scale = p.scale(), b = source.brightness(partial), seed = CinematicVisuals.seed(source, centre);
        float stretch = VisualEnvelope.smooth(VisualEnvelope.progress(t.age(), t.formEnd() * 0.14F, t.formEnd()));
        float remnant = 1 - VisualEnvelope.smooth(VisualEnvelope.progress(t.age(), t.formEnd() * 0.45F, t.formEnd() * 1.18F));
        float flash = (source.flare(partial) * 2 + t.flash(14)) * SpellLightConfig.cinematicFlashStrength;
        var scene = AstralScene.current(camera);
        float holeRadius = 1.65F * scale;
        boolean horizon = scene.body(centre, new Vec3(holeRadius, holeRadius, holeRadius), UP,
                t.age(), seed, 0, VisualEnvelope.clamp(b), 1, true);
        if (!horizon) EffectCore.add(centre, holeRadius, 0.44F, 0.67F, 1, b * (0.55F + flash));

        Vec3 tangent = TidalDisruptionShape.streamPoint(p, centre, t.age(), 1.0)
                .subtract(TidalDisruptionShape.streamPoint(p, centre, t.age(), 0.97)).normalize();
        double shortAxis = scale * (1.9 - stretch * 1.3);
        double longAxis = scale * (1.9 + stretch * 3.7);
        if (remnant > 0.005F) {
            boolean body = scene.body(star, new Vec3(shortAxis, longAxis, shortAxis), tangent,
                    t.age(), seed + 11, 0.04F + stretch * 0.23F, b * remnant, 1.12F, false);
            if (!body) EffectCore.add(star, shortAxis, 1, 0.44F, 0.10F, b * remnant);
            scene.volume(4, star, (float) shortAxis, tangent, t.age(), seed + 11,
                    b * remnant * 0.6F, 0.07F, stretch, 0, 0);
        }
        scene.volume(3, centre, 6.1F * scale, UP, t.age(), seed,
                b * (0.65F + t.formed() * 0.5F), t.formed(), flash, 0, 0);
        Vec3 hotspot = CinematicVisuals.tidalHotspot(source, partial);
        EffectCore.add(hotspot, scale * (0.45 + flash * 0.65), 0.50F, 0.78F, 1, b * (0.55F + flash));

        // The thin stripping tendrils wind AROUND the original stream, never invent extra attack paths.
        AstralGeometry.draw(camera, centre, t.age(), seed, g -> {
            for (int strand = 0; strand < 7; strand++) {
                final double phase = strand * AstralGeometry.TAU / 7;
                final float layer = 0.45F + strand * 0.065F;
                g.tube(130, 0.06, 1, f -> {
                    Vec3 at = TidalDisruptionShape.streamPoint(p, centre, t.age(), f);
                    Vec3 radial = new Vec3(at.x - centre.x, 0, at.z - centre.z).normalize();
                    double angle = f * AstralGeometry.TAU * 4 + phase - t.age() * 0.025;
                    double spread = TidalDisruptionShape.streamWidth(p, f) * (1.0 + stretch * 0.38);
                    return at.add(radial.scale(Math.cos(angle) * spread)).add(0, Math.sin(angle) * spread * 0.55, 0);
                }, f -> scale * (0.035 + 0.07 * Math.sin(Math.PI * f)),
                        CurveTube.MODE_DEBRIS, layer, 0.11F + flash * 0.06F, b * (0.15F + stretch * 0.55F));
            }
            // A narrow returning shock races over the existing orbital plane at the real flare event.
            if (t.age() >= t.release() && t.age() < t.release() + 24) {
                float eventAge = t.age() - t.release();
                g.ring(centre, UP, scale * (2.1 + eventAge * 0.50), scale * 0.07, 0,
                        0.2F, 0.35F, b * t.flash(24) * SpellLightConfig.cinematicFlashStrength);
            }
        });
        scene.distort(centre, 7.4F * scale, t.age(), b * (0.16F + flash * 0.4F), false);
        if (t.age() >= t.release() && t.age() < t.release() + 24)
            scene.distort(centre, scale * (2.1F + (t.age() - t.release()) * 0.5F), t.age(),
                    b * t.flash(24) * SpellLightConfig.cinematicFlashStrength, true);
    }
}

package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.fx.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;

/** Structural lights: emitted from actual moving material, never from an invisible central lamp. */
final class CinematicLightSources {
    private CinematicLightSources() { }

    static void append(List<SpellLightEmitter.Light> out, float partial) {
        for (MicroquasarSource source : sources(SpellLightEmitter.collectMicroquasars(), FxRegistry.microquasars())) {
            float age = source.getVisualAgeTicks(partial), b = source.brightness(partial);
            Vec3 c = source.centre(partial);
            float seed = CinematicVisuals.seed(source, c), scale = source.shapeParams().scale();
            add(out, c, 23 * scale, b * 1.2F, 1, 0.73F, 0.40F);
            for (int side = 0; side < 2; side++) {
                for (int i = 0; i < 3; i++) {
                    double f = Mth.frac((age * 0.05 * 2.1 + seed * 0.017 + 0.5 + i * 4) / 12);
                    // CurveTube maps its 0.004..1 geometric domain to the shader's 0..1 UV.
                    Vec3 at = MicroquasarShape.helixPoint(source.shapeParams(), c, age, side == 0, 0.004 + f * 0.996);
                    add(out, at, 13 * scale, b * (float) (1 - f * 0.55) * (side == 0 ? 0.85F : 0.45F),
                            side == 0 ? 0.32F : 1, side == 0 ? 0.64F : 0.30F, side == 0 ? 1 : 0.18F);
                }
            }
        }
        for (MagnetarSource source : sources(SpellLightEmitter.collectMagnetars(), FxRegistry.magnetars())) {
            float age = source.getVisualAgeTicks(partial), b = source.brightness(partial);
            float wound = source.wound(partial), flare = source.flare(partial);
            float scale = source.shapeParams().scale();
            Vec3 c = source.centre(partial), axis = MagnetarShape.axis(source.shapeParams());
            for (int sign : new int[]{-1, 1}) {
                add(out, c.add(axis.scale(sign * MagnetarShape.STAR_RADIUS * scale)), 24 * scale,
                        b * (0.65F + wound * 0.7F + flare * 2 * SpellLightConfig.cinematicFlashStrength),
                        0.63F, 0.36F, 1);
            }
            for (int i = 0; i < 3; i++) {
                double f = Mth.frac((age * 0.05 * (1.4 + wound)
                        + CinematicVisuals.seed(source, c) * 0.017 + 0.5 + i) / 3);
                Vec3 at = MagnetarShape.fieldPoint(source.shapeParams(), c, i * 4, f, wound);
                add(out, at, 11 * scale, b * 0.48F, 0.46F, 0.28F, 1);
            }
        }
        for (TidalDisruptionSource source : sources(SpellLightEmitter.collectTidalDisruptions(), FxRegistry.tidalDisruptions())) {
            var t = CinematicVisuals.tidal(source, partial);
            float b = source.brightness(partial), flare = source.flare(partial);
            float scale = source.shapeParams().scale();
            Vec3 c = source.centre(partial);
            float star = 1 - VisualEnvelope.smooth(VisualEnvelope.progress(t.age(),
                    t.formEnd() * 0.45F, t.formEnd() * 1.18F));
            add(out, CinematicVisuals.tidalStar(source, partial), 19 * scale, b * star * 1.5F,
                    1, 0.44F, 0.12F);
            add(out, CinematicVisuals.tidalHotspot(source, partial), 25 * scale,
                    b * (0.40F + flare * 4 * SpellLightConfig.cinematicFlashStrength), 0.48F, 0.76F, 1);
            for (int i = 0; i < 3; i++) {
                double f = Mth.frac((0.5 - t.age() * 0.05 * 1.85
                        - CinematicVisuals.seed(source, c) * 0.017 + i * 3) / 11);
                Vec3 at = TidalDisruptionShape.streamPoint(source.shapeParams(), c, t.age(), f);
                add(out, at, 11 * scale, b * (0.28F + 0.18F * (1 - (float) f)),
                        (float) (0.48 + f * 0.52), (float) (0.76 - f * 0.50), (float) (1 - f * 0.9));
            }
        }
        for (QuasarJetSource source : sources(SpellLightEmitter.collectQuasarJets(), FxRegistry.quasarJets())) {
            float age = source.getVisualAgeTicks(partial), b = source.brightness(partial);
            Vec3 c = source.centre(partial);
            float scale = source.shapeParams().scale();
            add(out, c, 25 * scale, b * 1.2F, 0.40F, 0.68F, 1);
            for (int i = 0; i < QuasarJetShape.KNOT_COUNT; i += 2) {
                Vec3 at = QuasarJetShape.knotPosition(source.shapeParams(), c, i, age);
                if (at != null) add(out, at, 18 * scale, b * 0.7F, 0.30F, 0.62F, 1);
            }
            if (QuasarJetShape.launched(age) > 0.98F) {
                add(out, QuasarJetShape.lobeCentre(source.shapeParams(), c), 24 * scale, b * 0.8F, 0.85F, 0.34F, 0.16F);
            }
        }
        for (HelixNebulaSource source : sources(SpellLightEmitter.collectHelixNebulae(), FxRegistry.helixNebulae())) {
            float age = source.getVisualAgeTicks(partial), b = source.brightness(partial);
            Vec3 c = source.centre(partial);
            var p = source.shapeParams();
            var frame = HelixNebulaShape.frame(p);
            add(out, c, 20 * p.scale(), b * 0.9F, 0.54F, 0.78F, 1);
            for (int i = 0; i < 6; i++) {
                var knot = HelixNebulaShape.knot(p, c, frame, age, i * 79);
                float ion = helixIonization(CinematicVisuals.helix(source, partial), c.distanceTo(knot.at()));
                add(out, knot.at(), 13 * p.scale(), b * ion * 0.40F,
                        knot.outerRing() ? 1 : 0.15F, knot.outerRing() ? 0.24F : 0.88F,
                        knot.outerRing() ? 0.12F : 0.67F);
            }
        }
        for (CrabNebulaSource source : sources(SpellLightEmitter.collectCrabNebulas(), FxRegistry.crabNebulae())) {
            var t = CinematicVisuals.crab(source, partial);
            float b = source.brightness(partial), scale = source.shapeParams().scale();
            Vec3 c = source.centre(partial);
            add(out, c, 23 * scale, b * (0.75F + VisualEnvelope.pulse(t.age(), 0, 1.8F, 10) * 0.7F
                    + t.flash(20) * SpellLightConfig.cinematicFlashStrength), 0.38F, 0.64F, 1);
            for (int i = 0; i < 5; i++) {
                Vec3 at = crabPoint(source, c, i * 4, 0.27, partial);
                float pulse = VisualEnvelope.pulse(t.age(), (float) c.distanceTo(at), 1.8F, 10);
                add(out, at, 12 * scale, b * (0.18F + pulse * 0.60F),
                        i % 3 == 0 ? 0.18F : 1, i % 3 == 0 ? 0.88F : 0.18F, i % 3 == 0 ? 0.50F : 0.095F);
            }
        }
        for (PinwheelSource source : sources(SpellLightEmitter.collectPinwheels(), FxRegistry.pinwheels())) {
            var t = CinematicVisuals.pinwheel(source, partial);
            float b = source.brightness(partial), scale = source.shapeParams().scale();
            Vec3 c = source.centre(partial), swing = CinematicVisuals.pinwheelSwing(source.shapeParams(), t.age());
            add(out, c.add(swing), 20 * scale, b * 1.0F, 0.38F, 0.66F, 1);
            add(out, c.subtract(swing), 17 * scale, b * 0.75F, 1, 0.49F, 0.14F);
            add(out, c, 25 * scale, b * (0.35F + t.flash(22) * 2 * SpellLightConfig.cinematicFlashStrength), 1, 0.64F, 0.24F);
            for (int arm = 0; arm < 2; arm++) {
                double packet = Mth.frac((t.age() * 0.05 * 0.7
                        + CinematicVisuals.seed(source, c) * 0.017 + 0.5 + arm * 3) / 8);
                double grown = Math.max(0.05, PinwheelShape.spunUp(t.age()));
                double f = 0.02 + packet * (grown - 0.02);
                Vec3 at = PinwheelShape.armPoint(source.shapeParams(), c, arm, f, PinwheelShape.rotation(t.age()));
                add(out, at, 12 * scale, b * 0.35F, 1, 0.39F, 0.075F);
            }
        }
    }

    /** The same two source lists as each world renderer, including client API-only effects. */
    private static <T> List<T> sources(List<? extends T> anchors, List<? extends T> api) {
        List<T> all = new ArrayList<>(anchors);
        all.addAll(api);
        return all;
    }

    static float helixIonization(CinematicVisuals.Stages t, double distance) {
        return VisualEnvelope.smooth(VisualEnvelope.progress(t.age(),
                t.formEnd() * 0.22F + (float) distance * 0.35F,
                t.formEnd() * 0.70F + (float) distance * 0.35F));
    }

    static Vec3 crabPoint(CrabNebulaSource source, Vec3 c, int filament, double f, float partial) {
        var t = CinematicVisuals.crab(source, partial);
        Vec3 point = CrabNebulaShape.filamentPoint(source.shapeParams(), c, filament, f, t.age());
        // Only the after-event remains contract. The damaging cage stays on its original path.
        return c.add(point.subtract(c).scale(1 - t.collapse(44) * 0.65));
    }

    private static void add(List<SpellLightEmitter.Light> out, Vec3 p, float radius, float intensity,
                            float r, float g, float b) {
        if (radius > 0 && intensity > 0.015F) out.add(new SpellLightEmitter.Light(p, radius, intensity, r, g, b));
    }
}

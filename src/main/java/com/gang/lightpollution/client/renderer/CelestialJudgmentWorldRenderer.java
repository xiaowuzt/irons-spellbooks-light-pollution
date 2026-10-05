package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The sky structure has its own world pass, not the tiny target anchor's frustum bounds. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CelestialJudgmentWorldRenderer {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final BufferBuilder CORES = new BufferBuilder(8192);
    private CelestialJudgmentWorldRenderer() { }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) return;
        var views = CelestialJudgmentVisuals.views(event.getPartialTick());
        if (views.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        var stack = RenderSystem.getModelViewStack();
        try (SceneRenderState ignored = new SceneRenderState()) {
            stack.pushPose();
            try {
                stack.setIdentity();
                stack.mulPoseMatrix(SpellRenderStage.levelPose(event));
                RenderSystem.applyModelViewMatrix();
                for (var view : views) {
                    if (camera.distanceToSqr(view.feet()) < 320 * 320) draw(view, camera);
                }
                EffectCore.flush(CORES, camera);
            } finally {
                stack.popPose();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    private static void draw(CelestialJudgmentVisuals.View view, Vec3 camera) {
        var t = view.time();
        float form = t.formation(), charge = t.charge(), fade = t.fade();
        if (fade <= 0.005F || form <= 0.001F) return;
        float flash = t.flash(18) * SpellLightConfig.cinematicFlashStrength;
        float after = VisualEnvelope.smooth(VisualEnvelope.progress(t.age(), t.impact() + 6, t.lifetime()));
        Vec3 sky = view.sky(), focus = sky.add(0, -3.2, 0);
        Vec3 hitAt = view.feet().add(0, Math.max(0.18, view.height() * 0.42), 0);
        float travel = t.beamTravel();
        // After contact there is ONE brief discharge, not a persistent fake area-damage pillar.
        float beam = t.age() < t.impact() ? travel * 0.42F : t.flash(14);
        Vec3 beamEnd = focus.lerp(hitAt, travel);
        float aperture = (0.45F + charge * 0.70F) * (1 - t.compression() * 0.64F);
        EffectCore.add(focus, aperture, 0.42F, 0.78F, 1,
                fade * form * (0.6F + charge * 0.8F + flash * 0.65F) * (1 - after));
        if (flash > 0) EffectCore.add(hitAt, Math.min(1.4, view.width() * 0.7 + 0.4),
                0.63F, 0.86F, 1, fade * flash * 1.3F);

        AstralGeometry.draw(camera, focus, t.age(), view.seed(), g -> {
            for (int layer = 0; layer < 3; layer++) {
                final int index = layer;
                double radius = (16 - layer * 3.7) * (0.22 + form * 0.78) * (1 + after * 0.12);
                double rotation = t.age() * (layer % 2 == 0 ? 0.009 : -0.012) + view.seed() * 0.003;
                Vec3 c = sky.add(0, layer * 3.2 - 3.2, 0);
                float alpha = fade * form * (0.70F - layer * 0.08F);
                g.ring(c, UP, radius, 0.085, rotation, layer == 1 ? 0.75F : 0.05F, 0.28F + charge * 0.12F, alpha);
                g.ring(c, UP, radius * 0.88, 0.036, -rotation, 0.18F, 0.21F, alpha);
                // Rotating diamond runes and interrupted spokes retain negative space between rings.
                for (int rune = 0; rune < 20; rune++) {
                    final double angle = rune * AstralGeometry.TAU / 20 + rotation;
                    Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    Vec3 across = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
                    Vec3 runeCentre = c.add(radial.scale(radius * 0.94));
                    double size = 0.22 + layer * 0.04;
                    g.loop(4, f -> {
                        double a = f * AstralGeometry.TAU;
                        return runeCentre.add(radial.scale(Math.cos(a) * size * 1.8))
                                .add(across.scale(Math.sin(a) * size));
                    }, f -> 0.038, CurveTube.MODE_RUNE, layer == 1 ? 1 : 0.2F, 0.30F, alpha);
                    if (rune % 2 == 0) g.tube(8, 0, 1,
                            f -> c.add(radial.scale(radius * (0.56 + f * 0.24))), f -> 0.042,
                            CurveTube.MODE_RUNE, 0.1F, 0.20F, alpha * charge);
                }
                // Each tilted collar rotates about a different normal, making the focus volumetric.
                Vec3 normal = new Vec3(Math.sin(rotation) * 0.30, 1, Math.cos(rotation) * 0.30).normalize();
                g.ring(focus, normal, 2.0 + index * 0.56 - t.compression() * 0.65, 0.065,
                        rotation, 0.2F, 0.27F, fade * form * charge * (1 - after));
            }
            if (charge > 0 && t.age() < t.impact() + 3) {
                for (int ray = 0; ray < 12; ray++) {
                    double a = ray * AstralGeometry.TAU / 12 + t.age() * 0.012;
                    Vec3 start = sky.add(Math.cos(a) * 12, 3 + Math.sin(a * 3), Math.sin(a) * 12);
                    Vec3 bend = sky.add(Math.cos(a + 0.45) * 5.6, 5, Math.sin(a + 0.45) * 5.6);
                    g.tube(42, 0, 1, f -> AstralGeometry.bezier(start, bend, focus, f),
                            f -> 0.030 + 0.05 * f, CurveTube.MODE_BEAM, 0.3F,
                            0.24F, fade * charge * 0.7F);
                }
            }
            if (beam > 0.001F) {
                for (int layer = 0; layer < 3; layer++) {
                    double width = layer == 0 ? 0.86 : layer == 1 ? 0.39 : 0.12;
                    float alpha = layer == 0 ? 0.12F : layer == 1 ? 0.30F : 0.88F;
                    g.tube(72, 0, 1, f -> focus.lerp(beamEnd, f), f -> width,
                            CurveTube.MODE_BEAM, layer * 0.5F, 0.36F + flash * 0.12F, beam * alpha);
                }
            }
            if (t.flash(28) > 0) {
                float pulse = t.flash(28) * SpellLightConfig.cinematicFlashStrength;
                double footprint = Math.min(2.4, Math.max(1.0, view.width() * 0.85));
                g.ring(view.feet().add(0, 0.09, 0), UP, footprint * (1.1 + (1 - pulse) * 0.36),
                        0.06, 0, 0.1F, 0.32F, pulse);
                for (int spark = 0; spark < 14; spark++) {
                    double a = spark * 2.39996 + view.seed() * 0.01;
                    double since = t.age() - t.impact();
                    Vec3 at = hitAt.add(Math.cos(a) * footprint, since * 0.10 - since * since * 0.003,
                            Math.sin(a) * footprint);
                    g.tube(3, 0, 1, f -> at.add(0, f * 0.32, 0), f -> 0.022,
                            CurveTube.MODE_RUNE, spark % 3 == 0 ? 1 : 0, 0.38F, pulse * 0.8F);
                }
            }
        });
        if (flash > 0) AstralScene.current(camera).distort(hitAt,
                1.0F + (t.age() - t.impact()) * 0.20F, t.age(), flash * 0.55F, true);
    }
}

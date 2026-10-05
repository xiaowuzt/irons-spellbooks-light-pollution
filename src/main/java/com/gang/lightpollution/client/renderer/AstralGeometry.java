package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

/** Fine emissive geometry. Own buffer, per-instance material clock, and a complete state bracket. */
final class AstralGeometry {
    private static final BufferBuilder BUFFER = new BufferBuilder(524_288);
    static final double TAU = Math.PI * 2;
    private final Vec3 camera;
    private int vertices;

    private AstralGeometry(Vec3 camera) { this.camera = camera; }

    static void draw(Vec3 camera, Vec3 centre, float age, float seed, Consumer<AstralGeometry> emit) {
        var shader = ConstellationShaders.strand();
        if (shader == null) return;
        long timing = PerfTracker.begin(PerfTracker.Section.GEOMETRY);
        try (SceneRenderState ignored = new SceneRenderState()) {
            BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            var batch = new AstralGeometry(camera);
            emit.accept(batch);
            if (batch.vertices == 0) { BUFFER.end().release(); return; }
            CinematicVisuals.strand(shader, age, seed, centre.subtract(camera));
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShader(() -> shader);
            BufferBuilder.RenderedBuffer mesh = BUFFER.end();
            int gpu = PerfTracker.beginGpu(PerfTracker.Section.GEOMETRY);
            try {
                BufferUploader.drawWithShader(mesh);
            } finally {
                PerfTracker.endGpu(gpu);
            }
        } finally {
            if (BUFFER.building()) BUFFER.end().release();
            PerfTracker.end(PerfTracker.Section.GEOMETRY, timing);
        }
    }

    void tube(int segments, double from, double to, CurveRibbon.Curve path, CurveRibbon.Width width,
              float mode, float aux, float intensity, float alpha) {
        if (alpha <= 0.003F || intensity <= 0 || to <= from) return;
        vertices += CurveTube.emit(BUFFER, camera, segments, from, to, path, width,
                mode, aux, intensity, (int) (VisualEnvelope.clamp(alpha) * 255));
    }

    void loop(int segments, CurveRibbon.Curve path, CurveRibbon.Width width,
              float mode, float aux, float intensity, float alpha) {
        if (alpha <= 0.003F || intensity <= 0) return;
        vertices += CurveTube.emitLoop(BUFFER, camera, segments, path, width,
                mode, aux, intensity, (int) (VisualEnvelope.clamp(alpha) * 255));
    }

    void ring(Vec3 centre, Vec3 normal, double radius, double width,
              double rotation, float aux, float intensity, float alpha) {
        Vec3 u = CinematicVisuals.planeU(normal), w = normal.cross(u).normalize();
        loop(128, f -> centre.add(u.scale(Math.cos(f * TAU + rotation) * radius))
                        .add(w.scale(Math.sin(f * TAU + rotation) * radius)),
                f -> width, CurveTube.MODE_RUNE, aux, intensity, alpha);
    }

    static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, double t) {
        double q = 1 - t;
        return a.scale(q * q).add(b.scale(2 * q * t)).add(c.scale(t * t));
    }
}

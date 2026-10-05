package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.BlackHoleVisualConfig;
import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.client.RedshiftAbyssShaders;
import com.gang.lightpollution.client.RedshiftAbyssShaders.Pass;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.gang.lightpollution.entity.RedshiftAbyssEntity;
import com.gang.lightpollution.fx.RedshiftAbyssShape;
import com.gang.lightpollution.fx.RedshiftDiskParameters;
import com.gang.lightpollution.fx.RedshiftTemporalPolicy;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owner Buffer A -> B atlas -> C/D blur -> Image grade, integrated with game depth. */
public final class RedshiftAbyssRenderer {
    private static final int MAX_VISIBLE = 2;
    private static final BufferBuilder QUAD = new BufferBuilder(256);
    private static final Map<UUID, History> HISTORIES = new HashMap<>();
    private static TextureTarget scene;
    private static TextureTarget trace;
    private static TextureTarget downsample;
    private static TextureTarget horizontal;
    private static TextureTarget bloom;
    private static ClientLevel level;
    private static boolean failed;

    private RedshiftAbyssRenderer() {}

    public static boolean available() {
        return !failed && RedshiftAbyssShaders.ready();
    }

    public static void render(List<RedshiftAbyssEntity> effects, Matrix4f projection,
                              Matrix4f view, Camera camera, float partialTick) {
        if (!RenderSystem.isOnRenderThread()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }
        if (effects.isEmpty()) {
            if (scene != null || trace != null || !HISTORIES.isEmpty()) {
                try (SceneRenderState ignored = new SceneRenderState(9)) {
                    releaseTargets();
                }
            }
            return;
        }
        if (BlackHoleRenderer.lowMode() || BlackHoleVisualConfig.mode == BlackHoleVisualConfig.Mode.PARTICLES) {
            if (scene != null || trace != null || !HISTORIES.isEmpty()) {
                try (SceneRenderState ignored = new SceneRenderState(9)) {
                    releaseTargets();
                }
            }
            return;
        }
        if (minecraft.level == null || failed || !RedshiftAbyssShaders.ready()) return;
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0 || main.getDepthTextureId() < 0) return;

        long timing = PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try (SceneRenderState ignored = new SceneRenderState(9)) {
            ensureTargets(main);
            pruneHistories(effects);
            Matrix4f rotation = new Matrix4f(view).setTranslation(0, 0, 0);
            Matrix4f inverse = new Matrix4f(projection).invert();
            Vec3 cameraPosition = camera.getPosition();
            int steps = stepBudget();
            RedshiftDiskParameters physics = RedshiftDiskParameters.from(
                    SpellLightConfig.redshiftAbyssMassSolar,
                    SpellLightConfig.redshiftAbyssAccretionRate,
                    SpellLightConfig.redshiftAbyssSourceTimeRate,
                    SpellLightConfig.redshiftAbyssRotationSpeed);
            float supportRs = RedshiftAbyssShape.volumeRadius(
                    SpellLightConfig.redshiftAbyssOuterRadiusRs,
                    SpellLightConfig.redshiftAbyssDiskThicknessRs);
            double gameTimeSeconds = (minecraft.level.getGameTime() + partialTick) / 20.0;

            var sorted = new ArrayList<>(effects);
            sorted.sort(Comparator.comparingDouble(effect -> effect.distanceToSqr(cameraPosition)));
            if (sorted.size() > MAX_VISIBLE) sorted.subList(MAX_VISIBLE, sorted.size()).clear();
            Collections.reverse(sorted);

            for (RedshiftAbyssEntity effect : sorted) {
                var parameters = BlackHoleUniforms.opticalParameters(effect, partialTick);
                float radius = parameters.bhHorizonRadius();
                float fade = parameters.bhEnvelope();
                if (radius <= 0.005F || fade <= 0.001F) continue;

                Vec3 relative = effect.position().subtract(cameraPosition);
                Vector3f centre = rotation.transformPosition(new Vector3f(
                        (float) relative.x, (float) relative.y, (float) relative.z));
                float support = radius * supportRs;
                var bounds = ScreenEffectBounds.sphere(
                        projection, centre, support, trace.width, trace.height);
                if (bounds.empty()) continue;

                Vec3 axis = effect.spinAxis();
                Vec3 side = axis.cross(new Vec3(0, 0, 1)).normalize();
                Vec3 forward = side.cross(axis).normalize();
                Vector3f x = eye(rotation, side);
                Vector3f y = eye(rotation, axis);
                Vector3f z = eye(rotation, forward);
                History history = history(effect.getUUID(), trace.width, trace.height);
                HistorySettings settings = historySettings(trace.width, trace.height, steps);
                int revision = Objects.equals(settings, history.settings)
                        ? history.revision : history.revision + 1;
                float[] transform = historyTransform(projection, centre, x, y, z, radius);
                boolean temporalEnabled = SpellLightConfig.redshiftAbyssTemporalAccumulation;
                double elapsed = gameTimeSeconds - history.gameTimeSeconds;
                boolean resetHistory = RedshiftTemporalPolicy.shouldReset(
                        temporalEnabled, history.valid, elapsed, transform, history.transform,
                        revision, history.revision);
                float blendWeight = resetHistory ? 1.0F
                        : RedshiftTemporalPolicy.blendWeight(elapsed, physics.temporalHalfLife());

                copyScene(main);
                fullscreenState();
                trace.setClearColor(0, 0, 0, 0);
                trace.clear(Minecraft.ON_OSX);
                trace.bindWrite(true);
                RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                ShaderInstance ray = RedshiftAbyssShaders.get(Pass.TRACE);
                ray.setSampler("bhDepthSampler", scene.getDepthTextureId());
                ray.setSampler("bhHistorySampler", history.target.getColorTextureId());
                ray.setSampler("bhHistoryDepthSampler", history.target.getDepthTextureId());
                vec4(ray, "bhCenterRadius", centre.x, centre.y, centre.z, radius);
                vec4(ray, "bhDiskX", x.x, x.y, x.z, 0);
                vec4(ray, "bhDiskY", y.x, y.y, y.z, 0);
                vec4(ray, "bhDiskZ", z.x, z.y, z.z, 0);
                BlackHoleUniforms.upload(ray, parameters, projection, inverse, rotation,
                        cameraPosition, main.width, main.height, support, steps);
                vec4(ray, "bhTiming", effect.visualTime(partialTick),
                        parameters.bhAgeTicks(), fade, parameters.bhTimeScale());
                vec4(ray, "bhDiskSettings",
                        SpellLightConfig.redshiftAbyssRotationSpeed,
                        SpellLightConfig.redshiftAbyssDiskBrightness,
                        SpellLightConfig.redshiftAbyssTemperature,
                        SpellLightConfig.redshiftAbyssDopplerStrength
                                * BlackHoleVisualConfig.dopplerStrength);
                vec4(ray, "bhSourcePhysics", physics.lightSpeedPerRs(),
                        physics.temperatureArgument(), physics.peakTemperature4(),
                        physics.rsLightYears());
                vec4(ray, "bhSourceDisk",
                        SpellLightConfig.redshiftAbyssInnerRadiusRs,
                        SpellLightConfig.redshiftAbyssOuterRadiusRs,
                        SpellLightConfig.redshiftAbyssDiskThicknessRs,
                        SpellLightConfig.redshiftAbyssNoiseContrast);
                vec4(ray, "bhSourceOptions",
                        SpellLightConfig.redshiftAbyssSourceTimeRate,
                        SpellLightConfig.redshiftAbyssShiftMax, 0, 0);
                vec4(ray, "bhTemporal", blendWeight, resetHistory ? 0 : 1, 0, 0);
                vec4(ray, "bhResolution", trace.width, trace.height,
                        1F / trace.width, 1F / trace.height);
                matrix(ray, "bhInverseProjectionMatrix", inverse);
                draw(ray);
                RenderSystem.disableScissor();

                boolean captured = !temporalEnabled || captureHistory(history.target);
                if (RedshiftTemporalPolicy.isHistoryCaptureValid(
                        temporalEnabled, captured, gameTimeSeconds, transform)) {
                    history.valid = true;
                    history.gameTimeSeconds = gameTimeSeconds;
                    history.transform = transform;
                    history.settings = settings;
                    history.revision = revision;
                } else {
                    history.valid = false;
                }

                ShaderInstance reduce = RedshiftAbyssShaders.get(Pass.DOWNSAMPLE);
                downsample.bindWrite(true);
                reduce.setSampler("bhInputSampler", trace.getColorTextureId());
                vec4(reduce, "bhResolution", trace.width, trace.height,
                        1F / trace.width, 1F / trace.height);
                draw(reduce);

                ShaderInstance blur = RedshiftAbyssShaders.get(Pass.BLOOM);
                horizontal.bindWrite(true);
                blur.setSampler("bhInputSampler", downsample.getColorTextureId());
                vec4(blur, "bhBlurStep", 0.5F / trace.width, 0, 0, 0);
                draw(blur);
                bloom.bindWrite(true);
                blur.setSampler("bhInputSampler", horizontal.getColorTextureId());
                vec4(blur, "bhBlurStep", 0, 0.5F / trace.height, 0, 0);
                draw(blur);

                main.bindWrite(true);
                ShaderInstance composite = RedshiftAbyssShaders.get(Pass.COMPOSITE);
                composite.setSampler("bhSceneSampler", scene.getColorTextureId());
                composite.setSampler("bhDepthSampler", scene.getDepthTextureId());
                composite.setSampler("bhEffectSampler", trace.getColorTextureId());
                composite.setSampler("bhBloomSampler", bloom.getColorTextureId());
                vec4(composite, "bhCenterRadius", centre.x, centre.y, centre.z, radius);
                BlackHoleUniforms.upload(composite, parameters, projection, inverse, rotation,
                        cameraPosition, main.width, main.height, support, steps);
                vec4(composite, "bhR1Options",
                        BlackHoleRenderer.hasDedicatedLens(effect.getUUID()) ? 0 : 1,
                        SpellLightConfig.redshiftAbyssBloomStrength, 0, 0);
                vec4(composite, "bhResolution", trace.width, trace.height,
                        1F / trace.width, 1F / trace.height);
                matrix(composite, "bhInverseProjectionMatrix", inverse);
                matrix(composite, "bhProjectionMatrix", projection);
                draw(composite);
            }
        } catch (RuntimeException failure) {
            if (QUAD.building()) QUAD.end().release();
            failed = true;
            ExampleMod.LOGGER.warn(
                    "Redshift Abyss rendering disabled until resource reload; gameplay and other spells are unaffected",
                    failure);
        } finally {
            PerfTracker.end(PerfTracker.Section.CINEMATIC, timing);
        }
    }

    private static HistorySettings historySettings(int width, int height, int steps) {
        return new HistorySettings(width, height, steps, BlackHoleVisualConfig.debugMode,
                SpellLightConfig.redshiftAbyssRotationSpeed,
                SpellLightConfig.redshiftAbyssDiskBrightness,
                SpellLightConfig.redshiftAbyssTemperature,
                SpellLightConfig.redshiftAbyssDopplerStrength * BlackHoleVisualConfig.dopplerStrength,
                SpellLightConfig.redshiftAbyssMassSolar,
                SpellLightConfig.redshiftAbyssAccretionRate,
                SpellLightConfig.redshiftAbyssSourceTimeRate,
                SpellLightConfig.redshiftAbyssInnerRadiusRs,
                SpellLightConfig.redshiftAbyssOuterRadiusRs,
                SpellLightConfig.redshiftAbyssDiskThicknessRs,
                SpellLightConfig.redshiftAbyssNoiseContrast,
                SpellLightConfig.redshiftAbyssShiftMax);
    }

    private static float[] historyTransform(Matrix4f projection, Vector3f centre,
                                            Vector3f x, Vector3f y, Vector3f z, float radius) {
        Matrix4f model = new Matrix4f();
        model.m00(x.x * radius).m01(x.y * radius).m02(x.z * radius);
        model.m10(y.x * radius).m11(y.y * radius).m12(y.z * radius);
        model.m20(z.x * radius).m21(z.y * radius).m22(z.z * radius);
        model.m30(centre.x).m31(centre.y).m32(centre.z);
        return new Matrix4f(projection).mul(model).get(new float[16]);
    }

    private static Vector3f eye(Matrix4f rotation, Vec3 value) {
        return rotation.transformDirection(new Vector3f(
                (float) value.x, (float) value.y, (float) value.z)).normalize();
    }

    private static int stepBudget() {
        int steps = switch (SpellLightConfig.qualityPreset) {
            case LOW -> 96;
            case MEDIUM -> 144;
            case HIGH -> 200;
            case ULTRA -> 240;
        };
        return Math.max(80, AdaptiveVisualQuality.volumeSteps(steps));
    }

    private static void ensureTargets(RenderTarget main) {
        if (scene != null && (scene.width != main.width || scene.height != main.height
                || scene.isStencilEnabled() != main.isStencilEnabled())) {
            releaseTargets();
        }
        if (scene == null) {
            scene = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled()) scene.enableStencil();
        }
        float scale = switch (SpellLightConfig.qualityPreset) {
            case LOW -> 0.4F;
            case MEDIUM -> 0.5F;
            case HIGH -> 0.67F;
            case ULTRA -> 1F;
        };
        scale *= AdaptiveVisualQuality.volumeResolutionScale();
        scale = Math.min(scale, 1280F / Math.max(1, main.width));
        int width = Math.max(1, Math.round(main.width * scale));
        int height = Math.max(1, Math.round(main.height * scale));
        if (trace != null && (trace.width != width || trace.height != height)) {
            releaseEffects();
        }
        if (trace == null) {
            trace = hdr(width, height, false, false);
            downsample = hdr(width, height, false, false);
            horizontal = hdr(width, height, false, false);
            bloom = hdr(width, height, false, false);
        }
    }

    private static History history(UUID id, int width, int height) {
        History history = HISTORIES.computeIfAbsent(id, ignored -> new History());
        if (history.target == null || history.target.width != width || history.target.height != height
                || history.target.isStencilEnabled() != scene.isStencilEnabled()) {
            if (history.target != null) history.target.destroyBuffers();
            history.target = hdr(width, height, true, scene.isStencilEnabled());
            history.target.setClearColor(0, 0, 0, 0);
            history.target.clear(Minecraft.ON_OSX);
            history.valid = false;
        }
        return history;
    }

    private static TextureTarget hdr(int width, int height, boolean useDepth, boolean stencil) {
        TextureTarget target = new TextureTarget(width, height, useDepth, Minecraft.ON_OSX);
        // Depth blits require identical formats. Forge replaces the attachment with
        // packed depth/stencil when enabled; do this BEFORE upgrading colour to HDR,
        // since enableStencil() rebuilds the entire TextureTarget (including colour).
        if (stencil) target.enableStencil();
        GlStateManager._bindTexture(target.getColorTextureId());
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        target.setFilterMode(GL11.GL_LINEAR);
        target.bindWrite(true);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            target.destroyBuffers();
            throw new IllegalStateException("Redshift Abyss HDR framebuffer incomplete");
        }
        return target;
    }

    private static boolean captureHistory(TextureTarget history) {
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR) {
            // Discard stale driver errors so the result below describes these blits.
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, trace.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, history.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, trace.width, trace.height,
                0, 0, history.width, history.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, history.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, scene.width, scene.height,
                0, 0, history.width, history.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                == GL30.GL_FRAMEBUFFER_COMPLETE && GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    private static void copyScene(RenderTarget main) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height,
                0, 0, scene.width, scene.height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void pruneHistories(List<RedshiftAbyssEntity> effects) {
        Set<UUID> active = new HashSet<>();
        for (RedshiftAbyssEntity effect : effects) active.add(effect.getUUID());
        var iterator = HISTORIES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (active.contains(entry.getKey())) continue;
            if (entry.getValue().target != null) entry.getValue().target.destroyBuffers();
            iterator.remove();
        }
    }

    private static void fullscreenState() {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableScissor();
    }

    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        QUAD.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        QUAD.vertex(-1, -1, 0).uv(0, 0).endVertex();
        QUAD.vertex(1, -1, 0).uv(1, 0).endVertex();
        QUAD.vertex(1, 1, 0).uv(1, 1).endVertex();
        QUAD.vertex(-1, 1, 0).uv(0, 1).endVertex();
        int gpu = PerfTracker.beginGpu(PerfTracker.Section.CINEMATIC);
        try {
            BufferUploader.drawWithShader(QUAD.end());
        } finally {
            PerfTracker.endGpu(gpu);
        }
    }

    private static void matrix(ShaderInstance shader, String key, Matrix4f value) {
        var uniform = shader.getUniform(key);
        if (uniform != null) uniform.set(value);
    }

    private static void vec4(ShaderInstance shader, String key,
                             float a, float b, float c, float d) {
        var uniform = shader.getUniform(key);
        if (uniform != null) uniform.set(a, b, c, d);
    }

    private static void releaseEffects() {
        if (trace != null) trace.destroyBuffers();
        if (downsample != null) downsample.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        if (bloom != null) bloom.destroyBuffers();
        trace = null;
        downsample = null;
        horizontal = null;
        bloom = null;
        for (History history : HISTORIES.values()) {
            if (history.target != null) history.target.destroyBuffers();
        }
        HISTORIES.clear();
    }

    private static void releaseTargets() {
        releaseEffects();
        if (scene != null) scene.destroyBuffers();
        scene = null;
    }

    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(RedshiftAbyssRenderer::reset);
            return;
        }
        if (scene != null || trace != null || !HISTORIES.isEmpty()) {
            try (SceneRenderState ignored = new SceneRenderState(9)) {
                releaseTargets();
            }
        }
        failed = false;
        level = null;
    }

    private static final class History {
        private TextureTarget target;
        private boolean valid;
        private double gameTimeSeconds;
        private float[] transform;
        private HistorySettings settings;
        private int revision;
    }

    private record HistorySettings(
            int width, int height, int steps, int debug,
            float rotationSpeed, float brightness, float temperature, float doppler,
            float massSolar, float accretionRate, float sourceTimeRate,
            float innerRadius, float outerRadius, float halfThickness,
            float noiseContrast, float shiftMax) {}
}

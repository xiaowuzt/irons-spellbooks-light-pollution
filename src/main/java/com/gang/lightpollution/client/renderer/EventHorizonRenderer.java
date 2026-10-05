package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.client.EventHorizonShaders;
import com.gang.lightpollution.client.EventHorizonShaders.Pass;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.gang.lightpollution.entity.EventHorizonEntity;
import com.gang.lightpollution.fx.EventHorizonShape;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.util.*;

/** World-space bent-ray volume -> HDR bloom pyramid -> depth-aware scene composite. */
public final class EventHorizonRenderer {
    private static final int LEVELS = 6, MAX_VISIBLE = 3;
    private static final BufferBuilder QUAD = new BufferBuilder(256);
    private static TextureTarget scene, trace;
    private static final TextureTarget[] bloom = new TextureTarget[LEVELS], scratch = new TextureTarget[LEVELS];
    private static int noise;
    private static ClientLevel level;
    private static boolean failed;
    private EventHorizonRenderer() {}

    public static void render(List<EventHorizonEntity> effects, Matrix4f projection, Matrix4f view, Camera camera, float partialTick) {
        if (!RenderSystem.isOnRenderThread()) return;
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level) { reset(); level = mc.level; }
        if (effects.isEmpty()) {
            // TextureTarget.destroyBuffers unbinds the framebuffer and textures too.
            // Expiring a spell must not redirect the rest of Minecraft's frame to framebuffer 0.
            if (scene != null || trace != null) {
                try (SceneRenderState ignored = new SceneRenderState(9)) { releaseTargets(); }
            }
            return;
        }
        if (mc.level == null || failed || !EventHorizonShaders.ready()) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0 || main.getDepthTextureId() < 0) return;
        long timing = PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try (SceneRenderState ignored = new SceneRenderState(9)) {
            ensureTargets(main);
            Matrix4f rotation = new Matrix4f(view).setTranslation(0, 0, 0);
            Matrix4f inverse = new Matrix4f(projection).invert();
            Vec3 cameraPos = camera.getPosition();
            // Keep the nearest three, draw back-to-front. A stack of casts cannot cause unbounded GPU work.
            var sorted = new ArrayList<>(effects);
            sorted.sort(Comparator.comparingDouble(e -> e.distanceToSqr(cameraPos)));
            if (sorted.size() > MAX_VISIBLE) sorted.subList(MAX_VISIBLE, sorted.size()).clear();
            Collections.reverse(sorted);
            for (EventHorizonEntity e : sorted) {
                float radius = e.unitRadius(partialTick) * SpellLightConfig.eventHorizonVisualScale, fade = e.envelope(partialTick);
                if (radius <= .005F || fade <= .001F) continue;
                Vec3 relative = e.position().subtract(cameraPos);
                Vector3f centre = rotation.transformPosition(new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
                var bounds = ScreenEffectBounds.sphere(projection, centre, radius * EventHorizonShape.VOLUME_RADIUS, trace.width, trace.height);
                if (bounds.empty()) continue;
                Vec3 axis = e.spinAxis();
                // The disk basis is fixed in the WORLD, not recomputed from the camera each frame.
                Vec3 side = axis.cross(new Vec3(0, 0, 1)).normalize();
                Vec3 forward = side.cross(axis).normalize();
                Vector3f x = eye(rotation, side), y = eye(rotation, axis), z = eye(rotation, forward);
                copyScene(main);
                fullscreenState();
                trace.setClearColor(0, 0, 0, 0); trace.clear(Minecraft.ON_OSX); trace.bindWrite(true);
                RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                ShaderInstance ray = EventHorizonShaders.get(Pass.TRACE);
                ray.setSampler("DepthSampler", scene.getDepthTextureId()); ray.setSampler("NoiseSampler", noiseTexture());
                vec4(ray, "HoleCentre", centre.x, centre.y, centre.z, radius);
                vec4(ray, "DiskX", x.x, x.y, x.z, 0); vec4(ray, "DiskY", y.x, y.y, y.z, 0); vec4(ray, "DiskZ", z.x, z.y, z.z, 0);
                vec4(ray, "State", e.visualTime(partialTick), fade, stepBudget(), EventHorizonShape.VOLUME_RADIUS);
                vec4(ray, "DiskSettings", SpellLightConfig.eventHorizonRotationSpeed,
                        SpellLightConfig.eventHorizonDiskBrightness, 0, 0);
                matrix(ray, "InverseProjectionMat", inverse);
                draw(ray);
                RenderSystem.disableScissor();
                ShaderInstance blur = EventHorizonShaders.get(Pass.BLOOM);
                RenderTarget source = trace;
                for (int i = 0; i < LEVELS; i++) {
                    scratch[i].bindWrite(true); blur.setSampler("InputSampler", source.getColorTextureId());
                    vec4(blur, "BlurStep", 1F / scratch[i].width, 0, 0, 0); draw(blur);
                    bloom[i].bindWrite(true); blur.setSampler("InputSampler", scratch[i].getColorTextureId());
                    vec4(blur, "BlurStep", 0, 1F / bloom[i].height, 0, 0); draw(blur);
                    source = bloom[i];
                }
                main.bindWrite(true);
                ShaderInstance composite = EventHorizonShaders.get(Pass.COMPOSITE);
                composite.setSampler("SceneSampler", scene.getColorTextureId());
                composite.setSampler("DepthSampler", scene.getDepthTextureId());
                composite.setSampler("EffectSampler", trace.getColorTextureId());
                for (int i = 0; i < LEVELS; i++) composite.setSampler("Bloom" + i, bloom[i].getColorTextureId());
                vec4(composite, "HoleCentre", centre.x, centre.y, centre.z, radius);
                vec4(composite, "State", e.visualTime(partialTick), fade, 0, EventHorizonShape.VOLUME_RADIUS);
                matrix(composite, "InverseProjectionMat", inverse); matrix(composite, "ProjectionMat", projection);
                draw(composite);
            }
        } catch (RuntimeException failure) {
            if (QUAD.building()) QUAD.end().release();
            failed = true;
            ExampleMod.LOGGER.warn("Event Horizon rendering disabled until resource reload; gameplay and other spells are unaffected", failure);
        } finally { PerfTracker.end(PerfTracker.Section.CINEMATIC, timing); }
    }
    private static Vector3f eye(Matrix4f rotation, Vec3 v) {
        return rotation.transformDirection(new Vector3f((float) v.x, (float) v.y, (float) v.z)).normalize();
    }
    private static int stepBudget() {
        int steps = switch (SpellLightConfig.qualityPreset) { case LOW -> 96; case MEDIUM -> 144; case HIGH -> 200; case ULTRA -> 240; };
        return Math.max(80, AdaptiveVisualQuality.volumeSteps(steps));
    }
    private static void ensureTargets(RenderTarget main) {
        if (scene != null && (scene.width != main.width || scene.height != main.height || scene.isStencilEnabled() != main.isStencilEnabled())) releaseTargets();
        if (scene == null) {
            scene = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled()) scene.enableStencil();
        }
        float scale = switch (SpellLightConfig.qualityPreset) { case LOW -> .4F; case MEDIUM -> .5F; case HIGH -> .67F; case ULTRA -> 1F; };
        scale *= AdaptiveVisualQuality.volumeResolutionScale();
        scale = Math.min(scale, 1280F / Math.max(1, main.width));
        int width = Math.max(1, Math.round(main.width * scale)), height = Math.max(1, Math.round(main.height * scale));
        if (trace != null && (trace.width != width || trace.height != height)) releaseEffects();
        if (trace == null) {
            trace = hdr(width, height);
            for (int i = 0; i < LEVELS; i++) {
                int w = Math.max(1, width >> (i + 1)), h = Math.max(1, height >> (i + 1));
                scratch[i] = hdr(w, h); bloom[i] = hdr(w, h);
            }
        }
    }
    private static TextureTarget hdr(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        GlStateManager._bindTexture(target.getColorTextureId());
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        target.setFilterMode(GL11.GL_LINEAR);
        target.bindWrite(true);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            target.destroyBuffers(); throw new IllegalStateException("Event Horizon HDR framebuffer incomplete");
        }
        return target;
    }
    private static void copyScene(RenderTarget main) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }
    private static int noiseTexture() {
        if (noise != 0) return noise;
        noise = GlStateManager._genTexture(); GlStateManager._bindTexture(noise);
        var pixels = BufferUtils.createByteBuffer(256 * 256 * 4);
        pixels.put(com.gang.lightpollution.fx.EventHorizonNoise.rgba());
        pixels.flip();
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 256, 256, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        return noise;
    }
    private static void fullscreenState() {
        RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.colorMask(true, true, true, true); RenderSystem.disableScissor();
    }
    private static void draw(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        QUAD.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        QUAD.vertex(-1,-1,0).uv(0,0).endVertex(); QUAD.vertex(1,-1,0).uv(1,0).endVertex();
        QUAD.vertex(1,1,0).uv(1,1).endVertex(); QUAD.vertex(-1,1,0).uv(0,1).endVertex();
        int gpu = PerfTracker.beginGpu(PerfTracker.Section.CINEMATIC);
        try { BufferUploader.drawWithShader(QUAD.end()); } finally { PerfTracker.endGpu(gpu); }
    }
    private static void matrix(ShaderInstance shader, String key, Matrix4f v) { var u = shader.getUniform(key); if (u != null) u.set(v); }
    private static void vec4(ShaderInstance shader, String key, float a, float b, float c, float d) { var u = shader.getUniform(key); if (u != null) u.set(a,b,c,d); }
    private static void releaseEffects() {
        if (trace != null) trace.destroyBuffers(); trace = null;
        for (int i = 0; i < LEVELS; i++) {
            if (bloom[i] != null) bloom[i].destroyBuffers(); bloom[i] = null;
            if (scratch[i] != null) scratch[i].destroyBuffers(); scratch[i] = null;
        }
    }
    private static void releaseTargets() { releaseEffects(); if (scene != null) scene.destroyBuffers(); scene = null; }
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(EventHorizonRenderer::reset); return; }
        if (scene != null || trace != null || noise != 0) {
            try (SceneRenderState ignored = new SceneRenderState(9)) {
                releaseTargets();
                if (noise != 0) GlStateManager._deleteTexture(noise);
                noise = 0;
            }
        }
        failed = false; level = null;
    }
}

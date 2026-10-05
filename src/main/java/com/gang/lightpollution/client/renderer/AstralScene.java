package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Local, world-space ray volumes and analytic bodies. Copies scene depth before sampling it;
 * never reads a texture attached to the framebuffer currently being written.
 * No camera-facing gas cards, persistent fullscreen blur, or cross-frame accumulation.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AstralScene {
    private static TextureTarget scene;
    private static TextureTarget volumeTarget;
    private static ClientLevel level;
    private static final BufferBuilder QUAD = new BufferBuilder(512);
    private static boolean warned;
    private static ShaderInstance lastVolumeShader;
    private static final java.util.Set<ShaderInstance> FAILED =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private AstralScene() { }

    public static Frame frame(RenderLevelStageEvent event) {
        return new Frame(event.getCamera().getPosition(), event.getProjectionMatrix(),
                SpellRenderStage.levelPose(event));
    }

    /** Also used by Constellation's renderer, which has already installed the level matrices. */
    public static Frame current(Vec3 camera) {
        return new Frame(camera, RenderSystem.getProjectionMatrix(), RenderSystem.getModelViewMatrix());
    }

    public static final class Frame {
        private final Vec3 camera;
        private final Matrix4f projection, rotationView, viewProjection, inverse;
        private final FrustumIntersection frustum;

        private Frame(Vec3 camera, Matrix4f projection, Matrix4f view) {
            this.camera = camera;
            this.projection = new Matrix4f(projection);
            this.rotationView = new Matrix4f(view).setTranslation(0, 0, 0);
            viewProjection = new Matrix4f(projection).mul(rotationView);
            inverse = new Matrix4f(viewProjection).invert();
            frustum = new FrustumIntersection(viewProjection);
        }

        /** Includes the full influence radius, also when the centre is outside the viewport. */
        public boolean visible(Vec3 centre, double radius) {
            Vec3 relative = centre.subtract(camera);
            if (!Double.isFinite(radius) || !Double.isFinite(relative.lengthSqr())) return true;
            if (relative.lengthSqr() <= radius * radius) return true;
            float x = (float) relative.x, y = (float) relative.y, z = (float) relative.z;
            float r = (float) Math.max(0.001, radius);
            // Ray volumes can extend beyond the terrain's far plane (or in front of its
            // near plane). Test the side planes only, using a conservative enclosing box.
            int sides = FrustumIntersection.PLANE_MASK_NX | FrustumIntersection.PLANE_MASK_PX
                    | FrustumIntersection.PLANE_MASK_NY | FrustumIntersection.PLANE_MASK_PY;
            return relative.lengthSqr() <= (320 + radius) * (320 + radius)
                    && frustum.intersectAab(x - r, y - r, z - r, x + r, y + r, z + r, sides) < 0;
        }

        public boolean available() {
            return ConstellationShaders.astralBody() != null && !FAILED.contains(ConstellationShaders.astralBody());
        }

        /** Local basis: U and W lie in the orbital plane, V is its normal. */
        public boolean volume(int mode, Vec3 centre, float radius, Vec3 normal, float age, float seed,
                           float visibility, float a, float b, float c, float d) {
            return draw(ConstellationShaders.astralVolume(), centre, radius, normal, age, seed, visibility,
                    mode, a, b, c, d, Vec3.ZERO, false, false);
        }

        /** A burning ellipsoid (mode 0), or an opaque horizon with a thin photon rim (mode 1). */
        public boolean body(Vec3 centre, Vec3 radii, Vec3 majorAxis, float age, float seed,
                         float temperature, float visibility, float intensity, boolean horizon) {
            if (radii.x <= 0 || radii.y <= 0 || radii.z <= 0) return false;
            return draw(ConstellationShaders.astralBody(), centre, (float) radii.length(), majorAxis,
                    age, seed, visibility, horizon ? 1 : 0, temperature, intensity, 0, 0,
                    radii, true, false);
        }

        public boolean distort(Vec3 centre, float radius, float age, float strength, boolean shock) {
            strength *= SpellLightConfig.localDistortionStrength;
            if (strength <= 0.001F) return false;
            return draw(ConstellationShaders.astralDistortion(), centre, radius, new Vec3(0, 1, 0),
                    age, 0, strength, shock ? 1 : 0, 0, 0, 0, 0, Vec3.ZERO, false, true);
        }

        private boolean draw(ShaderInstance shader, Vec3 centre, float radius, Vec3 normal,
                          float age, float seed, float visibility, int mode,
                          float a, float b, float c, float d, Vec3 radii, boolean body, boolean distortion) {
            if (shader == null || visibility <= 0.002F || radius <= 0.001F) return false;
            double influenceRadius = body || distortion ? radius : radius * (mode > 3 ? 2.85 : 1.88);
            // A successfully culled layer must not activate its old fallback geometry.
            if (!visible(centre, influenceRadius)) return true;
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            if (mc.level == null || main == null || main.getDepthTextureId() < 0
                    || main.width <= 0 || main.height <= 0) return false;
            if (lastVolumeShader != ConstellationShaders.astralVolume()) {
                lastVolumeShader = ConstellationShaders.astralVolume();
                warned = false;
                FAILED.clear();
            }
            if (FAILED.contains(shader)) return false;
            long timing = PerfTracker.begin(PerfTracker.Section.ASTRAL);
            ShaderInstance rendering = shader;
            try (SceneRenderState ignored = new SceneRenderState()) {
                // RenderTarget allocates on the active unit. Restrict it to a unit captured by
                // SceneRenderState rather than accidentally overwriting another mod's unit 3+.
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                ensureTarget(mc, main);
                ShaderInstance composite = ConstellationShaders.astralComposite();
                boolean reducedVolume = !body && !distortion && composite != null
                        && !FAILED.contains(composite) && AdaptiveVisualQuality.volumeResolutionScale() < 0.999F;
                if (reducedVolume) ensureVolumeTarget(main);
                // Blitting depth for EACH layer also includes opaque bodies drawn by earlier layers.
                GlStateManager._disableScissorTest();
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
                GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0,
                        main.width, main.height, GL11.GL_DEPTH_BUFFER_BIT
                                | (distortion ? GL11.GL_COLOR_BUFFER_BIT : 0), GL11.GL_NEAREST);
                RenderTarget destination = reducedVolume ? volumeTarget : main;
                if (reducedVolume) {
                    volumeTarget.setClearColor(0, 0, 0, 0);
                    volumeTarget.clear(Minecraft.ON_OSX);
                }
                destination.bindWrite(true);
                scissor(centre, influenceRadius, destination);
                if (body) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                RenderSystem.depthMask(body && visibility >= 0.995F);
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                if (distortion) RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ZERO);
                else if (body) RenderSystem.defaultBlendFunc();
                else RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                shader.setSampler("DepthSampler", scene.getDepthTextureId());
                if (distortion) shader.setSampler("SceneSampler", scene.getColorTextureId());
                matrix(shader, "InverseViewProjection", inverse);
                matrix(shader, "ViewProjection", viewProjection);
                if (shader.getUniform("SceneSize") != null) shader.getUniform("SceneSize").set((float) destination.width, (float) destination.height);
                Vec3 relative = centre.subtract(camera);
                shader.getUniform("CenterRadius").set((float) relative.x, (float) relative.y, (float) relative.z, radius);
                Vec3 v = normal.normalize();
                if (v.lengthSqr() < 0.1) v = new Vec3(0, 1, 0);
                Vec3 u = CinematicVisuals.planeU(v), w = v.cross(u).normalize();
                vector(shader, "AxisU", u); vector(shader, "AxisV", v); vector(shader, "AxisW", w);
                vector(shader, "BodyRadii", radii);
                CinematicVisuals.uniform(shader, "EffectTime", age * 0.05F);
                CinematicVisuals.uniform(shader, "EffectSeed", seed);
                CinematicVisuals.uniform(shader, "Visibility", visibility);
                CinematicVisuals.uniform(shader, "Mode", mode);
                CinematicVisuals.uniform(shader, "SampleCount", AdaptiveVisualQuality.volumeSteps(112));
                if (shader.getUniform("Parameters") != null) shader.getUniform("Parameters").set(a, b, c, d);
                RenderSystem.setShader(() -> shader);
                drawQuad();
                if (reducedVolume) {
                    rendering = composite;
                    main.bindWrite(true);
                    scissor(centre, influenceRadius, main);
                    composite.setSampler("VolumeSampler", volumeTarget.getColorTextureId());
                    composite.setSampler("DepthSampler", scene.getDepthTextureId());
                    matrix(composite, "InverseViewProjection", inverse);
                    composite.getUniform("SceneSize").set((float) main.width, (float) main.height);
                    composite.getUniform("VolumeSize").set((float) volumeTarget.width, (float) volumeTarget.height);
                    RenderSystem.setShader(() -> composite);
                    drawQuad();
                }
                return true;
            } catch (RuntimeException failure) {
                if (QUAD.building()) QUAD.end().release();
                FAILED.add(rendering);
                if (!warned) {
                    warned = true;
                    ExampleMod.LOGGER.warn("A local astral layer could not render; keeping the base spell geometry until resource reload", failure);
                }
                return false;
            } finally {
                PerfTracker.end(PerfTracker.Section.ASTRAL, timing);
            }
        }

        private void scissor(Vec3 centre, double radius, RenderTarget target) {
            Vec3 relative = centre.subtract(camera);
            Vector3f eye = new Vector3f((float) relative.x, (float) relative.y, (float) relative.z);
            rotationView.transformPosition(eye);
            ScreenEffectBounds bounds = ScreenEffectBounds.sphere(projection, eye, (float) radius,
                    target.width, target.height);
            RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
    }

    private static void drawQuad() {
        QUAD.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        QUAD.vertex(-1, -1, 0).uv(0, 0).color(255, 255, 255, 255).endVertex();
        QUAD.vertex(1, -1, 0).uv(1, 0).color(255, 255, 255, 255).endVertex();
        QUAD.vertex(1, 1, 0).uv(1, 1).color(255, 255, 255, 255).endVertex();
        QUAD.vertex(-1, 1, 0).uv(0, 1).color(255, 255, 255, 255).endVertex();
        BufferBuilder.RenderedBuffer mesh = QUAD.end();
        int gpu = PerfTracker.beginGpu(PerfTracker.Section.ASTRAL);
        try {
            BufferUploader.drawWithShader(mesh);
        } finally {
            PerfTracker.endGpu(gpu);
        }
    }

    private static void vector(ShaderInstance shader, String name, Vec3 v) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set((float) v.x, (float) v.y, (float) v.z);
    }
    private static void matrix(ShaderInstance shader, String name, Matrix4f m) {
        if (shader.getUniform(name) != null) shader.getUniform(name).set(m);
    }
    private static void ensureTarget(Minecraft mc, RenderTarget main) {
        if (level != mc.level || scene != null && (scene.width != main.width || scene.height != main.height
                || scene.isStencilEnabled() != main.isStencilEnabled())) release();
        level = mc.level;
        if (scene == null) {
            scene = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            // A depth-only and a packed depth/stencil texture cannot be depth-blitted to each
            // other. Forge lets other mods enable stencil on the main target at runtime.
            if (main.isStencilEnabled()) scene.enableStencil();
        }
    }
    private static void release() {
        if (scene != null) scene.destroyBuffers();
        if (volumeTarget != null) volumeTarget.destroyBuffers();
        scene = null;
        volumeTarget = null;
        level = null;
    }
    private static void ensureVolumeTarget(RenderTarget main) {
        float scale = Math.max(0.25F, Math.min(1.0F, AdaptiveVisualQuality.volumeResolutionScale()));
        int width = Math.max(1, (int) Math.ceil(main.width * scale));
        int height = Math.max(1, (int) Math.ceil(main.height * scale));
        if (volumeTarget != null && (volumeTarget.width != width || volumeTarget.height != height)) {
            volumeTarget.destroyBuffers();
            volumeTarget = null;
        }
        if (volumeTarget == null) volumeTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (RenderSystem.isOnRenderThread()) release(); else RenderSystem.recordRenderCall(AstralScene::release);
    }
}

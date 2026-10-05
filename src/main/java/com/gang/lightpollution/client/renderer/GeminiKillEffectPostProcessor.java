package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.GeminiKillEffectPostShaders;
import com.gang.lightpollution.client.GeminiKillEffectPostShaders.Pass;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Forge 1.20.1 implementation of Gemini's complete KillEffect post chain.
 *
 * <p>Each source {@code #ifdef} variant remains a separate shader program and
 * the passes run in Gemini's original order. Private ping-pong targets prevent
 * feedback sampling from Minecraft's main framebuffer.</p>
 */
public final class GeminiKillEffectPostProcessor {
    private static final float BASE_BLOOM = 0.7F;
    private static BufferBuilder fullscreenBuffer = new BufferBuilder(4_096);

    private static TextureTarget sceneCopy;
    private static TextureTarget bloom;
    private static TextureTarget ping;
    private static TextureTarget pong;
    private static TextureTarget history;
    private static ShaderInstance lastAcesShader;

    private static boolean enabled =
            !Boolean.getBoolean("dirge.funeralNovaPostDisabled");
    private static boolean runtimeDisabled;
    private static boolean failureLogged;
    private static boolean capabilityChecked;
    private static boolean capabilityAvailable;
    private static boolean historyValid;
    private static int historyStage = -1;
    private static float previousElapsed = -1.0F;
    private static int historyFramebuffer = -1;
    private static int historyColorTexture = -1;
    private static Vec3 historyCenter;

    private GeminiKillEffectPostProcessor() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            runtimeDisabled = false;
            failureLogged = false;
        } else {
            release();
        }
    }

    public static boolean isEnabled() {
        return enabled && !runtimeDisabled;
    }

    /**
     * Applies the post chain for the synchronized Funeral Nova visual anchor.
     * The center must match Gemini's visual center ({@code entityY + 1.5}).
     */
    public static boolean render(Vec3 center, int stage, float progress,
                                 float elapsedSeconds, float intensity, float chainFade) {
        if (!enabled || center == null || stage < 1 || stage > 10
                || chainFade <= 0.0001F || !RenderSystem.isOnRenderThread()) {
            return false;
        }

        refreshAfterShaderReload();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) {
            return false;
        }
        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        FrameTarget frameTarget = FrameTarget.capture(mainTarget, snapshot);
        if (runtimeDisabled || !validateRuntime(mainTarget, frameTarget)) {
            return false;
        }

        PostParameters parameters = PostParameters.forFrame(
                center, stage, Mth.clamp(progress, 0.0F, 1.0F),
                Math.max(elapsedSeconds, 0.0F), Math.max(intensity, 0.0F),
                Mth.clamp(chainFade, 0.0F, 1.0F));
        if (!parameters.visible()) {
            return false;
        }

        long started = PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try {
            ensureTargets(frameTarget.width, frameTarget.height);
            if (!targetsIndependent(frameTarget)) {
                throw new IllegalStateException("Funeral Nova post targets are not independent");
            }

            if (historyDiscontinuous(frameTarget, center, stage, elapsedSeconds)) {
                historyValid = false;
            }
            previousElapsed = elapsedSeconds;
            historyStage = stage;
            historyFramebuffer = frameTarget.framebufferId;
            historyColorTexture = frameTarget.colorTextureId;
            historyCenter = center;

            copyColor(frameTarget, sceneCopy);
            UniformData uniforms = createUniforms(minecraft, frameTarget, parameters);
            int depthTexture = frameTarget.depthTextureId;
            boolean hasDepth = depthTexture >= 0;

            runPass(hasDepth ? Pass.BRIGHT_EDGE : Pass.BRIGHT,
                    sceneCopy.getColorTextureId(), ping.getColorTextureId(), depthTexture,
                    ping, uniforms);
            runPass(Pass.BLUR_H, ping.getColorTextureId(), ping.getColorTextureId(), depthTexture,
                    pong, uniforms);
            runPass(Pass.BLUR_V, pong.getColorTextureId(), pong.getColorTextureId(), depthTexture,
                    bloom, uniforms);
            runPass(Pass.COMPOSITE, sceneCopy.getColorTextureId(), bloom.getColorTextureId(), depthTexture,
                    ping, uniforms);

            TextureTarget current = ping;
            if (hasDepth && parameters.lightIntensity > 0.01F) {
                current = runAlternating(Pass.SCREEN_LIGHTING, current, depthTexture, uniforms);
            }
            if (parameters.distortion > 0.001F) {
                current = runAlternating(Pass.DISTORTION, current, depthTexture, uniforms);
            }
            if (parameters.godRay > 0.001F) {
                current = runAlternating(Pass.GODRAY, current, depthTexture, uniforms);
            }
            if (hasDepth && parameters.godRay > 0.01F && parameters.hasLight()
                    && ((parameters.bhStage >= 3 && parameters.bhStage <= 8)
                    || parameters.lightIntensity > 0.01F)) {
                current = runAlternating(Pass.VOLUMETRIC_GODRAY, current, depthTexture, uniforms);
            }
            if (hasDepth && parameters.ssrIntensity > 0.01F
                    && (parameters.bhStage == 7 || parameters.bhStage == 8)) {
                current = runAlternating(Pass.SSRT, current, depthTexture, uniforms);
            }
            if (parameters.chromatic > 0.001F) {
                current = runAlternating(Pass.CHROMATIC, current, depthTexture, uniforms);
            }
            if (uniforms.bhRadiusUv.x > 0.005F
                    && parameters.bhStage >= 3 && parameters.bhStage <= 5) {
                current = runAlternating(Pass.BLACK_HOLE, current, depthTexture, uniforms);
            }
            if (parameters.bhStage == 7) {
                current = runAlternating(Pass.GLOW_FLASH, current, depthTexture, uniforms);
            }
            if (parameters.bhStage == 7 || parameters.bhStage == 8) {
                current = runAlternating(Pass.FLASH_SCREEN, current, depthTexture, uniforms);
            }
            if (parameters.bhStage == 8) {
                current = runAlternating(Pass.SHOCKWAVE, current, depthTexture, uniforms);
                if (!historyValid) {
                    history.clear(Minecraft.ON_OSX);
                }
                TextureTarget destination = other(current);
                runPass(Pass.AFTERIMAGE, current.getColorTextureId(), history.getColorTextureId(),
                        depthTexture, destination, uniforms);
                current = destination;
                copyColor(current, history);
                historyValid = true;
            }

            runFinalPass(Pass.ACES, current.getColorTextureId(), depthTexture,
                    frameTarget, uniforms);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
            return false;
        } finally {
            snapshot.restore();
            PerfTracker.end(PerfTracker.Section.CINEMATIC, started);
        }
    }

    public static void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> resize(width, height));
            return;
        }
        if (!enabled || runtimeDisabled || !checkCapabilities()) return;
        try {
            ensureTargets(width, height);
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
        }
    }

    public static void release() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(GeminiKillEffectPostProcessor::releasePreservingFramebuffers);
            return;
        }
        releasePreservingFramebuffers();
    }

    private static TextureTarget runAlternating(Pass pass, TextureTarget source,
                                                 int depthTexture, UniformData uniforms) {
        TextureTarget destination = other(source);
        ScreenEffectBounds bounds = null;
        if (pass == Pass.BLACK_HOLE) {
            float radius = uniforms.bhRadiusUv.x;
            float stage = uniforms.bhRadiusUv.y;
            float progress = uniforms.bhRadiusUv.z;
            if (stage > 2.5F && stage < 3.5F) radius *= 1.0F - (float) Math.exp(-progress * 4.0F);
            else if (stage > 4.5F) radius *= Math.max(1.0F - progress * progress * 0.85F, 0.01F);
            if (radius < 0.001F) return source;
            float x = uniforms.center1.x * 0.5F + 0.5F;
            float y = uniforms.center1.y * 0.5F + 0.5F;
            float rx = radius * 9.0F, ry = rx * source.width / source.height;
            bounds = ScreenEffectBounds.uv(x - rx, y - ry, x + rx, y + ry, source.width, source.height);
            if (bounds.empty()) return source;
            // Untouched pixels must be current input, never an older ping-pong pass.
            copyColor(source, destination);
        }
        runPass(pass, source.getColorTextureId(), bloom.getColorTextureId(), depthTexture,
                destination, uniforms, bounds);
        return destination;
    }

    private static TextureTarget other(TextureTarget target) {
        return target == ping ? pong : ping;
    }

    private static void runPass(Pass pass, int sceneTexture, int bloomTexture, int depthTexture,
                                TextureTarget destination, UniformData uniforms) {
        runPass(pass, sceneTexture, bloomTexture, depthTexture, destination, uniforms, null);
    }

    private static void runPass(Pass pass, int sceneTexture, int bloomTexture, int depthTexture,
                                TextureTarget destination, UniformData uniforms,
                                ScreenEffectBounds bounds) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(pass);
        if (shader == null) {
            throw new IllegalStateException("Missing Funeral Nova post shader: " + pass);
        }

        destination.bindWrite(true);
        configureFullscreenState();
        if (bounds != null) RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());

        shader.setSampler("SceneSampler", sceneTexture);
        shader.setSampler("BloomSampler", bloomTexture);
        if (depthTexture >= 0) shader.setSampler("DepthSampler", depthTexture);
        applyUniforms(shader, uniforms);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void runFinalPass(Pass pass, int sceneTexture, int depthTexture,
                                     FrameTarget destination, UniformData uniforms) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(pass);
        if (shader == null) {
            throw new IllegalStateException("Missing Funeral Nova post shader: " + pass);
        }

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, destination.framebufferId);
        RenderSystem.viewport(destination.viewportX, destination.viewportY,
                destination.width, destination.height);
        configureFullscreenState();

        shader.setSampler("SceneSampler", sceneTexture);
        if (depthTexture >= 0) shader.setSampler("DepthSampler", depthTexture);
        applyUniforms(shader, uniforms);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void applyUniforms(ShaderInstance shader, UniformData data) {
        set(shader, "Params", data.params);
        set(shader, "TimePack", data.timePack);
        set(shader, "Center1", data.center1);
        set(shader, "Center2", data.center2);
        set(shader, "PassParams", data.passParams);
        set(shader, "BHParams", data.bhRadiusUv);
        set(shader, "CameraParams", data.cameraParams);
        set(shader, "LightViewPos", data.lightViewPos);
        set(shader, "LightColor", data.lightColor);
        // SCREEN_LIGHTING is shared with the VanillaDI multi-light pass.
        // Explicitly clear its metadata discriminator so a prior light pass
        // cannot leak into this legacy single-light effect.
        set(shader, "LightDataParams", new Vec4(1.0F, 1.0F, 0.0F, 0.0F));
        set(shader, "MiscParams", data.miscParams);
    }

    private static void set(ShaderInstance shader, String name, Vec4 value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value.x, value.y, value.z, value.w);
        }
    }

    private static UniformData createUniforms(Minecraft minecraft, FrameTarget target,
                                              PostParameters parameters) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.getPosition();
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(camera.rotation());
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.rotation());
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.rotation());

        float fovRadians = (float) Math.toRadians(
                Mth.clamp(minecraft.options.fov().get(), 30, 110));
        float tanHalfFov = (float) Math.tan(fovRadians * 0.5F);
        float aspect = target.width / (float) target.height;

        Projection centerProjection = project(parameters.center, cameraPosition,
                right, up, forward, tanHalfFov, aspect);
        Projection lightProjection = project(parameters.lightPosition, cameraPosition,
                right, up, forward, tanHalfFov, aspect);
        float bhRadiusUv = 0.0F;
        if (centerProjection.viewZ > 0.05F) {
            bhRadiusUv = Mth.clamp((3.0F / centerProjection.viewZ) / tanHalfFov * 0.5F,
                    0.005F, 2.0F);
        }

        float time = System.currentTimeMillis() / 1_000.0F;
        float farPlane = Math.max(minecraft.options.getEffectiveRenderDistance() * 16.0F * 4.0F,
                256.0F);
        return new UniformData(
                new Vec4(target.width, target.height, parameters.bloom, 0.35F),
                new Vec4(time, time * 60.0F, 0.0F, 0.0F),
                new Vec4(centerProjection.ndcX, centerProjection.ndcY,
                        centerProjection.worldDistance, 0.0F),
                new Vec4(0.0F, 0.0F, 0.0F, 0.0F),
                new Vec4(parameters.distortion, parameters.godRay,
                        parameters.chromatic, parameters.bloomRadius),
                new Vec4(bhRadiusUv, parameters.bhStage,
                        parameters.bhProgress, parameters.bhIntensity),
                new Vec4(fovRadians, aspect, 0.05F, farPlane),
                new Vec4(lightProjection.viewX, lightProjection.viewY,
                        lightProjection.viewZ, parameters.lightRadius),
                new Vec4(parameters.lightRed, parameters.lightGreen,
                        parameters.lightBlue, parameters.lightIntensity),
                new Vec4(parameters.ssrIntensity, Math.max(8.0F,
                        parameters.volumetricSteps * AdaptiveVisualQuality.decorationScale()),
                        parameters.chainFade, 0.0F));
    }

    private static Projection project(Vec3 position, Vec3 camera,
                                      Vector3f right, Vector3f up, Vector3f forward,
                                      float tanHalfFov, float aspect) {
        if (position == null) return Projection.ZERO;
        float rx = (float) (position.x - camera.x);
        float ry = (float) (position.y - camera.y);
        float rz = (float) (position.z - camera.z);
        float viewX = rx * right.x + ry * right.y + rz * right.z;
        float viewY = rx * up.x + ry * up.y + rz * up.z;
        float viewZ = rx * forward.x + ry * forward.y + rz * forward.z;
        float distance = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        float ndcX = 0.0F;
        float ndcY = 0.0F;
        if (viewZ > 0.05F) {
            ndcX = Mth.clamp(viewX / (viewZ * aspect * tanHalfFov), -2.0F, 2.0F);
            ndcY = Mth.clamp(viewY / (viewZ * tanHalfFov), -2.0F, 2.0F);
        }
        return new Projection(ndcX, ndcY, viewX, viewY, viewZ, distance);
    }

    private static void configureFullscreenState() {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawFullscreenQuad() {
        finish(fullscreenBuffer);
        BufferBuilder builder = fullscreenBuffer;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            int gpu = PerfTracker.beginGpu(PerfTracker.Section.CINEMATIC);
            try {
                BufferUploader.drawWithShader(rendered);
            } finally {
                PerfTracker.endGpu(gpu);
            }
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        }
    }

    private static void finish(BufferBuilder builder) {
        if (builder == null || !builder.building()) {
            return;
        }
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            if (rendered != null) {
                rendered.release();
            }
        } catch (RuntimeException ignored) {
            if (builder == fullscreenBuffer) {
                fullscreenBuffer = new BufferBuilder(4_096);
            }
        }
    }

    private static boolean validateRuntime(RenderTarget mainTarget, FrameTarget frameTarget) {
        if (mainTarget == null || frameTarget == null || frameTarget.framebufferId < 0
                || frameTarget.width <= 0 || frameTarget.height <= 0
                || !GeminiKillEffectPostShaders.ready() || !checkCapabilities()) {
            return false;
        }
        int maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (frameTarget.width > maxTextureSize || frameTarget.height > maxTextureSize) {
            disableAfterFailure(new IllegalStateException(
                    "Active framebuffer exceeds the GPU maximum texture size"));
            return false;
        }
        return true;
    }

    private static boolean checkCapabilities() {
        if (capabilityChecked) return capabilityAvailable;
        capabilityChecked = true;
        try {
            var capabilities = GL.getCapabilities();
            boolean framebufferSupport = capabilities.OpenGL30
                    || capabilities.GL_ARB_framebuffer_object;
            boolean floatingPointSupport = capabilities.OpenGL30
                    || capabilities.GL_ARB_texture_float;
            capabilityAvailable = framebufferSupport && floatingPointSupport;
        } catch (IllegalStateException | LinkageError failure) {
            capabilityAvailable = false;
        }
        if (!capabilityAvailable && !failureLogged) {
            failureLogged = true;
            ExampleMod.LOGGER.warn(
                    "Funeral Nova post-processing is unavailable: HDR framebuffer support is missing");
        }
        return capabilityAvailable;
    }

    private static void refreshAfterShaderReload() {
        ShaderInstance aces = GeminiKillEffectPostShaders.shader(Pass.ACES);
        if (aces == lastAcesShader) return;
        lastAcesShader = aces;
        runtimeDisabled = false;
        failureLogged = false;
        historyValid = false;
    }

    private static void ensureTargets(int width, int height) {
        if (sceneCopy != null && sceneCopy.width == width && sceneCopy.height == height) return;
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GlStateManager._getActiveTexture();
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            releaseOnRenderThread();
            sceneCopy = createTarget(width, height);
            bloom = createTarget(width, height);
            ping = createTarget(width, height);
            pong = createTarget(width, height);
            history = createTarget(width, height);
            historyValid = false;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._bindTexture(boundTexture);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static TextureTarget createTarget(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        GlStateManager._bindTexture(target.getColorTextureId());
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        target.checkStatus();
        target.clear(Minecraft.ON_OSX);
        return target;
    }

    private static boolean targetsIndependent(FrameTarget output) {
        if (sceneCopy == null || bloom == null || ping == null || pong == null || history == null) {
            return false;
        }
        int[] textures = {
                sceneCopy.getColorTextureId(), bloom.getColorTextureId(),
                ping.getColorTextureId(), pong.getColorTextureId(), history.getColorTextureId()
        };
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] < 0 || textures[i] == output.colorTextureId) return false;
            for (int j = i + 1; j < textures.length; j++) {
                if (textures[i] == textures[j]) return false;
            }
        }
        return sceneCopy.frameBufferId != output.framebufferId
                && bloom.frameBufferId != output.framebufferId
                && ping.frameBufferId != output.framebufferId
                && pong.frameBufferId != output.framebufferId
                && history.frameBufferId != output.framebufferId;
    }

    private static void copyColor(RenderTarget source, RenderTarget destination) {
        blitColor(source.frameBufferId, GL30.GL_COLOR_ATTACHMENT0,
                0, 0, source.width, source.height, destination);
    }

    private static void copyColor(FrameTarget source, RenderTarget destination) {
        blitColor(source.framebufferId, source.readBuffer,
                source.viewportX, source.viewportY,
                source.viewportX + source.width, source.viewportY + source.height,
                destination);
    }

    private static void blitColor(int sourceFramebuffer, int sourceReadBuffer,
                                  int sourceX0, int sourceY0, int sourceX1, int sourceY1,
                                  RenderTarget destination) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        if (sourceReadBuffer != GL11.GL_NONE) {
            GL11.glReadBuffer(sourceReadBuffer);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                sourceX0, sourceY0, sourceX1, sourceY1,
                0, 0, destination.width, destination.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
        GL11.glReadBuffer(previousReadBuffer);
    }

    private static boolean historyDiscontinuous(FrameTarget frameTarget, Vec3 center,
                                                 int stage, float elapsedSeconds) {
        if (!historyValid) return true;
        if (stage != historyStage
                || elapsedSeconds + 0.05F < previousElapsed
                || elapsedSeconds - previousElapsed > 0.5F
                || frameTarget.framebufferId != historyFramebuffer
                || frameTarget.colorTextureId != historyColorTexture) {
            return true;
        }
        return historyCenter == null || historyCenter.distanceToSqr(center) > 0.25D;
    }

    private static void disableAfterFailure(Throwable failure) {
        runtimeDisabled = true;
        releaseOnRenderThread();
        if (!failureLogged) {
            failureLogged = true;
            ExampleMod.LOGGER.error(
                    "Funeral Nova post-processing failed and was disabled for this session; "
                            + "world-space visuals remain active", failure);
        }
    }

    private static void releaseOnRenderThread() {
        destroy(sceneCopy);
        destroy(bloom);
        destroy(ping);
        destroy(pong);
        destroy(history);
        sceneCopy = null;
        bloom = null;
        ping = null;
        pong = null;
        history = null;
        historyValid = false;
        historyStage = -1;
        previousElapsed = -1.0F;
        historyFramebuffer = -1;
        historyColorTexture = -1;
        historyCenter = null;
    }

    private static void releasePreservingFramebuffers() {
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GlStateManager._getActiveTexture();
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            releaseOnRenderThread();
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._bindTexture(boundTexture);
        }
    }

    private static void destroy(TextureTarget target) {
        if (target != null) target.destroyBuffers();
    }

    private static float smoothstep01(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static final class PostParameters {
        private final Vec3 center;
        private final Vec3 lightPosition;
        private final float bloom;
        private final float distortion;
        private final float godRay;
        private final float chromatic;
        private final float bloomRadius;
        private final int bhStage;
        private final float bhProgress;
        private final float bhIntensity;
        private final float lightRadius;
        private final float lightRed;
        private final float lightGreen;
        private final float lightBlue;
        private final float lightIntensity;
        private final float ssrIntensity;
        private final int volumetricSteps;
        private final float chainFade;

        private PostParameters(Vec3 center, Vec3 lightPosition,
                               float bloom, float distortion, float godRay,
                               float chromatic, float bloomRadius,
                               int bhStage, float bhProgress, float bhIntensity,
                               float lightRadius, float lightRed, float lightGreen,
                               float lightBlue, float lightIntensity,
                               float ssrIntensity, int volumetricSteps, float chainFade) {
            this.center = center;
            this.lightPosition = lightPosition;
            this.bloom = bloom;
            this.distortion = distortion;
            this.godRay = godRay;
            this.chromatic = chromatic;
            this.bloomRadius = bloomRadius;
            this.bhStage = bhStage;
            this.bhProgress = bhProgress;
            this.bhIntensity = bhIntensity;
            this.lightRadius = lightRadius;
            this.lightRed = lightRed;
            this.lightGreen = lightGreen;
            this.lightBlue = lightBlue;
            this.lightIntensity = lightIntensity;
            this.ssrIntensity = ssrIntensity;
            this.volumetricSteps = volumetricSteps;
            this.chainFade = chainFade;
        }

        private static PostParameters forFrame(Vec3 center, int stage, float progress,
                                               float elapsed, float intensity, float chainFade) {
            float mergeMult = Math.max(intensity, 0.01F);
            float globalFade = smoothstep01(elapsed / 0.9F);
            float bloom = BASE_BLOOM * mergeMult * 1.4F;
            float distortion = 0.0F;
            float godRay = 0.0F;
            float chromatic = 0.0F;
            float radius = 12.0F * bloom;
            int bhStage = 0;
            float bhProgress = 0.0F;
            float bhIntensity = 0.0F;

            if (stage >= 3 && stage <= 5) {
                float entry = stage == 3 ? smoothstep01(progress / 0.15F) : 1.0F;
                distortion = 0.85F * entry;
                godRay = 0.45F * entry;
                chromatic = 0.20F * entry;
                if (stage == 5) {
                    distortion *= 1.0F - progress * 0.9F;
                    godRay *= 1.0F - progress;
                    chromatic *= 1.0F - progress;
                }
                bhStage = stage;
                bhProgress = progress;
                bhIntensity = mergeMult;
            } else if (stage == 7) {
                float entry = smoothstep01(progress / 0.10F);
                bloom = Math.max(bloom, 3.2F * entry);
                chromatic = entry;
                godRay = 1.35F * entry;
                radius = 36.0F * entry;
                float delta = progress - 0.4F;
                float flash = (float) Math.exp(-(delta * delta)
                        / (progress < 0.4F ? 0.04F : 0.12F));
                bhStage = 7;
                bhProgress = progress;
                bhIntensity = Math.max(flash, 0.0F);
            } else if (stage == 8) {
                float entry = smoothstep01(progress / 0.05F);
                float pulse = 0.90F + 0.10F
                        * Math.abs((float) Math.sin(progress * Math.PI * 7.0D));
                bloom = Math.max(bloom, (3.2F + (3.0F - 3.2F) * entry) * pulse);
                godRay = 1.35F + (1.75F - 1.35F) * entry;
                chromatic = 1.0F + (0.72F - 1.0F) * entry;
                distortion = 0.88F * entry;
                radius = 36.0F + (40.0F - 36.0F) * entry;
                bhStage = 8;
                bhProgress = progress;
                bhIntensity = mergeMult;
            } else if (stage == 9) {
                float d1 = 1.0F - progress;
                float decay = d1 * d1;
                bloom = Math.max(BASE_BLOOM * mergeMult * 1.4F, 2.7F) * decay;
                distortion = 0.88F * decay;
                godRay = 0.35F + 1.40F * decay;
                chromatic = 0.72F * decay;
                radius = 40.0F * decay;
            } else if (stage == 10) {
                float fade = 1.0F - smoothstep01(progress);
                bloom = 0.0F;
                godRay = 0.35F * fade;
                radius = 0.0F;
            }

            float lightIntensity = 0.0F;
            float lightRadius = 28.0F;
            float lightRed = 1.0F;
            float lightGreen = 0.85F;
            float lightBlue = 0.55F;
            float ssr = 0.0F;
            int volumetricSteps = 16;
            if (stage >= 3 && stage <= 4) {
                float entry = stage == 3 ? smoothstep01(progress / 0.15F) : 1.0F;
                lightIntensity = 0.75F * entry;
                lightRadius = 24.0F;
                lightGreen = 0.55F;
                lightBlue = 0.15F;
            } else if (stage == 5) {
                float dieOut = 1.0F - smoothstep01((progress - 0.7F) / 0.3F);
                lightIntensity = (0.75F + progress * 1.05F) * dieOut;
                lightRadius = 24.0F + progress * 10.0F;
                lightGreen = 0.60F;
                lightBlue = 0.20F;
            } else if (stage == 7) {
                lightIntensity = 2.8F * bhIntensity;
                lightRadius = 42.0F;
                lightGreen = 0.97F;
                lightBlue = 0.90F;
                ssr = 0.9F;
            } else if (stage == 8) {
                float entry = smoothstep01(progress / 0.05F);
                float pulse = 0.90F + 0.10F
                        * Math.abs((float) Math.sin(progress * Math.PI * 7.0D));
                lightIntensity = 4.2F * (1.0F - progress * 0.35F) * entry * pulse;
                lightRadius = 54.0F;
                lightGreen = 0.95F;
                lightBlue = 0.85F;
                ssr = 0.9F * (1.0F - smoothstep01((progress - 0.35F) / 0.50F));
                volumetricSteps = 24;
            } else if (stage == 9) {
                float d1 = 1.0F - progress;
                float decay = d1 * d1;
                lightIntensity = 0.55F + 1.91F * decay;
                lightRadius = 28.0F + 26.0F * decay;
                lightGreen = 0.60F + 0.35F * decay;
                lightBlue = 0.28F + 0.57F * decay;
            } else if (stage == 10) {
                float fade = 1.0F - smoothstep01(progress);
                lightIntensity = 0.55F * fade;
                lightRadius = 17.0F + 11.0F * fade;
                lightGreen = 0.50F + 0.10F * fade;
                lightBlue = 0.22F + 0.06F * fade;
            }

            Vec3 lightPosition = lightIntensity > 0.01F ? center.add(0.0D, 1.0D, 0.0D) : null;
            return new PostParameters(
                    center, lightPosition,
                    bloom * globalFade, distortion * globalFade, godRay * globalFade,
                    chromatic * globalFade, radius * globalFade,
                    bhStage, bhProgress, bhIntensity,
                    lightRadius, lightRed, lightGreen, lightBlue, lightIntensity,
                    ssr, volumetricSteps, chainFade);
        }

        private boolean visible() {
            return bloom > 0.001F || distortion > 0.001F || godRay > 0.001F
                    || chromatic > 0.001F || bhStage > 0 || lightIntensity > 0.01F;
        }

        private boolean hasLight() {
            return lightPosition != null && lightIntensity > 0.01F;
        }
    }

    private record Vec4(float x, float y, float z, float w) {
    }

    private record UniformData(Vec4 params, Vec4 timePack, Vec4 center1, Vec4 center2,
                               Vec4 passParams, Vec4 bhRadiusUv, Vec4 cameraParams,
                               Vec4 lightViewPos, Vec4 lightColor, Vec4 miscParams) {
    }

    private record Projection(float ndcX, float ndcY, float viewX, float viewY,
                              float viewZ, float worldDistance) {
        private static final Projection ZERO = new Projection(0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F);
    }

    private record FrameTarget(int framebufferId, int viewportX, int viewportY,
                               int width, int height, int readBuffer,
                               int colorTextureId, int depthTextureId) {
        private static FrameTarget capture(RenderTarget mainTarget,
                                           RenderStateSnapshot snapshot) {
            int framebuffer = snapshot.drawFramebuffer;
            int width = snapshot.viewportWidth;
            int height = snapshot.viewportHeight;
            int viewportX = snapshot.viewportX;
            int viewportY = snapshot.viewportY;
            if (width <= 0 || height <= 0) {
                viewportX = 0;
                viewportY = 0;
                width = mainTarget.viewWidth;
                height = mainTarget.viewHeight;
            }

            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                int readBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
                if (readBuffer == GL11.GL_NONE) {
                    readBuffer = framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0;
                }

                int colorTexture = framebuffer == mainTarget.frameBufferId
                        ? mainTarget.getColorTextureId()
                        : queryAttachmentTexture(framebuffer, readBuffer);
                int depthTexture = framebuffer == mainTarget.frameBufferId
                        ? mainTarget.getDepthTextureId()
                        : queryDepthTexture(framebuffer);
                return new FrameTarget(framebuffer, viewportX, viewportY, width, height,
                        readBuffer, colorTexture, depthTexture);
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            }
        }

        private static int queryDepthTexture(int framebuffer) {
            int texture = queryAttachmentTexture(framebuffer, GL30.GL_DEPTH_ATTACHMENT);
            return texture >= 0
                    ? texture
                    : queryAttachmentTexture(framebuffer, GL30.GL_DEPTH_STENCIL_ATTACHMENT);
        }

        private static int queryAttachmentTexture(int framebuffer, int attachment) {
            if (framebuffer == 0
                    || (attachment < GL30.GL_COLOR_ATTACHMENT0
                    && attachment != GL30.GL_DEPTH_ATTACHMENT
                    && attachment != GL30.GL_DEPTH_STENCIL_ATTACHMENT)) {
                return -1;
            }
            int type = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE) return -1;
            return GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        }
    }

    private static final class RenderStateSnapshot {
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final int scissorX;
        private final int scissorY;
        private final int scissorWidth;
        private final int scissorHeight;
        private final boolean blend;
        private final boolean depth;
        private final boolean cull;
        private final boolean scissor;
        private final boolean depthWrite;
        private final int srcRgb;
        private final int dstRgb;
        private final int srcAlpha;
        private final int dstAlpha;
        private final int activeTexture;
        private final int[] shaderTextures;
        private final int[] boundTextures;
        private final int program;
        private final ShaderInstance shader;
        private final float[] shaderColor;

        private RenderStateSnapshot(int readFramebuffer, int drawFramebuffer,
                                    int[] viewport, int[] scissorBox,
                                    boolean blend, boolean depth, boolean cull, boolean scissor,
                                    boolean depthWrite, int srcRgb, int dstRgb,
                                    int srcAlpha, int dstAlpha, int activeTexture,
                                    int[] shaderTextures, int[] boundTextures, int program,
                                    ShaderInstance shader, float[] shaderColor) {
            this.readFramebuffer = readFramebuffer;
            this.drawFramebuffer = drawFramebuffer;
            this.viewportX = viewport[0];
            this.viewportY = viewport[1];
            this.viewportWidth = viewport[2];
            this.viewportHeight = viewport[3];
            this.scissorX = scissorBox[0];
            this.scissorY = scissorBox[1];
            this.scissorWidth = scissorBox[2];
            this.scissorHeight = scissorBox[3];
            this.blend = blend;
            this.depth = depth;
            this.cull = cull;
            this.scissor = scissor;
            this.depthWrite = depthWrite;
            this.srcRgb = srcRgb;
            this.dstRgb = dstRgb;
            this.srcAlpha = srcAlpha;
            this.dstAlpha = dstAlpha;
            this.activeTexture = activeTexture;
            this.shaderTextures = shaderTextures;
            this.boundTextures = boundTextures;
            this.program = program;
            this.shader = shader;
            this.shaderColor = shaderColor;
        }

        private static RenderStateSnapshot capture() {
            int[] viewport = new int[4];
            int[] scissor = new int[4];
            int[] textures = new int[12];
            int[] bindings = new int[12];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            int activeTexture = GlStateManager._getActiveTexture();
            for (int i = 0; i < textures.length; i++) {
                textures[i] = RenderSystem.getShaderTexture(i);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            GlStateManager._activeTexture(activeTexture);
            return new RenderStateSnapshot(
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    viewport, scissor,
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    activeTexture, textures, bindings,
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    RenderSystem.getShader(), RenderSystem.getShaderColor().clone());
        }

        private void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthWrite);
            if (scissor) {
                RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            } else {
                RenderSystem.disableScissor();
            }
            for (int i = 0; i < shaderTextures.length; i++) {
                RenderSystem.setShaderTexture(i, shaderTextures[i]);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(boundTextures[i]);
            }
            GlStateManager._activeTexture(activeTexture);
            ProgramManager.glUseProgram(program);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShader(() -> shader);
        }
    }
}

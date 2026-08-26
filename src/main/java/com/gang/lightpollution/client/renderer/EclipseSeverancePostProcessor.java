package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.EclipseSeveranceShaders;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Isolated full-screen finish for Eclipse Severance.
 *
 * <p>The fragment program is a Forge 1.20.1 port of Gemini's sweep post pass.
 * The scene is always copied to a separate color texture before the main
 * framebuffer is rebound for output, so the shader never samples from the
 * texture it is currently writing.</p>
 */
public final class EclipseSeverancePostProcessor {
    private static BufferBuilder fullscreenBuffer = new BufferBuilder(4_096);
    private static ShaderInstance lastShader;
    private static TextureTarget sceneCopyTarget;
    private static boolean enabled = !Boolean.getBoolean("dirge.eclipsePostDisabled");
    private static boolean capabilityChecked;
    private static boolean capabilityAvailable;
    private static boolean runtimeDisabled;
    private static boolean failureLogged;
    private static boolean staleBufferWarningLogged;

    private EclipseSeverancePostProcessor() {
    }

    /** Enables or disables only this spell's full-screen post-processing pass. */
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
     * Applies one full-screen pass to Minecraft's main render target.
     *
     * @return true when the pass rendered, or false when it was skipped and
     *         the caller should keep its non-post fallback.
     */
    public static boolean render(EffectParameters parameters) {
        if (!enabled || parameters == null || !parameters.isVisible()) {
            return false;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance postShader = EclipseSeveranceShaders.postShader();
        refreshAfterShaderReload(postShader);
        if (runtimeDisabled || !validateRuntime(mainTarget, postShader)) {
            return false;
        }

        RenderStateSnapshot state = RenderStateSnapshot.capture();
        try {
            ensureSceneCopyTarget(mainTarget.width, mainTarget.height);
            if (sceneCopyTarget == null
                    || sceneCopyTarget.getColorTextureId() == mainTarget.getColorTextureId()) {
                disableAfterFailure(new IllegalStateException(
                        "Eclipse post-processing could not allocate an independent scene texture"));
                return false;
            }

            copyColor(mainTarget, sceneCopyTarget);
            mainTarget.bindWrite(true);
            configureFullscreenState();
            configureShader(parameters, mainTarget, postShader);
            drawFullscreenQuad();
            return true;
        } catch (RuntimeException | LinkageError exception) {
            disableAfterFailure(exception);
            return false;
        } finally {
            state.restore();
        }
    }

    /** Preallocates or resizes the private scene copy without touching other render targets. */
    public static void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> resizeOnRenderThread(width, height));
            return;
        }
        resizeOnRenderThread(width, height);
    }

    /** Releases the private framebuffer. The registered ShaderInstance is owned by Minecraft. */
    public static void release() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(EclipseSeverancePostProcessor::releaseOnRenderThread);
            return;
        }
        releaseOnRenderThread();
    }

    private static void resizeOnRenderThread(int width, int height) {
        if (!enabled || runtimeDisabled || !checkCapabilities()) {
            return;
        }
        try {
            ensureSceneCopyTarget(width, height);
        } catch (RuntimeException | LinkageError exception) {
            disableAfterFailure(exception);
        }
    }

    private static void refreshAfterShaderReload(ShaderInstance postShader) {
        if (postShader == lastShader) {
            return;
        }
        lastShader = postShader;
        capabilityChecked = false;
        runtimeDisabled = false;
        failureLogged = false;
    }

    private static boolean validateRuntime(RenderTarget mainTarget, ShaderInstance postShader) {
        if (postShader == null || mainTarget == null || mainTarget.frameBufferId < 0
                || mainTarget.getColorTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }
        if (!checkCapabilities()) {
            return false;
        }
        int maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (mainTarget.width > maxTextureSize || mainTarget.height > maxTextureSize) {
            disableAfterFailure(new IllegalStateException(
                    "Main framebuffer exceeds the GPU maximum texture size"));
            return false;
        }
        return postShader.getUniform("Params") != null
                && postShader.getUniform("Strength") != null
                && postShader.getUniform("Tint") != null;
    }

    private static boolean checkCapabilities() {
        if (capabilityChecked) {
            return capabilityAvailable;
        }
        capabilityChecked = true;
        try {
            var capabilities = GL.getCapabilities();
            capabilityAvailable = capabilities.OpenGL30 || capabilities.GL_ARB_framebuffer_object;
        } catch (IllegalStateException | LinkageError exception) {
            capabilityAvailable = false;
        }
        if (!capabilityAvailable && !failureLogged) {
            failureLogged = true;
            ExampleMod.LOGGER.warn(
                    "Eclipse Severance post-processing is unavailable: framebuffer blitting is unsupported");
        }
        return capabilityAvailable;
    }

    private static void ensureSceneCopyTarget(int width, int height) {
        if (sceneCopyTarget != null
                && sceneCopyTarget.width == width
                && sceneCopyTarget.height == height) {
            return;
        }
        releaseOnRenderThread();
        sceneCopyTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        sceneCopyTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        sceneCopyTarget.setFilterMode(GL11.GL_LINEAR);
    }

    private static void copyColor(RenderTarget source, RenderTarget destination) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, source.width, source.height,
                0, 0, destination.width, destination.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void configureFullscreenState() {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void configureShader(EffectParameters parameters, RenderTarget mainTarget,
                                        ShaderInstance postShader) {
        postShader.setSampler("SceneSampler", sceneCopyTarget.getColorTextureId());
        EclipseSeveranceShaders.applyPostUniforms(postShader,
                new EclipseSeveranceShaders.PostUniformData(
                        new EclipseSeveranceShaders.Vec4(
                                mainTarget.width, mainTarget.height,
                                System.currentTimeMillis() / 1_000.0F, 0.0F),
                        new EclipseSeveranceShaders.Vec4(
                                parameters.distortion(), parameters.chromatic(),
                                parameters.flash(), parameters.vignette()),
                        new EclipseSeveranceShaders.Vec4(
                                parameters.tintRed(), parameters.tintGreen(),
                                parameters.tintBlue(), 0.0F)));
        RenderSystem.setShader(() -> postShader);
    }

    private static void drawFullscreenQuad() {
        // Do not borrow Minecraft's global Tesselator here.  If a shader
        // fails between begin/end, that shared builder can crash the next
        // world-render batch with "Already building!".
        finish(fullscreenBuffer);
        BufferBuilder builder = fullscreenBuffer;
        try {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        } catch (IllegalStateException staleState) {
            if (staleState.getMessage() == null
                    || !staleState.getMessage().contains("Already building")) {
                throw staleState;
            }

            fullscreenBuffer = new BufferBuilder(4_096);
            builder = fullscreenBuffer;
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            if (!staleBufferWarningLogged) {
                staleBufferWarningLogged = true;
                ExampleMod.LOGGER.warn(
                        "Recovered a stale Eclipse Severance post-process vertex buffer");
            }
        }
        builder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        try {
            BufferUploader.drawWithShader(builder.end());
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

    private static void disableAfterFailure(Throwable failure) {
        runtimeDisabled = true;
        releaseOnRenderThread();
        if (!failureLogged) {
            failureLogged = true;
            ExampleMod.LOGGER.error(
                    "Eclipse Severance post-processing failed and was disabled for this session; "
                            + "the world-space fallback remains active",
                    failure);
        }
    }

    private static void releaseOnRenderThread() {
        if (sceneCopyTarget != null) {
            sceneCopyTarget.destroyBuffers();
            sceneCopyTarget = null;
        }
    }

    /** Strengths follow Gemini's original sweep-post ranges. */
    public record EffectParameters(float distortion, float chromatic, float flash, float vignette,
                                   float tintRed, float tintGreen, float tintBlue) {
        public EffectParameters {
            distortion = Mth.clamp(distortion, 0.0F, 1.5F);
            chromatic = Mth.clamp(chromatic, 0.0F, 1.5F);
            flash = Mth.clamp(flash, 0.0F, 1.0F);
            vignette = Mth.clamp(vignette, 0.0F, 1.0F);
            tintRed = Mth.clamp(tintRed, 0.0F, 1.0F);
            tintGreen = Mth.clamp(tintGreen, 0.0F, 1.0F);
            tintBlue = Mth.clamp(tintBlue, 0.0F, 1.0F);
        }

        private boolean isVisible() {
            return distortion > 0.001F || chromatic > 0.001F
                    || flash > 0.001F || vignette > 0.001F;
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
        private final boolean blendEnabled;
        private final boolean depthEnabled;
        private final boolean cullEnabled;
        private final boolean scissorEnabled;
        private final boolean depthWrite;
        private final int blendSourceRgb;
        private final int blendDestinationRgb;
        private final int blendSourceAlpha;
        private final int blendDestinationAlpha;
        private final ShaderInstance previousShader;
        private final float[] shaderColor;

        private RenderStateSnapshot(int readFramebuffer, int drawFramebuffer,
                                    int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                                    int scissorX, int scissorY, int scissorWidth, int scissorHeight,
                                    boolean blendEnabled, boolean depthEnabled, boolean cullEnabled,
                                    boolean scissorEnabled, boolean depthWrite,
                                    int blendSourceRgb, int blendDestinationRgb,
                                    int blendSourceAlpha, int blendDestinationAlpha,
                                    ShaderInstance previousShader, float[] shaderColor) {
            this.readFramebuffer = readFramebuffer;
            this.drawFramebuffer = drawFramebuffer;
            this.viewportX = viewportX;
            this.viewportY = viewportY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.scissorX = scissorX;
            this.scissorY = scissorY;
            this.scissorWidth = scissorWidth;
            this.scissorHeight = scissorHeight;
            this.blendEnabled = blendEnabled;
            this.depthEnabled = depthEnabled;
            this.cullEnabled = cullEnabled;
            this.scissorEnabled = scissorEnabled;
            this.depthWrite = depthWrite;
            this.blendSourceRgb = blendSourceRgb;
            this.blendDestinationRgb = blendDestinationRgb;
            this.blendSourceAlpha = blendSourceAlpha;
            this.blendDestinationAlpha = blendDestinationAlpha;
            this.previousShader = previousShader;
            this.shaderColor = shaderColor;
        }

        private static RenderStateSnapshot capture() {
            int[] viewport = new int[4];
            int[] scissor = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            return new RenderStateSnapshot(
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    viewport[0], viewport[1], viewport[2], viewport[3],
                    scissor[0], scissor[1], scissor[2], scissor[3],
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    RenderSystem.getShader(),
                    RenderSystem.getShaderColor().clone());
        }

        private void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            RenderSystem.blendFuncSeparate(
                    blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(depthWrite);
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (scissorEnabled) {
                RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            } else {
                RenderSystem.disableScissor();
            }
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (previousShader != null) {
                RenderSystem.setShader(() -> previousShader);
            }
        }
    }
}

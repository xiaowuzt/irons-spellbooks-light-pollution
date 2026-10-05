package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot dump of the framebuffer state at every {@link RenderLevelStageEvent}
 * stage, so the mod's assumptions about the pipeline can be checked rather than
 * guessed at.
 *
 * <p>Three questions decide whether this mod can work under a shader pack, and none
 * of them can be answered from documentation:</p>
 * <ol>
 *   <li><b>How many colour attachments are bound while we draw?</b> Vanilla binds
 *   one. A pack's gbuffer binds several, and a fragment shader that writes only
 *   {@code fragColor} leaves the rest holding garbage that the pack's deferred pass
 *   then reads as normals and specular.</li>
 *   <li><b>Is the bound framebuffer still Minecraft's main target?</b> Under Iris the
 *   level is rendered into Iris's own targets, so anything that reaches for
 *   {@code getMainRenderTarget()} may be addressing the wrong buffer entirely.</li>
 *   <li><b>At {@code AFTER_LEVEL}, does the main target's depth attachment hold this
 *   frame's depth?</b> Iris copies depth back at the end of level rendering; if that
 *   happens after this stage, the screen-space passes are sampling last frame's
 *   depth. The attachment's internal format matters too — a depth blit between
 *   mismatched formats fails silently.</li>
 * </ol>
 *
 * <p>Armed by a command and disarmed after one frame, so it costs nothing in normal
 * play and cannot spam the log.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ShaderPipelineProbe {
    private static volatile boolean armed;
    private static final List<String> rows = new ArrayList<>();
    /** Wall clock of the first stage seen this launch, for the automatic probe. */
    private static long firstStageNanos;
    private static boolean autoProbeDone;
    /** Pack state at the last probe, so a toggle can re-trigger one. */
    private static boolean lastPackActive;
    /** How long to let the world settle before probing, so sizes are final. */
    private static final long AUTO_PROBE_DELAY_NANOS = 4_000_000_000L;
    /** Where to sample depth: centre plus the four quadrants. */
    private static final float[][] SAMPLE_FRACTIONS = {
            {0.5F, 0.5F}, {0.25F, 0.25F}, {0.75F, 0.25F},
            {0.25F, 0.75F}, {0.75F, 0.75F},
    };

    private ShaderPipelineProbe() {
    }

    /** Captures the next frame. */
    public static void arm() {
        rows.clear();
        armed = true;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();

        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (Boolean.getBoolean("lightpollution.autoPipelineProbe")) maybeAutoArm();
        }

        if (!armed) {
            return;
        }
        rows.add(sample(stage.toString()));
        // AFTER_LEVEL is the last stage Forge fires, so the frame is done.
        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            armed = false;
            lastPackActive = ShaderPackState.packActive();
            report();
        }
    }

    /**
     * Opt-in with -Dlightpollution.autoPipelineProbe=true: probe on launch and pack changes.
     *
     * <p>The re-probe on toggle is the point. Packs are selected from the in-game
     * menu long after the world has loaded, so a launch-time probe only ever
     * captures the unshaded pipeline — which is the one case that needed no
     * measuring. Watching for the transition means the interesting sample is taken
     * without anyone having to remember to ask for it.</p>
     */
    private static void maybeAutoArm() {
        boolean packActive = ShaderPackState.packActive();
        if (autoProbeDone && packActive != lastPackActive) {
            ExampleMod.LOGGER.info("Shader pack turned {} -- re-probing the pipeline",
                    packActive ? "ON" : "OFF");
            arm();
            return;
        }
        if (autoProbeDone) {
            return;
        }
        long now = System.nanoTime();
        if (firstStageNanos == 0L) {
            firstStageNanos = now;
        } else if (now - firstStageNanos > AUTO_PROBE_DELAY_NANOS) {
            autoProbeDone = true;
            arm();
        }
    }

    private static String sample(String stage) {
        int boundDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int mainTarget = Minecraft.getInstance().getMainRenderTarget().frameBufferId;

        return String.format(
                "%-22s boundFbo=%-4d mainFbo=%-4d %s  colourAttachments=%s  depth=%s  %s  %s",
                stage, boundDraw, mainTarget,
                boundDraw == mainTarget ? "SAME" : "DIFFERENT",
                drawBuffers(), depthAttachment(), content(boundDraw), projection());
    }

    /**
     * Whether {@code ProjMat} is still a world projection at this stage.
     *
     * <p>Every world renderer here pushes the event's pose into {@code ModelViewMat}
     * and relies on {@code ProjMat} already holding the level's perspective matrix. A
     * shader pack's composite and final passes draw fullscreen quads through an
     * orthographic matrix, and if one is left in place afterwards then world geometry
     * submitted later is transformed by it and lands nowhere — which presents exactly
     * as "the draw happened and not one pixel changed". A perspective matrix has -1 in
     * m23; an orthographic one has 0, which is the whole test.</p>
     */
    private static String projection() {
        org.joml.Matrix4f matrix = com.mojang.blaze3d.systems.RenderSystem
                .getProjectionMatrix();
        boolean perspective = Math.abs(matrix.m23()) > 1.0E-6F;
        return String.format("proj=%s m00=%.4f m11=%.4f m23=%.4f",
                perspective ? "PERSPECTIVE" : "ORTHOGRAPHIC",
                matrix.m00(), matrix.m11(), matrix.m23());
    }

    /**
     * What is actually <em>in</em> the bound buffer right now.
     *
     * <p>The attachment layout turned out to look identical to vanilla under a pack,
     * which rules out the obvious explanations for effects going missing and leaves
     * the question of ordering: is the scene already in this buffer when our stage
     * runs, and does anything overwrite it afterwards? Depth that reads back as a
     * uniform 1.0 means the buffer has been cleared and the scene lives elsewhere;
     * depth with variation means we are drawing onto the real thing.</p>
     *
     * <p>A read-back stalls the pipeline, which is why this only ever runs on a
     * single probed frame.</p>
     */
    private static String content(int fbo) {
        var window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        if (width < 4 || height < 4) {
            return "content=?";
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        FloatBuffer depth = BufferUtils.createFloatBuffer(1);
        ByteBuffer colour = BufferUtils.createByteBuffer(4);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < SAMPLE_FRACTIONS.length; i++) {
                int x = (int) (width * SAMPLE_FRACTIONS[i][0]);
                int y = (int) (height * SAMPLE_FRACTIONS[i][1]);
                depth.clear();
                GL11.glReadPixels(x, y, 1, 1,
                        GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
                float value = depth.get(0);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            GL11.glReadPixels(width / 2, height / 2, 1, 1,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, colour);
            return String.format(
                    "depthRange=[%.4f..%.4f]%s centreRGB=(%d,%d,%d)",
                    min, max, max - min < 1.0E-6F ? " FLAT" : "",
                    colour.get(0) & 0xFF, colour.get(1) & 0xFF, colour.get(2) & 0xFF);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
    }

    /**
     * Which colour attachments the bound framebuffer is currently writing to.
     *
     * <p>This is the measurement that decides whether the gbuffer-pollution problem
     * is real. More than one entry means every fragment we draw is leaving undefined
     * data in the attachments we do not write.</p>
     */
    private static String drawBuffers() {
        int max = Math.min(8, GL11.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS));
        List<String> active = new ArrayList<>();
        for (int index = 0; index < max; index++) {
            int target = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index);
            if (target == GL11.GL_NONE) {
                continue;
            }
            active.add(target >= GL30.GL_COLOR_ATTACHMENT0
                    && target <= GL30.GL_COLOR_ATTACHMENT0 + 15
                    ? "COLOR" + (target - GL30.GL_COLOR_ATTACHMENT0)
                    : "0x" + Integer.toHexString(target));
        }
        return active.isEmpty() ? "[none]" : active.size() + active.toString();
    }

    /** Type, object name and internal format of the bound framebuffer's depth. */
    private static String depthAttachment() {
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL11.GL_NONE) {
            return "none";
        }
        int name = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (type == GL11.GL_TEXTURE) {
            return "texture id=" + name + " format=" + textureFormat(name);
        }
        if (type == GL30.GL_RENDERBUFFER) {
            return "renderbuffer id=" + name;
        }
        return "type=0x" + Integer.toHexString(type) + " id=" + name;
    }

    /**
     * The internal format of a depth texture, restoring the previous binding.
     *
     * <p>Leaving a texture bound here would corrupt whatever the renderer does
     * next, and this runs mid-frame.</p>
     */
    private static String textureFormat(int texture) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            int format = GL11.glGetTexLevelParameteri(
                    GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            return switch (format) {
                case GL11.GL_DEPTH_COMPONENT -> "DEPTH_COMPONENT";
                case GL30.GL_DEPTH_COMPONENT16 -> "DEPTH_COMPONENT16";
                case GL30.GL_DEPTH_COMPONENT24 -> "DEPTH_COMPONENT24";
                case GL30.GL_DEPTH_COMPONENT32F -> "DEPTH_COMPONENT32F";
                case GL30.GL_DEPTH24_STENCIL8 -> "DEPTH24_STENCIL8";
                case GL30.GL_DEPTH32F_STENCIL8 -> "DEPTH32F_STENCIL8";
                default -> "0x" + Integer.toHexString(format);
            };
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    private static void report() {
        var target = Minecraft.getInstance().getMainRenderTarget();
        ExampleMod.LOGGER.info("=== render pipeline probe ===");
        ExampleMod.LOGGER.info("  shader pack: {}", ShaderPackState.describe());
        ExampleMod.LOGGER.info("  shadow pass right now: {}",
                ShaderPackState.renderingShadowPass());
        ExampleMod.LOGGER.info("  main target: fbo={} size={}x{} depthTex={}",
                target.frameBufferId, target.width, target.height,
                target.getDepthTextureId());
        ExampleMod.LOGGER.info("  GL_MAX_DRAW_BUFFERS={} GL_MAX_COLOR_ATTACHMENTS={}",
                GL11.glGetInteger(GL30.GL_MAX_DRAW_BUFFERS),
                GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS));
        for (String row : rows) {
            ExampleMod.LOGGER.info("  {}", row);
        }
        ExampleMod.LOGGER.info("=== end probe ===");
    }
}

package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Captures the GL state these renderers disturb, so it can be put back afterwards.
 *
 * <p>This replaces fourteen copy-pasted {@code private record GlState} blocks, one per world
 * renderer. They had already drifted: seven of them reset the shader colour on the way out and
 * seven did not, which is a real leak — every one of those renderers sets a shader colour, and
 * {@link EffectCore} sets its alpha as high as 2.0 to pick a layer. Whatever ran next inherited it.
 * Rather than pick one of the two versions, this restores the shader colour that was actually there
 * before.</p>
 *
 * <p>Deliberately narrower than ArcaneVortex's {@code RenderStateSnapshot}, which this is otherwise
 * modelled on. That one also carries depth func, colour mask, polygon mode, and the modelview and
 * projection matrices. Nothing in this mod calls {@code colorMask}, {@code polygonMode},
 * {@code depthFunc} or {@code setProjectionMatrix} — restoring them would be machinery that can
 * never fire, and a reader would reasonably assume it was there for a reason.</p>
 *
 * <p>The modelview stack is left alone for a sharper reason. The original's {@code restore()}
 * <em>pushes</em> onto that stack and a separate {@code cleanup()} pops it, so the two must be
 * paired or the stack drifts every frame. The renderers here already bracket their own
 * {@code pushPose}/{@code popPose} around the draw, so the modelview is already handled and adding
 * a second mechanism would only create a way to get it wrong.</p>
 */
public record GlStateGuard(boolean blend, boolean depthTest, boolean cull, boolean depthWrite,
                           int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                           float[] shaderColour) {
    public static GlStateGuard capture() {
        float[] live = RenderSystem.getShaderColor();
        return new GlStateGuard(
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                // Copied, not aliased: RenderSystem hands back the live array, so keeping the
                // reference would record whatever the value ends up as rather than what it was.
                new float[] {live[0], live[1], live[2], live[3]});
    }

    public void restore() {
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (blend) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (cull) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.depthMask(depthWrite);
        RenderSystem.setShaderColor(shaderColour[0], shaderColour[1],
                shaderColour[2], shaderColour[3]);
    }
}

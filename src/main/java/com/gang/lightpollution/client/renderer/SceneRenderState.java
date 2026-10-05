package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;

/** The additional state touched by a scene/depth sampling pass, beyond ordinary world geometry. */
final class SceneRenderState implements AutoCloseable {
    private final GlStateGuard geometry = GlStateGuard.capture();
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    // TextureTarget.createBuffers/clear changes these even when we only need its textures.
    private final float[] clearColour = new float[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final int active = GlStateManager._getActiveTexture();
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final ShaderInstance shader = RenderSystem.getShader();
    private final int[] textures, bindings;

    SceneRenderState() { this(2); }

    SceneRenderState(int textureUnits) {
        textures = new int[textureUnits];
        bindings = new int[textureUnits];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColour);
        for (int i = 0; i < textures.length; i++) {
            textures[i] = RenderSystem.getShaderTexture(i);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GlStateManager._activeTexture(active);
    }

    @Override
    public void close() {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (scissor) GlStateManager._enableScissorTest();
        else GlStateManager._disableScissorTest();
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.setShaderTexture(i, textures[i]);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(bindings[i]);
        }
        GlStateManager._activeTexture(active);
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        geometry.restore();
        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        GlStateManager._clearColor(clearColour[0], clearColour[1], clearColour[2], clearColour[3]);
        GlStateManager._clearDepth(clearDepth);
    }
}

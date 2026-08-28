package com.gang.lightpollution.text.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the floating texts.
 *
 * <p>Ported from ArcaneVortex's {@code WorldTextRenderer}, credited in {@code CREDITS.txt}.</p>
 *
 * <p>The scale is negated on x and y, which is not a mistake: text is laid out with y running down
 * the screen, so drawing it in world space needs both axes flipped or it comes out upside down and
 * mirrored.</p>
 */
public final class WorldTextRenderer {
    /**
     * World units per pixel of text.
     *
     * <p>The same figure vanilla uses for nameplates, so a scale of 1 here matches a name tag.</p>
     */
    private static final float PIXEL = 0.025F;
    /** Font line height, for centring the background box. */
    private static final float LINE_HEIGHT = 9.0F;
    /** Padding around the background box, in text pixels. */
    private static final float BOX_PADDING = 2.0F;

    private WorldTextRenderer() {
    }

    public static void renderAll(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                 float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (WorldTextPopup popup : WorldTextManager.activePopups()) {
            render(poseStack, buffers, popup, camera, partialTick);
        }
    }

    private static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                               WorldTextPopup popup, Vec3 camera, float partialTick) {
        float alpha = popup.interpolatedAlpha(partialTick);
        if (alpha <= 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Vec3 at = popup.getPosition().add(popup.interpolatedOffset(partialTick)).subtract(camera);

        poseStack.pushPose();
        try {
            poseStack.translate(at.x, at.y, at.z);
            if (popup.getConfig().isBillboard()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(
                        -minecraft.gameRenderer.getMainCamera().getYRot()));
                poseStack.mulPose(Axis.XP.rotationDegrees(
                        minecraft.gameRenderer.getMainCamera().getXRot()));
            }
            float turn = popup.interpolatedRotation(partialTick);
            if (turn != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(turn));
            }
            float scale = popup.interpolatedScale(partialTick) * PIXEL;
            poseStack.scale(-scale, -scale, scale);

            Component text = popup.getText();
            float x = -font.width(text) / 2.0F;
            int packedAlpha = Mth.clamp((int) (alpha * 255.0F), 0, 255);
            Matrix4f pose = poseStack.last().pose();

            if (popup.getConfig().hasBackground()) {
                int background = popup.getConfig().getBackgroundColour();
                int backgroundAlpha = Mth.clamp(
                        (int) (((background >>> 24) & 0xFF) * alpha), 0, 255);
                box(pose, buffers, x - BOX_PADDING, -BOX_PADDING,
                        x + font.width(text) + BOX_PADDING, LINE_HEIGHT + BOX_PADDING,
                        (backgroundAlpha << 24) | (background & 0xFFFFFF));
            }

            font.drawInBatch(text, x, 0.0F, 0xFFFFFF | (packedAlpha << 24),
                    popup.getConfig().hasShadow(), pose, buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        } finally {
            poseStack.popPose();
        }
    }

    private static void box(Matrix4f pose, MultiBufferSource.BufferSource buffers,
                            float x1, float y1, float x2, float y2, int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        consumer.vertex(pose, x1, y2, 0.0F).color(r, g, b, a)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(pose, x2, y2, 0.0F).color(r, g, b, a)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(pose, x2, y1, 0.0F).color(r, g, b, a)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(pose, x1, y1, 0.0F).color(r, g, b, a)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}

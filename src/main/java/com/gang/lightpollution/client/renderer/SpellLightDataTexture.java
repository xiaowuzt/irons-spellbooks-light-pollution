package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * VanillaDI-compatible light metadata texture.
 *
 * <p>The texture is a linear RGBA8 texel stream. The first 36 texels are the
 * projection/view matrices, the rolling-volume marker, and the light count;
 * every light then occupies the same eleven texels as the upstream shader
 * pack. The width is fixed to the largest practical row and the shader maps
 * the linear index to (x, y), so the record count is limited only by the
 * driver's texture capacity.</p>
 */
final class SpellLightDataTexture {
    private static final int HEADER_TEXELS = 36;
    private static final int TEXELS_PER_LIGHT = 11;
    private static final float MATRIX_SCALE = 40000.0F;
    private static final float POSITION_SCALE = 1024.0F;
    private static final float DIRECTION_SCALE = 40000.0F;
    private static final int LIGHT_TYPE_SPHERE = 1;

    private TextureTarget target;
    private TextureTarget historyTarget;
    private NativeImage image;
    private int width;
    private int height;

    int textureId() {
        return target == null ? -1 : target.getColorTextureId();
    }

    /** Previous frame's encoded metadata, matching VanillaDI's previousData target. */
    int historyTextureId() {
        return historyTarget == null ? -1 : historyTarget.getColorTextureId();
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean hasHistory() {
        return historyTarget != null;
    }

    /** Publishes the current metadata texture as the next frame's previousData. */
    void copyToHistory() {
        if (target == null || historyTarget == null) {
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                historyTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, width, height,
                0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /** Uploads the complete VanillaDI metadata stream and returns its count. */
    int upload(List<SpellLightEmitter.Light> lights, Vec3 cameraPosition,
               Matrix4f projection, Matrix4f worldToView,
               Vec3 markerPosition) {
        if (lights == null || lights.isEmpty()) {
            return 0;
        }

        int maxTextureSize = Math.max(256, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE));
        int rowWidth = Math.min(2048, maxTextureSize);
        long requiredTexels = HEADER_TEXELS + (long) lights.size() * TEXELS_PER_LIGHT;
        if (requiredTexels > (long) rowWidth * maxTextureSize) {
            throw new IllegalArgumentException("Too many spell lights for the GPU metadata texture");
        }
        int requiredHeight = Math.max(1,
                (int) ((requiredTexels + rowWidth - 1L) / rowWidth));
        ensureTarget(rowWidth, requiredHeight);

        float[] projectionValues = new float[16];
        float[] viewValues = new float[16];
        projection.get(projectionValues);
        worldToView.get(viewValues);
        for (int i = 0; i < 16; i++) {
            writeFloat(i, projectionValues[i], MATRIX_SCALE);
            writeFloat(16 + i, viewValues[i], MATRIX_SCALE);
        }

        Vec3 marker = markerPosition == null ? cameraPosition : markerPosition;
        writeFloat1024(32, marker == null ? 0.0F : (float) marker.x);
        writeFloat1024(33, marker == null ? 0.0F : (float) marker.y);
        writeFloat1024(34, marker == null ? 0.0F : (float) marker.z);
        writeInt(35, lights.size());

        for (int index = 0; index < lights.size(); index++) {
            SpellLightEmitter.Light light = lights.get(index);
            int base = HEADER_TEXELS + index * TEXELS_PER_LIGHT;

            float dx = (float) (light.position().x - cameraPosition.x);
            float dy = (float) (light.position().y - cameraPosition.y);
            float dz = (float) (light.position().z - cameraPosition.z);

            Vector3f eye = worldToView.transformDirection(new Vector3f(dx, dy, dz));
            // VanillaDI's lighting view points forward along +Z.
            writeFloat1024(base, eye.x);
            writeFloat1024(base + 1, eye.y);
            writeFloat1024(base + 2, -eye.z);

            // Canonical frame expected by VanillaDI's light record. Sphere
            // emitters use the fixed 0.5-unit source radius in the shader;
            // the spell gameplay radius is not part of this record.
            writeFloat(base + 3, 1.0F, DIRECTION_SCALE);
            writeFloat(base + 4, 0.0F, DIRECTION_SCALE);
            writeFloat(base + 5, 0.0F, DIRECTION_SCALE);
            writeFloat(base + 6, 0.0F, DIRECTION_SCALE);
            writeFloat(base + 7, 1.0F, DIRECTION_SCALE);
            writeFloat(base + 8, 0.0F, DIRECTION_SCALE);
            writeRgb(base + 9, light.red(), light.green(), light.blue());
            // Preserve the emitter's gameplay influence radius separately
            // from the author's fixed 0.5-unit spherical source.
            writeRgb(base + 10, light.intensity() / 100.0F,
                    LIGHT_TYPE_SPHERE / 255.0F,
                    Math.max(0.0F, Math.min(1.0F, light.radius() / 64.0F)));
        }

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(textureId());
        image.upload(0, 0, 0, false);
        return lights.size();
    }

    void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        if (historyTarget != null) {
            historyTarget.destroyBuffers();
            historyTarget = null;
        }
        if (image != null) {
            image.close();
            image = null;
        }
        width = 0;
        height = 0;
    }

    private void ensureTarget(int requiredWidth, int requiredHeight) {
        if (target != null && width == requiredWidth && height == requiredHeight) {
            return;
        }
        if (target != null) {
            target.destroyBuffers();
        }
        if (historyTarget != null) {
            historyTarget.destroyBuffers();
        }
        if (image != null) {
            image.close();
        }
        target = new TextureTarget(requiredWidth, requiredHeight, false, Minecraft.ON_OSX);
        historyTarget = new TextureTarget(requiredWidth, requiredHeight, false, Minecraft.ON_OSX);
        image = new NativeImage(NativeImage.Format.RGBA, requiredWidth, requiredHeight, true);
        width = requiredWidth;
        height = requiredHeight;
        GlStateManager._bindTexture(target.getColorTextureId());
        target.setFilterMode(GL11.GL_NEAREST);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
        GlStateManager._bindTexture(historyTarget.getColorTextureId());
        historyTarget.setFilterMode(GL11.GL_NEAREST);
        historyTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        historyTarget.clear(Minecraft.ON_OSX);
    }

    private void writeFloat(int index, float value, float scale) {
        writeInt(index, encodeScaled(value, scale));
    }

    private void writeFloat1024(int index, float value) {
        writeInt(index, encodeScaled(value, POSITION_SCALE));
    }

    private void writeInt(int index, int value) {
        long magnitude = Math.abs((long) value);
        setTexel(index, (int) (magnitude & 0xFFL),
                (int) ((magnitude >>> 8) & 0xFFL),
                (int) ((magnitude >>> 16) & 0x7FL)
                        | (value < 0 ? 0x80 : 0), 0xFF);
    }

    private void writeRgb(int index, float red, float green, float blue) {
        setTexel(index, quantize(red), quantize(green), quantize(blue), 0xFF);
    }

    private void setTexel(int index, int red, int green, int blue, int alpha) {
        int x = index % width;
        int y = index / width;
        // NativeImage.Format.RGBA is little-endian in memory: R is the low
        // byte, followed by G, B, and A.
        image.setPixelRGBA(x, y,
                (alpha << 24) | (blue << 16) | (green << 8) | red);
    }

    private static int encodeScaled(float value, float scale) {
        double scaled = Math.floor(value * scale);
        if (scaled >= 8_388_607.0D) return 8_388_607;
        if (scaled <= -8_388_607.0D) return -8_388_607;
        return (int) scaled;
    }

    private static int quantize(float value) {
        return Math.max(0, Math.min(255,
                Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F)));
    }
}

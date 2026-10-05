package com.gang.lightpollution.client.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Conservative framebuffer bounds, in OpenGL bottom-left pixel coordinates. */
public record ScreenEffectBounds(int x, int y, int width, int height) {
    public boolean empty() {
        return width <= 0 || height <= 0;
    }

    public static ScreenEffectBounds full(int width, int height) {
        return new ScreenEffectBounds(0, 0, width, height);
    }

    /** The projected enclosing cube contains the sphere; near-plane crossings use the full frame. */
    public static ScreenEffectBounds sphere(Matrix4f projection, Vector3f eye,
                                             float radius, int width, int height) {
        if (width <= 0 || height <= 0) return new ScreenEffectBounds(0, 0, 0, 0);
        if (!Float.isFinite(radius) || !eye.isFinite()) return full(width, height);
        if (radius <= 0.0F || eye.z - radius >= 0.0F) return new ScreenEffectBounds(0, 0, 0, 0);
        if (eye.z + radius >= -0.05F) return full(width, height);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Vector4f clip = new Vector4f();
        for (int corner = 0; corner < 8; corner++) {
            clip.set(eye.x + ((corner & 1) == 0 ? -radius : radius),
                    eye.y + ((corner & 2) == 0 ? -radius : radius),
                    eye.z + ((corner & 4) == 0 ? -radius : radius), 1.0F);
            projection.transform(clip);
            if (!clip.isFinite() || clip.w <= 0.000001F) return full(width, height);
            float x = clip.x / clip.w * 0.5F + 0.5F;
            float y = clip.y / clip.w * 0.5F + 0.5F;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return uv(minX, minY, maxX, maxY, width, height);
    }

    public static ScreenEffectBounds uv(float minX, float minY, float maxX, float maxY,
                                         int width, int height) {
        if (!Float.isFinite(minX + minY + maxX + maxY)) return full(width, height);
        int left = (int) Math.max(0, Math.min(width, Math.floor(minX * width) - 2));
        int bottom = (int) Math.max(0, Math.min(height, Math.floor(minY * height) - 2));
        int right = (int) Math.max(0, Math.min(width, Math.ceil(maxX * width) + 2));
        int top = (int) Math.max(0, Math.min(height, Math.ceil(maxY * height) + 2));
        return new ScreenEffectBounds(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }
}

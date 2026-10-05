package com.gang.lightpollution.client.renderer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/** Tightly packed CPU-backed 2D transfers, including allocation with null pixels.
 * NativeImage/atlas uploads leave UNPACK row/skip state behind. Reusing it can read
 * past our direct buffer (native driver crash), or upload unrelated data (flicker).
 * A bound pixel-unpack buffer also changes pointers, including null, into offsets.
 * Isolate those inputs without changing texture bindings or the caller's state. */
final class TextureUpload {
    private TextureUpload() {}

    static void image2D(int target, int level, int internalFormat, int width, int height,
                        int border, int format, int type, ByteBuffer pixels) {
        try (UnpackState ignored = new UnpackState()) {
            GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
        }
    }

    static void image2D(int target, int level, int internalFormat, int width, int height,
                        int border, int format, int type, FloatBuffer pixels) {
        try (UnpackState ignored = new UnpackState()) {
            GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
        }
    }

    static void subImage2D(int target, int level, int x, int y, int width, int height,
                           int format, int type, ByteBuffer pixels) {
        try (UnpackState ignored = new UnpackState()) {
            GL11.glTexSubImage2D(target, level, x, y, width, height, format, type, pixels);
        }
    }

    static void subImage2D(int target, int level, int x, int y, int width, int height,
                           int format, int type, FloatBuffer pixels) {
        try (UnpackState ignored = new UnpackState()) {
            GL11.glTexSubImage2D(target, level, x, y, width, height, format, type, pixels);
        }
    }

    /** Only the state that affects these non-bitmap 2D transfers is touched. */
    private static final class UnpackState implements AutoCloseable {
        private final int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        private final int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        private final int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        private final int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        private final int swapBytes = GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES);
        private final int buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);

        private UnpackState() {
            if (buffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
        }

        @Override
        public void close() {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, swapBytes);
            if (buffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    }
}

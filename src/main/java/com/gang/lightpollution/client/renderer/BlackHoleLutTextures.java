package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** R2 resources generated offline, not dummy images. Call inside SceneRenderState on render thread. */
final class BlackHoleLutTextures {
    private int deflection, inverse;
    int deflection() throws IOException { ensure(); return deflection; }
    int inverse() throws IOException { ensure(); return inverse; }
    private void ensure() throws IOException {
        if (deflection != 0 && inverse != 0) return;
        try { deflection = load("deflection", 512, 512); inverse = load("inverse-radius", 64, 32); }
        catch (IOException | RuntimeException failure) { close(); throw failure; }
    }
    private static int load(String name, int width, int height) throws IOException {
        var id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/effect/black_hole/" + name + ".lut");
        byte[] bytes;
        try (var stream = Minecraft.getInstance().getResourceManager().open(id)) { bytes = stream.readAllBytes(); }
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length != 12 + width * height * 8 || data.getInt() != 0x314C4842 || data.getInt() != width || data.getInt() != height)
            throw new IOException("Invalid R2 lookup table: " + id);
        // Keep the existing four-channel HDR storage; transfer safety is handled
        // by TextureUpload, not by changing the source data format.
        var floats = BufferUtils.createFloatBuffer(width * height * 4);
        while (data.hasRemaining()) {
            float x = data.getFloat();
            float y = data.getFloat();
            if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IOException("Nonfinite R2 LUT");
            floats.put(x).put(y).put(0.0F).put(0.0F);
        }
        floats.flip();
        int texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        // Allocation and transfer both require isolated pixel-unpack state.
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        TextureUpload.subImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_FLOAT, floats);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }
    void close() {
        if (deflection != 0) GlStateManager._deleteTexture(deflection);
        if (inverse != 0) GlStateManager._deleteTexture(inverse);
        deflection = inverse = 0;
    }
}
